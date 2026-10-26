import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { fixture } from './contract.test.mjs';
import { hashes, verifyFiles } from './artifacts.mjs';
test('artifact hashes cover lengths, missing files and changed bytes', async () => {
  await mkdir('.tmp', { recursive: true }); const dir = await mkdtemp('.tmp/artifact-test-');
  try {
    const m = fixture();
    for (const name of Object.keys(m.artifacts)) await writeFile(join(dir, name), name);
    m.artifacts = await hashes(dir, Object.keys(m.artifacts));
    await verifyFiles(dir, m);
    await writeFile(join(dir, 'mapping.txt'), 'changed');
    await assert.rejects(verifyFiles(dir, m), /Hash\/length mismatch/);
    await rm(join(dir, 'mapping.txt'));
    await assert.rejects(verifyFiles(dir, m), /ENOENT/);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('shared updater fixtures are exactly what the publisher writes', async () => {
  const { writeUpdateFixtures, FIXTURE_PIN } = await import('./update-fixtures.mjs');
  const { readFile } = await import('node:fs/promises');
  const { manifest, certificate } = await import('./contract.mjs');
  await mkdir('.tmp', { recursive: true }); const dir = await mkdtemp('.tmp/update-fixture-');
  try {
    const m = await writeUpdateFixtures(dir);
    manifest(m, true);
    const committed = new URL('./fixtures/update-schema1/', import.meta.url);
    for (const name of ['release.json', 'SHA256SUMS']) {
      assert.deepEqual(await readFile(join(dir, name)), await readFile(new URL(name, committed)), `${name} drifted from the publisher`);
    }
    // The fixture signer is the tracked pin the Android build packages.
    const pin = certificate(JSON.parse(await readFile(new URL('../signing-certificate.json', import.meta.url), 'utf8')));
    assert.equal(m.signerSha256, pin.sha256);
    assert.equal(FIXTURE_PIN, pin.sha256);
    for (const bad of [{ ...pin, sha256: 'F'.repeat(64) }, { ...pin, applicationId: 'other' }, { ...pin, alias: 'x' }, { ...pin, firstVersionCode: 0 }]) {
      assert.throws(() => certificate(bad), /not provisioned or invalid/);
    }
  } finally { await rm(dir, { recursive: true, force: true }); }
});
