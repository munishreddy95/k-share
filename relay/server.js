'use strict';

const crypto = require('crypto');
const http = require('http');

const DEFAULT_PUBLIC_PORT = 4444;
const DEFAULT_MAX_FRAME_BYTES = 2 * 1024 * 1024;
const DEFAULT_REQUEST_TIMEOUT_MS = 120000;
const DEFAULT_TINY_URL_TIMEOUT_MS = 3000;
const MAX_PROXY_REQUEST_BODY_BYTES = 64 * 1024;
const WEBSOCKET_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function boolFromEnv(value, fallback = false) {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  return /^(1|true|yes|on)$/i.test(String(value));
}

function intFromEnv(value, fallback) {
  const parsed = Number.parseInt(String(value || ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizePath(value, fallback = '/agent') {
  const path = String(value || fallback).trim() || fallback;
  return path.startsWith('/') ? path : `/${path}`;
}

function safeSlug(value) {
  const slug = String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 48);
  return slug || `flux-${crypto.randomBytes(4).toString('hex')}`;
}

function appendQuery(url, key, value) {
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
}

function publicUrlFor(slug, token, config) {
  const base = String(config.publicBase || '').trim();
  let url;
  if (base.includes('{slug}')) {
    url = base.replaceAll('{slug}', slug);
  } else if (base) {
    url = `${base.replace(/\/+$/, '')}/${slug}/`;
  } else {
    url = `http://localhost:${config.publicPort}/${slug}/`;
  }
  if (!/[/?#]$/.test(url) && !url.includes('?')) {
    url += '/';
  }
  return token ? appendQuery(url, 'token', token) : url;
}

async function createTinyUrl(fullUrl, config) {
  const apiUrl = String(config.tinyUrlApiUrl || '').trim();
  const createToken = String(config.tinyUrlCreateToken || '').trim();
  if (!apiUrl || !createToken) {
    return null;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.tinyUrlTimeoutMs);
  try {
    const body = {
      url: fullUrl
    };
    if (config.tinyUrlTtlSeconds > 0) {
      body.ttlSeconds = config.tinyUrlTtlSeconds;
    }
    const response = await fetch(apiUrl, {
      method: 'POST',
      headers: {
        'authorization': `Bearer ${createToken}`,
        'content-type': 'application/json'
      },
      body: JSON.stringify(body),
      signal: controller.signal
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || !payload.shortUrl) {
      throw new Error(payload.error || `Tiny URL service returned HTTP ${response.status}`);
    }
    return String(payload.shortUrl);
  } finally {
    clearTimeout(timeout);
  }
}

function parseCookies(header) {
  const cookies = {};
  String(header || '')
    .split(';')
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      const separator = part.indexOf('=');
      if (separator > 0) {
        const key = part.slice(0, separator).trim();
        const value = part.slice(separator + 1).trim();
        cookies[key] = decodeURIComponent(value);
      }
    });
  return cookies;
}

function writeFrame(socket, frame, maxFrameBytes = DEFAULT_MAX_FRAME_BYTES) {
  const payload = Buffer.from(JSON.stringify(frame), 'utf8');
  if (payload.length > maxFrameBytes) {
    throw new Error(`Frame too large: ${payload.length}`);
  }
  const header = Buffer.allocUnsafe(4);
  header.writeUInt32BE(payload.length, 0);
  socket.write(Buffer.concat([header, payload]));
}

function attachFrameReader(socket, onFrame, onError, maxFrameBytes = DEFAULT_MAX_FRAME_BYTES) {
  let buffer = Buffer.alloc(0);
  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 4) {
      const size = buffer.readUInt32BE(0);
      if (size <= 0 || size > maxFrameBytes) {
        socket.destroy(new Error(`Invalid frame size: ${size}`));
        return;
      }
      if (buffer.length < size + 4) {
        return;
      }
      const payload = buffer.subarray(4, 4 + size);
      buffer = buffer.subarray(4 + size);
      try {
        onFrame(JSON.parse(payload.toString('utf8')));
      } catch (error) {
        onError(error);
      }
    }
  });
  socket.on('error', onError);
}

