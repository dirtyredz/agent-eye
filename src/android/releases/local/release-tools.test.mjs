import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveReleaseTools } from './release-tools.mjs';

const recorder = (failOn) => { const calls = []; return { calls, run: async (file, args) => { calls.push([file, ...args]); if (failOn && args.includes(failOn)) throw new Error('boom'); return { code: 0, stdout: '' }; } }; };
test('releaseTools runs then stops the daemon (windows via cmd.exe)', async () => {
  const { calls, run } = recorder();
  await resolveReleaseTools({ root: 'R', run, platform: 'win32' });
  assert.equal(calls.length, 2);
  assert.equal(calls[0][0], 'cmd.exe'); assert.ok(calls[0].includes('releaseTools')); assert.ok(calls[1].includes('--stop'));
});
test('daemon is stopped even when releaseTools fails, and the failure propagates', async () => {
  const { calls, run } = recorder('releaseTools');
  await assert.rejects(resolveReleaseTools({ root: 'R', run, platform: 'linux' }), /boom/);
  assert.ok(calls[1].includes('--stop'));
});
