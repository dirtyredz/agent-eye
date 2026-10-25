import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { provision, keyCertificate, recoverKey } from './key-provisioning.mjs';
test('existing certificate refuses provisioning before vault or subprocess', async () => {
  await mkdir('.tmp', { recursive: true }); const dir = await mkdtemp('.tmp/key-test-'); const path = join(dir, 'pin.json');
  try {
    await writeFile(path, JSON.stringify({ applicationId: 'local.agenteye', alias: 'agent-eye', sha256: 'a'.repeat(64) }));
    await assert.rejects(provision({ vault: () => assert.fail('vault touched') }, { pinPath: path, firstVersionCode: 1 }), /Refusing existing/);
  } finally { await rm(dir, { recursive: true, force: true }); }
});
test('recovery requires one RSA-4096 private key and correct alias', async () => {
  const text = `Your keystore contains 1 entry\nAlias name: agent-eye\nEntry type: PrivateKeyEntry\n4096-bit RSA key\nSHA256: ${'AA:'.repeat(31)}AA`;
  assert.equal(await keyCertificate(async () => ({ stdout: text }), 'key', 'secret'), 'a'.repeat(64));
  for (const changed of [text.replace('1 entry', '2 entries'), text.replace('agent-eye', 'other'), text.replace('4096', '2048'), text.replace('PrivateKeyEntry', 'trustedCertEntry')]) await assert.rejects(keyCertificate(async () => ({ stdout: changed }), 'key', 'secret'), /Unexpected key/);
});
function provisioningFixture(failRecovery = false) {
  const events = [];
  const pin = JSON.stringify({ applicationId: 'local.agenteye', alias: 'agent-eye', sha256: null });
  const deps = {
    root: 'checkout',
    io: {
      readFile: async path => path === 'pin' ? pin : Buffer.from('test-key'),
      writeFile: async path => { events.push(`write:${path}`); },
      access: async () => { throw Object.assign(new Error('missing'), { code: 'ENOENT' }); },
    },
    withKeyDirectory: async (_deps, action) => { try { return await action({ path: 'key' }); } finally { events.push('cleanup'); } },
    vault: async () => ({ assertAbsent: async () => { events.push('absent'); }, store: async () => { events.push('store'); }, recover: async () => { events.push('recover'); if (failRecovery) throw new Error('failed'); return { bytes: Buffer.from('test-key'), password: 'test' }; } }),
    run: async (_file, args) => {
      events.push(args[0]);
      return { stdout: `Your keystore contains 1 entry\nAlias name: agent-eye\nPrivateKeyEntry\n4096-bit RSA\nSHA256: ${'AA:'.repeat(31)}AA` };
    },
  };
  return { deps, events };
}
test('provisioning reads vault back and verifies before public pin write', async () => {
  const f = provisioningFixture(); await provision(f.deps, { pinPath: 'pin', firstVersionCode: 1 });
  assert.ok(f.events.indexOf('recover') < f.events.indexOf('write:pin'));
  assert.ok(f.events.lastIndexOf('-list') < f.events.indexOf('write:pin'));
  assert.equal(f.events.at(-1), 'cleanup');
});
test('partial vault provisioning fails without writing a pin and cleans keys', async () => {
  const f = provisioningFixture(true);
  await assert.rejects(provision(f.deps, { pinPath: 'pin', firstVersionCode: 1 }), /Partial provisioning/);
  assert.equal(f.events.includes('write:pin'), false); assert.equal(f.events.at(-1), 'cleanup');
});
test('existing vault item prevents generating another key', async () => {
  const f = provisioningFixture(); f.deps.vault = async () => ({ assertAbsent: async () => { throw new Error('already exists'); } });
  await assert.rejects(provision(f.deps, { pinPath: 'pin', firstVersionCode: 1 }), /already exists/);
  assert.equal(f.events.includes('-genkeypair'), false);
});
test('changed recovered certificate prevents signing and cleans key material', async () => {
  const f = provisioningFixture();
  await assert.rejects(recoverKey(f.deps, { applicationId: 'local.agenteye', alias: 'agent-eye', sha256: 'b'.repeat(64), firstVersionCode: 1 }, () => assert.fail('must not sign')), /does not match public pin/);
  assert.equal(f.events.at(-1), 'cleanup');
});
