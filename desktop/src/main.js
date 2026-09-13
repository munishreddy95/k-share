const http = require("http");
const net = require("net");
const tls = require("tls");
const os = require("os");
const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");
const crypto = require("crypto");
const { EventEmitter } = require("events");
const { spawn } = require("child_process");

const NEARBY_TRANSFER_PORT = 8988;
const NEARBY_QR_SEND_PORT = 8990;
const TRANSFER_BUFFER_SIZE = 64 * 1024;
const NEARBY_CONNECTED_EVENT = "FLUX_NEARBY_CONNECTED_V1";
const NEARBY_TRANSFER_MAGIC = "FLUX_NEARBY_V4";
const NEARBY_QUEUE_MAGIC = "FLUX_NEARBY_QUEUE_V1";
const NEARBY_PRESENCE_MAGIC = "FLUX_NEARBY_PRESENCE_V1";
const NEARBY_CANCEL_MAGIC = "FLUX_NEARBY_CANCEL_V1";
const NEARBY_FILE_CANCEL_MAGIC = "FLUX_NEARBY_FILE_CANCEL_V1";
const NEARBY_ACK_MAGIC = "FLUX_NEARBY_ACK_V1";
const NEARBY_SESSION_MISMATCH_MAGIC = "FLUX_NEARBY_SESSION_MISMATCH_V1";
const NEARBY_SESSION_ERROR = "Different Nearby session. Refresh QR and scan again.";
const NEARBY_CHUNK_DONE = -1;
const NEARBY_CHUNK_CANCELLED = -2;
const NEARBY_RECEIVER_CANCEL_OFFSET = -1n;
const PUBLIC_RECEIVE_DIR = "Flux File Share";

let mainWindow = null;

class BufferedSocket {
  constructor(socket) {
    this.socket = socket;
    this.chunks = [];
    this.length = 0;
    this.ended = false;
    this.error = null;
    this.waiters = [];
    socket.on("data", (chunk) => {
      this.chunks.push(chunk);
      this.length += chunk.length;
      this.resolveWaiters();
    });
    socket.on("end", () => {
      this.ended = true;
      this.resolveWaiters();
    });
    socket.on("close", () => {
      this.ended = true;
      this.resolveWaiters();
    });
    socket.on("error", (error) => {
      this.error = error;
      this.resolveWaiters();
    });
  }

  resolveWaiters() {
    const waiters = this.waiters.splice(0);
    for (const waiter of waiters) {
      waiter();
    }
  }

  async read(size) {
    while (this.length < size) {
      if (this.error) {
        throw this.error;
      }
      if (this.ended) {
        throw new Error("Socket ended before enough data arrived");
      }
      await new Promise((resolve) => this.waiters.push(resolve));
    }
    const output = Buffer.allocUnsafe(size);
    let offset = 0;
    while (offset < size) {
      const chunk = this.chunks[0];
      const needed = size - offset;
      if (chunk.length <= needed) {
        chunk.copy(output, offset);
        offset += chunk.length;
        this.chunks.shift();
      } else {
        chunk.copy(output, offset, 0, needed);
        this.chunks[0] = chunk.subarray(needed);
        offset += needed;
      }
    }
    this.length -= size;
    return output;
  }

  async readUInt16() {
    return (await this.read(2)).readUInt16BE(0);
  }

  async readInt() {
    return (await this.read(4)).readInt32BE(0);
  }

  async readLong() {
    return (await this.read(8)).readBigInt64BE(0);
  }

  async readJavaUtf() {
    const length = await this.readUInt16();
    return decodeModifiedUtf8(await this.read(length));
  }
}

function encodeModifiedUtf8(value) {
  const bytes = [];
  const text = String(value || "");
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    if (code !== 0 && code <= 0x7f) {
      bytes.push(code);
    } else if (code <= 0x07ff) {
      bytes.push(0xc0 | ((code >> 6) & 0x1f), 0x80 | (code & 0x3f));
    } else {
      bytes.push(
        0xe0 | ((code >> 12) & 0x0f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f)
      );
    }
  }
  if (bytes.length > 65535) {
    throw new Error("Java UTF value is too long");
  }
  return Buffer.from(bytes);
}

function decodeModifiedUtf8(buffer) {
  const codes = [];
  for (let i = 0; i < buffer.length;) {
    const first = buffer[i++];
    if ((first & 0x80) === 0) {
      codes.push(first);
    } else if ((first & 0xe0) === 0xc0) {
      const second = buffer[i++];
      codes.push(((first & 0x1f) << 6) | (second & 0x3f));
    } else {
      const second = buffer[i++];
      const third = buffer[i++];
      codes.push(((first & 0x0f) << 12) | ((second & 0x3f) << 6) | (third & 0x3f));
    }
  }
  return String.fromCharCode(...codes);
}

function writeJavaUtf(socket, value) {
  const encoded = encodeModifiedUtf8(value);
  const header = Buffer.allocUnsafe(2);
  header.writeUInt16BE(encoded.length, 0);
  socket.write(header);
  socket.write(encoded);
}

function newNearbySessionToken() {
  return crypto.randomUUID().replace(/-/g, "");
}

function nearbyConnectedEvent(sessionToken) {
  return sessionToken ? `${NEARBY_CONNECTED_EVENT}|${sessionToken}` : NEARBY_CONNECTED_EVENT;
}

function parseNearbyConnectedEvent(event) {
  if (event === NEARBY_CONNECTED_EVENT) {
    return "";
  }
  const prefix = `${NEARBY_CONNECTED_EVENT}|`;
  if (String(event || "").startsWith(prefix)) {
    return String(event).slice(prefix.length);
  }
  throw new Error("Missing nearby connection event");
}

function writeInt(socket, value) {
  const buffer = Buffer.allocUnsafe(4);
  buffer.writeInt32BE(value, 0);
  socket.write(buffer);
}

function writeLong(socket, value) {
  const buffer = Buffer.allocUnsafe(8);
  buffer.writeBigInt64BE(BigInt(value), 0);
  socket.write(buffer);
}

function writeBuffer(socket, buffer) {
  return new Promise((resolve, reject) => {
    if (socket.destroyed) {
      reject(new Error("Socket closed"));
      return;
    }
    socket.write(buffer, (error) => {
      if (error) {
        reject(error);
      } else {
        resolve();
      }
    });
  });
}

function intBuffer(value) {
  const buffer = Buffer.allocUnsafe(4);
  buffer.writeInt32BE(value, 0);
  return buffer;
}

async function writeIntAsync(socket, value) {
  await writeBuffer(socket, intBuffer(value));
}

function defaultReceiveDir() {
  return path.join(os.homedir(), "Downloads", PUBLIC_RECEIVE_DIR);
}

function listLocalAddresses() {
  const addresses = [];
  const interfaces = os.networkInterfaces();
  for (const [name, entries] of Object.entries(interfaces)) {
    for (const entry of entries || []) {
      if (entry.family !== "IPv4" || entry.internal) {
        continue;
      }
      addresses.push({
        name,
        address: entry.address,
        label: `${name} - ${entry.address}`
      });
    }
  }
  return addresses;
}

function preferredLocalAddress() {
  const addresses = listLocalAddresses();
  return addresses.find((item) => item.address.startsWith("192.168."))
    || addresses.find((item) => item.address.startsWith("10."))
    || addresses.find((item) => item.address.startsWith("172."))
    || addresses[0]
    || { name: "localhost", address: "127.0.0.1", label: "localhost - 127.0.0.1" };
}

function localAddressOrPreferred(host) {
  if (host === "127.0.0.1" || host === "localhost") {
    return host;
  }
  const addresses = listLocalAddresses();
  if (host && addresses.some((item) => item.address === host)) {
    return host;
  }
  return (addresses.find((item) => item.address.startsWith("192.168."))
    || addresses.find((item) => item.address.startsWith("10."))
    || addresses.find((item) => item.address.startsWith("172."))
    || addresses[0]
    || { address: "127.0.0.1" }).address;
}

