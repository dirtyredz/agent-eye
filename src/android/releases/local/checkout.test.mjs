import test from 'node:test';
import assert from 'node:assert/strict';
import { checkout } from './checkout.mjs';
test('checkout preflight rejects changed commit, dirty files and wrong repository', async () => {
  for (const mode of ['dirty', 'origin', 'commit']) {
    await assert.rejects(checkout(async (_file, args) => {
      if (args[0] === 'status') return { stdout: mode === 'dirty' ? ' M source' : '' };
      if (args[0] === 'remote') return { stdout: mode === 'origin' ? 'https://github.com/other/repo.git' : 'https://github.com/dirtyredz/agent-eye.git' };
      if (args[0] === 'rev-parse') return { stdout: args[1] === 'HEAD' ? 'a'.repeat(40) : 'b'.repeat(40) };
      throw new Error('Unexpected command');
    }, '.', 'v1.0.0'), /Clean checkout|Unexpected origin|does not match tag/);
  }
});
