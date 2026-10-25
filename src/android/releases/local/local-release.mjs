import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { finalNames, requireThat } from '../artifacts/contract.mjs';
import { digest } from '../artifacts/artifacts.mjs';
import { recoverKey } from '../../signing/key-provisioning.mjs';
import { releaseLock } from './release-lock.mjs';
import { draftVerification } from './draft-verification.mjs';
import { retryCache } from './retry-cache.mjs';

export function localRelease(deps) {
  const { root, run, gh, vault } = deps;
  const lock = releaseLock(root);
  const { fresh, tempRoot } = lock;
  const verification = draftVerification({ ...deps, fresh, tempRoot });
  return {
    lock: lock.lock,
    preflight: verification.preflight,
    recheck: verification.check,
    history: m => gh.history(m),
    withKey: (publicPin, action) => recoverKey({ root, run, vault }, publicPin, action),
    ...retryCache({ ...deps, fresh }),
    async upload(state, dir) { for (const name of finalNames) await gh.upload(state.id, state.tag, join(dir, name), name, lock.scratch); },
    async verifyDownloads(state, dir) {
      const checkDir = await fresh('uploaded-');
      for (const name of finalNames) requireThat(digest(await gh.download(state.tag, name, checkDir)).sha256 === digest(await readFile(join(dir, name))).sha256, `Uploaded hash mismatch: ${name}`);
    },
    async removeUnsigned(state) { await gh.removeUnsigned(state.id, state.tag); state.cleaned = true; },
    publish: state => gh.publish(state.id, state.tag),
  };
}
