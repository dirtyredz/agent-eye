import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtemp, readFile, rm, stat, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createClient } from './client.mjs';
import { createTokenStore, restrictToOwner } from './token-store.mjs';
import { parse, redaction, resolveTarget, USAGE } from './snapshot-args.mjs';
import { createScriptLog } from '../script-run/script-run.mjs';

const token = 'ab'.repeat(32);
const base = 'http://192.168.68.20:8765';

async function pairingClient(t, handler) {
  const server = http.createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); });
  return createClient({ request(url, config, callback) {
    return http.request({ ...config, host: '127.0.0.1', port: server.address().port, path: url.pathname }, callback);
  } });
}
function body(req) { return new Promise(resolve => { let text = ''; req.on('data', c => { text += c; }); req.on('end', () => resolve(text)); }); }
const tempRoot = async t => { const dir = await mkdtemp(path.join(tmpdir(), 'agent-eye-')); t.after(() => rm(dir, { recursive: true, force: true })); return dir; };

test('pair POSTs the code unauthenticated and returns the token', async t => {
  let seen;
  const client = await pairingClient(t, async (req, res) => {
    seen = { method: req.method, url: req.url, auth: req.headers.authorization, type: req.headers['content-type'], body: await body(req) };
    const out = JSON.stringify({ token });
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(out) });
    res.end(out);
  });
  assert.equal(await client.pair(base, '012345'), token);
  assert.deepEqual(seen, { method: 'POST', url: '/pair', auth: undefined, type: 'application/json', body: '{"code":"012345"}' });
});

test('pair validates the code locally and explains a rejected code without echoing it', async t => {
  const client = await pairingClient(t, (_, res) => { res.writeHead(403, { 'Content-Length': 0 }); res.end(); });
  for (const bad of ['12345', '1234567', 'abcdef', '12 456', undefined]) await assert.rejects(client.pair(base, bad), /exactly 6 digits/);
  await assert.rejects(client.pair(base, '123456'), error => /rejected, expired or already used/.test(error.message) && !error.message.includes('123456'));
});

test('pair rejects malformed token responses', async t => {
  let index = 0;
  const replies = [{ token: 'short' }, { nope: 1 }, { token: token.toUpperCase() }];
  const client = await pairingClient(t, (_, res) => {
    const out = JSON.stringify(replies[index++]);
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(out) }); res.end(out);
  });
  for (const _ of replies) await assert.rejects(client.pair(base, '123456'), /Invalid pairing response/);
});

test('token store round-trips, replaces, clears and refuses junk', async t => {
  const root = await tempRoot(t);
  const calls = [];
  const store = createTokenStore(root, { restrict: async file => { calls.push(file); return false; } });
  assert.equal(await store.load(), undefined);
  assert.deepEqual(await store.save({ token, url: base }), { restricted: false });
  assert.deepEqual(await store.load(), { token, url: base });
  assert.equal(calls.length, 1, 'permissions are tightened before the secret is written');
  assert.equal(store.file, path.join(root, '.tmp', 'agent-eye', 'token.json'));
  assert.deepEqual(await readdir(path.dirname(store.file)), ['token.json'], 'no temp files left behind');
  if (process.platform !== 'win32') assert.equal((await stat(store.file)).mode & 0o777, 0o600);
  await assert.rejects(store.save({ token: 'nope', url: base }), /malformed/);
  await writeFile(store.file, '{"token":"bad"}');
  assert.equal(await store.load(), undefined);
  await writeFile(store.file, 'not json');
  assert.equal(await store.load(), undefined);
  await store.clear(); await store.clear();
  assert.equal(await store.load(), undefined);
});

test('restrictToOwner uses icacls on Windows only', async () => {
  const run = [];
  assert.equal(await restrictToOwner('f', { platform: 'linux', run: async (...a) => run.push(a) }), true);
  assert.equal(run.length, 0);
  assert.equal(await restrictToOwner('f', { platform: 'win32', env: { USERDOMAIN: 'PC', USERNAME: 'me' }, run: async (...a) => run.push(a) }), true);
  assert.deepEqual(run[0], ['icacls', ['f', '/inheritance:r', '/grant:r', 'PC\\me:(R,W,D)']]);
  assert.equal(await restrictToOwner('f', { platform: 'win32', env: { USERNAME: 'me' }, run: async () => { throw new Error('x'); } }), false);
});

test('token resolution: --token, then env, then stored; url falls back to the paired phone', async t => {
  const root = await tempRoot(t);
  const tokens = createTokenStore(root, { restrict: async () => true });
  await tokens.save({ token, url: base });
  const other = 'cd'.repeat(32);
  assert.deepEqual(await resolveTarget({}, { env: {}, tokens }), { url: base, token });
  assert.deepEqual(await resolveTarget({ url: 'http://10.0.0.5:8765' }, { env: {}, tokens }), { url: 'http://10.0.0.5:8765', token });
  assert.deepEqual(await resolveTarget({}, { env: { AGENT_EYE_TOKEN: other }, tokens }), { url: undefined, token: other });
  assert.deepEqual(await resolveTarget({ token: 'flag' }, { env: { AGENT_EYE_TOKEN: other }, tokens }), { url: undefined, token: 'flag' });
  await tokens.clear();
  assert.deepEqual(await resolveTarget({}, { env: {}, tokens }), { url: undefined, token: undefined });
});

test('argument parsing is strict', () => {
  assert.deepEqual(parse(['pair', '--url', base, '--code', '123456']), { command: 'pair', url: base, code: '123456' });
  assert.deepEqual(parse(['clean']), { command: 'clean' });
  for (const bad of [['shot', 'extra'], ['shot', '--bogus', 'x'], ['shot', '--url']]) assert.throws(() => parse(bad), new RegExp(USAGE.slice(0, 20)));
});

test('run log redacts the pairing code, the token and the --token argument', async t => {
  const root = await tempRoot(t);
  for (const argv of [['pair', '--url', base, '--code', '482913'], ['pair', '--url', base, '--code=482913'], ['status', '--token', token]]) {
    const log = createScriptLog({ name: 'snapshot', from: new URL('snapshot.mjs', import.meta.url), root, argv, redaction, echo() {} });
    log.write(`argv: ${JSON.stringify(log.argv)}`);
    log.write(`got ${token} and Bearer ${token}`);
    const text = await readFile(log.path, 'utf8');
    assert.ok(!text.includes('482913'), text);
    assert.ok(!text.includes(token), text);
    assert.match(text, /\[REDACTED\]/);
  }
});