function safeFileName(value) {
  const cleaned = String(value || "received-file")
    .replace(/[\\/:*?"<>|]/g, "_")
    .replace(/\s+/g, " ")
    .trim();
  return cleaned || "received-file";
}

function safePartialName(value) {
  return safeFileName(value).replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 140) || "partial";
}

function deviceName() {
  const name = os.hostname().replace(/\.local$/i, "").trim();
  return name || "Flux Desktop";
}

function mimeTypeForFile(filePath) {
  const extension = path.extname(filePath).toLowerCase();
  return {
    ".aac": "audio/aac",
    ".avi": "video/x-msvideo",
    ".csv": "text/csv",
    ".doc": "application/msword",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".flac": "audio/flac",
    ".gif": "image/gif",
    ".heic": "image/heic",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".m4a": "audio/mp4",
    ".mkv": "video/x-matroska",
    ".mov": "video/quicktime",
    ".mp3": "audio/mpeg",
    ".mp4": "video/mp4",
    ".pdf": "application/pdf",
    ".png": "image/png",
    ".ppt": "application/vnd.ms-powerpoint",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".rar": "application/vnd.rar",
    ".txt": "text/plain",
    ".wav": "audio/wav",
    ".webm": "video/webm",
    ".xls": "application/vnd.ms-excel",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".zip": "application/zip"
  }[extension] || "application/octet-stream";
}

function desktopFileId(filePath, stat) {
  return `desktop-${crypto
    .createHash("sha1")
    .update(`${filePath}|${stat.size}|${Math.round(stat.mtimeMs)}`)
    .digest("hex")}`;
}

async function pathExists(filePath) {
  try {
    await fsp.access(filePath);
    return true;
  } catch (_) {
    return false;
  }
}

async function uniquePath(directory, name) {
  const safeName = safeFileName(name);
  const dot = safeName.lastIndexOf(".");
  const base = dot > 0 ? safeName.slice(0, dot) : safeName;
  const extension = dot > 0 ? safeName.slice(dot) : "";
  let candidate = path.join(directory, safeName);
  let index = 1;
  while (await pathExists(candidate)) {
    candidate = path.join(directory, `${base}-${index}${extension}`);
    index += 1;
  }
  return candidate;
}

function escapeHtml(value) {
  return String(value == null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function jsString(value) {
  return JSON.stringify(String(value == null ? "" : value));
}

function categoryForFile(file) {
  const mimeType = String(file.mimeType || "").toLowerCase();
  const extension = path.extname(file.name || file.path || "").toLowerCase();
  if (mimeType.startsWith("video/") || [".mkv", ".mp4", ".mov", ".webm", ".avi"].includes(extension)) {
    return "videos";
  }
  if (mimeType.startsWith("audio/") || [".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg"].includes(extension)) {
    return "songs";
  }
  if ([".zip", ".rar", ".7z", ".tar", ".gz", ".tgz", ".bz2", ".xz", ".jar", ".apk"].includes(extension)) {
    return "compressed";
  }
  return "files";
}

function isPlayableInBrowser(file) {
  const mimeType = String(file.mimeType || "").toLowerCase();
  const extension = path.extname(file.name || "").toLowerCase();
  return mimeType.startsWith("video/")
    || mimeType.startsWith("audio/")
    || [".mkv", ".mp4", ".mov", ".webm", ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg"].includes(extension);
}

function contentDisposition(fileName, download) {
  const safeName = safeFileName(fileName);
  const encoded = encodeURIComponent(safeName);
  const mode = download ? "attachment" : "inline";
  return `${mode}; filename="${safeName.replace(/"/g, "_")}"; filename*=UTF-8''${encoded}`;
}

function parseCookies(header) {
  const cookies = {};
  String(header || "")
    .split(";")
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      const separator = part.indexOf("=");
      if (separator > 0) {
        cookies[part.slice(0, separator)] = decodeURIComponent(part.slice(separator + 1));
      }
    });
  return cookies;
}

function clientLabel(request) {
  const forwarded = String(request.headers["x-forwarded-for"] || "").split(",")[0].trim();
  return forwarded || String(request.socket.remoteAddress || "visitor").replace(/^::ffff:/, "");
}

class DesktopWebShare extends EventEmitter {
  constructor(options = {}) {
    super();
    this.scope = options.scope || "local";
    this.title = options.title || "Desktop Web Share";
    this.defaultPort = options.defaultPort == null ? 8080 : options.defaultPort;
    this.loopbackOnly = !!options.loopbackOnly;
    this.approvalRequired = !!options.approvalRequired;
    this.host = options.host || preferredLocalAddress().address;
    this.server = null;
    this.running = false;
    this.status = options.status || "Stopped.";
    this.publicUrl = "";
    this.files = [];
    this.visitors = new Map();
    this.pendingRequests = new Map();
    this.approved = new Map();
    this.bytesServed = 0;
  }

  state() {
    this.refreshAdvertisedHost();
    return {
      scope: this.scope,
      title: this.title,
      running: this.running,
      host: this.host,
      port: this.port(),
      url: this.url(),
      publicUrl: this.publicUrl,
      status: this.status,
      approvalRequired: this.approvalRequired,
      interfaces: listLocalAddresses(),
      files: this.files.map((file) => ({ ...file })),
      fileCount: this.files.length,
      visitors: Array.from(this.visitors.values()).map((visitor) => ({ ...visitor })),
      pendingRequests: Array.from(this.pendingRequests.values()).map((request) => ({ ...request })),
      bytesServed: this.bytesServed
    };
  }

  refreshAdvertisedHost() {
    if (!this.loopbackOnly) {
      this.host = localAddressOrPreferred(this.host);
    }
  }

  emitState() {
    this.emit("state", this.state());
  }

  setStatus(status) {
    this.status = status;
    this.emitState();
  }

  async setFiles(filePaths) {
    const seen = new Set();
    const nextFiles = [];
    for (const filePath of filePaths || []) {
      const resolved = path.resolve(String(filePath || ""));
      if (!resolved || seen.has(resolved)) {
        continue;
      }
      const stat = await fsp.stat(resolved);
      if (!stat.isFile()) {
        continue;
      }
      seen.add(resolved);
      const name = safeFileName(path.basename(resolved));
      const mimeType = mimeTypeForFile(resolved);
      nextFiles.push({
        id: desktopFileId(resolved, stat),
        path: resolved,
        name,
        size: stat.size,
        mimeType,
        category: categoryForFile({ name, mimeType, path: resolved }),
        playable: isPlayableInBrowser({ name, mimeType })
      });
    }
    this.files = nextFiles;
    this.setStatus(nextFiles.length
      ? `${nextFiles.length} file${nextFiles.length === 1 ? "" : "s"} selected.`
      : "No files selected.");
    return this.state();
  }

  clearFiles() {
    this.files = [];
    this.setStatus(this.running ? "Running with no selected files." : "No files selected.");
    return this.state();
  }

  async start(options = {}) {
    if (!this.files.length) {
      throw new Error("Choose files first.");
    }
    if (Object.prototype.hasOwnProperty.call(options, "host") && !this.loopbackOnly) {
      this.host = localAddressOrPreferred(options.host);
    }
    if (this.running) {
      this.emitState();
      return this.state();
    }
    const bindHost = this.loopbackOnly ? "127.0.0.1" : "0.0.0.0";
    const requestedPort = this.loopbackOnly ? 0 : this.defaultPort;
    this.server = http.createServer((request, response) => this.handle(request, response));
    await new Promise((resolve, reject) => {
      const onError = reject;
      this.server.once("error", onError);
      this.server.listen(requestedPort, bindHost, () => {
        this.server.off("error", onError);
        resolve();
      });
    }).catch(async (error) => {
      if (this.loopbackOnly || error.code !== "EADDRINUSE") {
        throw error;
      }
      this.server = http.createServer((request, response) => this.handle(request, response));
      await new Promise((resolve, reject) => {
        this.server.once("error", reject);
        this.server.listen(0, bindHost, resolve);
      });
    });
    this.running = true;
    this.setStatus(this.loopbackOnly ? "Private web server ready." : `Local web share ready on ${this.url()}.`);
    return this.state();
  }

  async stop() {
    if (!this.running) {
      return this.state();
    }
    await new Promise((resolve) => this.server.close(resolve));
    this.server = null;
    this.running = false;
    this.publicUrl = "";
    this.pendingRequests.clear();
    this.approved.clear();
    this.setStatus("Stopped.");
    return this.state();
  }

  port() {
    return this.server && this.server.address() ? this.server.address().port : 0;
  }

  url() {
    const port = this.port();
    if (!port) {
      return "";
    }
    return `http://${this.loopbackOnly ? "127.0.0.1" : this.host}:${port}/`;
  }

  isApproved(request, response) {
    if (!this.approvalRequired) {
      return true;
    }
    const requestUrl = new URL(request.url || "/", "http://desktop.local");
    const cookies = parseCookies(request.headers.cookie);
    const token = requestUrl.searchParams.get("access") || cookies[`flux_desktop_${this.scope}`] || "";
    const session = this.approved.get(token);
    if (!session) {
      return false;
    }
    session.lastSeenAt = Date.now();
    if (requestUrl.searchParams.get("access")) {
      response.setHeader(
        "Set-Cookie",
        `flux_desktop_${this.scope}=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax`
      );
    }
    this.trackVisitor(request, session);
    return true;
  }

  trackVisitor(request, session = null, bytes = 0) {
    if (!session && this.approvalRequired) {
      const requestUrl = new URL(request.url || "/", "http://desktop.local");
      const cookies = parseCookies(request.headers.cookie);
      const token = requestUrl.searchParams.get("access") || cookies[`flux_desktop_${this.scope}`] || "";
      session = this.approved.get(token) || null;
    }
    const key = session && session.token ? session.token : `${clientLabel(request)}|${request.headers["user-agent"] || ""}`;
    const now = Date.now();
    const existing = this.visitors.get(key) || {
      token: session && session.token || key,
      email: session && session.email || "",
      location: session && session.location || "",
      device: session && session.device || "",
      ip: clientLabel(request),
      userAgent: String(request.headers["user-agent"] || ""),
      connectedAt: now,
      lastSeenAt: now,
      bytesServed: 0,
      status: "Connected"
    };
    existing.lastSeenAt = now;
    existing.bytesServed += Math.max(0, bytes);
    existing.status = "Connected";
    this.visitors.set(key, existing);
    if (bytes > 0) {
      this.bytesServed += bytes;
    }
    this.emitState();
    return existing;
  }

  approveRequest(id) {
    const request = this.pendingRequests.get(id);
    if (!request) {
      throw new Error("Access request is no longer pending.");
    }
    this.pendingRequests.delete(id);
    const token = crypto.randomBytes(18).toString("hex");
    const session = {
      token,
      requestId: id,
      email: request.email,
      location: request.location,
      device: request.device,
      ip: request.ip,
      userAgent: request.userAgent,
      connectedAt: Date.now(),
      lastSeenAt: Date.now(),
      bytesServed: 0,
      status: "Approved"
    };
    this.approved.set(token, session);
    this.visitors.set(token, session);
    this.emitState();
    return session;
  }

  denyRequest(id) {
    const request = this.pendingRequests.get(id);
    if (!request) {
      return false;
    }
    request.status = "Denied";
    this.pendingRequests.delete(id);
    this.emitState();
    return true;
  }

  revokeVisitor(token) {
    this.approved.delete(token);
    this.visitors.delete(token);
    this.emitState();
    return true;
  }

  async handle(request, response) {
    const requestUrl = new URL(request.url || "/", "http://desktop.local");
    try {
      if (requestUrl.pathname === "/healthz") {
        this.json(response, { ok: true, scope: this.scope, files: this.files.length });
        return;
      }
      if (requestUrl.pathname === "/access/request" && request.method === "POST") {
        await this.handleAccessRequest(request, response);
        return;
      }
      if (requestUrl.pathname === "/access/session") {
        this.handleAccessSession(requestUrl, response);
        return;
      }
      if (request.method !== "GET" && request.method !== "HEAD") {
        this.text(response, 405, "Only GET and HEAD are supported.");
        return;
      }
      if (!this.isApproved(request, response)) {
        this.sendApprovalPage(response);
        return;
      }
      this.trackVisitor(request);
      if (requestUrl.pathname === "/api/files") {
        this.json(response, { files: this.files.map((file) => ({ ...file, path: "" })) });
        return;
      }
      if (requestUrl.pathname.startsWith("/file/")) {
        await this.serveFile(request, response, decodeURIComponent(requestUrl.pathname.slice("/file/".length)));
        return;
      }
      this.sendBrowserApp(response);
    } catch (error) {
      this.text(response, 500, error.message || "Desktop share failed.");
    }
  }

  async handleAccessRequest(request, response) {
    if (!this.approvalRequired) {
      this.json(response, { ok: true, approved: true });
      return;
    }
    const body = await readJsonBody(request).catch(() => ({}));
    const email = String(body.email || "").trim();
    if (!email || !email.includes("@")) {
      this.json(response, { ok: false, error: "Enter a valid email address." }, 400);
      return;
    }
    const id = crypto.randomUUID();
    const accessRequest = {
      id,
      email,
      location: String(body.location || "Location not shared").slice(0, 160),
      device: String(body.device || "Browser").slice(0, 160),
      userAgent: String(body.userAgent || request.headers["user-agent"] || "").slice(0, 240),
      ip: clientLabel(request),
      createdAt: Date.now(),
      status: "Pending"
    };
    this.pendingRequests.set(id, accessRequest);
    this.emitState();
    this.json(response, { ok: true, id });
  }

  handleAccessSession(requestUrl, response) {
    const id = requestUrl.searchParams.get("id") || "";
    const pending = this.pendingRequests.get(id);
    if (pending) {
      this.json(response, { ok: true, status: "pending" });
      return;
    }
    for (const session of this.approved.values()) {
      if ((session.requestId && session.requestId === id) || (session.token && session.token === id)) {
        this.json(response, { ok: true, status: "approved", token: session.token });
        return;
      }
    }
    this.json(response, { ok: false, status: "not-found" }, 404);
  }

  sendApprovalPage(response) {
    const body = `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(this.title)} Access</title>
<style>
body{margin:0;min-height:100vh;display:grid;place-items:center;background:radial-gradient(circle at top left,#15474d,transparent 38%),#05080a;color:#f6fbf9;font-family:system-ui,-apple-system,sans-serif}
.card{width:min(440px,calc(100vw - 28px));border:1px solid #263c45;border-radius:26px;background:#0e181cee;padding:24px;box-shadow:0 24px 70px #0008}
.eyebrow{color:#ffaa60;text-transform:uppercase;letter-spacing:.14em;font-size:11px;font-weight:900}.field{display:grid;gap:8px;margin:16px 0}input{height:48px;border-radius:16px;border:1px solid #263c45;background:#071013;color:#f6fbf9;padding:0 14px;font:inherit}button{width:100%;height:48px;border:0;border-radius:16px;background:linear-gradient(135deg,#3dc6be,#7af7ed);color:#041113;font-weight:900}.status{color:#93aaa8;line-height:1.45}.code{margin-top:14px;padding:12px;border-radius:14px;background:#09272c;color:#bdfbf6;font-weight:900}.hidden{display:none}
</style></head><body><main class="card">
<p class="eyebrow">${escapeHtml(this.scope)} access</p><h1>Request access</h1>
<p class="status">The desktop owner must approve before files open.</p>
<form id="form"><label class="field"><span>Email address</span><input id="email" type="email" required autocomplete="email"></label><button id="button">Send approval request</button></form>
<p id="status" class="status"></p><p id="code" class="code hidden"></p>
</main><script>
const form=document.getElementById('form'),email=document.getElementById('email'),status=document.getElementById('status'),code=document.getElementById('code'),button=document.getElementById('button');
function device(){return [navigator.platform,navigator.vendor].filter(Boolean).join(' / ')||'Browser'}
function poll(id){setTimeout(async()=>{try{const r=await fetch('/access/session?id='+encodeURIComponent(id),{cache:'no-store'});const p=await r.json();if(p.status==='approved'&&p.token){location.href='/?access='+encodeURIComponent(p.token);return;}if(p.status==='pending'){status.textContent='Waiting for desktop approval...';poll(id);return;}status.textContent='Request was denied or expired.';}catch(e){status.textContent='Still waiting for approval...';poll(id);}},1200)}
form.addEventListener('submit',async event=>{event.preventDefault();button.disabled=true;status.textContent='Sending request...';try{const r=await fetch('/access/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:email.value,device:device(),location:'Browser shared no precise location',userAgent:navigator.userAgent||''})});const p=await r.json();if(!r.ok||!p.ok)throw new Error(p.error||'Could not request access');code.textContent='Request sent';code.classList.remove('hidden');status.textContent='Waiting for desktop approval...';poll(p.id)}catch(e){button.disabled=false;status.textContent=e.message||'Could not request access';}});
</script></body></html>`;
    response.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Length": Buffer.byteLength(body),
      "Cache-Control": "no-store"
    });
    response.end(body);
  }

  sendBrowserApp(response) {
    const filesJson = JSON.stringify(this.files.map((file) => ({ ...file, path: "" })));
    const body = `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(this.title)}</title>
<style>
:root{color-scheme:dark;--bg:#05080a;--panel:#0e181c;--line:#263c45;--ink:#f6fbf9;--muted:#93aaa8;--teal:#3dc6be;--amber:#ffaa60}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at 0 0,#16484e,transparent 36%),linear-gradient(135deg,#061014,#05080a);color:var(--ink);font-family:system-ui,-apple-system,sans-serif}.shell{width:min(1040px,100%);margin:auto;padding:20px}.hero{display:flex;justify-content:space-between;gap:14px;align-items:center;margin-bottom:14px}.eyebrow{margin:0 0 6px;color:var(--amber);letter-spacing:.14em;text-transform:uppercase;font-size:11px;font-weight:900}h1{margin:0;font-size:clamp(32px,6vw,64px);line-height:.95;letter-spacing:-.06em}.subtle{color:var(--muted)}.tabs{display:flex;gap:8px;overflow:auto;margin:12px 0}.tab{border:1px solid var(--line);background:#0e181c;color:var(--ink);border-radius:999px;padding:10px 14px;font-weight:900;white-space:nowrap}.tab.active{border-color:var(--teal);color:#bdfbf6;background:#09272c}.search{width:100%;height:46px;border-radius:16px;border:1px solid var(--line);background:#071013;color:var(--ink);padding:0 14px;font:inherit;font-weight:800}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px;margin-top:14px}.file{border:1px solid var(--line);border-radius:20px;background:#0e181ce8;padding:14px;min-width:0}.badge{display:inline-grid;place-items:center;min-width:48px;height:38px;border-radius:14px;background:#132228;color:#bdfbf6;font-weight:1000}.file h2{font-size:16px;overflow-wrap:anywhere}.meta{color:var(--muted);font-size:13px}.actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}.btn{display:grid;place-items:center;text-decoration:none;min-height:40px;border-radius:14px;font-weight:900}.watch{background:linear-gradient(135deg,var(--teal),#7af7ed);color:#041113}.download{background:#132228;color:#f6fbf9;border:1px solid var(--line)}.empty{padding:24px;border:1px dashed var(--line);border-radius:20px;color:var(--muted);text-align:center}.modal{position:fixed;inset:0;background:#000b;display:none;place-items:center;padding:20px}.modal.open{display:grid}.player{width:min(920px,100%);border:1px solid var(--line);border-radius:24px;background:#071013;padding:14px}.player video,.player audio{width:100%;max-height:72vh}.player-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:10px}.close{border:1px solid var(--line);background:#132228;color:#fff;border-radius:12px;min-height:38px;padding:0 12px}@media(max-width:560px){.hero{display:grid}.shell{padding:12px}.grid{grid-template-columns:1fr}.actions{grid-template-columns:1fr}}
</style></head><body><main class="shell">
<section class="hero"><div><p class="eyebrow">Flux desktop share</p><h1>${escapeHtml(this.title)}</h1><p class="subtle">${this.files.length} selected file${this.files.length === 1 ? "" : "s"} available.</p></div></section>
<input class="search" id="search" placeholder="Search files">
<nav class="tabs" id="tabs"></nav><section class="grid" id="grid"></section>
</main><section class="modal" id="modal"><div class="player"><div class="player-head"><strong id="playerTitle"></strong><button class="close" id="close">Close</button></div><div id="playerBody"></div></div></section>
<script>const files=${filesJson};const categories=[['all','All'],['videos','Videos'],['songs','Songs'],['files','Files'],['compressed','Compressed']];let active='all';const grid=document.getElementById('grid'),tabs=document.getElementById('tabs'),search=document.getElementById('search'),modal=document.getElementById('modal'),playerBody=document.getElementById('playerBody'),playerTitle=document.getElementById('playerTitle');function esc(v){return String(v??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}function size(bytes){let v=Number(bytes)||0,u=['B','KB','MB','GB','TB'],i=0;while(v>=1024&&i<u.length-1){v/=1024;i++}return (v>=10||i===0?v.toFixed(0):v.toFixed(1))+' '+u[i]}function ext(n){return (n.includes('.')?n.split('.').pop():'FILE').slice(0,4).toUpperCase()}function filtered(){const q=search.value.trim().toLowerCase();return files.filter(f=>(active==='all'||f.category===active)&&(!q||f.name.toLowerCase().includes(q)))}function renderTabs(){tabs.innerHTML=categories.map(([id,label])=>'<button class="tab '+(id===active?'active':'')+'" data-id="'+id+'">'+label+' '+files.filter(f=>id==='all'||f.category===id).length+'</button>').join('');tabs.querySelectorAll('button').forEach(b=>b.onclick=()=>{active=b.dataset.id;render()})}function render(){renderTabs();const list=filtered();grid.innerHTML=list.length?list.map(f=>'<article class="file"><span class="badge">'+esc(ext(f.name))+'</span><h2>'+esc(f.name)+'</h2><p class="meta">'+esc(f.mimeType)+' · '+size(f.size)+'</p><div class="actions">'+(f.playable?'<button class="btn watch" data-watch="'+esc(f.id)+'">Watch</button>':'<span></span>')+'<a class="btn download" href="/file/'+encodeURIComponent(f.id)+'?download=1">Download</a></div></article>').join(''):'<p class="empty">No files match this view.</p>';grid.querySelectorAll('[data-watch]').forEach(b=>b.onclick=()=>openPlayer(b.dataset.watch))}function openPlayer(id){const f=files.find(x=>x.id===id);if(!f)return;playerTitle.textContent=f.name;const src='/file/'+encodeURIComponent(f.id);playerBody.innerHTML=f.mimeType.startsWith('audio/')?'<audio controls autoplay src="'+src+'"></audio>':'<video controls autoplay playsinline src="'+src+'"></video><p class="meta">If this codec cannot play in your browser, use Download or open with VLC.</p>';modal.classList.add('open')}document.getElementById('close').onclick=()=>{modal.classList.remove('open');playerBody.innerHTML=''};search.oninput=render;render();</script></body></html>`;
    response.writeHead(200, {
      "Content-Type": "text/html; charset=utf-8",
      "Content-Length": Buffer.byteLength(body),
      "Cache-Control": "no-store"
    });
    response.end(body);
  }

  async serveFile(request, response, id) {
    const file = this.files.find((item) => item.id === id);
    if (!file) {
      this.text(response, 404, "File not found.");
      return;
    }
    const stat = await fsp.stat(file.path);
    const range = String(request.headers.range || "");
    const download = new URL(request.url || "/", "http://desktop.local").searchParams.get("download") === "1";
    let start = 0;
    let end = stat.size - 1;
    let status = 200;
    if (range.startsWith("bytes=")) {
      const [rawStart, rawEnd] = range.slice(6).split("-");
      start = rawStart ? Number.parseInt(rawStart, 10) : 0;
      end = rawEnd ? Number.parseInt(rawEnd, 10) : end;
      if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || end >= stat.size || start > end) {
        response.writeHead(416, { "Content-Range": `bytes */${stat.size}` });
        response.end();
        return;
      }
      status = 206;
    }
    const length = end - start + 1;
    const headers = {
      "Content-Type": file.mimeType,
      "Content-Length": length,
      "Accept-Ranges": "bytes",
      "Content-Disposition": contentDisposition(file.name, download),
      "Cache-Control": "private, max-age=60"
    };
    if (status === 206) {
      headers["Content-Range"] = `bytes ${start}-${end}/${stat.size}`;
    }
    response.writeHead(status, headers);
    if (request.method === "HEAD") {
      response.end();
      return;
    }
    let sent = 0;
    const stream = fs.createReadStream(file.path, { start, end });
    stream.on("data", (chunk) => {
      sent += chunk.length;
    });
    stream.on("close", () => {
      this.trackVisitor(request, null, sent);
    });
    stream.pipe(response);
  }

  json(response, data, status = 200) {
    const raw = JSON.stringify(data);
    response.writeHead(status, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(raw),
      "Cache-Control": "no-store"
    });
    response.end(raw);
  }

  text(response, status, body) {
    const raw = String(body || "");
    response.writeHead(status, {
      "Content-Type": "text/plain; charset=utf-8",
      "Content-Length": Buffer.byteLength(raw),
      "Cache-Control": "no-store"
    });
    response.end(raw);
  }
}

