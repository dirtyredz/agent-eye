import test from 'node:test';
import assert from 'node:assert/strict';
import { setup } from './owner-setup.mjs';
test('setup refuses existing origin before any network mutation', async () => {
  const calls = [];
  await assert.rejects(setup(async (file, args) => { calls.push([file, ...args]); return { stdout: args[0] === 'remote' ? 'origin\n' : '' }; }), /Existing origin/);
  assert.ok(calls.every(c => c[0] === 'git'));
});
test('setup refuses existing repo and ambiguous lookup failures', async () => {
  for (const probe of [{ code: 0, stdout: '{}' }, { code: 1, stderr: 'network failed' }]) {
    const calls = [];
    await assert.rejects(setup(async (file, args) => { calls.push([file, ...args]); return file === 'gh' ? probe : { stdout: '' }; }), /Existing repository or ambiguous/);
    assert.equal(calls.some(c => c.includes('create') || c.includes('push')), false);
  }
});
