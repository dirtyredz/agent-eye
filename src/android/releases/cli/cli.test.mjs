import test from 'node:test';
import assert from 'node:assert/strict';
import { main, parse } from './cli.mjs';
test('every dry-run stops before loading execution adapters', async () => {
  let executions = 0, outputs = 0;
  for (const command of ['setup', 'generate-key', 'verify-key', 'stage', 'draft', 'validate-tag', 'release-sign']) {
    const args = command === 'release-sign' ? [command, '--tag', 'v1.0.0', '--release-id', '123', '--publish'] : [command];
    await main(args, { output: () => outputs++, execute: () => executions++ });
    await main([...args, '--dry-run'], { output: () => outputs++, execute: () => executions++ });
  }
  assert.equal(executions, 0); assert.equal(outputs, 14);
});
test('invalid or conflicting CLI flags fail closed', () => {
  for (const args of [['setup', '--execute', '--dry-run'], ['setup', '--publish'], ['setup', '--clobber'], ['release-sign'], ['release-sign', '--tag', 'v1.0.0', '--release-id', '-1'], ['setup', '--execute', '--execute']]) assert.throws(() => parse(args));
});
