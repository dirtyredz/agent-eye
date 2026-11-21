import { readFile, mkdir, copyFile } from 'node:fs/promises';
import { join } from 'node:path';
import { UNSIGNED, FINAL, SIGNED_MANIFEST, hashedFinalNames, finalNames, manifest } from '../artifacts/contract.mjs';
import { requireThat } from '../../shared/assert.mjs';
import { verifyFiles, finalManifest, hashes, sha256sums } from '../artifacts/artifacts.mjs';

export function signedBundle({ apk, bundle, localVersion, pin, fresh }) {
  return {
    async sign(state, key) {
      // Stage separately, and persist a complete transaction before the first upload.
      const dir = await fresh('signed-');
      await apk.sign(join(state.input, UNSIGNED[0]), join(dir, FINAL[0]), key, pin, localVersion);
      await bundle.sign(join(state.input, UNSIGNED[1]), join(dir, FINAL[1]), key, pin, localVersion);
      await copyFile(join(state.input, 'mapping.txt'), join(dir, 'mapping.txt'));
      await finalManifest(dir, state.manifest, pin.sha256);
      await mkdir(state.cache, { recursive: true });
      for (const name of finalNames) await copyFile(join(dir, name), join(state.cache, name));
      return state.cache;
    },
    async verify(state, dir) {
      const m = manifest(JSON.parse(await readFile(join(dir, SIGNED_MANIFEST), 'utf8')), true);
      for (const field of ['tag', 'commit', 'versionName', 'versionCode', 'runId', 'runAttempt']) requireThat(m[field] === state.manifest[field], `Cached ${field} mismatch`);
      requireThat(m.signerSha256 === pin.sha256, 'Cached signer mismatch');
      for (const name of UNSIGNED) requireThat(m.unsignedHashes[name] === state.manifest.artifacts[name].sha256, 'Cached provenance mismatch');
      requireThat(m.artifacts['mapping.txt'].sha256 === state.manifest.artifacts['mapping.txt'].sha256, 'Mapping changed');
      await verifyFiles(dir, m, true);
      await apk.verify(join(dir, FINAL[0]), pin, localVersion);
      await bundle.verify(join(dir, FINAL[1]), pin, localVersion);
      requireThat(await readFile(join(dir, 'SHA256SUMS'), 'utf8') === sha256sums(await hashes(dir, hashedFinalNames)), 'SHA256SUMS mismatch');
    },
  };
}
