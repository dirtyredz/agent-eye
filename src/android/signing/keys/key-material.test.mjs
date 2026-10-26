import test from 'node:test';
import assert from 'node:assert/strict';
import { protectedDirectory } from './key-material.mjs';
import { mkdir, mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { run } from '../../tooling/process.mjs';
test('ACL failure cleans directory before key bytes can be written', async () => {
  const calls = [];
  const io = { mkdtemp: async () => 'C:/protected/unique', rm: async () => calls.push('cleanup') };
  await assert.rejects(protectedDirectory({ root: 'C:/checkout', platform: 'win32', io, run: async () => { calls.push('acl'); throw new Error('ACL denied'); } }), /ACL denied/);
  assert.deepEqual(calls, ['acl', 'cleanup']);
});
test('key storage refuses checkout paths and non-Windows hosts', async () => {
  await assert.rejects(protectedDirectory({ platform: 'linux' }), /Windows/);
  await assert.rejects(protectedDirectory({ root: 'C:/checkout', platform: 'win32', io: { mkdtemp: async () => 'C:/checkout/temp', rm: async () => {} }, run: async () => assert.fail('must not run') }), /outside checkout/);
});
test('ACL command verifies protected inheritance, owner and one explicit SID', async () => {
  let script;
  const result = await protectedDirectory({ root: 'C:/checkout', platform: 'win32', io: { mkdtemp: async () => 'C:/protected/unique', rm: async () => {} }, run: async (_file, args) => { script = args.at(-1); } });
  assert.match(script, /AreAccessRulesProtected/); assert.match(script, /rules.Count -ne 1/); assert.match(script, /GetOwner/); assert.match(script, /IsInherited/);
  await result.cleanup();
});
test('Windows verifies a real restricted ACL before writing fixture bytes', { skip: process.platform !== 'win32' }, async () => {
  await mkdir('.tmp', { recursive: true });
  const fixture = await mkdtemp(resolve('.tmp/acl-fixture-'));
  // Model a checkout and a sibling temp root, both inside this worktree for testing.
  const checkout = join(fixture, 'checkout'); await mkdir(checkout);
  let key;
  try {
    key = await protectedDirectory({ root: checkout, tempRoot: fixture, run });
    await writeFile(key.path, 'non-secret fixture bytes', { flag: 'wx' });
  } finally { await key?.cleanup(); await rm(fixture, { recursive: true, force: true }); }
});
test('catchable termination awaits cleanup even when child failure starts normal finally cleanup', async () => {
  await mkdir('.tmp', { recursive: true });
  const fixture = await mkdtemp(resolve('.tmp/termination-fixture-'));
  const marker = join(fixture, 'cleaned');
  const script = `
    import { writeFile } from 'node:fs/promises';
    import { withKeyDirectory } from ${JSON.stringify(new URL('./key-material.mjs', import.meta.url).href)};
    import { onTermination } from ${JSON.stringify(new URL('../../tooling/process.mjs', import.meta.url).href)};
    let fail, ready;
    const started = new Promise(resolve => ready = resolve);
    const cleanup = async () => { await new Promise(resolve => setTimeout(resolve, 100)); await writeFile(${JSON.stringify(marker)}, 'cleaned'); };
    const action = withKeyDirectory({ root: 'C:/fixture-checkout', platform: 'win32', run: async () => {}, io: { mkdtemp: async () => 'C:/fixture-temp/key', rm: cleanup } }, async () => new Promise((_resolve, reject) => { fail = reject; ready(); }));
    action.catch(() => {});
    await started;
    onTermination(async () => { fail(new Error('child terminated')); await new Promise(resolve => setTimeout(resolve, 10)); }, 0);
    process.emit('SIGTERM');
  `;
  try {
    await run(process.execPath, ['--input-type=module', '-e', script], { allowed: [130] });
    assert.equal(await readFile(marker, 'utf8'), 'cleaned');
  } finally { await rm(fixture, { recursive: true, force: true }); }
});
