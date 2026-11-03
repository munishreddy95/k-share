'use strict';

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const path = require('path');

const fsp = fs.promises;

const DEFAULT_PORT = 4100;
const DEFAULT_SLUG_LENGTH = 7;
const DEFAULT_MAX_URL_LENGTH = 4096;
const DEFAULT_MAX_TTL_SECONDS = 60 * 60 * 24 * 365;
const DEFAULT_RATE_LIMIT_WINDOW_MS = 60 * 1000;
const DEFAULT_RATE_LIMIT_CREATES = 30;
const RESERVED_SLUGS = new Set(['api', 'healthz', 'metrics', 'favicon.ico']);
const SLUG_PATTERN = /^[a-z0-9_-]{3,64}$/;
const SLUG_ALPHABET = '23456789abcdefghijkmnopqrstuvwxyz';

function boolFromEnv(value, fallback = false) {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  return /^(1|true|yes|on)$/i.test(String(value));
}

function intFromEnv(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value || ''), 10);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, parsed));
}

function parseConfig(env = process.env) {
  const port = intFromEnv(env.PORT, DEFAULT_PORT, 1, 65535);
  const slugLength = intFromEnv(env.SLUG_LENGTH, DEFAULT_SLUG_LENGTH, 4, 32);
  const maxUrlLength = intFromEnv(env.MAX_URL_LENGTH, DEFAULT_MAX_URL_LENGTH, 128, 65536);
  const redirectStatus = intFromEnv(env.REDIRECT_STATUS, 302, 301, 308);
  return {
    host: env.HOST || '0.0.0.0',
    port,
    publicBase: String(env.PUBLIC_BASE || `http://localhost:${port}`).replace(/\/+$/, ''),
    dataFile: env.DATA_FILE || path.join(__dirname, 'data', 'links.json'),
    createToken: env.CREATE_TOKEN || '',
    allowPublicCreate: boolFromEnv(env.ALLOW_PUBLIC_CREATE, false),
    enableCreateUi: boolFromEnv(env.ENABLE_CREATE_UI, false),
    createAllowedIps: parseCsv(env.CREATE_ALLOWED_IPS || ''),
    slugLength,
    maxUrlLength,
    maxTtlSeconds: intFromEnv(env.MAX_TTL_SECONDS, DEFAULT_MAX_TTL_SECONDS, 60, 60 * 60 * 24 * 365 * 10),
    redirectStatus: [301, 302, 307, 308].includes(redirectStatus) ? redirectStatus : 302,
    trustProxy: boolFromEnv(env.TRUST_PROXY, false),
    rateLimitWindowMs: intFromEnv(env.RATE_LIMIT_WINDOW_MS, DEFAULT_RATE_LIMIT_WINDOW_MS, 1000, 60 * 60 * 1000),
    rateLimitCreates: intFromEnv(env.RATE_LIMIT_CREATES, DEFAULT_RATE_LIMIT_CREATES, 1, 10000),
  };
}

