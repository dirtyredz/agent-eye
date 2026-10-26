import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectVault, cleanupOrphans } from './vault-maintenance.mjs';

const config = { documentTitle: 'Agent Eye release key', passwordTitle: 'Agent Eye release key password' };
function fakeVault(items) {
  const deleted = [];
  return { config, deleted, overview: async () => items, deleteItem: async id => { deleted.push(id); } };
}
const item = (id, title) => ({ id, title, category: 'Password', state: 'active', createdAt: new Date('2026-10-26T02:00:00Z') });
const noRelease = { api: async () => [] };
const lines = () => { const out = []; out.push = out.push.bind(out); return out; };

test('inspect lists only Agent Eye items and never values', async () => {
  const out = lines();
  const result = await inspectVault({ vault: fakeVault([item('a', 'Agent Eye release key password'), item('b', 'Bank login')]), say: m => out.push(m) });
  assert.equal(result.length, 1);
  assert.ok(out.join('\n').includes('Agent Eye release key password [Password, active, created 2026-10-26T02:00:00.000Z]'));
  assert.ok(!out.join('\n').includes('Bank login'));
});

test('cleanup deletes exactly the two Agent Eye titles with execute', async () => {
  const vault = fakeVault([item('a', 'Agent Eye release key password'), item('b', 'Agent Eye release key'), item('c', 'Agent Eye other'), item('d', 'Unrelated')]);
  const result = await cleanupOrphans({ vault, pin: { sha256: null }, gh: noRelease, execute: true, say: () => {} });
  assert.deepEqual(vault.deleted.sort(), ['a', 'b']); assert.equal(result.deleted, 2);
});

test('cleanup without execute deletes nothing', async () => {
  const vault = fakeVault([item('a', 'Agent Eye release key password')]);
  await cleanupOrphans({ vault, pin: { sha256: null }, gh: noRelease, execute: false, say: () => {} });
  assert.deepEqual(vault.deleted, []);
});

test('cleanup fails closed: pin set, release exists, gh failure, odd gh output, duplicates', async () => {
  const vault = fakeVault([item('a', 'Agent Eye release key')]);
  const cases = [
    [{ sha256: 'a'.repeat(64) }, noRelease, /pin is set/],
    [{ sha256: null }, { api: async () => [{ tag_name: 'v1.0.0' }] }, /release\(s\) exist/],
    [{ sha256: null }, { api: async () => { throw new Error('gh: not logged in'); } }, /could not list GitHub releases/],
    [{ sha256: null }, { api: async () => null }, /could not list GitHub releases/],
  ];
  for (const [pin, gh, pattern] of cases) await assert.rejects(cleanupOrphans({ vault, pin, gh, execute: true, say: () => {} }), pattern);
  const dup = fakeVault([item('a', 'Agent Eye release key'), item('b', 'Agent Eye release key')]);
  await assert.rejects(cleanupOrphans({ vault: dup, pin: { sha256: null }, gh: noRelease, execute: true, say: () => {} }), /duplicate/);
  assert.deepEqual(vault.deleted.concat(dup.deleted), []);
});
