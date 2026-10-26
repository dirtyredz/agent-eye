import test from 'node:test';
import assert from 'node:assert/strict';
import { main, parse } from './cli.mjs';
test('every dry-run stops before loading execution adapters', async () => {
  let executions = 0, outputs = 0;
  for (const command of ['setup', 'generate-key', 'verify-key', 'inspect-vault', 'cleanup-orphans', 'stage', 'draft', 'validate-tag', 'release-sign']) {
    const args = command === 'release-sign' ? [command, '--tag', 'v1.0.0', '--release-id', '123', '--publish'] : [command];
    await main(args, { output: () => outputs++, execute: () => executions++ });
    await main([...args, '--dry-run'], { output: () => outputs++, execute: () => executions++ });
  }
  assert.equal(executions, 0); assert.equal(outputs, 18);
});
test('invalid or conflicting CLI flags fail closed', () => {
  for (const args of [['setup', '--execute', '--dry-run'], ['setup', '--publish'], ['setup', '--clobber'], ['release-sign'], ['release-sign', '--tag', 'v1.0.0', '--release-id', '-1'], ['setup', '--execute', '--execute']]) assert.throws(() => parse(args));
});
test('release-sign resolves the draft by default; --release-id only overrides; tools step can be skipped', () => {
  const lookup = parse(['release-sign', '--tag', 'v1.0.0', '--execute']);
  assert.equal(lookup.releaseId, undefined);
  const skip = parse(['release-sign', '--tag', 'v1.0.0', '--release-id', '12', '--skip-release-tools', '--execute']);
  assert.equal(skip.releaseId, '12'); assert.equal(skip.skipReleaseTools, true);
  assert.throws(() => parse(['release-sign', '--tag', '1.0', '--execute']));
  assert.throws(() => parse(['setup', '--skip-release-tools']));
});
