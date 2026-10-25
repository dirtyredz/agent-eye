import { mkdir, mkdtemp, open, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { onTermination } from '../../tooling/process.mjs';

export function releaseLock(root) {
  const tempRoot = join(root, '.tmp/releases');
  let scratch;
  const fresh = prefix => mkdtemp(join(scratch, prefix));
  return {
    tempRoot, fresh,
    get scratch() { return scratch; },
    async lock() {
      await mkdir(tempRoot, { recursive: true });
      const lockPath = join(root, '.release-sign.lock');
      const handle = await open(lockPath, 'wx');
      await handle.writeFile(JSON.stringify({ pid: process.pid, started: new Date().toISOString() }));
      await handle.close();
      scratch = await mkdtemp(join(tempRoot, 'inspection-'));
      const cleanup = async () => { await rm(scratch, { recursive: true, force: true }); await rm(lockPath, { force: true }); };
      const removeCleanup = onTermination(cleanup, 2);
      return async () => { try { await cleanup(); } finally { removeCleanup(); } };
    },
  };
}
