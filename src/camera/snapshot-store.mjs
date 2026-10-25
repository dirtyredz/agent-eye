import { mkdir, lstat, open, rename, unlink, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const partialPattern = /^\.agent-eye-[0-9a-f-]{36}\.partial$/;

export function createStore(root = repository) {
  const base = path.resolve(root);
  const directory = path.join(base, '.tmp', 'agent-eye');
  const latest = path.join(directory, 'latest.jpg');
  async function prepare() {
    // No following a redirected .tmp or snapshot directory, even for cleanup.
    for (const dir of [base, path.join(base, '.tmp'), directory]) {
      try { await mkdir(dir); } catch (e) { if (e.code !== 'EEXIST') throw e; }
      const stat = await lstat(dir);
      if (stat.isSymbolicLink() || !stat.isDirectory()) throw new Error('Snapshot directory must be a real directory');
    }
  }
  return {
    async save(bytes) {
      await prepare();
      const temporary = path.join(directory, `.agent-eye-${randomUUID()}.partial`);
      let file;
      try {
        file = await open(temporary, 'wx', 0o600);
        await file.writeFile(bytes);
        await file.sync();
        await file.close();
        file = undefined;
        await rename(temporary, latest);
        return latest;
      } finally {
        await file?.close();
        await unlink(temporary).catch(e => { if (e.code !== 'ENOENT') throw e; });
      }
    },
    async clean() {
      await prepare();
      for (const name of await readdir(directory)) {
        if (name === 'latest.jpg' || partialPattern.test(name)) await unlink(path.join(directory, name));
      }
    }
  };
}
