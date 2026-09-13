'use strict';

const assert = require('assert');
const http = require('http');
const os = require('os');
const path = require('path');

const { JsonLinkStore, createServer, parseConfig } = require('./server');

function request(port, options = {}) {
  const body = options.body ? JSON.stringify(options.body) : null;
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: '127.0.0.1',
        port,
        path: options.path || '/',
        method: options.method || 'GET',
        headers: {
          ...(body ? { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) } : {}),
          ...(options.headers || {}),
        },
      },
      (res) => {
        let data = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => {
          data += chunk;
        });
        res.on('end', () => resolve({ statusCode: res.statusCode, headers: res.headers, body: data }));
      }
    );
    req.on('error', reject);
    if (body) {
      req.write(body);
    }
    req.end();
  });
}

async function main() {
  const dataFile = path.join(os.tmpdir(), `flux-tiny-url-test-${process.pid}.json`);
  const config = parseConfig({
    HOST: '127.0.0.1',
    PORT: '4100',
    PUBLIC_BASE: 'https://t.example.com',
    DATA_FILE: dataFile,
    CREATE_TOKEN: 'secret',
    TRUST_PROXY: 'true',
    CREATE_ALLOWED_IPS: '127.0.0.1',
    SLUG_LENGTH: '6',
  });
  const store = new JsonLinkStore(dataFile);
  const server = createServer(config, store);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;

  const unauthorized = await request(port, {
    method: 'POST',
    path: '/api/links',
    body: { url: 'https://share.example.com/file' },
  });
  assert.strictEqual(unauthorized.statusCode, 401);

  const publicSource = await request(port, {
    method: 'POST',
    path: '/api/links',
    headers: {
      authorization: 'Bearer secret',
      'x-forwarded-for': '203.0.113.10',
    },
    body: { url: 'https://share.example.com/file' },
  });
  assert.strictEqual(publicSource.statusCode, 403);

  const created = await request(port, {
    method: 'POST',
    path: '/api/links',
    headers: { authorization: 'Bearer secret' },
    body: { url: 'https://share.example.com/file', slug: 'demo-file' },
  });
  assert.strictEqual(created.statusCode, 201);
  const createdBody = JSON.parse(created.body);
  assert.strictEqual(createdBody.shortUrl, 'https://t.example.com/demo-file');
  assert.strictEqual(createdBody.slug, 'demo-file');

  const redirected = await request(port, { path: '/demo-file' });
  assert.strictEqual(redirected.statusCode, 302);
  assert.strictEqual(redirected.headers.location, 'https://share.example.com/file');

  const mixedCaseRedirected = await request(port, { path: '/Demo-FILE' });
  assert.strictEqual(mixedCaseRedirected.statusCode, 302);
  assert.strictEqual(mixedCaseRedirected.headers.location, 'https://share.example.com/file');

  const duplicate = await request(port, {
    method: 'POST',
    path: '/api/links',
    headers: { 'x-create-token': 'secret' },
    body: { url: 'https://share.example.com/other', slug: 'DEMO-file' },
  });
  assert.strictEqual(duplicate.statusCode, 409);

  const generated = await request(port, {
    method: 'POST',
    path: '/api/links',
    headers: { authorization: 'Bearer secret' },
    body: { url: 'https://share.example.com/generated' },
  });
  assert.strictEqual(generated.statusCode, 201);
  const generatedBody = JSON.parse(generated.body);
  assert.match(generatedBody.slug, /^[a-z0-9_-]{6}$/);
  assert.strictEqual(generatedBody.shortUrl, `https://t.example.com/${generatedBody.slug}`);

  const invalid = await request(port, {
    method: 'POST',
    path: '/api/links',
    headers: { authorization: 'Bearer secret' },
    body: { url: 'javascript:alert(1)' },
  });
  assert.strictEqual(invalid.statusCode, 400);

  await new Promise((resolve) => server.close(resolve));
  console.log('tiny-url tests passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
