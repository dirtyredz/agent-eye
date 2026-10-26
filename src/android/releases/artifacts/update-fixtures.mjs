// Shared schema-1 updater fixtures, produced by the real publisher (`finalManifest`) from
// deterministic synthetic artifacts: each final file's bytes are `synthetic <name>\n`.
// The Android JVM tests read the committed copies under fixtures/update-schema1/ and rebuild
// the same synthetic APK bytes, so both sides prove the same wire format.
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { FINAL, UNSIGNED, SDK, REPOSITORY, APPLICATION_ID, WORKFLOW } from './contract.mjs';
import { finalManifest } from './artifacts.mjs';

export const FIXTURE_PIN = 'f0d99c5825fbc240e7fefb68bb791d47cb61c37f13c42a5f9f81730168d6d6e2';
export const syntheticBytes = name => Buffer.from(`synthetic ${name}\n`);

export async function writeUpdateFixtures(dir) {
  await mkdir(dir, { recursive: true });
  for (const name of FINAL) await writeFile(join(dir, name), syntheticBytes(name));
  const original = {
    schema: 1, repository: REPOSITORY, applicationId: APPLICATION_ID, ...SDK,
    versionName: '1.1.1', versionCode: 3, tag: 'v1.1.1', commit: 'c'.repeat(40), runId: 4242, runAttempt: 1,
    workflow: WORKFLOW,
    artifacts: Object.fromEntries(UNSIGNED.map((name, i) => [name, { size: 10 + i, sha256: String(i + 1).repeat(64) }])),
  };
  return finalManifest(dir, original, FIXTURE_PIN);
}