function writeWebSocketFrame(socket, opcode, payload = Buffer.alloc(0), maxFrameBytes = DEFAULT_MAX_FRAME_BYTES) {
  const data = Buffer.isBuffer(payload) ? payload : Buffer.from(payload);
  if (data.length > maxFrameBytes) {
    throw new Error(`Frame too large: ${data.length}`);
  }
  let header;
  if (data.length <= 125) {
    header = Buffer.from([0x80 | opcode, data.length]);
  } else if (data.length <= 0xffff) {
    header = Buffer.allocUnsafe(4);
    header[0] = 0x80 | opcode;
    header[1] = 126;
    header.writeUInt16BE(data.length, 2);
  } else {
    header = Buffer.allocUnsafe(10);
    header[0] = 0x80 | opcode;
    header[1] = 127;
    header.writeUInt32BE(0, 2);
    header.writeUInt32BE(data.length, 6);
  }
  socket.write(Buffer.concat([header, data]));
}

function writeWebSocketJson(socket, frame, maxFrameBytes = DEFAULT_MAX_FRAME_BYTES) {
  writeWebSocketFrame(socket, 0x1, Buffer.from(JSON.stringify(frame), 'utf8'), maxFrameBytes);
}

function attachWebSocketFrameReader(socket, initialData, onFrame, onError, maxFrameBytes = DEFAULT_MAX_FRAME_BYTES) {
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
        socket.destroy(new Error('Fragmented WebSocket frames are not supported'));
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
          socket.destroy(new Error('WebSocket frame is too large'));
          return;
        }
        length = low;
        offset += 8;
      }

      const maskOffset = offset;
      if (masked) {
        offset += 4;
      }
      if (length > maxFrameBytes) {
        socket.destroy(new Error(`Frame too large: ${length}`));
        return;
      }
      if (buffer.length < offset + length) {
        return;
      }

      const mask = masked ? buffer.subarray(maskOffset, maskOffset + 4) : null;
      let payload = buffer.subarray(offset, offset + length);
      buffer = buffer.subarray(offset + length);
      if (masked) {
        payload = Buffer.from(payload);
        for (let index = 0; index < payload.length; index++) {
          payload[index] ^= mask[index % 4];
        }
      }

      if (opcode === 0x8) {
        writeWebSocketFrame(socket, 0x8, Buffer.alloc(0), maxFrameBytes);
        socket.end();
        return;
      }
      if (opcode === 0x9) {
        writeWebSocketFrame(socket, 0xA, payload, maxFrameBytes);
        continue;
      }
      if (opcode === 0xA) {
        continue;
      }
      if (opcode !== 0x1 && opcode !== 0x2) {
        socket.destroy(new Error(`Unsupported WebSocket opcode: ${opcode}`));
        return;
      }

      try {
        onFrame(JSON.parse(payload.toString('utf8')));
      } catch (error) {
        onError(error);
      }
    }
  }

  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    processBuffer();
  });
  socket.on('error', onError);
  processBuffer();
}

function websocketAcceptKey(key) {
  return crypto
    .createHash('sha1')
    .update(String(key || '') + WEBSOCKET_GUID)
    .digest('base64');
}

function resolveTarget(req, sessions, config) {
  const host = String(req.headers.host || '').split(':')[0].toLowerCase();
  const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const cookies = parseCookies(req.headers.cookie);
  let slug = '';
  let proxiedPath = requestUrl.pathname + requestUrl.search;

  const baseDomain = String(config.baseDomain || '').toLowerCase().replace(/^\.+|\.+$/g, '');
  if (baseDomain && host.endsWith(`.${baseDomain}`)) {
    slug = safeSlug(host.slice(0, -(baseDomain.length + 1)).split('.')[0]);
  }

  if (!slug) {
    const segments = requestUrl.pathname.split('/').filter(Boolean);
    if (segments.length > 0 && sessions.has(safeSlug(segments[0]))) {
      slug = safeSlug(segments[0]);
      proxiedPath = `/${segments.slice(1).join('/')}`;
      if (proxiedPath === '/') {
        proxiedPath = '/';
      }
      proxiedPath += requestUrl.search;
    }
  }

  if (!slug && cookies.flux_slug && sessions.has(safeSlug(cookies.flux_slug))) {
    slug = safeSlug(cookies.flux_slug);
  }

  if (!slug || !sessions.has(slug)) {
    return null;
  }

  const sanitized = new URL(proxiedPath || '/', 'http://phone.local');
  sanitized.searchParams.delete('token');
  return {
    slug,
    session: sessions.get(slug),
    path: sanitized.pathname + sanitized.search,
    providedToken: requestUrl.searchParams.get('token') || cookies.flux_token || '',
    authenticatedByQuery: requestUrl.searchParams.get('token') || ''
  };
}