function parseCsv(value) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function publicUrlFor(slug, config) {
  if (config.publicBase.includes('{slug}')) {
    return config.publicBase.replaceAll('{slug}', encodeURIComponent(slug));
  }
  return `${config.publicBase}/${encodeURIComponent(slug)}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function parseBearerToken(req) {
  const authorization = String(req.headers.authorization || '');
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (match) {
    return match[1].trim();
  }
  return String(req.headers['x-create-token'] || '').trim();
}

function isAuthorized(req, config) {
  if (!config.createToken) {
    return config.allowPublicCreate;
  }
  const provided = Buffer.from(parseBearerToken(req));
  const expected = Buffer.from(config.createToken);
  return provided.length === expected.length && crypto.timingSafeEqual(provided, expected);
}

function getClientIp(req, config) {
  if (config.trustProxy) {
    const forwardedFor = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
    if (forwardedFor) {
      return forwardedFor;
    }
  }
  return req.socket.remoteAddress || 'unknown';
}

function normalizeIp(value) {
  const ip = String(value || '').trim();
  if (ip.startsWith('::ffff:')) {
    return ip.slice('::ffff:'.length);
  }
  return ip;
}

function isCreateSourceAllowed(req, config) {
  if (!config.createAllowedIps || config.createAllowedIps.length === 0) {
    return true;
  }
  const clientIp = normalizeIp(getClientIp(req, config));
  return config.createAllowedIps.some((allowed) => normalizeIp(allowed) === clientIp);
}

function createAccessError(req, config) {
  if (!isCreateSourceAllowed(req, config)) {
    return httpError(403, 'Tiny URL creation is restricted to the relay service.');
  }
  if (!isAuthorized(req, config)) {
    return httpError(401, 'Create token is required.');
  }
  return null;
}

function validateDestinationUrl(value, config) {
  const rawUrl = String(value || '').trim();
  if (!rawUrl) {
    throw httpError(400, 'URL is required.');
  }
  if (rawUrl.length > config.maxUrlLength) {
    throw httpError(400, `URL is too long. Max length is ${config.maxUrlLength}.`);
  }

  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw httpError(400, 'URL must be a valid absolute URL.');
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw httpError(400, 'Only http and https URLs can be shortened.');
  }
  parsed.hash = parsed.hash || '';
  return parsed.toString();
}

function validateSlug(value) {
  const slug = String(value || '').trim().toLowerCase();
  if (!slug) {
    return '';
  }
  if (!SLUG_PATTERN.test(slug)) {
    throw httpError(400, 'Slug must be 3-64 characters using lowercase letters, numbers, underscore, or dash.');
  }
  if (RESERVED_SLUGS.has(slug)) {
    throw httpError(400, 'That slug is reserved.');
  }
  return slug;
}

function httpError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

class JsonLinkStore {
  constructor(filePath) {
    this.filePath = filePath;
    this.links = new Map();
    this.loadedAt = null;
    this.writeQueue = Promise.resolve();
    this.pendingSaveTimer = null;
    this.ready = this.load();
  }

  async load() {
    await fsp.mkdir(path.dirname(this.filePath), { recursive: true });
    try {
      const content = await fsp.readFile(this.filePath, 'utf8');
      const parsed = JSON.parse(content);
      const links = Array.isArray(parsed.links) ? parsed.links : [];
      for (const link of links) {
        if (link && typeof link.url === 'string') {
          let slug = '';
          try {
            slug = validateSlug(link.slug);
          } catch {
            continue;
          }
          if (!slug || this.links.has(slug)) {
            continue;
          }
          this.links.set(slug, {
            slug,
            url: link.url,
            createdAt: link.createdAt || new Date().toISOString(),
            expiresAt: link.expiresAt || null,
            visits: Number.isFinite(link.visits) ? link.visits : 0,
            lastVisitAt: link.lastVisitAt || null,
          });
        }
      }
    } catch (error) {
      if (error.code !== 'ENOENT') {
        throw error;
      }
    }
    this.loadedAt = new Date().toISOString();
  }

  async saveNow() {
    clearTimeout(this.pendingSaveTimer);
    this.pendingSaveTimer = null;
    const write = async () => {
      await fsp.mkdir(path.dirname(this.filePath), { recursive: true });
      const payload = JSON.stringify(
        {
          version: 1,
          savedAt: new Date().toISOString(),
          links: Array.from(this.links.values()).sort((a, b) => a.slug.localeCompare(b.slug)),
        },
        null,
        2
      );
      const tempFile = `${this.filePath}.${process.pid}.${Date.now()}.tmp`;
      await fsp.writeFile(tempFile, payload, 'utf8');
      await fsp.rename(tempFile, this.filePath);
    };
    this.writeQueue = this.writeQueue.then(write, write);
    return this.writeQueue;
  }

  saveSoon() {
    if (this.pendingSaveTimer) {
      return;
    }
    this.pendingSaveTimer = setTimeout(() => {
      this.saveNow().catch((error) => {
        console.error('[tiny-url] Failed to persist link stats:', error);
      });
    }, 1000);
  }

  async create({ url, slug, ttlSeconds }, config) {
    await this.ready;
    const now = new Date();
    const expiresAt = ttlSeconds ? new Date(now.getTime() + ttlSeconds * 1000).toISOString() : null;
    const finalSlug = slug || this.generateUniqueSlug(config.slugLength);
    if (this.links.has(finalSlug)) {
      throw httpError(409, 'Slug already exists.');
    }
    const link = {
      slug: finalSlug,
      url,
      createdAt: now.toISOString(),
      expiresAt,
      visits: 0,
      lastVisitAt: null,
    };
    this.links.set(finalSlug, link);
    await this.saveNow();
    return link;
  }

  async get(slug) {
    await this.ready;
    const link = this.links.get(slug);
    if (!link) {
      return null;
    }
    if (link.expiresAt && Date.parse(link.expiresAt) <= Date.now()) {
      this.links.delete(slug);
      this.saveSoon();
      return { expired: true };
    }
    return link;
  }

  async recordVisit(slug) {
    await this.ready;
    const link = this.links.get(slug);
    if (!link) {
      return;
    }
    link.visits += 1;
    link.lastVisitAt = new Date().toISOString();
    this.saveSoon();
  }

  async delete(slug) {
    await this.ready;
    const deleted = this.links.delete(slug);
    if (deleted) {
      await this.saveNow();
    }
    return deleted;
  }

  async list() {
    await this.ready;
    return Array.from(this.links.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  generateUniqueSlug(length) {
    for (let attempt = 0; attempt < 50; attempt++) {
      let slug = '';
      for (let index = 0; index < length; index++) {
        slug += SLUG_ALPHABET[crypto.randomInt(0, SLUG_ALPHABET.length)];
      }
      if (!this.links.has(slug) && !RESERVED_SLUGS.has(slug.toLowerCase())) {
        return slug;
      }
    }
    throw httpError(503, 'Could not create a unique slug. Try again.');
  }
}

class RateLimiter {
  constructor(config) {
    this.config = config;
    this.buckets = new Map();
  }

  check(ip) {
    const now = Date.now();
    const bucket = this.buckets.get(ip) || { resetAt: now + this.config.rateLimitWindowMs, count: 0 };
    if (bucket.resetAt <= now) {
      bucket.resetAt = now + this.config.rateLimitWindowMs;
      bucket.count = 0;
    }
    bucket.count += 1;
    this.buckets.set(ip, bucket);
    this.prune(now);
    if (bucket.count > this.config.rateLimitCreates) {
      const retryAfter = Math.max(1, Math.ceil((bucket.resetAt - now) / 1000));
      const error = httpError(429, 'Too many links created. Please wait and try again.');
      error.retryAfter = retryAfter;
      throw error;
    }
  }

  prune(now) {
    if (this.buckets.size < 1000) {
      return;
    }
    for (const [ip, bucket] of this.buckets.entries()) {
      if (bucket.resetAt <= now) {
        this.buckets.delete(ip);
      }
    }
  }
}

function readJsonBody(req, config) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > config.maxUrlLength + 2048) {
        reject(httpError(413, 'Request body is too large.'));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!body.trim()) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(httpError(400, 'Request body must be valid JSON.'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, payload, extraHeaders = {}) {
  res.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    ...extraHeaders,
  });
  res.end(JSON.stringify(payload));
}

function sendHtml(res, statusCode, html) {
  res.writeHead(statusCode, {
    'content-type': 'text/html; charset=utf-8',
    'cache-control': 'no-store',
  });
  res.end(html);
}

function linkResponse(link, config) {
  return {
    slug: link.slug,
    url: link.url,
    shortUrl: publicUrlFor(link.slug, config),
    createdAt: link.createdAt,
    expiresAt: link.expiresAt,
    visits: link.visits,
    lastVisitAt: link.lastVisitAt,
  };
}

function renderHome(config) {
  if (!config.enableCreateUi) {
    return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>K-Share Short Links</title>
  <style>
    :root { color-scheme: dark; --bg: #061014; --text: #f5fbfb; --muted: #9fb8bb; --accent: #27e0d0; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); background: radial-gradient(circle at top right, #0c6c72, transparent 24rem), var(--bg); }
    main { width: min(680px, calc(100% - 32px)); border: 1px solid #1e4752; border-radius: 28px; padding: 28px; background: rgba(13, 29, 36, .82); box-shadow: 0 24px 70px rgba(0,0,0,.26); }
    .eyebrow { color: #ff9f53; font-weight: 800; letter-spacing: .22em; text-transform: uppercase; font-size: 12px; }
    h1 { margin: 12px 0 10px; font-size: clamp(36px, 8vw, 68px); line-height: .9; letter-spacing: -.06em; }
    p { color: var(--muted); line-height: 1.6; }
    code { color: var(--accent); }
  </style>
</head>
<body>
  <main>
    <div class="eyebrow">K-Share Short Links</div>
    <h1>Relay-only short links.</h1>
    <p>This service creates tiny URLs only through the K-Share relay API. Public visitors can use existing short links, but cannot create new ones here.</p>
    <p>Health check: <code>/healthz</code></p>
  </main>
</body>
</html>`;
  }
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>K-Share Short Links</title>
  <style>
    :root { color-scheme: dark; --bg: #061014; --card: #0d1d24; --line: #1e4752; --text: #f5fbfb; --muted: #9fb8bb; --accent: #27e0d0; --warn: #ffb454; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--text); background: radial-gradient(circle at top left, #123f43, transparent 34rem), var(--bg); }
    main { width: min(760px, calc(100% - 32px)); margin: 0 auto; padding: 56px 0; }
    .eyebrow { color: #ff9f53; font-weight: 800; letter-spacing: .22em; text-transform: uppercase; font-size: 12px; }
    h1 { margin: 12px 0 10px; font-size: clamp(38px, 8vw, 78px); line-height: .9; letter-spacing: -.06em; }
    p { color: var(--muted); line-height: 1.6; font-size: 16px; }
    form, .result { margin-top: 24px; border: 1px solid var(--line); background: color-mix(in srgb, var(--card), transparent 12%); border-radius: 28px; padding: 22px; box-shadow: 0 24px 70px rgba(0,0,0,.24); }
    label { display: block; margin: 14px 0 8px; color: var(--muted); font-size: 13px; font-weight: 700; }
    input { width: 100%; border: 1px solid #24535f; border-radius: 18px; padding: 14px 16px; background: #071318; color: var(--text); outline: none; font: inherit; }
    input:focus { border-color: var(--accent); box-shadow: 0 0 0 4px rgba(39,224,208,.12); }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    button { margin-top: 18px; width: 100%; border: 0; border-radius: 18px; padding: 15px 18px; background: linear-gradient(135deg, #1df2dc, #0e86ff); color: #001012; font-weight: 900; cursor: pointer; }
    button:disabled { opacity: .65; cursor: wait; }
    .result { display: none; }
    .result a { color: var(--accent); font-weight: 800; overflow-wrap: anywhere; }
    .note { color: var(--warn); font-size: 13px; }
    @media (max-width: 640px) { main { padding: 34px 0; } .grid { grid-template-columns: 1fr; } form, .result { border-radius: 22px; padding: 18px; } }
  </style>
</head>
<body>
  <main>
    <div class="eyebrow">K-Share Short Links</div>
    <h1>Short links for big shares.</h1>
    <p>Create a tiny URL that redirects to the actual K-Share global share link.</p>
    ${config.createToken ? '<p class="note">Creation is protected. Paste your create token below; it is saved only in this browser.</p>' : '<p class="note">Public creation is disabled unless ALLOW_PUBLIC_CREATE=true is set.</p>'}
    <form id="form">
      <label for="url">Actual URL</label>
      <input id="url" name="url" type="url" placeholder="https://abc.share.example.com/" required>
      <div class="grid">
        <div>
          <label for="slug">Custom slug (optional)</label>
          <input id="slug" name="slug" placeholder="family-video">
        </div>
        <div>
          <label for="ttl">Expire after minutes (optional)</label>
          <input id="ttl" name="ttl" type="number" min="1" placeholder="1440">
        </div>
      </div>
      <label for="token">Create token</label>
      <input id="token" name="token" type="password" placeholder="Required when CREATE_TOKEN is configured">
      <button id="button" type="submit">Create tiny URL</button>
    </form>
    <div class="result" id="result"></div>
  </main>
  <script>
    const form = document.getElementById('form');
    const result = document.getElementById('result');
    const button = document.getElementById('button');
    const tokenInput = document.getElementById('token');
    tokenInput.value = localStorage.getItem('fluxTinyToken') || '';
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      button.disabled = true;
      result.style.display = 'block';
      result.textContent = 'Creating...';
      const token = tokenInput.value.trim();
      localStorage.setItem('fluxTinyToken', token);
      try {
        const ttlMinutes = Number(form.ttl.value || 0);
        const response = await fetch('/api/links', {
          method: 'POST',
          headers: {
            'content-type': 'application/json',
            ...(token ? { authorization: 'Bearer ' + token } : {}),
          },
          body: JSON.stringify({
            url: form.url.value,
            slug: form.slug.value || undefined,
            ttlSeconds: ttlMinutes > 0 ? Math.round(ttlMinutes * 60) : undefined,
          }),
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.error || 'Could not create link.');
        }
        result.innerHTML = '<p>Tiny URL ready:</p><p><a href="' + payload.shortUrl + '" target="_blank" rel="noreferrer">' + payload.shortUrl + '</a></p>';
      } catch (error) {
        result.textContent = error.message;
      } finally {
        button.disabled = false;
      }
    });
  </script>
</body>
</html>`;
}

function createServer(config = parseConfig(), store = new JsonLinkStore(config.dataFile)) {
  const limiter = new RateLimiter(config);
  return http.createServer(async (req, res) => {
    try {
      const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
      const pathname = decodeURIComponent(requestUrl.pathname);

      if (req.method === 'GET' && pathname === '/healthz') {
        await store.ready;
        sendJson(res, 200, {
          ok: true,
          links: store.links.size,
          loadedAt: store.loadedAt,
          publicBase: config.publicBase,
        });
        return;
      }

      if (req.method === 'GET' && pathname === '/') {
        sendHtml(res, 200, renderHome(config));
        return;
      }

      if (pathname === '/api/links' && req.method === 'POST') {
        const accessError = createAccessError(req, config);
        if (accessError) {
          sendJson(res, accessError.statusCode, { error: accessError.message });
          return;
        }
        limiter.check(getClientIp(req, config));
        const body = await readJsonBody(req, config);
        const url = validateDestinationUrl(body.url, config);
        const slug = validateSlug(body.slug);
        const ttlSeconds = body.ttlSeconds
          ? intFromEnv(body.ttlSeconds, 0, 1, config.maxTtlSeconds)
          : 0;
        const link = await store.create({ url, slug, ttlSeconds }, config);
        sendJson(res, 201, linkResponse(link, config));
        return;
      }

      if (pathname === '/api/links' && req.method === 'GET') {
        const accessError = createAccessError(req, config);
        if (accessError) {
          sendJson(res, accessError.statusCode, { error: accessError.message });
          return;
        }
        const links = await store.list();
        sendJson(res, 200, { links: links.map((link) => linkResponse(link, config)) });
        return;
      }

      if (pathname.startsWith('/api/links/') && req.method === 'DELETE') {
        const accessError = createAccessError(req, config);
        if (accessError) {
          sendJson(res, accessError.statusCode, { error: accessError.message });
          return;
        }
        const slug = validateSlug(pathname.slice('/api/links/'.length));
        const deleted = await store.delete(slug);
        sendJson(res, deleted ? 200 : 404, deleted ? { ok: true } : { error: 'Link not found.' });
        return;
      }

      if (req.method !== 'GET' && req.method !== 'HEAD') {
        sendJson(res, 405, { error: 'Method not allowed.' }, { allow: 'GET, HEAD, POST, DELETE' });
        return;
      }

      const slug = validateSlug(pathname.replace(/^\/+|\/+$/g, ''));
      const link = await store.get(slug);
      if (!link) {
        sendHtml(res, 404, `<h1>Link not found</h1><p>${escapeHtml(slug)} does not exist.</p>`);
        return;
      }
      if (link.expired) {
        sendHtml(res, 410, '<h1>Link expired</h1><p>This tiny URL is no longer available.</p>');
        return;
      }

      await store.recordVisit(slug);
      res.writeHead(config.redirectStatus, {
        location: link.url,
        'cache-control': 'no-store',
      });
      res.end();
    } catch (error) {
      const statusCode = error.statusCode || 500;
      const headers = error.retryAfter ? { 'retry-after': String(error.retryAfter) } : {};
      sendJson(res, statusCode, { error: statusCode >= 500 ? 'Internal server error.' : error.message }, headers);
      if (statusCode >= 500) {
        console.error('[tiny-url] Request failed:', error);
      }
    }
  });
}

if (require.main === module) {
  const config = parseConfig();
  const store = new JsonLinkStore(config.dataFile);
  const server = createServer(config, store);
  server.listen(config.port, config.host, () => {
    console.log(`[tiny-url] Listening on http://${config.host}:${config.port}`);
    console.log(`[tiny-url] Public base: ${config.publicBase}`);
    if (!config.createToken && !config.allowPublicCreate) {
      console.warn('[tiny-url] Link creation is disabled. Set CREATE_TOKEN or ALLOW_PUBLIC_CREATE=true.');
    }
  });

  const shutdown = async () => {
    console.log('[tiny-url] Shutting down...');
    server.close(async () => {
      await store.saveNow().catch((error) => console.error('[tiny-url] Failed to save on shutdown:', error));
      process.exit(0);
    });
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

module.exports = {
  JsonLinkStore,
  createServer,
  parseConfig,
  publicUrlFor,
};
