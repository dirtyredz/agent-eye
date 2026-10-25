import { createHash } from 'node:crypto';
import { readFile, writeFile, copyFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { APPLICATION_ID, REPOSITORY, SDK, WORKFLOW, UNSIGNED, FINAL, manifest, requireThat } from './contract.mjs';

export const digest = bytes => ({ size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') });
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
  await writeFile(join(dir, 'release-unsigned.json'), JSON.stringify(m, null, 2) + '\n');
  return m;
}
export async function finalManifest(dir, original, signerSha256) {
  const m = manifest({ ...original, artifacts: await hashes(dir, FINAL), signerSha256, unsignedHashes: Object.fromEntries(UNSIGNED.map(name => [name, original.artifacts[name].sha256])) }, true);
  await writeFile(join(dir, 'release.json'), JSON.stringify(m, null, 2) + '\n');
  const sums = await hashes(dir, [...FINAL, 'release.json']);
  await writeFile(join(dir, 'SHA256SUMS'), Object.entries(sums).map(([name, a]) => `${a.sha256}  ${name}\n`).join(''));
  return m;
}
