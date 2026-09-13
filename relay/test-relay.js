'use strict';

const assert = require('assert');
const crypto = require('crypto');
const http = require('http');
const net = require('net');
const {
  attachWebSocketFrameReader,
  createRelayServer,
  websocketAcceptKey
} = require('./server');

function waitForListening(server) {
  return new Promise((resolve) => {
    if (server.listening) {
      resolve();
      return;
    }
    server.once('listening', resolve);
  });
}

function waitForCondition(predicate, timeoutMs = 3000) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    const tick = () => {
      if (predicate()) {
        resolve();
        return;
      }
      if (Date.now() - startedAt > timeoutMs) {
        reject(new Error('Timed out waiting for condition'));
        return;
      }
      setTimeout(tick, 20);
    };
    tick();
  });
}

function writeClientWebSocketJson(socket, frame) {
  const payload = Buffer.from(JSON.stringify(frame), 'utf8');
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
  const masked = Buffer.from(payload);
  for (let index = 0; index < masked.length; index++) {
    masked[index] ^= mask[index % 4];
  }
  socket.write(Buffer.concat([header, mask, masked]));
}

async function connectAgentWebSocket(port) {
  const socket = net.connect(port, '127.0.0.1');
  await new Promise((resolve, reject) => {
    socket.once('connect', resolve);
    socket.once('error', reject);
  });
  const key = crypto.randomBytes(16).toString('base64');
  socket.write([
    'GET /agent HTTP/1.1',
    `Host: 127.0.0.1:${port}`,
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Key: ${key}`,
    'Sec-WebSocket-Version: 13',
    '\r\n'
  ].join('\r\n'));

  const header = await new Promise((resolve, reject) => {
    let buffer = Buffer.alloc(0);
    const onData = (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      const headerEnd = buffer.indexOf('\r\n\r\n');
      if (headerEnd < 0) {
        return;
      }
      socket.off('data', onData);
      resolve({
        raw: buffer.subarray(0, headerEnd + 4).toString('utf8'),
        rest: buffer.subarray(headerEnd + 4)
      });
    };
    socket.on('data', onData);
    socket.once('error', reject);
  });
  assert.match(header.raw, /^HTTP\/1\.1 101 /);
  assert.ok(header.raw.toLowerCase().includes(`sec-websocket-accept: ${websocketAcceptKey(key).toLowerCase()}`));
  return { socket, initialData: header.rest };
}

function httpGet(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { headers }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: Buffer.concat(chunks).toString('utf8')
      }));
    });
    req.on('error', reject);
  });
}

function httpPost(url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const payload = Buffer.from(body, 'utf8');
    const req = http.request(url, {
      method: 'POST',
      headers: {
        'content-length': payload.length,
        ...headers
      }
    }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: Buffer.concat(chunks).toString('utf8')
      }));
    });
    req.on('error', reject);
    req.end(payload);
  });
}

async function main() {
  let tinyCreateBody = null;
  const tinyApi = http.createServer((req, res) => {
    assert.strictEqual(req.method, 'POST');
    assert.strictEqual(req.url, '/api/links');
    assert.strictEqual(req.headers.authorization, 'Bearer tiny-secret');
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => {
      tinyCreateBody = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const payload = Buffer.from(JSON.stringify({
        slug: 'abc123',
        url: tinyCreateBody.url,
        shortUrl: 'https://tiny.example/abc123',
        createdAt: new Date().toISOString(),
        expiresAt: null,
        visits: 0,
        lastVisitAt: null
      }), 'utf8');
      res.writeHead(201, {
        'content-type': 'application/json; charset=utf-8',
        'content-length': payload.length
      });
      res.end(payload);
    });
  });
  tinyApi.listen(0, '127.0.0.1');
  await waitForListening(tinyApi);

  const relay = createRelayServer({
    publicHost: '127.0.0.1',
    publicPort: 0,
    agentPath: '/agent',
    publicBase: 'https://{slug}.share.example',
    requestTimeoutMs: 5000,
    tinyUrlApiUrl: `http://127.0.0.1:${tinyApi.address().port}/api/links`,
    tinyUrlCreateToken: 'tiny-secret',
    tinyUrlTtlSeconds: 3600
  });
  relay.listen();
  await waitForListening(relay.publicServer);

  const publicPort = relay.publicServer.address().port;
  const { socket: agent, initialData } = await connectAgentWebSocket(publicPort);

  let registeredFrame = null;
  let postedFrame = null;
  attachWebSocketFrameReader(agent, initialData, (frame) => {
    if (frame.type === 'registered') {
      registeredFrame = frame;
      return;
    }
    if (frame.type === 'request') {
      if (frame.method === 'POST') {
        postedFrame = frame;
        assert.strictEqual(frame.path, '/access/request');
        assert.strictEqual(Buffer.from(frame.body, 'base64').toString('utf8'), '{"email":"guest@example.com"}');
        writeClientWebSocketJson(agent, {
          type: 'responseStart',
          id: frame.id,
          status: 200,
          headers: {
            'content-type': 'application/json; charset=utf-8',
            'content-length': '35'
          }
        });
        writeClientWebSocketJson(agent, {
          type: 'responseChunk',
          id: frame.id,
          data: Buffer.from('{"ok":true,"id":"req1","code":"42"}', 'utf8').toString('base64')
        });
        writeClientWebSocketJson(agent, { type: 'responseEnd', id: frame.id });
        return;
      }
      assert.strictEqual(frame.method, 'GET');
      assert.strictEqual(frame.path, '/');
      writeClientWebSocketJson(agent, {
        type: 'responseStart',
        id: frame.id,
        status: 200,
        headers: {
          'content-type': 'text/plain; charset=utf-8',
          'content-length': '12'
        }
      });
      writeClientWebSocketJson(agent, {
        type: 'responseChunk',
        id: frame.id,
        data: Buffer.from('hello remote', 'utf8').toString('base64')
      });
      writeClientWebSocketJson(agent, { type: 'responseEnd', id: frame.id });
    }
  }, (error) => {
    throw error;
  });

  writeClientWebSocketJson(agent, {
    type: 'register',
    slug: 'Test Phone',
    token: 'secret',
    deviceName: 'Pixel Test'
  });

  await waitForCondition(() => registeredFrame !== null);
  assert.strictEqual(registeredFrame.publicUrl, 'https://tiny.example/abc123');
  assert.strictEqual(registeredFrame.shortUrl, 'https://tiny.example/abc123');
  assert.strictEqual(registeredFrame.fullUrl, 'https://test-phone.share.example/?token=secret');
  assert.deepStrictEqual(tinyCreateBody, {
    url: 'https://test-phone.share.example/?token=secret',
    ttlSeconds: 3600
  });

  const denied = await httpGet(`http://127.0.0.1:${publicPort}/test-phone/`);
  assert.strictEqual(denied.status, 401);

  const allowed = await httpGet(`http://127.0.0.1:${publicPort}/test-phone/?token=secret`);
  assert.strictEqual(allowed.status, 200);
  assert.strictEqual(allowed.body, 'hello remote');
  assert.ok(String(allowed.headers['set-cookie']).includes('flux_token=secret'));

  const accessCookie = Array.isArray(allowed.headers['set-cookie'])
    ? allowed.headers['set-cookie'].map((value) => value.split(';')[0]).join('; ')
    : String(allowed.headers['set-cookie'] || '').split(';')[0];
  const accessRequest = await httpPost(
    `http://127.0.0.1:${publicPort}/test-phone/access/request`,
    '{"email":"guest@example.com"}',
    {
      'content-type': 'application/json',
      'cookie': accessCookie
    }
  );
  assert.strictEqual(accessRequest.status, 200);
  assert.strictEqual(accessRequest.body, '{"ok":true,"id":"req1","code":"42"}');
  assert.ok(postedFrame);

  agent.destroy();
  await new Promise((resolve) => relay.close(resolve));
  await new Promise((resolve) => tinyApi.close(resolve));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
