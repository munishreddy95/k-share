importScripts("db.js");

const DEFAULT_RELAY_WS_URL = "wss://share.example.com/agent";
const CHUNK_BYTES = 512 * 1024;
const ACCESS_NOTIFICATION_PREFIX = "flux-access-";

let socket = null;
let reconnectTimer = null;
let state = {
  running: false,
  connected: false,
  status: "Live Share is off.",
  relayWsUrl: DEFAULT_RELAY_WS_URL,
  slug: "",
  token: "",
  publicUrl: "",
  fullUrl: "",
  shortUrl: "",
  tinyUrlError: "",
  bytesServed: 0,
  pendingRequests: [],
  visitors: []
};

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.local.get({ relayWsUrl: DEFAULT_RELAY_WS_URL }, (stored) => {
    chrome.storage.local.set({ relayWsUrl: stored.relayWsUrl || DEFAULT_RELAY_WS_URL });
  });
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  handleMessage(message || {})
    .then((payload) => sendResponse({ ok: true, ...payload }))
    .catch((error) => sendResponse({ ok: false, error: error.message || "Extension failed." }));
  return true;
});

chrome.notifications.onClicked.addListener((notificationId) => {
  if (!notificationId.startsWith(ACCESS_NOTIFICATION_PREFIX)) {
    return;
  }
  chrome.notifications.clear(notificationId);
  openAccessView().catch(() => {});
});

chrome.notifications.onButtonClicked.addListener((notificationId, buttonIndex) => {
  if (!notificationId.startsWith(ACCESS_NOTIFICATION_PREFIX)) {
    return;
  }
  const requestId = notificationId.slice(ACCESS_NOTIFICATION_PREFIX.length);
  try {
    if (buttonIndex === 0) {
      approveRequest(requestId);
    } else {
      denyRequest(requestId);
    }
    chrome.notifications.clear(notificationId);
    broadcastState();
  } catch (error) {
    setStatus(error.message || "Approval request is no longer pending.");
  }
});

async function handleMessage(message) {
  if (message.type === "state.get") {
    return { state: await publicState() };
  }
  if (message.type === "files.updated") {
    setStatus("Files selected.");
    return { state: await publicState() };
  }
  if (message.type === "files.clear") {
    await FluxDb.clearFiles();
    setStatus("Selected files cleared.");
    return { state: await publicState() };
  }
  if (message.type === "file.remove") {
    await FluxDb.removeFile(message.id);
    setStatus("File removed.");
    return { state: await publicState() };
  }
  if (message.type === "share.start") {
    if (message.relayWsUrl) {
      state.relayWsUrl = normalizeRelayWsUrl(message.relayWsUrl);
      await chromeStorageSet({ relayWsUrl: state.relayWsUrl });
    }
    await startShare();
    return { state: await publicState() };
  }
  if (message.type === "share.stop") {
    await stopShare();
    return { state: await publicState() };
  }
  if (message.type === "access.approve") {
    approveRequest(message.id);
    return { state: await publicState() };
  }
  if (message.type === "access.deny") {
    denyRequest(message.id);
    return { state: await publicState() };
  }
  if (message.type === "visitor.remove") {
    removeVisitor(message.token);
    return { state: await publicState() };
  }
  throw new Error("Unknown extension command.");
}

async function publicState() {
  const stored = await chromeStorageGet({ relayWsUrl: DEFAULT_RELAY_WS_URL });
  state.relayWsUrl = state.relayWsUrl || stored.relayWsUrl || DEFAULT_RELAY_WS_URL;
  return {
    ...state,
    files: await FluxDb.listFiles()
  };
}

async function startShare() {
  const files = await FluxDb.listFiles();
  if (!files.length) {
    throw new Error("Choose files first.");
  }
  await closeSocket();
  clearTimeout(reconnectTimer);
  state.running = true;
  state.connected = false;
  state.slug = createSlug();
  state.token = randomHex(16);
  state.publicUrl = "";
  state.fullUrl = "";
  state.shortUrl = "";
  state.tinyUrlError = "";
  state.pendingRequests = [];
  state.visitors = [];
  updateBadge();
  setStatus("Connecting to K-Share relay...");
  connectRelay();
}