function boolEnv(name, fallback) {
  const value = process.env[name];
  if (value == null || value === "") {
    return fallback;
  }
  return /^(1|true|yes|on)$/i.test(value);
}

function intEnv(name, fallback) {
  const parsed = Number.parseInt(String(process.env[name] || ""), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function relayConfigFromEnv() {
  const host = process.env.FLUX_DESKTOP_RELAY_HOST
    || process.env.FLUX_RELAY_HOST
    || "share.fv.munishkumarm.com";
  const tlsEnabled = boolEnv("FLUX_DESKTOP_RELAY_TLS", boolEnv("FLUX_RELAY_TLS", true));
  const port = intEnv("FLUX_DESKTOP_RELAY_PORT", intEnv("FLUX_RELAY_PORT", tlsEnabled ? 443 : 4444));
  const agentPath = process.env.FLUX_DESKTOP_RELAY_AGENT_PATH
    || process.env.FLUX_RELAY_AGENT_PATH
    || "/agent";
  return {
    host,
    port,
    tls: tlsEnabled,
    agentPath: agentPath.startsWith("/") ? agentPath : `/${agentPath}`
  };
}

function desktopSlug() {
  const base = deviceName()
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 22)
    .replace(/-$/g, "");
  return `${base || "desktop"}-${crypto.randomBytes(4).toString("hex")}`;
}

function writeWebSocketClientJson(socket, frame) {
  const payload = Buffer.from(JSON.stringify(frame), "utf8");
  let header;
  if (payload.length <= 125) {
    header = Buffer.from([0x81, 0x80 | payload.length]);
  } else if (payload.length <= 0xffff) {
    header = Buffer.allocUnsafe(4);
    header[0] = 0x81;
    header[1] = 0x80 | 126;
    header.writeUInt16BE(payload.length, 2);
  } else {
    header = Buffer.allocUnsafe(10);
    header[0] = 0x81;
    header[1] = 0x80 | 127;
    header.writeUInt32BE(0, 2);
    header.writeUInt32BE(payload.length, 6);
  }
  const mask = crypto.randomBytes(4);
  const masked = Buffer.allocUnsafe(payload.length);
  for (let index = 0; index < payload.length; index++) {
    masked[index] = payload[index] ^ mask[index % 4];
  }
  socket.write(Buffer.concat([header, mask, masked]));
}

function readWebSocketUpgrade(socket) {
  return new Promise((resolve, reject) => {
    let buffer = Buffer.alloc(0);
    const cleanup = () => {
      socket.off("data", onData);
      socket.off("error", onError);
      socket.off("close", onClose);
    };
    const onError = (error) => {
      cleanup();
      reject(error);
    };
    const onClose = () => {
      cleanup();
      reject(new Error("Relay closed during WebSocket handshake"));
    };
    const onData = (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      const marker = buffer.indexOf("\r\n\r\n");
      if (marker < 0) {
        return;
      }
      cleanup();
      const raw = buffer.subarray(0, marker + 4).toString("latin1");
      const rest = buffer.subarray(marker + 4);
      resolve({ raw, rest });
    };
    socket.on("data", onData);
    socket.once("error", onError);
    socket.once("close", onClose);
  });
}

function attachWebSocketFrameReader(socket, initialData, onFrame, onError) {
  let buffer = Buffer.from(initialData || []);
  function processBuffer() {
    while (buffer.length >= 2) {
      const first = buffer[0];
      const second = buffer[1];
      const fin = (first & 0x80) !== 0;
      const opcode = first & 0x0f;
      const masked = (second & 0x80) !== 0;
      let length = second & 0x7f;
      let offset = 2;
      if (!fin) {
        socket.destroy(new Error("Fragmented WebSocket frames are not supported"));
        return;
      }
      if (length === 126) {
        if (buffer.length < offset + 2) {
          return;
        }
        length = buffer.readUInt16BE(offset);
        offset += 2;
      } else if (length === 127) {
        if (buffer.length < offset + 8) {
          return;
        }
        const high = buffer.readUInt32BE(offset);
        const low = buffer.readUInt32BE(offset + 4);
        if (high !== 0) {
          socket.destroy(new Error("WebSocket frame is too large"));
          return;
        }
        length = low;
        offset += 8;
      }
      let mask = null;
      if (masked) {
        if (buffer.length < offset + 4) {
          return;
        }
        mask = buffer.subarray(offset, offset + 4);
        offset += 4;
      }
      if (buffer.length < offset + length) {
        return;
      }
      let payload = buffer.subarray(offset, offset + length);
      buffer = buffer.subarray(offset + length);
      if (masked) {
        payload = Buffer.from(payload);
        for (let index = 0; index < payload.length; index++) {
          payload[index] ^= mask[index % 4];
        }
      }
      if (opcode === 0x8) {
        socket.end();
        return;
      }
      if (opcode === 0x9 || opcode === 0xA) {
        continue;
      }
      if (opcode !== 0x1 && opcode !== 0x2) {
        socket.destroy(new Error(`Unsupported WebSocket opcode: ${opcode}`));
        return;
      }
      try {
        onFrame(JSON.parse(payload.toString("utf8")));
      } catch (error) {
        onError(error);
      }
    }
  }
  socket.on("data", (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    processBuffer();
  });
  socket.on("error", onError);
  processBuffer();
}

class DesktopLiveShare extends EventEmitter {
  constructor(webShare, options = {}) {
    super();
    this.webShare = webShare;
    this.config = options.config || relayConfigFromEnv();
    this.slug = "";
    this.token = "";
    this.socket = null;
    this.running = false;
    this.connected = false;
    this.status = "Global link off.";
    this.publicUrl = "";
    this.fullUrl = "";
    this.shortUrl = "";
    this.tinyUrlError = "";
    this.pendingLocal = new Map();
    this.reconnectTimer = null;
    this.webShare.on("state", () => this.emitState());
  }

  state() {
    const shareState = this.webShare.state();
    return {
      ...shareState,
      running: this.running,
      connected: this.connected,
      status: this.status,
      url: this.publicUrl,
      publicUrl: this.publicUrl,
      fullUrl: this.fullUrl,
      shortUrl: this.shortUrl,
      tinyUrlError: this.tinyUrlError,
      relayHost: this.config.host,
      relayPort: this.config.port
    };
  }

  emitState() {
    this.emit("state", this.state());
  }

  setStatus(status) {
    this.status = status;
    this.emitState();
  }

  async setFiles(paths) {
    return this.webShare.setFiles(paths);
  }

  clearFiles() {
    return this.webShare.clearFiles();
  }

  approveRequest(id) {
    return this.webShare.approveRequest(id);
  }

  denyRequest(id) {
    return this.webShare.denyRequest(id);
  }

  revokeVisitor(token) {
    return this.webShare.revokeVisitor(token);
  }

  async start() {
    if (!this.webShare.files.length) {
      throw new Error("Choose Live Share files first.");
    }
    if (!this.webShare.running) {
      await this.webShare.start();
    }
    if (this.running) {
      this.emitState();
      return this.state();
    }
    this.running = true;
    this.slug = desktopSlug();
    this.token = crypto.randomBytes(16).toString("hex");
    this.setStatus("Connecting to relay...");
    this.connect().catch((error) => this.handleDisconnect(error));
    return this.state();
  }

  async stop() {
    this.running = false;
    this.connected = false;
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    if (this.socket) {
      this.socket.destroy();
      this.socket = null;
    }
    for (const request of this.pendingLocal.values()) {
      request.destroy();
    }
    this.pendingLocal.clear();
    await this.webShare.stop();
    this.publicUrl = "";
    this.fullUrl = "";
    this.shortUrl = "";
    this.tinyUrlError = "";
    this.setStatus("Global link off.");
    return this.state();
  }

  async connect() {
    const socket = this.config.tls
      ? tls.connect({
          host: this.config.host,
          port: this.config.port,
          servername: this.config.host,
          rejectUnauthorized: boolEnv("FLUX_DESKTOP_RELAY_VERIFY_TLS", true)
        })
      : net.createConnection({ host: this.config.host, port: this.config.port });
    this.socket = socket;
    socket.setKeepAlive(true, 30000);
    await new Promise((resolve, reject) => {
      socket.once("connect", resolve);
      socket.once("secureConnect", resolve);
      socket.once("error", reject);
    });
    const key = crypto.randomBytes(16).toString("base64");
    const defaultPort = (this.config.tls && this.config.port === 443) || (!this.config.tls && this.config.port === 80);
    socket.write([
      `GET ${this.config.agentPath} HTTP/1.1`,
      `Host: ${defaultPort ? this.config.host : `${this.config.host}:${this.config.port}`}`,
      "Upgrade: websocket",
      "Connection: Upgrade",
      `Sec-WebSocket-Key: ${key}`,
      "Sec-WebSocket-Version: 13",
      "User-Agent: FluxFileShare-Desktop",
      "\r\n"
    ].join("\r\n"));
    const upgrade = await readWebSocketUpgrade(socket);
    if (!upgrade.raw.includes(" 101 ")) {
      throw new Error("Relay did not accept WebSocket upgrade");
    }
    attachWebSocketFrameReader(
      socket,
      upgrade.rest,
      (frame) => this.handleFrame(frame),
      (error) => this.handleDisconnect(error)
    );
    socket.on("close", () => this.handleDisconnect(new Error("Relay socket closed")));
    this.send({
      type: "register",
      slug: this.slug,
      token: this.token,
      deviceName: deviceName(),
      app: "Flux File Share Desktop"
    });
  }

  send(frame) {
    if (!this.socket || this.socket.destroyed) {
      throw new Error("Relay is not connected");
    }
    writeWebSocketClientJson(this.socket, frame);
  }

  handleFrame(frame) {
    if (frame.type === "registered") {
      this.connected = true;
      this.publicUrl = String(frame.publicUrl || frame.shortUrl || frame.fullUrl || "");
      this.fullUrl = String(frame.fullUrl || "");
      this.shortUrl = String(frame.shortUrl || "");
      this.tinyUrlError = String(frame.tinyUrlError || "");
      this.webShare.publicUrl = this.publicUrl;
      this.setStatus("Global link online.");
      return;
    }
    if (frame.type === "request") {
      this.proxyRelayRequest(frame);
      return;
    }
    if (frame.type === "cancel") {
      const request = this.pendingLocal.get(frame.id);
      if (request) {
        request.destroy();
        this.pendingLocal.delete(frame.id);
      }
      return;
    }
    if (frame.type === "ping") {
      this.send({ type: "pong" });
    }
  }

  proxyRelayRequest(frame) {
    const id = String(frame.id || "");
    if (!id) {
      return;
    }
    const body = frame.body ? Buffer.from(String(frame.body), "base64") : Buffer.alloc(0);
    const request = http.request({
      host: "127.0.0.1",
      port: this.webShare.port(),
      method: String(frame.method || "GET").toUpperCase(),
      path: String(frame.path || "/").startsWith("/") ? String(frame.path || "/") : "/",
      headers: {
        ...(frame.headers || {}),
        host: "127.0.0.1",
        connection: "close",
        "content-length": body.length
      }
    }, (response) => {
      this.send({
        type: "responseStart",
        id,
        status: response.statusCode || 502,
        headers: response.headers || {}
      });
      response.on("data", (chunk) => {
        this.send({
          type: "responseChunk",
          id,
          data: chunk.toString("base64")
        });
      });
      response.on("end", () => {
        this.pendingLocal.delete(id);
        this.send({ type: "responseEnd", id });
      });
    });
    this.pendingLocal.set(id, request);
    request.on("error", (error) => {
      this.pendingLocal.delete(id);
      this.sendRelayError(id, error.message);
    });
    if (body.length) {
      request.write(body);
    }
    request.end();
  }

  sendRelayError(id, message) {
    try {
      const data = Buffer.from(`Desktop could not serve this request: ${message || "unknown error"}`, "utf8");
      this.send({
        type: "responseStart",
        id,
        status: 502,
        headers: {
          "content-type": "text/plain; charset=utf-8",
          "content-length": data.length,
          "cache-control": "no-store"
        }
      });
      this.send({ type: "responseChunk", id, data: data.toString("base64") });
      this.send({ type: "responseEnd", id });
    } catch (_) {
    }
  }

  handleDisconnect(error) {
    if (!this.running) {
      return;
    }
    if (this.socket) {
      this.socket.destroy();
      this.socket = null;
    }
    this.connected = false;
    this.setStatus(`Relay disconnected. Reconnecting... ${error && error.message ? error.message : ""}`.trim());
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = setTimeout(() => {
      if (this.running) {
        this.connect().catch((nextError) => this.handleDisconnect(nextError));
      }
    }, 2500);
    this.reconnectTimer.unref?.();
  }
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return "-";
  }
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
}

