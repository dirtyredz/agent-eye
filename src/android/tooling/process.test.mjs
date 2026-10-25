import test from 'node:test';
import assert from 'node:assert/strict';
import { run } from './process.mjs';
test('subprocess output and errors redact child-only secrets', async () => {
  const secret = 'do-not-print-this';
  const options = { env: { TEST_SECRET: secret }, secrets: [secret] };
  const args = ['-e', 'console.log(process.env.TEST_SECRET);console.error(process.env.TEST_SECRET)'];
  const result = await run(process.execPath, args, options);
  assert.equal(result.stdout.trim(), '[REDACTED]'); assert.equal(result.stderr.trim(), '[REDACTED]');
  await assert.rejects(run(process.execPath, ['-e', 'console.error(process.env.TEST_SECRET);process.exit(2)'], options), error => !error.message.includes(secret) && error.message.includes('[REDACTED]'));
  assert.equal(process.env.TEST_SECRET, undefined);
});
test('unbounded output is terminated and fails', async () => {
  await assert.rejects(run(process.execPath, ['-e', 'console.log("x".repeat(5000))'], { maxBytes: 100 }), /output limit/);
});