async function stopShare() {
  state.running = false;
  state.connected = false;
  state.publicUrl = "";
  state.fullUrl = "";
  state.shortUrl = "";
  state.tinyUrlError = "";
  state.pendingRequests = [];
  state.visitors = [];
  updateBadge();
  clearTimeout(reconnectTimer);
  reconnectTimer = null;
  await closeSocket();
  setStatus("Live Share is off.");
}

function connectRelay() {
  if (!state.running) {
    return;
  }
  try {
    socket = new WebSocket(state.relayWsUrl || DEFAULT_RELAY_WS_URL);
  } catch (error) {
    scheduleReconnect(error);
    return;
  }
  socket.onopen = () => {
    sendFrame({
      type: "register",
      slug: state.slug,
      token: state.token,
      deviceName: deviceName(),
      app: "K-Share Browser Extension"
    });
  };
  socket.onmessage = (event) => {
    let frame;
    try {
      frame = JSON.parse(event.data);
    } catch (error) {
      setStatus("Relay sent an invalid message.");
      return;
    }
    handleRelayFrame(frame);
  };
  socket.onerror = () => {
    scheduleReconnect(new Error("Relay connection failed."));
  };
  socket.onclose = () => {
    if (state.running) {
      scheduleReconnect(new Error("Relay disconnected."));
    }
  };
}

function handleRelayFrame(frame) {
  if (frame.type === "registered") {
    state.connected = true;
    state.publicUrl = String(frame.publicUrl || frame.shortUrl || frame.fullUrl || "");
    state.fullUrl = String(frame.fullUrl || "");
    state.shortUrl = String(frame.shortUrl || "");
    state.tinyUrlError = String(frame.tinyUrlError || "");
    setStatus("Live Share is online.");
    return;
  }
  if (frame.type === "request") {
    handleRelayRequest(frame).catch((error) => {
      sendResponseError(frame.id, error.message || "Extension could not serve this request.");
    });
    return;
  }
  if (frame.type === "cancel") {
    return;
  }
  if (frame.type === "ping") {
    sendFrame({ type: "pong" });
  }
}

async function handleRelayRequest(frame) {
  const id = String(frame.id || "");
  if (!id) {
    return;
  }
  const method = String(frame.method || "GET").toUpperCase();
  const requestUrl = new URL(frame.path || "/", "https://flux-extension.local");
  const headers = frame.headers || {};
  const bodyBytes = frame.body ? base64ToBytes(String(frame.body)) : new Uint8Array();

  if (requestUrl.pathname === "/healthz") {
    await sendJson(id, 200, { ok: true, app: "K-Share Browser Extension" });
    return;
  }
  if (requestUrl.pathname === "/access/request" && method === "POST") {
    await handleAccessRequest(id, headers, bodyBytes);
    return;
  }
  if (requestUrl.pathname === "/access/session") {
    await handleAccessSession(id, requestUrl);
    return;
  }
  if (method !== "GET" && method !== "HEAD") {
    await sendText(id, 405, "Only GET and HEAD are supported.");
    return;
  }

  const visitor = approvedVisitor(requestUrl, headers);
  if (!visitor) {
    await sendApprovalPage(id);
    return;
  }
  touchVisitor(visitor, headers, 0);

  if (requestUrl.pathname === "/api/files") {
    await sendJson(id, 200, { files: await FluxDb.listFiles() }, approvedHeaders(requestUrl, visitor));
    return;
  }
  if (requestUrl.pathname.startsWith("/file/")) {
    await sendFile(id, method, headers, requestUrl, decodeURIComponent(requestUrl.pathname.slice("/file/".length)), visitor);
    return;
  }
  await sendLibraryPage(id, visitor, requestUrl);
}

