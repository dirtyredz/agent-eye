import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, readdir, writeFile, rm, symlink } from 'node:fs/promises';
import path from 'node:path';
import { createStore } from './snapshot-store.mjs';

async function sandbox(t) {
  // Keep test artifacts in this worktree, including on failure.
  const parent = path.resolve('.tmp');
  await mkdir(parent, { recursive: true });
  const root = await mkdtemp(path.join(parent, 'store-test-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  return { root, directory: path.join(root, '.tmp', 'agent-eye'), store: createStore(root) };
}

test('atomic replacement retains one photograph and no partial files', async t => {
  const { store, directory } = await sandbox(t);
  const first = await store.save(Buffer.from('first'));
  const next = await store.save(Buffer.from('second'));
  assert.equal(first, next);
  assert.ok(path.isAbsolute(next));
  assert.equal(await readFile(next, 'utf8'), 'second');
  assert.deepEqual(await readdir(directory), ['latest.jpg']);
});

test('failed writes do not claim success or leave partial files', async t => {
  const { store, directory } = await sandbox(t);
  const latest = await store.save(Buffer.from('previous'));
  await assert.rejects(store.save({ invalid: 'not bytes' }));
  assert.equal(await readFile(latest, 'utf8'), 'previous');
  assert.deepEqual(await readdir(directory), ['latest.jpg']);
});

test('failed replacement removes partial file', async t => {
  const { store, directory } = await sandbox(t);
  await store.clean();
  await mkdir(path.join(directory, 'latest.jpg'));
  await assert.rejects(store.save(Buffer.from('new')));
  assert.deepEqual(await readdir(directory), ['latest.jpg']);
});

test('clean removes only owned names and never traverses directories', async t => {
  const { store, directory, root } = await sandbox(t);
  await store.save(Buffer.from('photo'));
  await writeFile(path.join(directory, 'keep.txt'), 'keep');
  await writeFile(path.join(root, 'latest.jpg'), 'outside');
  await writeFile(path.join(directory, '.agent-eye-00000000-0000-0000-0000-000000000000.partial'), 'partial');
  await store.clean();
  assert.deepEqual(await readdir(directory), ['keep.txt']);
  assert.equal(await readFile(path.join(root, 'latest.jpg'), 'utf8'), 'outside');
});

test('redirected snapshot directory fails closed for save and cleanup', async t => {
  const { root, store } = await sandbox(t);
  const outside = path.join(root, 'outside');
  await mkdir(outside);
  await writeFile(path.join(outside, 'latest.jpg'), 'keep');
  await symlink(outside, path.join(root, '.tmp'), process.platform === 'win32' ? 'junction' : 'dir');
  await assert.rejects(store.clean(), /real directory/);
  await assert.rejects(store.save(Buffer.from('bad')), /real directory/);
  assert.equal(await readFile(path.join(outside, 'latest.jpg'), 'utf8'), 'keep');
});
