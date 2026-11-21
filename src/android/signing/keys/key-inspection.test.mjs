import test from 'node:test';
import assert from 'node:assert/strict';
import { keyCertificate } from './key-inspection.mjs';

test('inspection requires one RSA-4096 private key and correct alias', async () => {
  const text = `Your keystore contains 1 entry\nAlias name: agent-eye\nEntry type: PrivateKeyEntry\n4096-bit RSA key\nSHA256: ${'AA:'.repeat(31)}AA`;
  assert.equal(await keyCertificate(async () => ({ stdout: text }), 'key', 'secret'), 'a'.repeat(64));
  for (const changed of [text.replace('1 entry', '2 entries'), text.replace('agent-eye', 'other'), text.replace('agent-eye', 'agent-eye-extra'), text.replace('4096', '2048'), text.replace('PrivateKeyEntry', 'trustedCertEntry')]) await assert.rejects(keyCertificate(async () => ({ stdout: changed }), 'key', 'secret'), /Unexpected key/);
});