async function handleAccessRequest(id, headers, bodyBytes) {
  let payload = {};
  try {
    payload = JSON.parse(new TextDecoder().decode(bodyBytes));
  } catch (_) {
    payload = {};
  }
  const email = String(payload.email || "").trim();
  if (!email || !email.includes("@")) {
    await sendJson(id, 400, { ok: false, error: "Enter a valid email address." });
    return;
  }
  const request = {
    id: crypto.randomUUID(),
    email,
    location: String(payload.location || "Location not shared").slice(0, 180),
    device: String(payload.device || "Browser").slice(0, 180),
    userAgent: String(payload.userAgent || headers["user-agent"] || "").slice(0, 240),
    ip: visitorIp(headers),
    createdAt: Date.now(),
    status: "Pending"
  };
  state.pendingRequests = [request, ...state.pendingRequests].slice(0, 20);
  updateBadge();
  notifyAccessRequest(request);
  broadcastState();
  await sendJson(id, 200, { ok: true, id: request.id });
}

async function handleAccessSession(id, requestUrl) {
  const requestId = requestUrl.searchParams.get("id") || "";
  const pending = state.pendingRequests.find((request) => request.id === requestId);
  if (pending) {
    await sendJson(id, 200, { ok: true, status: "pending" });
    return;
  }
  const visitor = state.visitors.find((session) => session.requestId === requestId || session.token === requestId);
  if (visitor) {
    await sendJson(id, 200, { ok: true, status: "approved", token: visitor.token });
    return;
  }
  await sendJson(id, 404, { ok: false, status: "not-found" });
}

function approveRequest(id) {
  const request = state.pendingRequests.find((item) => item.id === id);
  if (!request) {
    throw new Error("Approval request is no longer pending.");
  }
  state.pendingRequests = state.pendingRequests.filter((item) => item.id !== id);
  state.visitors = [{
    token: randomHex(18),
    requestId: request.id,
    email: request.email,
    location: request.location,
    device: request.device,
    userAgent: request.userAgent,
    ip: request.ip,
    connectedAt: Date.now(),
    lastSeenAt: Date.now(),
    bytesServed: 0,
    status: "Approved"
  }, ...state.visitors].slice(0, 30);
  clearAccessNotification(id);
  updateBadge();
  setStatus("Visitor approved.");
}

function denyRequest(id) {
  state.pendingRequests = state.pendingRequests.filter((item) => item.id !== id);
  clearAccessNotification(id);
  updateBadge();
  setStatus("Visitor denied.");
}

function removeVisitor(token) {
  state.visitors = state.visitors.filter((visitor) => visitor.token !== token);
  setStatus("Visitor removed.");
}

function notifyAccessRequest(request) {
  const notificationId = `${ACCESS_NOTIFICATION_PREFIX}${request.id}`;
  const messageParts = [
    request.email,
    request.device,
    request.ip
  ].filter(Boolean);
  chrome.notifications.create(notificationId, {
    type: "basic",
    iconUrl: "icons/icon-128.png",
    title: "K-Share access request",
    message: messageParts.join(" | ") || "Someone wants to access your shared files.",
    priority: 2,
    requireInteraction: true,
    buttons: [
      { title: "Approve" },
      { title: "Deny" }
    ]
  }, () => {
    chrome.runtime.lastError;
  });
}

function clearAccessNotification(requestId) {
  chrome.notifications.clear(`${ACCESS_NOTIFICATION_PREFIX}${requestId}`);
}

function updateBadge() {
  const count = state.pendingRequests.length;
  chrome.action.setBadgeText({ text: count ? String(Math.min(count, 99)) : "" });
  chrome.action.setBadgeBackgroundColor({ color: count ? "#ff806b" : "#3dc6be" });
}

async function openAccessView() {
  await chromeStorageSet({ preferredPopupTab: "access" });
  if (chrome.action && typeof chrome.action.openPopup === "function") {
    try {
      const result = chrome.action.openPopup();
      if (result && typeof result.then === "function") {
        await result;
      }
      return;
    } catch (_) {
      // Some Chrome builds only allow openPopup from narrow user gestures.
    }
  }
  await new Promise((resolve) => {
    chrome.windows.create({
      url: chrome.runtime.getURL("popup.html#access"),
      type: "popup",
      width: 410,
      height: 660,
      focused: true
    }, () => resolve());
  });
}

