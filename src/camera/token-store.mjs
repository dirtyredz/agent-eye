import { mkdir, lstat, open, readFile, rename, unlink, writeFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const exec = promisify(execFile);
export const TOKEN_PATTERN = /^[0-9a-f]{64}$/;

// Owner-only on POSIX comes from mode 0600. On Windows, mode is ignored, so strip inherited ACEs
// and grant only the current user with icacls (best effort; failure is reported, not hidden).
export async function restrictToOwner(file, { platform = process.platform, env = process.env, run = exec } = {}) {
  if (platform !== 'win32') return true;
  const user = env.USERDOMAIN && env.USERNAME ? `${env.USERDOMAIN}\\${env.USERNAME}` : env.USERNAME;
  if (!user) return false;
  try { await run('icacls', [file, '/inheritance:r', '/grant:r', `${user}:(R,W,D)`]); return true; }
  catch { return false; }
}

// The paired session token (and the phone URL it came from) lives in git-ignored .tmp/agent-eye/token.json.
export function createTokenStore(root = repository, { restrict = restrictToOwner } = {}) {
  const base = path.resolve(root);
  const directory = path.join(base, '.tmp', 'agent-eye');
  const file = path.join(directory, 'token.json');
  async function prepare() {
    for (const dir of [base, path.join(base, '.tmp'), directory]) {
      try { await mkdir(dir); } catch (e) { if (e.code !== 'EEXIST') throw e; }
      const stat = await lstat(dir);
      if (stat.isSymbolicLink() || !stat.isDirectory()) throw new Error('Token directory must be a real directory');
    }
  }
  return {
    file,
    // Returns { restricted } so the caller can tell the owner when the ACL could not be tightened.
    async save({ token, url }) {
      if (!TOKEN_PATTERN.test(token)) throw new Error('Refusing to store a malformed token');
      await prepare();
      const temporary = path.join(directory, `.agent-eye-token-${randomUUID()}.tmp`);
      let handle;
      try {
        handle = await open(temporary, 'wx', 0o600);
        await handle.close();
        handle = undefined;
        const restricted = await restrict(temporary); // tighten before the secret is written
        await writeFile(temporary, JSON.stringify({ token, url })); // keeps the restricted ACL/mode
        await rename(temporary, file);
        return { restricted };
      } finally {
        await handle?.close();
        await unlink(temporary).catch(e => { if (e.code !== 'ENOENT') throw e; });
      }
    },
    async load() {
      try {
        const data = JSON.parse(await readFile(file, 'utf8'));
        if (!TOKEN_PATTERN.test(data.token)) return undefined;
        return { token: data.token, url: typeof data.url === 'string' ? data.url : undefined };
      } catch (e) {
        if (e.code === 'ENOENT' || e instanceof SyntaxError) return undefined;
        throw e;
      }
    },
    async clear() { await unlink(file).catch(e => { if (e.code !== 'ENOENT') throw e; }); }
  };
}