class NearbyReceiver extends EventEmitter {
  constructor(options = {}) {
    super();
    this.host = options.host || preferredLocalAddress().address;
    this.port = options.port == null ? NEARBY_TRANSFER_PORT : options.port;
    this.saveDir = options.saveDir || defaultReceiveDir();
    this.partialDir = path.join(this.saveDir, ".flux-partials");
    this.server = null;
    this.running = false;
    this.status = "Stopped.";
    this.connectedPeer = "";
    this.connectedControlPort = 0;
    this.sessionToken = newNearbySessionToken();
    this.transfers = [];
    this.activeSockets = new Set();
  }

  qrPayload() {
    return `fluxnearby://receiver?host=${this.host}&port=${this.port}&server=${this.sessionToken}`;
  }

  state() {
    this.refreshAdvertisedHost();
    return {
      running: this.running,
      host: this.host,
      port: this.port,
      payload: this.qrPayload(),
      saveDir: this.saveDir,
      status: this.status,
      connectedPeer: this.connectedPeer,
      interfaces: listLocalAddresses(),
      transfers: this.transfers.map((item) => ({ ...item }))
    };
  }

  setStatus(status) {
    this.status = status;
    this.emitState();
  }

  emitState() {
    this.emit("state", this.state());
  }

  refreshAdvertisedHost() {
    if (!this.connectedPeer) {
      this.host = localAddressOrPreferred(this.host);
    }
  }