function approvedVisitor(requestUrl, headers) {
  const cookies = parseCookies(headers.cookie);
  const token = requestUrl.searchParams.get("access") || cookies.flux_ext_access || "";
  return state.visitors.find((visitor) => visitor.token === token) || null;
}

function approvedHeaders(requestUrl, visitor) {
  if (!requestUrl.searchParams.get("access")) {
    return {};
  }
  return {
    "set-cookie": `flux_ext_access=${encodeURIComponent(visitor.token)}; Path=/; HttpOnly; SameSite=Lax`
  };
}

function touchVisitor(visitor, headers, bytes) {
  visitor.lastSeenAt = Date.now();
  visitor.ip = visitor.ip || visitorIp(headers);
  visitor.bytesServed += Math.max(0, bytes || 0);
  visitor.status = "Connected";
  state.bytesServed += Math.max(0, bytes || 0);
  broadcastState();
}

async function sendFile(id, method, headers, requestUrl, fileId, visitor) {
  const record = await FluxDb.getFile(fileId);
  if (!record || !record.blob) {
    await sendText(id, 404, "File not found.");
    return;
  }
  const size = Number(record.size || record.blob.size || 0);
  let start = 0;
  let end = Math.max(0, size - 1);
  let status = 200;
  const range = String(headers.range || "");
  if (range.startsWith("bytes=")) {
    const parts = range.slice(6).split("-");
    start = parts[0] ? Number.parseInt(parts[0], 10) : 0;
    end = parts[1] ? Number.parseInt(parts[1], 10) : end;
    if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || end >= size || start > end) {
      sendFrame({
        type: "responseStart",
        id,
        status: 416,
        headers: { "content-range": `bytes */${size}` }
      });
      sendFrame({ type: "responseEnd", id });
      return;
    }
    status = 206;
  }
  const length = size === 0 ? 0 : end - start + 1;
  const responseHeaders = {
    ...approvedHeaders(requestUrl, visitor),
    "content-type": record.type || "application/octet-stream",
    "content-length": String(length),
    "accept-ranges": "bytes",
    "cache-control": "private, max-age=60",
    "content-disposition": contentDisposition(record.name, requestUrl.searchParams.get("download") === "1")
  };
  if (status === 206) {
    responseHeaders["content-range"] = `bytes ${start}-${end}/${size}`;
  }
  sendFrame({ type: "responseStart", id, status, headers: responseHeaders });
  if (method === "HEAD" || length === 0) {
    sendFrame({ type: "responseEnd", id });
    return;
  }
  let sent = 0;
  for (let offset = start; offset <= end; offset += CHUNK_BYTES) {
    const chunkEnd = Math.min(end + 1, offset + CHUNK_BYTES);
    const chunk = new Uint8Array(await record.blob.slice(offset, chunkEnd).arrayBuffer());
    sent += chunk.byteLength;
    sendFrame({ type: "responseChunk", id, data: bytesToBase64(chunk) });
  }
  touchVisitor(visitor, headers, sent);
  sendFrame({ type: "responseEnd", id });
}

