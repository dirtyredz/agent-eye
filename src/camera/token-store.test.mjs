import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, stat, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createTokenStore, restrictToOwner } from './token-store.mjs';

const token = 'ab'.repeat(32);
const base = 'http://192.168.68.20:8765';

const tempRoot = async t => { const dir = await mkdtemp(path.join(tmpdir(), 'agent-eye-')); t.after(() => rm(dir, { recursive: true, force: true })); return dir; };

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
