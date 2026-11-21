import { unlink, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAgentEyeDir } from './agent-eye-dir.mjs';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const partialPattern = /^\.agent-eye-[0-9a-f-]{36}\.partial$/;

export function createStore(root = repository) {
  const { directory, ensureRealDir, atomicWrite } = createAgentEyeDir(root, 'Snapshot');
  const latest = path.join(directory, 'latest.jpg');
  return {
    async save(bytes) {
      await atomicWrite({ name: 'latest.jpg', prefix: '.agent-eye-', suffix: '.partial' }, async file => {
        await file.writeFile(bytes);
        await file.sync();
      });
      return latest;
    },
    async clean() {
      await ensureRealDir();
      for (const name of await readdir(directory)) {
        if (name === 'latest.jpg' || partialPattern.test(name)) await unlink(path.join(directory, name));
      }
    }
  };
}