async function sendLibraryPage(id, visitor, requestUrl) {
  const files = await FluxDb.listFiles();
  const filesJson = JSON.stringify(files);
  const body = `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>K-Share Browser Share</title>
<style>:root{color-scheme:dark;--bg:#05080a;--panel:#0e181c;--line:#263c45;--ink:#f6fbf9;--muted:#93aaa8;--teal:#3dc6be;--amber:#ffaa60}*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at 0 0,#16484e,transparent 36%),linear-gradient(135deg,#061014,#05080a);color:var(--ink);font-family:system-ui,-apple-system,sans-serif}.shell{width:min(1040px,100%);margin:auto;padding:20px}.eyebrow{margin:0 0 6px;color:var(--amber);letter-spacing:.14em;text-transform:uppercase;font-size:11px;font-weight:900}h1{margin:0;font-size:clamp(34px,7vw,68px);line-height:.95;letter-spacing:-.06em}.subtle{color:var(--muted)}.search{width:100%;height:46px;border-radius:16px;border:1px solid var(--line);background:#071013;color:var(--ink);padding:0 14px;font:inherit;font-weight:800}.tabs{display:flex;gap:8px;overflow:auto;margin:12px 0}.tab{border:1px solid var(--line);background:#0e181c;color:var(--ink);border-radius:999px;padding:10px 14px;font-weight:900;white-space:nowrap}.tab.active{border-color:var(--teal);background:#09272c;color:#bdfbf6}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px;margin-top:14px}.file{border:1px solid var(--line);border-radius:20px;background:#0e181ce8;padding:14px;min-width:0}.badge{display:inline-grid;place-items:center;min-width:48px;height:38px;border-radius:14px;background:#132228;color:#bdfbf6;font-weight:1000}.file h2{font-size:16px;overflow-wrap:anywhere}.meta{color:var(--muted);font-size:13px}.actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}.btn{display:grid;place-items:center;text-decoration:none;min-height:40px;border:0;border-radius:14px;font-weight:900}.watch{background:linear-gradient(135deg,var(--teal),#7af7ed);color:#041113}.download{background:#132228;color:#f6fbf9;border:1px solid var(--line)}.empty{padding:24px;border:1px dashed var(--line);border-radius:20px;color:var(--muted);text-align:center}.modal{position:fixed;inset:0;background:#000b;display:none;place-items:center;padding:20px}.modal.open{display:grid}.player{width:min(920px,100%);border:1px solid var(--line);border-radius:24px;background:#071013;padding:14px}.player video,.player audio{width:100%;max-height:72vh}.player-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:10px}.close{border:1px solid var(--line);background:#132228;color:#fff;border-radius:12px;min-height:38px;padding:0 12px}@media(max-width:560px){.shell{padding:12px}.grid{grid-template-columns:1fr}.actions{grid-template-columns:1fr}}</style></head>
<body><main class="shell"><p class="eyebrow">K-Share browser share</p><h1>Shared from browser.</h1><p class="subtle">${files.length} selected file${files.length === 1 ? "" : "s"} available.</p><input class="search" id="search" placeholder="Search files"><nav class="tabs" id="tabs"></nav><section class="grid" id="grid"></section></main><section class="modal" id="modal"><div class="player"><div class="player-head"><strong id="playerTitle"></strong><button class="close" id="close">Close</button></div><div id="playerBody"></div></div></section>
<script>const files=${filesJson};const categories=[['all','All'],['videos','Videos'],['songs','Songs'],['files','Files'],['compressed','Compressed']];let active='all';const grid=document.getElementById('grid'),tabs=document.getElementById('tabs'),search=document.getElementById('search'),modal=document.getElementById('modal'),playerBody=document.getElementById('playerBody'),playerTitle=document.getElementById('playerTitle');function esc(v){return String(v??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}function size(bytes){let v=Number(bytes)||0,u=['B','KB','MB','GB','TB'],i=0;while(v>=1024&&i<u.length-1){v/=1024;i++}return (v>=10||i===0?v.toFixed(0):v.toFixed(1))+' '+u[i]}function ext(n){return (n.includes('.')?n.split('.').pop():'FILE').slice(0,4).toUpperCase()}function filtered(){const q=search.value.trim().toLowerCase();return files.filter(f=>(active==='all'||f.category===active)&&(!q||f.name.toLowerCase().includes(q)))}function renderTabs(){tabs.innerHTML=categories.map(([key,label])=>'<button class="tab '+(key===active?'active':'')+'" data-key="'+key+'">'+label+' '+files.filter(f=>key==='all'||f.category===key).length+'</button>').join('');tabs.querySelectorAll('button').forEach(button=>button.onclick=()=>{active=button.dataset.key;render()})}function render(){renderTabs();const list=filtered();grid.innerHTML=list.length?list.map(f=>'<article class="file"><span class="badge">'+esc(ext(f.name))+'</span><h2>'+esc(f.name)+'</h2><p class="meta">'+esc(f.mimeType||f.type)+' · '+size(f.size)+'</p><div class="actions">'+(f.playable?'<button class="btn watch" data-watch="'+esc(f.id)+'">Watch</button>':'<span></span>')+'<a class="btn download" href="/file/'+encodeURIComponent(f.id)+'?download=1">Download</a></div></article>').join(''):'<p class="empty">No files match this view.</p>';grid.querySelectorAll('[data-watch]').forEach(button=>button.onclick=()=>openPlayer(button.dataset.watch))}function openPlayer(id){const f=files.find(file=>file.id===id);if(!f)return;playerTitle.textContent=f.name;const src='/file/'+encodeURIComponent(f.id);playerBody.innerHTML=(f.mimeType||f.type||'').startsWith('audio/')?'<audio controls autoplay src="'+src+'"></audio>':'<video controls autoplay playsinline src="'+src+'"></video><p class="meta">If this codec cannot play in your browser, use Download.</p>';modal.classList.add('open')}document.getElementById('close').onclick=()=>{modal.classList.remove('open');playerBody.innerHTML=''};search.oninput=render;render();</script></body></html>`;
  await sendHtml(id, 200, body, approvedHeaders(requestUrl, visitor));
}