  async start(options = {}) {
    if (options.fresh) {
      this.transfers = [];
      this.connectedPeer = "";
      this.connectedControlPort = 0;
      this.sessionToken = newNearbySessionToken();
    }
    const requestedHost = Object.prototype.hasOwnProperty.call(options, "host")
      ? options.host
      : options.fresh ? "" : this.host;
    this.host = localAddressOrPreferred(requestedHost);
    if (options.saveDir) {
      this.saveDir = options.saveDir;
      this.partialDir = path.join(this.saveDir, ".flux-partials");
    }
    if (this.running) {
      this.emitState();
      return this.state();
    }
    this.sessionToken = newNearbySessionToken();
    await fsp.mkdir(this.saveDir, { recursive: true });
    await fsp.mkdir(this.partialDir, { recursive: true });
    this.server = net.createServer((socket) => this.handleSocket(socket));
    await new Promise((resolve, reject) => {
      const onError = (error) => reject(error);
      this.server.once("error", onError);
      this.server.listen(this.port, "0.0.0.0", () => {
        this.server.off("error", onError);
        this.port = this.server.address().port;
        resolve();
      });
    }).catch(async (error) => {
      if (error.code !== "EADDRINUSE") {
        throw error;
      }
      this.server = net.createServer((socket) => this.handleSocket(socket));
      await new Promise((resolve, reject) => {
        this.server.once("error", reject);
        this.server.listen(0, "0.0.0.0", () => {
          this.port = this.server.address().port;
          resolve();
        });
      });
    });
    this.running = true;
    this.setStatus(`Ready on ${this.host}:${this.port}.`);
    return this.state();
  }

