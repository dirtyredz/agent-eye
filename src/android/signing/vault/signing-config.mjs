// Locates the git-ignored android/signing.local.json (1Password IDs and item titles, no secrets).
// A fresh clean checkout of a release tag never has it: fall back to the main worktree's copy.
import { access } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { dirname, isAbsolute, join, resolve } from 'node:path';
import { CONFIG_FILE } from './vault-setup.mjs';

const execFileAsync = promisify(execFile);

const defaultExists = path => access(path).then(() => true, () => false);

async function defaultCommonDir(root) {
  const { stdout } = await execFileAsync('git', ['rev-parse', '--git-common-dir'], { cwd: root });
  return stdout.trim();
}

export async function resolveSigningConfigPath(root, { exists = defaultExists, commonDir = defaultCommonDir } = {}) {
  const local = join(root, CONFIG_FILE);
  if (await exists(local)) return local;
  let main = null;
  try {
    const common = await commonDir(root);
    main = dirname(isAbsolute(common) ? common : resolve(root, common));
  } catch { /* not a git checkout: no fallback */ }
  const fallback = main ? join(main, CONFIG_FILE) : null;
  if (fallback && resolve(fallback) !== resolve(local) && await exists(fallback)) return fallback;
  throw new Error(`Signing config not found. Looked in the checkout (${local}) and the main worktree (${fallback ?? 'unavailable: not a git checkout'}). Run scripts\\release\\provision.ps1 from the main checkout first.`);
}