async function sendApprovalPage(id) {
  const body = `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>K-Share Access</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:radial-gradient(circle at top left,#15474d,transparent 38%),#05080a;color:#f6fbf9;font-family:system-ui,-apple-system,sans-serif}.card{width:min(440px,calc(100vw - 28px));border:1px solid #263c45;border-radius:26px;background:#0e181cee;padding:24px;box-shadow:0 24px 70px #0008}.eyebrow{color:#ffaa60;text-transform:uppercase;letter-spacing:.14em;font-size:11px;font-weight:900}.field{display:grid;gap:8px;margin:16px 0}input{height:48px;border-radius:16px;border:1px solid #263c45;background:#071013;color:#f6fbf9;padding:0 14px;font:inherit}button{width:100%;height:48px;border:0;border-radius:16px;background:linear-gradient(135deg,#3dc6be,#7af7ed);color:#041113;font-weight:900}.status{color:#93aaa8;line-height:1.45}.code{margin-top:14px;padding:12px;border-radius:14px;background:#09272c;color:#bdfbf6;font-weight:900}.hidden{display:none}</style></head>
<body><main class="card"><p class="eyebrow">K-Share browser share</p><h1>Request access</h1><p class="status">The browser owner must approve before files open.</p><form id="form"><label class="field"><span>Email address</span><input id="email" type="email" required autocomplete="email"></label><button id="button">Send approval request</button></form><p id="status" class="status"></p><p id="code" class="code hidden"></p></main>
<script>const form=document.getElementById('form'),email=document.getElementById('email'),status=document.getElementById('status'),code=document.getElementById('code'),button=document.getElementById('button');function device(){return [navigator.platform,navigator.vendor].filter(Boolean).join(' / ')||'Browser'}function poll(id){setTimeout(async()=>{try{const r=await fetch('/access/session?id='+encodeURIComponent(id),{cache:'no-store'});const p=await r.json();if(p.status==='approved'&&p.token){location.href='/?access='+encodeURIComponent(p.token);return;}if(p.status==='pending'){status.textContent='Waiting for approval...';poll(id);return;}status.textContent='Request was denied or expired.'}catch(e){status.textContent='Still waiting for approval...';poll(id)}},1200)}form.addEventListener('submit',async event=>{event.preventDefault();button.disabled=true;status.textContent='Sending request...';try{const r=await fetch('/access/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({email:email.value,device:device(),location:'Browser shared no precise location',userAgent:navigator.userAgent||''})});const p=await r.json();if(!r.ok||!p.ok)throw new Error(p.error||'Could not request access');code.textContent='Request sent';code.classList.remove('hidden');status.textContent='Waiting for approval...';poll(p.id)}catch(e){button.disabled=false;status.textContent=e.message||'Could not request access'}});</script></body></html>`;
  await sendHtml(id, 200, body);
}

