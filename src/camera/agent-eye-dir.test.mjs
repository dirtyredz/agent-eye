import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, readdir, rm, symlink } from 'node:fs/promises';
import path from 'node:path';
import { createAgentEyeDir } from './agent-eye-dir.mjs';

async function sandbox(t) {
  const parent = path.resolve('.tmp');
  await mkdir(parent, { recursive: true });
  const root = await mkdtemp(path.join(parent, 'dir-test-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  return root;
}

test('redirected directory is rejected with the caller label', async t => {
  const root = await sandbox(t);
  const outside = path.join(root, 'outside');
  await mkdir(outside);
  await symlink(outside, path.join(root, '.tmp'), process.platform === 'win32' ? 'junction' : 'dir');
  const dir = createAgentEyeDir(root, 'Thing');
  await assert.rejects(dir.ensureRealDir(), /Thing directory must be a real directory/);
  await assert.rejects(dir.atomicWrite({ name: 'x', prefix: 'p', suffix: '.t' }, async () => {}), /real directory/);
});

test('atomic write replaces the target and leaves no temporary file, also on failure', async t => {
  const root = await sandbox(t);
  const { directory, atomicWrite } = createAgentEyeDir(root, 'Thing');
  const target = path.join(directory, 'out.txt');
  const name = 'out.txt';
  await atomicWrite({ name, prefix: '.p-', suffix: '.tmp' }, h => h.writeFile('one'));
  await assert.rejects(atomicWrite({ name, prefix: '.p-', suffix: '.tmp' }, async () => { throw new Error('boom'); }), /boom/);
  assert.equal(await readFile(target, 'utf8'), 'one');
  assert.deepEqual(await readdir(directory), ['out.txt']);
});

test('file name with a path separator or dot segment is rejected', async t => {
  const root = await sandbox(t);
  const { atomicWrite } = createAgentEyeDir(root, 'Thing');
  for (const name of ['../x', 'a/b', 'a\\b', '..', '.']) {
    await assert.rejects(atomicWrite({ name, prefix: 'p', suffix: '.t' }, async () => {}), /must not contain a path/);
  }
});