  clearSession() {
    this.transfers = [];
    this.connectedPeer = "";
    this.connectedControlPort = 0;
    this.setStatus(this.running ? `Ready on ${this.host}:${this.port}.` : "Stopped.");
    return this.state();
  }

  async stop() {
    if (!this.running) {
      return this.state();
    }
    for (const socket of this.activeSockets) {
      socket.destroy();
    }
    this.activeSockets.clear();
    await new Promise((resolve) => this.server.close(resolve));
    this.server = null;
    this.running = false;
    this.connectedPeer = "";
    this.connectedControlPort = 0;
    this.setStatus("Stopped.");
    return this.state();
  }

  async handleSocket(socket) {
    this.activeSockets.add(socket);
    socket.on("close", () => this.activeSockets.delete(socket));
    try {
      const reader = new BufferedSocket(socket);
      const event = await reader.readJavaUtf();
      const sessionToken = parseNearbyConnectedEvent(event);
      const peerName = await reader.readJavaUtf();
      const magic = await reader.readJavaUtf();
      const peer = peerName || socket.remoteAddress || "phone";
      if (sessionToken !== this.sessionToken) {
        if (magic === NEARBY_PRESENCE_MAGIC) {
          writeJavaUtf(socket, NEARBY_SESSION_MISMATCH_MAGIC);
        }
        throw new Error(NEARBY_SESSION_ERROR);
      }
      if (magic === NEARBY_PRESENCE_MAGIC) {
        this.connectedPeer = peer;
        this.connectedControlPort = await reader.readInt();
        this.setStatus(`Connected to ${peer}. Select files on the phone.`);
        writeJavaUtf(socket, NEARBY_ACK_MAGIC);
        socket.end();
        return;
      }
      if (magic === NEARBY_CANCEL_MAGIC) {
        this.connectedPeer = "";
        this.setStatus("Phone cancelled the session.");
        socket.end();
        return;
      }
      if (magic === NEARBY_FILE_CANCEL_MAGIC) {
        const fileId = await reader.readJavaUtf();
        this.markTransfer(fileId, { status: "Cancelled" });
        socket.end();
        return;
      }
      if (magic === NEARBY_QUEUE_MAGIC) {
        const count = await reader.readInt();
        await this.readIncomingHeaders(reader, peer, count, true);
        this.setStatus(`${count} file${count === 1 ? "" : "s"} queued from ${peer}.`);
        socket.end();
        return;
      }
      if (magic !== NEARBY_TRANSFER_MAGIC) {
        throw new Error(`Unknown nearby message: ${magic}`);
      }
      const count = await reader.readInt();
      const incomingFiles = await this.readIncomingHeaders(reader, peer, count, false);
      this.setStatus(`Receiving ${count} file${count === 1 ? "" : "s"} from ${peer}.`);
      for (const file of incomingFiles) {
        await this.receiveOneFile(reader, socket, file);
      }
      this.setStatus(`Received ${count} file${count === 1 ? "" : "s"} from ${peer}.`);
      socket.end();
    } catch (error) {
      if (isSocketDisconnectError(error)) {
        this.connectedPeer = "";
        this.connectedControlPort = 0;
        this.setStatus("Sender disconnected. Scan again to resume.");
      } else {
        this.setStatus(`Receive failed: ${error.message}`);
      }
      socket.destroy();
    }
  }

  async readIncomingHeaders(reader, peer, count, queuedOnly) {
    const files = [];
    for (let index = 0; index < count; index++) {
      const id = await reader.readJavaUtf();
      const name = safeFileName(await reader.readJavaUtf());
      const mimeType = await reader.readJavaUtf();
      const size = Number(await reader.readLong());
      const item = this.upsertTransfer({
        id,
        peer,
        name,
        mimeType,
        size,
        transferred: 0,
        speed: 0,
        status: queuedOnly ? "Pending" : "Waiting",
        path: "",
        updatedAt: Date.now(),
        sampleAt: Date.now(),
        sampleBytes: 0
      });
      files.push({ id, name, mimeType, size, item });
    }
    this.emitState();
    return files;
  }

  upsertTransfer(next) {
    const existing = this.transfers.find((item) => item.id === next.id);
    if (existing) {
      Object.assign(existing, next);
      return existing;
    }
    this.transfers.unshift(next);
    return next;
  }

  markTransfer(id, patch) {
    const item = this.transfers.find((entry) => entry.id === id);
    if (item) {
      Object.assign(item, patch, { updatedAt: Date.now() });
      this.emitState();
    }
  }

  async receiveOneFile(reader, socket, file) {
    const partialPath = path.join(this.partialDir, `${safePartialName(file.id)}.part`);
    let savedBytes = 0;
    try {
      const stat = await fsp.stat(partialPath);
      savedBytes = Math.min(stat.size, file.size);
    } catch (_) {
      savedBytes = 0;
    }
    writeLong(socket, savedBytes);
    this.updateTransferProgress(file.item, savedBytes > 0 ? "Resuming" : "Copying", savedBytes, file.size);
    const handle = await fsp.open(partialPath, "a+");
    let copied = savedBytes;
    let completed = false;
    let cancelled = false;
    try {
      await handle.truncate(savedBytes);
      while (true) {
        const chunkLength = await reader.readInt();
        if (chunkLength === NEARBY_CHUNK_DONE) {
          completed = true;
          break;
        }
        if (chunkLength === NEARBY_CHUNK_CANCELLED) {
          cancelled = true;
          break;
        }
        if (chunkLength <= 0 || chunkLength > TRANSFER_BUFFER_SIZE) {
          throw new Error("Invalid transfer chunk");
        }
        const data = await reader.read(chunkLength);
        await handle.write(data, 0, data.length, copied);
        copied += data.length;
        this.updateTransferProgress(file.item, "Copying", copied, file.size);
      }
    } catch (error) {
      if (isSocketDisconnectError(error)) {
        this.connectedPeer = "";
        this.connectedControlPort = 0;
        this.setStatus("Sender disconnected. Scan again to resume.");
      }
      this.updateTransferProgress(file.item, "Interrupted", copied, file.size, partialPath);
      throw error;
    } finally {
      try {
        await handle.close();
      } catch (_) {
      }
    }
    if (cancelled) {
      this.updateTransferProgress(file.item, "Cancelled", copied, file.size);
      await fsp.rm(partialPath, { force: true });
      return;
    }
    if (!completed || copied < file.size) {
      this.updateTransferProgress(file.item, "Interrupted", copied, file.size, partialPath);
      throw new Error("Transfer ended early");
    }
    const finalPath = await uniquePath(this.saveDir, file.name);
    await fsp.rename(partialPath, finalPath);
    this.updateTransferProgress(file.item, "Completed", file.size, file.size, finalPath);
    this.emit("transfer-complete", file.item);
  }

  updateTransferProgress(item, status, transferred, total, filePath = item.path || "") {
    const now = Date.now();
    const elapsed = Math.max(1, now - (item.sampleAt || now));
    if (elapsed >= 500) {
      const delta = Math.max(0, transferred - (item.sampleBytes || 0));
      item.speed = Math.round(delta * 1000 / elapsed);
      item.sampleAt = now;
      item.sampleBytes = transferred;
    }
    if (status === "Completed" || status === "Cancelled" || status === "Interrupted") {
      item.speed = 0;
    }
    item.status = status;
    item.transferred = Math.max(0, transferred);
    item.size = total;
    item.path = filePath;
    item.updatedAt = now;
    this.emitState();
  }
}

class NearbySender extends EventEmitter {
  constructor(options = {}) {
    super();
    this.host = options.host || preferredLocalAddress().address;
    this.port = options.port == null ? NEARBY_QR_SEND_PORT : options.port;
    this.server = null;
    this.running = false;
    this.status = "Tap Send To Phone to show QR.";
    this.connectedPeer = "";
    this.sessionToken = newNearbySessionToken();
    this.files = [];
    this.transfers = [];
    this.activeSockets = new Set();
    this.fileWaiters = [];
  }

  qrPayload() {
    return `fluxnearby://receive?host=${this.host}&port=${this.port}&server=${this.sessionToken}`;
  }

  state() {
    this.refreshAdvertisedHost();
    return {
      running: this.running,
      host: this.host,
      port: this.port,
      payload: this.qrPayload(),
      status: this.status,
      connectedPeer: this.connectedPeer,
      deviceName: deviceName(),
      interfaces: listLocalAddresses(),
      selectedFiles: this.files.map((item) => ({
        id: item.id,
        name: item.name,
        size: item.size,
        mimeType: item.mimeType,
        path: item.path
      })),
      transfers: this.transfers.map((item) => ({ ...item }))
    };
  }

  setStatus(status) {
    this.status = status;
    this.emitState();
  }

  emitState() {
    this.emit("state", this.state());
  }

  refreshAdvertisedHost() {
    if (!this.connectedPeer) {
      this.host = localAddressOrPreferred(this.host);
    }
  }

  async setFiles(filePaths) {
    const seen = new Set();
    const nextFiles = [];
    for (const filePath of filePaths || []) {
      const resolved = path.resolve(String(filePath || ""));
      if (!resolved || seen.has(resolved)) {
        continue;
      }
      const stat = await fsp.stat(resolved);
      if (!stat.isFile()) {
        continue;
      }
      seen.add(resolved);
      const name = safeFileName(path.basename(resolved));
      nextFiles.push({
        id: desktopFileId(resolved, stat),
        path: resolved,
        name,
        mimeType: mimeTypeForFile(resolved),
        size: stat.size
      });
    }
    this.files = nextFiles;
    this.transfers = nextFiles.map((file) => this.upsertTransfer({
      id: file.id,
      name: file.name,
      size: file.size,
      transferred: 0,
      speed: 0,
      status: "Ready",
      path: file.path,
      updatedAt: Date.now(),
      sampleAt: Date.now(),
      sampleBytes: 0
    }));
    if (nextFiles.length) {
      this.resolveFileWaiters();
    }
    this.setStatus(this.senderReadyStatus(nextFiles.length));
    return this.state();
  }

