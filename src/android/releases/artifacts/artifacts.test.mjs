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
