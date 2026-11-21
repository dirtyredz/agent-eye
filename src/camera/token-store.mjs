import { readFile, unlink } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAgentEyeDir } from './agent-eye-dir.mjs';

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
  const { directory, atomicWrite } = createAgentEyeDir(root, 'Token');
  const file = path.join(directory, 'token.json');
  return {
    file,
    // Returns { restricted } so the caller can tell the owner when the ACL could not be tightened.
    async save({ token, url }) {
      if (!TOKEN_PATTERN.test(token)) throw new Error('Refusing to store a malformed token');
      let restricted;
      await atomicWrite(
        { name: 'token.json', prefix: '.agent-eye-token-', suffix: '.tmp', harden: async temporary => { restricted = await restrict(temporary); } },
        handle => handle.writeFile(JSON.stringify({ token, url }))
      );
      return { restricted };
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