async function sendJson(id, status, payload, extraHeaders) {
  await sendBytes(id, status, new TextEncoder().encode(JSON.stringify(payload)), {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    ...(extraHeaders || {})
  });
}

async function sendText(id, status, text, extraHeaders) {
  await sendBytes(id, status, new TextEncoder().encode(String(text || "")), {
    "content-type": "text/plain; charset=utf-8",
    "cache-control": "no-store",
    ...(extraHeaders || {})
  });
}

async function sendHtml(id, status, html, extraHeaders) {
  await sendBytes(id, status, new TextEncoder().encode(html), {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
    ...(extraHeaders || {})
  });
}

async function sendBytes(id, status, bytes, headers) {
  const responseHeaders = {
    ...headers,
    "content-length": String(bytes.byteLength)
  };
  sendFrame({ type: "responseStart", id, status, headers: responseHeaders });
  if (bytes.byteLength > 0) {
    sendFrame({ type: "responseChunk", id, data: bytesToBase64(bytes) });
  }
  sendFrame({ type: "responseEnd", id });
}

function sendResponseError(id, message) {
  if (!id) {
    return;
  }
  sendText(id, 502, message || "Extension could not serve this request.").catch(() => {});
}

function sendFrame(frame) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    throw new Error("Relay is not connected.");
  }
  socket.send(JSON.stringify(frame));
}

async function closeSocket() {
  if (socket) {
    socket.onclose = null;
    socket.onerror = null;
    socket.close();
    socket = null;
  }
}

function scheduleReconnect(error) {
  if (!state.running) {
    return;
  }
  state.connected = false;
  setStatus(`Relay offline. Reconnecting... ${error && error.message ? error.message : ""}`.trim());
  clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => connectRelay(), 3000);
}

function setStatus(status) {
  state.status = status;
  broadcastState();
}

function broadcastState() {
  try {
    const result = chrome.runtime.sendMessage({ type: "state.changed" });
    if (result && typeof result.catch === "function") {
      result.catch(() => {});
    }
  } catch (_) {
    // The popup is usually closed; state polling will catch up when it opens.
  }
}

function chromeStorageGet(defaults) {
  return new Promise((resolve) => chrome.storage.local.get(defaults, resolve));
}

function chromeStorageSet(values) {
  return new Promise((resolve) => chrome.storage.local.set(values, resolve));
}

function normalizeRelayWsUrl(value) {
  const raw = String(value || DEFAULT_RELAY_WS_URL).trim();
  if (raw.startsWith("wss://") || raw.startsWith("ws://")) {
    return raw;
  }
  if (raw.startsWith("https://")) {
    return raw.replace(/^https:\/\//, "wss://").replace(/\/+$/, "") + "/agent";
  }
  if (raw.startsWith("http://")) {
    return raw.replace(/^http:\/\//, "ws://").replace(/\/+$/, "") + "/agent";
  }
  return DEFAULT_RELAY_WS_URL;
}

function createSlug() {
  return `browser-${randomHex(4)}`.toLowerCase();
}

function randomHex(bytes) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return Array.from(data).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function deviceName() {
  return "Browser Extension";
}

function visitorIp(headers) {
  return String(headers["x-forwarded-for"] || headers["x-real-ip"] || "remote visitor").split(",")[0].trim();
}

function parseCookies(header) {
  const cookies = {};
  String(header || "").split(";").map((part) => part.trim()).filter(Boolean).forEach((part) => {
    const separator = part.indexOf("=");
    if (separator > 0) {
      cookies[part.slice(0, separator)] = decodeURIComponent(part.slice(separator + 1));
    }
  });
  return cookies;
}

function contentDisposition(fileName, download) {
  const safeName = String(fileName || "shared-file").replace(/[\\/:*?"<>|]/g, "_");
  return `${download ? "attachment" : "inline"}; filename="${safeName.replace(/"/g, "_")}"; filename*=UTF-8''${encodeURIComponent(safeName)}`;
}

function bytesToBase64(bytes) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.byteLength; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

function base64ToBytes(value) {
  if (!value) {
    return new Uint8Array();
  }
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}