function proxyRequestHeaders(headers) {
  const blocked = new Set([
    'connection',
    'content-length',
    'expect',
    'host',
    'keep-alive',
    'proxy-authenticate',
    'proxy-authorization',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade'
  ]);
  const proxied = {};
  Object.entries(headers || {}).forEach(([key, value]) => {
    const lower = key.toLowerCase();
    if (blocked.has(lower)) {
      return;
    }
    proxied[lower] = Array.isArray(value) ? value.join(', ') : String(value);
  });
  return proxied;
}

function readProxyRequestBody(req) {
  if (req.method === 'GET' || req.method === 'HEAD') {
    return Promise.resolve(Buffer.alloc(0));
  }
  const declaredLength = Number.parseInt(String(req.headers['content-length'] || '0'), 10);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_PROXY_REQUEST_BODY_BYTES) {
    return Promise.reject(new Error('Request body is too large.'));
  }
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > MAX_PROXY_REQUEST_BODY_BYTES) {
        reject(new Error('Request body is too large.'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks, total)));
    req.on('error', reject);
  });
}

function proxyResponseHeaders(headers, cookies) {
  const blocked = new Set([
    'connection',
    'keep-alive',
    'proxy-authenticate',
    'proxy-authorization',
    'te',
    'trailer',
    'transfer-encoding',
    'upgrade'
  ]);
  const responseHeaders = {};
  Object.entries(headers || {}).forEach(([key, value]) => {
    const lower = key.toLowerCase();
    if (blocked.has(lower)) {
      return;
    }
    responseHeaders[lower] = String(value);
  });
  if (cookies.length > 0) {
    responseHeaders['set-cookie'] = cookies;
  }
  return responseHeaders;
}

function authCookies(slug, token, config) {
  const secure = config.secureCookies ? '; Secure' : '';
  return [
    `flux_slug=${encodeURIComponent(slug)}; Path=/; HttpOnly; SameSite=Lax${secure}`,
    `flux_token=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax${secure}`
  ];
}

function sendText(res, status, body, extraHeaders = {}) {
  const payload = Buffer.from(body, 'utf8');
  res.writeHead(status, {
    'content-type': 'text/plain; charset=utf-8',
    'content-length': payload.length,
    'cache-control': 'no-store',
    ...extraHeaders
  });
  res.end(payload);
}