  clearFiles() {
    this.files = [];
    this.transfers = [];
    this.setStatus(this.connectedPeer ? "Phone connected. Choose files to send." : "Choose files to send.");
    return this.state();
  }

  senderReadyStatus(fileCount) {
    if (!fileCount) {
      return this.connectedPeer ? "Phone connected. Choose files to send." : "Choose files to send.";
    }
    if (this.connectedPeer) {
      return `${fileCount} file${fileCount === 1 ? "" : "s"} ready. Sending now.`;
    }
    if (this.running) {
      return `${fileCount} file${fileCount === 1 ? "" : "s"} ready. Let the phone scan the QR.`;
    }
    return `${fileCount} file${fileCount === 1 ? "" : "s"} ready. Start sender.`;
  }

  async start(options = {}) {
    if (options.fresh) {
      this.files = [];
      this.transfers = [];
      this.connectedPeer = "";
      this.sessionToken = newNearbySessionToken();
      this.rejectFileWaiters(new Error("Sender restarted."));
    }
    const requestedHost = Object.prototype.hasOwnProperty.call(options, "host")
      ? options.host
      : options.fresh ? "" : this.host;
    this.host = localAddressOrPreferred(requestedHost);
    if (this.running) {
      this.emitState();
      return this.state();
    }
    this.sessionToken = newNearbySessionToken();
    this.server = net.createServer((socket) => this.handleSocket(socket));
    await new Promise((resolve, reject) => {
      const onError = (error) => reject(error);
      this.server.once("error", onError);
      this.server.listen(this.port, "0.0.0.0", () => {
        this.server.off("error", onError);
        this.port = this.server.address().port;
        resolve();
      });
    }).catch(async (error) => {
      if (error.code !== "EADDRINUSE") {
        throw error;
      }
      this.server = net.createServer((socket) => this.handleSocket(socket));
      await new Promise((resolve, reject) => {
        this.server.once("error", reject);
        this.server.listen(0, "0.0.0.0", () => {
          this.port = this.server.address().port;
          resolve();
        });
      });
    });
    this.running = true;
    this.setStatus(`Show this QR to the phone. Waiting for scan.`);
    return this.state();
  }

  clearSession() {
    this.files = [];
    this.transfers = [];
    this.connectedPeer = "";
    this.sessionToken = newNearbySessionToken();
    this.rejectFileWaiters(new Error("Sender reset."));
    this.setStatus(this.running ? "Show this QR to the phone. Waiting for scan." : "Tap Send To Phone to show QR.");
    return this.state();
  }

  async stop() {
    if (!this.running) {
      return this.state();
    }
    for (const socket of this.activeSockets) {
      socket.destroy();
    }
    this.activeSockets.clear();
    await new Promise((resolve) => this.server.close(resolve));
    this.server = null;
    this.running = false;
    this.connectedPeer = "";
    this.rejectFileWaiters(new Error("Sender stopped."));
    this.setStatus("Sender stopped.");
    return this.state();
  }

  async handleSocket(socket) {
    this.activeSockets.add(socket);
    socket.on("close", () => this.activeSockets.delete(socket));
    const reader = new BufferedSocket(socket);
    const peer = String(socket.remoteAddress || "phone").replace(/^::ffff:/, "");
    try {
      this.connectedPeer = peer;
      if (!this.files.length) {
        this.setStatus("Phone connected. Choose files to send.");
        await this.waitForFiles(socket);
      }
      const files = this.files.slice();
      this.setStatus(`Phone connected. Sending ${files.length} file${files.length === 1 ? "" : "s"}.`);
      await this.sendFiles(socket, reader, files);
      this.connectedPeer = "";
      this.setStatus(`Sent ${files.length} file${files.length === 1 ? "" : "s"}.`);
      socket.end();
    } catch (error) {
      this.connectedPeer = "";
      this.markActiveTransfersInterrupted();
      this.setStatus(isSocketDisconnectError(error)
        ? "Phone disconnected. Scan again to resume."
        : `Send failed: ${error.message}`);
      socket.destroy();
    }
  }

  waitForFiles(socket) {
    if (this.files.length) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      let waiter = null;
      const cleanup = () => {
        socket.off("close", onClose);
        const index = this.fileWaiters.indexOf(waiter);
        if (index >= 0) {
          this.fileWaiters.splice(index, 1);
        }
      };
      const onClose = () => waiter.reject(new Error("Socket closed"));
      waiter = {
        resolve: () => {
          cleanup();
          resolve();
        },
        reject: (error) => {
          cleanup();
          reject(error);
        }
      };
      socket.once("close", onClose);
      this.fileWaiters.push(waiter);
    });
  }

  resolveFileWaiters() {
    const waiters = this.fileWaiters.splice(0);
    for (const waiter of waiters) {
      waiter.resolve();
    }
  }

  rejectFileWaiters(error) {
    const waiters = this.fileWaiters.splice(0);
    for (const waiter of waiters) {
      waiter.reject(error);
    }
  }

  async sendFiles(socket, reader, files) {
    writeJavaUtf(socket, nearbyConnectedEvent(this.sessionToken));
    writeJavaUtf(socket, deviceName());
    writeJavaUtf(socket, NEARBY_TRANSFER_MAGIC);
    writeInt(socket, files.length);
    for (const file of files) {
      writeJavaUtf(socket, file.id);
      writeJavaUtf(socket, file.name);
      writeJavaUtf(socket, file.mimeType);
      writeLong(socket, file.size);
    }
    for (const file of files) {
      const item = this.upsertTransfer({
        id: file.id,
        name: file.name,
        size: file.size,
        transferred: 0,
        speed: 0,
        status: "Waiting",
        path: file.path,
        updatedAt: Date.now(),
        sampleAt: Date.now(),
        sampleBytes: 0
      });
      const requestedOffset = Number(await reader.readLong());
      if (requestedOffset === Number(NEARBY_RECEIVER_CANCEL_OFFSET)) {
        await writeIntAsync(socket, NEARBY_CHUNK_CANCELLED);
        this.updateTransferProgress(item, "Cancelled", 0, file.size);
        continue;
      }
      const offset = Math.max(0, Math.min(requestedOffset, file.size));
      await this.sendOneFile(socket, file, item, offset);
    }
  }

  async sendOneFile(socket, file, item, offset) {
    this.updateTransferProgress(item, offset > 0 ? "Resuming" : "Copying", offset, file.size);
    let sent = offset;
    const stream = fs.createReadStream(file.path, {
      start: offset,
      highWaterMark: TRANSFER_BUFFER_SIZE
    });
    try {
      for await (const chunk of stream) {
        await writeIntAsync(socket, chunk.length);
        await writeBuffer(socket, chunk);
        sent += chunk.length;
        this.updateTransferProgress(item, "Copying", sent, file.size);
      }
      await writeIntAsync(socket, NEARBY_CHUNK_DONE);
      this.updateTransferProgress(item, "Completed", file.size, file.size);
    } catch (error) {
      this.updateTransferProgress(item, "Interrupted", sent, file.size);
      throw error;
    }
  }

  upsertTransfer(next) {
    const existing = this.transfers.find((item) => item.id === next.id);
    if (existing) {
      Object.assign(existing, next);
      return existing;
    }
    this.transfers.unshift(next);
    return next;
  }

  markActiveTransfersInterrupted() {
    let changed = false;
    for (const item of this.transfers) {
      if (item.status === "Copying" || item.status === "Resuming" || item.status === "Waiting") {
        item.status = "Interrupted";
        item.speed = 0;
        item.updatedAt = Date.now();
        changed = true;
      }
    }
    if (changed) {
      this.emitState();
    }
  }

  updateTransferProgress(item, status, transferred, total) {
    const now = Date.now();
    const elapsed = Math.max(1, now - (item.sampleAt || now));
    if (elapsed >= 500) {
      const delta = Math.max(0, transferred - (item.sampleBytes || 0));
      item.speed = Math.round(delta * 1000 / elapsed);
      item.sampleAt = now;
      item.sampleBytes = transferred;
    }
    if (status === "Completed" || status === "Cancelled" || status === "Interrupted") {
      item.speed = 0;
    }
    item.status = status;
    item.transferred = Math.max(0, transferred);
    item.size = total;
    item.updatedAt = now;
    this.emitState();
  }
}

class DesktopHttpApp {
  constructor(
    receiver,
    sender = new NearbySender({ host: receiver.host }),
    localShare = new DesktopWebShare({ scope: "local", title: "Local Web Share", approvalRequired: false }),
    liveShare = new DesktopLiveShare(new DesktopWebShare({
      scope: "live",
      title: "Live Share",
      loopbackOnly: true,
      approvalRequired: true,
      status: "Live Share stopped."
    }))
  ) {
    this.receiver = receiver;
    this.sender = sender;
    this.localShare = localShare;
    this.liveShare = liveShare;
    this.server = null;
    this.clients = new Set();
    this.receiver.on("state", () => this.broadcastState());
    this.sender.on("state", () => this.broadcastState());
    this.localShare.on("state", () => this.broadcastState());
    this.liveShare.on("state", () => this.broadcastState());
  }

  state() {
    return {
      receiver: this.receiver.state(),
      sender: this.sender.state(),
      localShare: this.localShare.state(),
      liveShare: this.liveShare.state()
    };
  }

  async start(port = 0) {
    this.server = http.createServer((request, response) => this.handle(request, response));
    await new Promise((resolve, reject) => {
      this.server.once("error", reject);
      this.server.listen(port, "127.0.0.1", resolve);
    });
    return this.url();
  }

  url() {
    const address = this.server.address();
    return `http://127.0.0.1:${address.port}`;
  }

