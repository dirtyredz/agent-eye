import { mkdir, lstat, open, rename, unlink } from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

// The git-ignored .tmp/agent-eye directory shared by the snapshot and token stores. The symlink
// rejection and the temp-then-rename write live here once; the stores only name their files.
export function createAgentEyeDir(root, label) {
  const base = path.resolve(root);
  const directory = path.join(base, '.tmp', 'agent-eye');

  // No following a redirected .tmp or agent-eye directory, even for cleanup.
  async function ensureRealDir() {
    for (const dir of [base, path.join(base, '.tmp'), directory]) {
      try { await mkdir(dir); } catch (e) { if (e.code !== 'EEXIST') throw e; }
      const stat = await lstat(dir);
      if (stat.isSymbolicLink() || !stat.isDirectory()) throw new Error(`${label} directory must be a real directory`);
    }
  }

  // Creates `<prefix><uuid><suffix>` owner-only (0600) inside the directory, runs `harden(temporaryPath)`
  // (if given) before any content exists, lets `fill(handle)` write it, then renames it over `name`.
  // `name` is a plain file name: no separators or '..'. The temporary file never survives a failure.
  async function atomicWrite({ name, prefix, suffix, harden }, fill) {
    if (path.basename(name) !== name || name === '..' || name === '.' || /[\\/]/.test(name)) {
      throw new Error(`${label} file name must not contain a path`);
    }
    const target = path.join(directory, name);
    await ensureRealDir();
    const temporary = path.join(directory, `${prefix}${randomUUID()}${suffix}`);
    let handle;
    try {
      handle = await open(temporary, 'wx', 0o600);
      await harden?.(temporary);
      await fill(handle);
      await handle.close();
      handle = undefined;
      await rename(temporary, target);
    } finally {
      await handle?.close();
      await unlink(temporary).catch(e => { if (e.code !== 'ENOENT') throw e; });
    }
  }

  return { directory, ensureRealDir, atomicWrite };
}
