import test from 'node:test';
import assert from 'node:assert/strict';
import { openVault } from './vault.mjs';

const config = { accountId: 'ACCT', vaultId: 'vault1', documentTitle: 'Agent Eye release key', passwordTitle: 'Agent Eye release key password' };
const sdk = { ItemCategory: { Password: 'Password', Document: 'Document' }, ItemFieldType: { Concealed: 'Concealed' } };

// Mirrors @1password/sdk 0.5.0 serialization (dist/types.js ReplacerFunc): only `instanceof Uint8Array` becomes a
// number array. A Buffer hits Buffer.prototype.toJSON first and arrives as {type,data}, which the core rejects.
const replacer = (_key, value) => value instanceof Uint8Array ? Array.from(value) : value;
function fakeClient() {
  const items = [];
  return {
    items: {
      list: async () => items.map(({ id, title, category }) => ({ id, title, category, state: 'active', createdAt: new Date(0) })),
      create: async params => {
        const wire = JSON.parse(JSON.stringify(params, replacer));
        if (wire.document && !Array.isArray(wire.document.content)) throw new Error('invalid type: map, expected a sequence (document.content)');
        const item = { ...wire, id: `id${items.length}` };
        items.push(item); return item;
      },
      get: async (_vault, id) => {
        const item = items.find(i => i.id === id);
        return { ...item, document: item.document && { name: item.document.name, id: 'file1', size: item.document.content.length } };
      },
      delete: async (_vault, id) => { items.splice(items.findIndex(i => i.id === id), 1); },
      files: { read: async (_vault, id) => Uint8Array.from(items.find(i => i.id === id).document.content) },
    },
    stored: items,
  };
}

test('store sends the key as a plain Uint8Array so it survives SDK JSON serialization (Buffer input)', async () => {
  const client = fakeClient();
  const vault = await openVault(config, { client, sdk });
  await vault.store(Buffer.from([1, 2, 3, 250]), 'pw');
  const recovered = await vault.recover();
  assert.deepEqual([...recovered.bytes], [1, 2, 3, 250]);
  assert.equal(recovered.password, 'pw');
});

test('a failing document create is tagged store-document and leaves the password item orphaned', async () => {
  const client = fakeClient();
  client.items.create = (create => async params => { if (params.document) throw new Error('document rejected'); return create(params); })(client.items.create);
  const vault = await openVault(config, { client, sdk });
  await assert.rejects(vault.store(Buffer.from('x'), 'pw'), error => error.stage === 'store-document' && /document rejected/.test(error.message));
  assert.equal(client.stored.length, 1);
});
