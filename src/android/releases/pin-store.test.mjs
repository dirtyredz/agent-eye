import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pinPathFor, readPin } from './pin-store.mjs';

test('pinPathFor names the tracked certificate file under the root', () => {
  assert.equal(pinPathFor('/r'), join('/r', 'src/android/releases/signing-certificate.json'));
});

test('readPin parses the pin file', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'pin-store-'));
  try {
    const path = join(dir, 'pin.json');
    await writeFile(path, '{"sha256":"ab"}'+'
');
    assert.deepEqual(await readPin(path), { sha256: 'ab' });
  } finally { await rm(dir, { recursive: true, force: true }); }
});
