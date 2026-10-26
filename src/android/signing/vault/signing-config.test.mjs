import test from 'node:test';
import assert from 'node:assert/strict';
import { join, resolve } from 'node:path';
import { resolveSigningConfigPath } from './signing-config.mjs';

const root = resolve('/work/tag-checkout');
const main = resolve('/work/main');
const local = join(root, 'android/signing.local.json');
const mainCfg = join(main, 'android/signing.local.json');
const common = join(main, '.git');

test('prefers the checkout copy', async () => {
  assert.equal(await resolveSigningConfigPath(root, { exists: async p => p === local || p === mainCfg, commonDir: async () => common }), local);
});
test('falls back to the main worktree (absolute common dir)', async () => {
  assert.equal(await resolveSigningConfigPath(root, { exists: async p => p === mainCfg, commonDir: async () => common }), mainCfg);
});
test('the main checkout itself (relative common dir) does not fall back to itself', async () => {
  await assert.rejects(resolveSigningConfigPath(main, { exists: async () => false, commonDir: async () => '.git' }), /Looked in the checkout/);
});
test('error names both paths', async () => {
  await assert.rejects(resolveSigningConfigPath(root, { exists: async () => false, commonDir: async () => common }),
    e => e.message.includes(local) && e.message.includes(mainCfg));
});
test('error without git still names the checkout path', async () => {
  await assert.rejects(resolveSigningConfigPath(root, { exists: async () => false, commonDir: async () => { throw new Error('no git'); } }),
    e => e.message.includes(local) && e.message.includes('unavailable'));
});
