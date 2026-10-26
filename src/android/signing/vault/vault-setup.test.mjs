import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setupVault, VAULT_TITLE } from './vault-setup.mjs';

async function repo() { const root = await mkdtemp(join(tmpdir(), 'vault-')); await mkdir(join(root, 'android')); return root; }
const fake = (vaults, creates = true) => {
  const calls = [];
  return { calls, vaults: { list: async () => vaults, ...(creates ? { create: async v => { calls.push(v.title); return { id: 'new-vault', title: v.title }; } } : {}) } };
};

test('creates the vault when absent and writes the config', async () => {
  const root = await repo(), client = fake([{ id: 'x', title: 'Other' }]);
  const result = await setupVault({ root, account: 'ACC', client, env: {} });
  assert.deepEqual(client.calls, [VAULT_TITLE]);
  const config = JSON.parse(await readFile(join(root, 'android/signing.local.json'), 'utf8'));
  assert.equal(config.vaultId, 'new-vault'); assert.equal(config.accountId, 'ACC');
  assert.match(result.status, /created/);
  await rm(root, { recursive: true, force: true });
});
test('reuses an existing vault; a correct rerun changes nothing', async () => {
  const root = await repo(), client = fake([{ id: 'v1', title: VAULT_TITLE }]);
  await setupVault({ root, account: 'ACC', client, env: {} });
  const again = await setupVault({ root, client, env: {} });
  assert.deepEqual(client.calls, []);
  assert.equal(again.status, 'already set up');
  await rm(root, { recursive: true, force: true });
});
test('refuses duplicate vaults, a conflicting config and missing create support', async () => {
  const root = await repo();
  await assert.rejects(setupVault({ root, account: 'A', client: fake([{ id: '1', title: VAULT_TITLE }, { id: '2', title: VAULT_TITLE }]), env: {} }), /Ambiguous/);
  await assert.rejects(setupVault({ root, account: 'A', client: fake([], false), env: {} }), /cannot create vaults/);
  await writeFile(join(root, 'android/signing.local.json'), JSON.stringify({ accountId: 'A', vaultId: 'other' }));
  await assert.rejects(setupVault({ root, account: 'A', client: fake([{ id: 'v1', title: VAULT_TITLE }]), env: {} }), /never overwritten/);
  await rm(root, { recursive: true, force: true });
});
