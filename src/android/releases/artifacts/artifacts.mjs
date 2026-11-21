import { readFile, writeFile, copyFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { UNSIGNED_MANIFEST, SIGNED_MANIFEST, hashedFinalNames, REPOSITORY, WORKFLOW, UNSIGNED, FINAL, manifest } from './contract.mjs';
import { requireThat } from '../../shared/assert.mjs';
import { APPLICATION_ID, SDK } from '../../shared/release-identity.mjs';
import { digest } from '../../shared/digest.mjs';

export async function hashes(dir, names) {
  return Object.fromEntries(await Promise.all(names.map(async name => [name, digest(await readFile(join(dir, name)))])));
}
export async function verifyFiles(dir, m, signed = false) {
  manifest(m, signed);
  const got = await hashes(dir, Object.keys(m.artifacts));
  for (const name of Object.keys(got)) requireThat(JSON.stringify(got[name]) === JSON.stringify(m.artifacts[name]), `Hash/length mismatch: ${name}`);
}
export async function stage({ root, dir, version, tag, commit, runId, runAttempt, inspect }) {
  await mkdir(dir, { recursive: true });
  const sources = ['apk/release/app-release-unsigned.apk', 'bundle/release/app-release.aab', 'mapping/release/mapping.txt'];
  for (let i = 0; i < UNSIGNED.length; i++) await copyFile(join(root, 'android/app/build/outputs', sources[i]), join(dir, UNSIGNED[i]));
  await inspect(dir, version);
  const m = manifest({ schema: 1, repository: REPOSITORY, applicationId: APPLICATION_ID, ...SDK, ...version, tag, commit, runId, runAttempt, workflow: WORKFLOW, artifacts: await hashes(dir, UNSIGNED) });
  await writeFile(join(dir, UNSIGNED_MANIFEST), JSON.stringify(m, null, 2) + '\n');
  return m;
}
export function sha256sums(hashed) {
  return Object.entries(hashed).map(([name, a]) => `${a.sha256}  ${name}\n`).join('');
}
export async function finalManifest(dir, original, signerSha256) {
  const m = manifest({ ...original, artifacts: await hashes(dir, FINAL), signerSha256, unsignedHashes: Object.fromEntries(UNSIGNED.map(name => [name, original.artifacts[name].sha256])) }, true);
  await writeFile(join(dir, SIGNED_MANIFEST), JSON.stringify(m, null, 2) + '\n');
  await writeFile(join(dir, 'SHA256SUMS'), sha256sums(await hashes(dir, hashedFinalNames)));
  return m;
}