  broadcastState() {
    const payload = `data: ${JSON.stringify(this.state())}\n\n`;
    for (const client of this.clients) {
      try {
        client.response.write(payload);
      } catch (_) {
        clearInterval(client.heartbeat);
        this.clients.delete(client);
      }
    }
  }

  async handle(request, response) {
    const requestUrl = new URL(request.url, "http://127.0.0.1");
    try {
      if (requestUrl.pathname === "/events") {
        this.handleEvents(response);
        return;
      }
      if (requestUrl.pathname === "/api/state") {
        this.json(response, this.state());
        return;
      }
      if ((requestUrl.pathname === "/api/start" || requestUrl.pathname === "/api/receive/start")
          && request.method === "POST") {
        const body = await readJsonBody(request);
        if (body.fresh) {
          await this.sender.stop();
          this.sender.clearSession();
        }
        await this.receiver.start({ host: body.host, fresh: !!body.fresh });
        this.json(response, this.state());
        return;
      }
      if ((requestUrl.pathname === "/api/stop" || requestUrl.pathname === "/api/receive/stop")
          && request.method === "POST") {
        await this.receiver.stop();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/send/start" && request.method === "POST") {
        const body = await readJsonBody(request);
        if (body.fresh) {
          await this.receiver.stop();
          this.receiver.clearSession();
        }
        await this.sender.start({ host: body.host, fresh: !!body.fresh });
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/send/stop" && request.method === "POST") {
        await this.sender.stop();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/send/files" && request.method === "POST") {
        const body = await readJsonBody(request);
        await this.sender.setFiles(body.paths || []);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/send/clear" && request.method === "POST") {
        this.sender.clearFiles();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/local/files" && request.method === "POST") {
        const body = await readJsonBody(request);
        await this.localShare.setFiles(body.paths || []);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/local/clear" && request.method === "POST") {
        this.localShare.clearFiles();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/local/start" && request.method === "POST") {
        const body = await readJsonBody(request);
        await this.localShare.start({ host: body.host });
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/local/stop" && request.method === "POST") {
        await this.localShare.stop();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/live/files" && request.method === "POST") {
        const body = await readJsonBody(request);
        await this.liveShare.setFiles(body.paths || []);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/live/clear" && request.method === "POST") {
        this.liveShare.clearFiles();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/live/start" && request.method === "POST") {
        await this.liveShare.start();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/live/stop" && request.method === "POST") {
        await this.liveShare.stop();
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/web/approve" && request.method === "POST") {
        const body = await readJsonBody(request);
        const share = body.scope === "live" ? this.liveShare : this.localShare;
        share.approveRequest(body.id);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/web/deny" && request.method === "POST") {
        const body = await readJsonBody(request);
        const share = body.scope === "live" ? this.liveShare : this.localShare;
        share.denyRequest(body.id);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/web/revoke" && request.method === "POST") {
        const body = await readJsonBody(request);
        const share = body.scope === "live" ? this.liveShare : this.localShare;
        share.revokeVisitor(body.token);
        this.json(response, this.state());
        return;
      }
      if (requestUrl.pathname === "/api/open-folder" && request.method === "POST") {
        openFolder(this.receiver.saveDir);
        this.json(response, { ok: true });
        return;
      }
      await this.serveStatic(requestUrl.pathname, response);
    } catch (error) {
      this.json(response, { error: error.message }, 500);
    }
  }

  handleEvents(response) {
    response.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no"
    });
    response.flushHeaders?.();
    response.write(`data: ${JSON.stringify(this.state())}\n\n`);
    const client = {
      response,
      heartbeat: setInterval(() => {
        try {
          response.write(": keepalive\n\n");
        } catch (_) {
          clearInterval(client.heartbeat);
          this.clients.delete(client);
        }
      }, 15000)
    };
    client.heartbeat.unref?.();
    this.clients.add(client);
    response.on("close", () => {
      clearInterval(client.heartbeat);
      this.clients.delete(client);
    });
  }

  async serveStatic(routePath, response) {
    const publicDir = path.join(__dirname, "..", "public");
    const safeRoute = routePath === "/" ? "/index.html" : routePath;
    const filePath = path.normalize(path.join(publicDir, safeRoute));
    if (!filePath.startsWith(publicDir)) {
      this.text(response, "Forbidden", 403);
      return;
    }
    const data = await fsp.readFile(filePath);
    const extension = path.extname(filePath);
    const type = {
      ".html": "text/html; charset=utf-8",
      ".css": "text/css; charset=utf-8",
      ".js": "text/javascript; charset=utf-8",
      ".svg": "image/svg+xml"
    }[extension] || "application/octet-stream";
    response.writeHead(200, { "Content-Type": type, "Cache-Control": "no-store" });
    response.end(data);
  }

  json(response, data, status = 200) {
    const raw = JSON.stringify(data);
    response.writeHead(status, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(raw),
      "Cache-Control": "no-store"
    });
    response.end(raw);
  }

  text(response, data, status = 200) {
    response.writeHead(status, { "Content-Type": "text/plain; charset=utf-8" });
    response.end(data);
  }
}

async function readJsonBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  if (chunks.length === 0) {
    return {};
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function openFolder(folder) {
  const platform = process.platform;
  if (platform === "darwin") {
    spawn("open", [folder], { detached: true, stdio: "ignore" }).unref();
  } else if (platform === "win32") {
    spawn("explorer", [folder], { detached: true, stdio: "ignore" }).unref();
  } else {
    spawn("xdg-open", [folder], { detached: true, stdio: "ignore" }).unref();
  }
}

function isSocketDisconnectError(error) {
  const code = String(error && error.code || "");
  if (["ECONNRESET", "EPIPE", "ETIMEDOUT"].includes(code)) {
    return true;
  }
  const message = String(error && error.message || "").toLowerCase();
  return message.includes("socket ended")
    || message.includes("socket closed")
    || message.includes("connection reset")
    || message.includes("broken pipe")
    || message.includes("timed out")
    || message.includes("transfer ended early");
}

async function createElectronWindow(electron, url) {
  const window = new electron.BrowserWindow({
    width: 1080,
    height: 760,
    minWidth: 360,
    minHeight: 520,
    backgroundColor: "#071013",
    title: "Flux File Share Desktop",
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow = window;
  let shown = false;
  const showWindow = () => {
    if (shown || window.isDestroyed()) {
      return;
    }
    shown = true;
    window.show();
    window.focus();
  };

  window.once("ready-to-show", showWindow);
  window.on("closed", () => {
    if (mainWindow === window) {
      mainWindow = null;
    }
  });

  await window.loadURL(url);
  showWindow();
  setTimeout(showWindow, 1200).unref?.();
  console.log("[desktop] Electron window opened.");
  return window;
}

async function boot() {
  const receiver = new NearbyReceiver();
  const sender = new NearbySender({ host: receiver.host });
  const localShare = new DesktopWebShare({
    scope: "local",
    title: "Local Web Share",
    approvalRequired: false,
    defaultPort: 8080
  });
  const liveShare = new DesktopLiveShare(new DesktopWebShare({
    scope: "live",
    title: "Live Share",
    loopbackOnly: true,
    approvalRequired: true,
    status: "Live Share stopped."
  }));
  const webApp = new DesktopHttpApp(receiver, sender, localShare, liveShare);
  const url = await webApp.start(0);
  console.log(`[desktop] UI server: ${url}`);
  console.log("[desktop] Choose Nearby, Local Web Share, or Live Share in the desktop window.");

  const browserMode = process.argv.includes("--browser");
  let electron = null;
  if (!browserMode) {
    try {
      electron = require("electron");
    } catch (_) {
      electron = null;
    }
  }

  if (electron && electron.app) {
    electron.app.setName("Flux File Share");
    await electron.app.whenReady();
    electron.ipcMain.removeHandler("flux:choose-files");
    electron.ipcMain.handle("flux:choose-files", async () => {
      const result = await electron.dialog.showOpenDialog(mainWindow || undefined, {
        title: "Choose Files To Send",
        properties: ["openFile", "multiSelections"]
      });
      return result.canceled ? [] : result.filePaths;
    });
    await createElectronWindow(electron, url);
    electron.app.on("activate", async () => {
      if (electron.BrowserWindow.getAllWindows().length === 0) {
        await createElectronWindow(electron, url);
      } else if (mainWindow) {
        mainWindow.show();
        mainWindow.focus();
      }
    });
    electron.app.on("window-all-closed", () => {
      Promise.allSettled([
        receiver.stop(),
        sender.stop(),
        localShare.stop(),
        liveShare.stop()
      ]).finally(() => electron.app.quit());
    });
  } else {
    console.log(`Flux File Share Desktop is running at ${url}`);
    console.log("Choose Nearby, Local Web Share, or Live Share in the desktop UI.");
  }
}

if (require.main === module || process.versions.electron) {
  boot().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}

module.exports = {
  NearbyReceiver,
  NearbySender,
  DesktopWebShare,
  DesktopLiveShare,
  BufferedSocket,
  DesktopHttpApp,
  constants: {
    NEARBY_TRANSFER_PORT,
    NEARBY_QR_SEND_PORT,
    TRANSFER_BUFFER_SIZE,
    NEARBY_CONNECTED_EVENT,
    NEARBY_TRANSFER_MAGIC,
    NEARBY_QUEUE_MAGIC,
    NEARBY_PRESENCE_MAGIC,
    NEARBY_CANCEL_MAGIC,
    NEARBY_FILE_CANCEL_MAGIC,
    NEARBY_ACK_MAGIC,
    NEARBY_SESSION_MISMATCH_MAGIC,
    NEARBY_CHUNK_DONE,
    NEARBY_CHUNK_CANCELLED,
    NEARBY_RECEIVER_CANCEL_OFFSET
  },
  writeJavaUtf,
  writeInt,
  writeLong,
  nearbyConnectedEvent,
  formatBytes,
  listLocalAddresses
};