function createRelayServer(options = {}) {
  const config = {
    publicHost: options.publicHost || process.env.PUBLIC_HOST || '0.0.0.0',
    publicPort: options.publicPort ?? intFromEnv(process.env.PUBLIC_PORT, DEFAULT_PUBLIC_PORT),
    agentPath: normalizePath(options.agentPath || process.env.AGENT_PATH, '/agent'),
    publicBase: options.publicBase || process.env.PUBLIC_BASE || '',
    baseDomain: options.baseDomain || process.env.BASE_DOMAIN || '',
    secureCookies: options.secureCookies ?? boolFromEnv(process.env.SECURE_COOKIES, /^https:\/\//i.test(process.env.PUBLIC_BASE || '')),
    maxFrameBytes: options.maxFrameBytes || intFromEnv(process.env.MAX_FRAME_BYTES, DEFAULT_MAX_FRAME_BYTES),
    requestTimeoutMs: options.requestTimeoutMs || intFromEnv(process.env.REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS),
    tinyUrlApiUrl: options.tinyUrlApiUrl || process.env.TINY_URL_API_URL || '',
    tinyUrlCreateToken: options.tinyUrlCreateToken || process.env.TINY_URL_CREATE_TOKEN || '',
    tinyUrlTtlSeconds: options.tinyUrlTtlSeconds ?? intFromEnv(process.env.TINY_URL_TTL_SECONDS, 0),
    tinyUrlTimeoutMs: options.tinyUrlTimeoutMs || intFromEnv(process.env.TINY_URL_TIMEOUT_MS, DEFAULT_TINY_URL_TIMEOUT_MS)
  };
  const sessions = new Map();

  function closeSession(session, reason) {
    if (!session || session.closed) {
      return;
    }
    session.closed = true;
    sessions.delete(session.slug);
    for (const pending of session.pending.values()) {
      clearTimeout(pending.timer);
      if (!pending.res.writableEnded) {
        pending.res.destroy(new Error(reason || 'Relay session closed'));
      }
    }
    session.pending.clear();
    session.transport.close(reason);
  }

  function handleAgent(transport) {
    let session = null;
    let registering = false;
    let transportClosed = false;
    transport.onFrame((frame) => {
      if (!session) {
        if (frame.type !== 'register') {
          transport.close('Agent must register first');
          return;
        }
        if (registering) {
          transport.close('Agent is already registering');
          return;
        }
        registering = true;
        registerSession(frame, transport)
          .then((registeredSession) => {
            if (transportClosed) {
              closeSession(registeredSession, 'Agent socket closed during registration');
              return;
            }
            session = registeredSession;
          })
          .catch((error) => {
            console.error('[relay] Could not register agent:', error);
            transport.close(error);
          });
        return;
      }

      if (frame.type === 'register') {
        transport.close('Agent is already registered');
        return;
      }

      if (frame.type === 'responseStart') {
        const pending = session.pending.get(frame.id);
        if (!pending || pending.started) {
          return;
        }
        pending.started = true;
        const cookies = pending.setAuthCookies ? authCookies(session.slug, session.token, config) : [];
        pending.res.writeHead(
          Number(frame.status) || 502,
          proxyResponseHeaders(frame.headers, cookies)
        );
        return;
      }

      if (frame.type === 'responseChunk') {
        const pending = session.pending.get(frame.id);
        if (!pending || pending.res.writableEnded) {
          return;
        }
        pending.res.write(Buffer.from(String(frame.data || ''), 'base64'));
        return;
      }

      if (frame.type === 'responseEnd') {
        const pending = session.pending.get(frame.id);
        if (!pending) {
          return;
        }
        clearTimeout(pending.timer);
        session.pending.delete(frame.id);
        if (!pending.res.writableEnded) {
          pending.res.end();
        }
        return;
      }

      if (frame.type === 'responseError') {
        const pending = session.pending.get(frame.id);
        if (!pending) {
          return;
        }
        clearTimeout(pending.timer);
        session.pending.delete(frame.id);
        if (!pending.res.headersSent) {
          sendText(pending.res, 502, frame.message || 'Phone could not serve this request.');
        } else {
          pending.res.destroy(new Error(frame.message || 'Phone relay error'));
        }
        return;
      }

      if (frame.type === 'pong') {
        session.lastPongAt = Date.now();
      }
    });
    transport.onClose(() => {
      transportClosed = true;
      closeSession(session, 'Agent socket closed');
    });
  }

  async function registerSession(frame, transport) {
    const slug = safeSlug(frame.slug);
    const existing = sessions.get(slug);
    if (existing) {
      closeSession(existing, 'Session replaced by a reconnect');
    }
    const token = String(frame.token || crypto.randomBytes(16).toString('hex'));
    const fullUrl = publicUrlFor(slug, token, config);
    let shortUrl = '';
    let tinyUrlError = '';
    try {
      shortUrl = await createTinyUrl(fullUrl, config);
    } catch (error) {
      tinyUrlError = error.message || 'Tiny URL creation failed';
      console.warn('[relay] Tiny URL creation failed; using full URL:', error.message);
    }
    const session = {
      slug,
      token,
      transport,
      deviceName: String(frame.deviceName || 'Android phone').slice(0, 80),
      pending: new Map(),
      connectedAt: Date.now(),
      closed: false,
      fullUrl,
      shortUrl: shortUrl || '',
      tinyUrlError
    };
    sessions.set(slug, session);
    transport.send({
      type: 'registered',
      slug,
      publicUrl: shortUrl || fullUrl,
      fullUrl,
      shortUrl: shortUrl || '',
      tinyUrlError
    });
    return session;
  }

  async function handlePublicRequest(req, res) {
    if ((req.url || '').split('?', 1)[0] === '/healthz') {
      const payload = Buffer.from(JSON.stringify({
        ok: true,
        sessions: sessions.size,
        tinyUrlConfigured: Boolean(config.tinyUrlApiUrl && config.tinyUrlCreateToken),
        uptimeSeconds: Math.floor(process.uptime())
      }), 'utf8');
      res.writeHead(200, {
        'content-type': 'application/json; charset=utf-8',
        'content-length': payload.length,
        'cache-control': 'no-store'
      });
      res.end(payload);
      return;
    }

    if (req.method !== 'GET' && req.method !== 'HEAD' && req.method !== 'POST') {
      sendText(res, 405, 'Only GET, HEAD, and approval POST are supported.');
      return;
    }

    const target = resolveTarget(req, sessions, config);
    if (!target) {
      sendText(res, 404, 'This Flux File Share link is offline or expired.');
      return;
    }

    if (target.session.token && target.providedToken !== target.session.token) {
      sendText(res, 401, 'This Flux File Share link needs the private access token.');
      return;
    }

    let requestBody;
    try {
      requestBody = await readProxyRequestBody(req);
    } catch (error) {
      sendText(res, 413, error.message || 'Request body is too large.');
      return;
    }

    const id = crypto.randomUUID ? crypto.randomUUID() : crypto.randomBytes(16).toString('hex');
    const timer = setTimeout(() => {
      target.session.pending.delete(id);
      if (!res.headersSent) {
        sendText(res, 504, 'The phone did not respond in time.');
      } else {
        res.destroy(new Error('Phone response timed out'));
      }
    }, config.requestTimeoutMs);

    target.session.pending.set(id, {
      res,
      timer,
      started: false,
      setAuthCookies: target.authenticatedByQuery === target.session.token
    });
    res.on('close', () => {
      const pending = target.session.pending.get(id);
      if (pending && !res.writableEnded) {
        clearTimeout(pending.timer);
        target.session.pending.delete(id);
        try {
          target.session.transport.send({ type: 'cancel', id });
        } catch (error) {
          closeSession(target.session, error.message);
        }
      }
    });

    try {
      target.session.transport.send({
        type: 'request',
        id,
        method: req.method,
        path: target.path,
        headers: proxyRequestHeaders(req.headers),
        body: requestBody.length > 0 ? requestBody.toString('base64') : ''
      });
    } catch (error) {
      clearTimeout(timer);
      target.session.pending.delete(id);
      closeSession(target.session, error.message);
      if (!res.headersSent) {
        sendText(res, 502, 'Could not reach the phone relay session.');
      }
    }
  }

  const publicServer = http.createServer(handlePublicRequest);
  publicServer.on('upgrade', (req, socket, head) => {
    const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (requestUrl.pathname !== config.agentPath) {
      socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    const key = req.headers['sec-websocket-key'];
    if (!key || String(req.headers.upgrade || '').toLowerCase() !== 'websocket') {
      socket.write('HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    socket.setKeepAlive(true, 30000);
    socket.write([
      'HTTP/1.1 101 Switching Protocols',
      'Upgrade: websocket',
      'Connection: Upgrade',
      `Sec-WebSocket-Accept: ${websocketAcceptKey(key)}`,
      '\r\n'
    ].join('\r\n'));

    let frameHandler = () => {};
    let closeHandler = () => {};
    const transport = {
      send(frame) {
        writeWebSocketJson(socket, frame, config.maxFrameBytes);
      },
      close(reason) {
        socket.destroy(reason instanceof Error ? reason : undefined);
      },
      onFrame(handler) {
        frameHandler = handler;
      },
      onClose(handler) {
        closeHandler = handler;
      }
    };
    handleAgent(transport);
    attachWebSocketFrameReader(
      socket,
      head,
      (frame) => frameHandler(frame),
      (error) => {
        socket.destroy(error);
      },
      config.maxFrameBytes
    );
    socket.on('close', () => closeHandler());
  });

  return {
    config,
    sessions,
    publicServer,
    listen(callback) {
      publicServer.listen(config.publicPort, config.publicHost, callback);
    },
    close(callback) {
      for (const session of sessions.values()) {
        closeSession(session, 'Relay server shutting down');
      }
      publicServer.close(callback);
    }
  };
}

if (require.main === module) {
  const relay = createRelayServer();
  relay.listen(() => {
    const publicAddress = relay.publicServer.address();
    console.log(`Flux relay HTTP and WebSocket listening on ${publicAddress.address}:${publicAddress.port}`);
    console.log(`Flux relay phone agent path is ${relay.config.agentPath}`);
  });
}

module.exports = {
  attachFrameReader,
  attachWebSocketFrameReader,
  createRelayServer,
  createTinyUrl,
  publicUrlFor,
  safeSlug,
  websocketAcceptKey,
  writeWebSocketFrame,
  writeWebSocketJson,
  writeFrame
};
