import { readFile, mkdir, copyFile, access } from 'node:fs/promises';
import { join } from 'node:path';
import { UNSIGNED, FINAL, finalNames, manifest, requireThat } from '../artifacts/contract.mjs';
import { verifyFiles, finalManifest, hashes } from '../artifacts/artifacts.mjs';

export function retryCache({ gh, apk, bundle, localVersion, pin, fresh }) {
  return {
    async resume(state) {
      try { await access(join(state.cache, 'release.json')); return state.cache; }
      catch (e) { if (e.code !== 'ENOENT') throw e; }
      const existing = state.list.filter(a => finalNames.includes(a.name) && a.name !== 'mapping.txt');
      if (!existing.length) return null;
      requireThat(finalNames.every(name => state.list.some(a => a.name === name)), 'Partial upload requires retained local .tmp/releases cache; restore it before retry');
      await mkdir(state.cache, { recursive: true });
      for (const name of finalNames) await gh.download(state.tag, name, state.cache);
      return state.cache;
    },
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
      const m = manifest(JSON.parse(await readFile(join(dir, 'release.json'), 'utf8')), true);
      for (const field of ['tag', 'commit', 'versionName', 'versionCode', 'runId', 'runAttempt']) requireThat(m[field] === state.manifest[field], `Cached ${field} mismatch`);
      requireThat(m.signerSha256 === pin.sha256, 'Cached signer mismatch');
      for (const name of UNSIGNED) requireThat(m.unsignedHashes[name] === state.manifest.artifacts[name].sha256, 'Cached provenance mismatch');
      requireThat(m.artifacts['mapping.txt'].sha256 === state.manifest.artifacts['mapping.txt'].sha256, 'Mapping changed');
      await verifyFiles(dir, m, true);
      await apk.verify(join(dir, FINAL[0]), pin, localVersion);
      await bundle.verify(join(dir, FINAL[1]), pin, localVersion);
      const sums = await hashes(dir, [...FINAL, 'release.json']);
      requireThat(await readFile(join(dir, 'SHA256SUMS'), 'utf8') === Object.entries(sums).map(([name, a]) => `${a.sha256}  ${name}\n`).join(''), 'SHA256SUMS mismatch');
    },
  };
}
