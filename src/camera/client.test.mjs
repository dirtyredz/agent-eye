import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createClient, destination, validateJpeg } from './client.mjs';

// Structural JPEG fixture: SOI, baseline frame, scan, entropy byte, EOI.
const jpeg = Buffer.from('ffd8ffc0000b080001000101011100ffda0008010100003f0000ffd9', 'hex');
const token = 'a'.repeat(64);
const base = 'http://192.168.68.20:8765';

async function fixture(t, handler, options) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); });
  const client = createClient({ ...options, request(url, config, callback) {
    assert.equal(url.hostname, '192.168.68.20');
    return http.request({ ...config, host: '127.0.0.1', port: server.address().port, path: url.pathname }, callback);
  } });
  return client;
}
function send(res, body = jpeg, extra = {}) {
  res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': body.length,
    'Cache-Control': 'no-store', 'X-Capture-Timestamp': '1700000000000', 'X-Capture-Sequence': '1', ...extra });
  res.end(body);
}

test('only canonical literal RFC1918 HTTP destinations are accepted', () => {
  for (const url of [base, 'http://10.0.0.1', 'http://172.16.0.1', 'http://172.31.255.254']) assert.ok(destination(url));
  for (const url of ['https://192.168.1.1', 'http://localhost', 'http://127.0.0.1', 'http://169.254.1.1',
    'http://172.15.1.1', 'http://172.32.0.1', 'http://8.8.8.8', 'http://192.168.1.1@evil.test',
    'http://user:pass@192.168.1.1', 'http://192.168.1.1/shot.jpg', 'http://192.168.1.1?token=x',
    'http://0xc0a80101', 'http://3232235777', 'http://192.168.01.1', 'http://[::ffff:192.168.1.1]', 'http://192.168.1.1:99999']) {
    assert.throws(() => destination(url), undefined, url);
  }
});

test('valid JPEG and status use bearer authentication and preserve metadata', async t => {
  const client = await fixture(t, (req, res) => {
    assert.equal(req.headers.authorization, `Bearer ${token}`);
    if (req.url === '/status') {
      const body = JSON.stringify({ protocolVersion: 1, cameraReady: true, sessionReady: true });
      res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
      res.end(body);
    } else send(res);
  });
  assert.equal((await client.status(base, token)).cameraReady, true);
  const shot = await client.shot(base, token);
  assert.deepEqual(shot.bytes, jpeg);
  assert.equal(shot.sequence, 1);
  assert.equal(shot.capturedAt, 1700000000000);
});

for (const code of [401, 503, 429, 302]) test(`rejects HTTP ${code} without following redirects`, async t => {
  let count = 0;
  const client = await fixture(t, (_, res) => { count++; res.writeHead(code, { Location: 'http://8.8.8.8' }); res.end(); });
  await assert.rejects(client.shot(base, token), new RegExp(`HTTP ${code}`));
  assert.equal(count, 1);
});

test('rejects HTML disguised as JPEG, bad MIME, caching and missing metadata', async t => {
  let index = 0;
  const cases = [
    [Buffer.from('<html>no image</html>'), {}],
    [jpeg, { 'Content-Type': 'text/html' }],
    [jpeg, { 'Cache-Control': 'public' }],
    [jpeg, { 'X-Capture-Sequence': 'NaN' }],
    [Buffer.concat([Buffer.from([255, 216]), Buffer.from('<html>'), Buffer.from([255, 217])]), {}]
  ];
  const client = await fixture(t, (_, res) => send(res, ...cases[index++]));
  for (const _ of cases) await assert.rejects(client.shot(base, token));
});

test('rejects oversized declared response before reading body', async t => {
  const client = await fixture(t, (_, res) => send(res, jpeg, { 'Content-Length': 8 * 1024 * 1024 + 1 }));
  await assert.rejects(client.shot(base, token), /size out of bounds/);
});

test('rejects truncated responses', async t => {
  const client = await fixture(t, (_, res) => {
    res.writeHead(200, { 'Content-Length': 100 }); res.write(jpeg); res.flushHeaders();
    setImmediate(() => res.destroy());
  });
  await assert.rejects(client.shot(base, token), /Truncated|failed/);
});

test('absolute deadline includes a stalled response', async t => {
  const client = await fixture(t, (_, res) => { res.writeHead(200, { 'Content-Length': 100 }); res.write('x'); }, { timeoutMs: 60 });
  await assert.rejects(client.shot(base, token), /deadline/);
});

test('rejects chunked responses and unsupported status protocol', async t => {
  let first = true;
  const client = await fixture(t, (_, res) => {
    if (first) { first = false; res.writeHead(200, { 'Transfer-Encoding': 'chunked' }); res.end(jpeg); }
    else {
      const body = '{"protocolVersion":2,"cameraReady":true,"sessionReady":true}';
      res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': body.length }); res.end(body);
    }
  });
  await assert.rejects(client.shot(base, token), /length/);
  await assert.rejects(client.status(base, token), /protocol/);
});

test('JPEG validation rejects incomplete frame and scan markers', () => {
  assert.doesNotThrow(() => validateJpeg(jpeg));
  for (let length = 0; length < jpeg.length; length++) assert.throws(() => validateJpeg(jpeg.subarray(0, length)));
});
