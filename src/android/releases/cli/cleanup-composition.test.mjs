import test from 'node:test';
import assert from 'node:assert/strict';
import { github } from '../github/github.mjs';
import { cleanupOrphans } from '../../signing/vault/vault-maintenance.mjs';

const fakeVault = items => ({ config: { documentTitle: 'Agent Eye release key', passwordTitle: 'Agent Eye release key password' }, overview: async () => items, deleteItem: async () => {} });
const item = (id, title) => ({ id, title, category: 'Password', state: 'active', createdAt: new Date('2026-10-26T02:00:00Z') });

test('cleanup guard sees a release on page 2 (via the github adapter) and refuses', async () => {
  const gh = github(async (_file, args) => {
    const route = args[1];
    const page = Number(/&page=(\d+)/.exec(route)[1]);
    return { stdout: JSON.stringify(page === 1 ? Array(100).fill({ tag_name: 'x', draft: true }) : [{ tag_name: 'v1.0.0' }]) };
  });
  await assert.rejects(cleanupOrphans({ vault: fakeVault([item('a', 'Agent Eye release key')]), pin: { sha256: null }, gh, execute: true, say: () => {} }), /101 GitHub release\(s\) exist/);
});
