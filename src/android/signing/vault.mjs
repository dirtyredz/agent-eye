import { requireThat } from '../releases/artifacts/contract.mjs';
let clientPromise;
let account;
async function sdkModule() { const imported = await import('@1password/sdk'); return imported.default ?? imported; }
// One DesktopAuth client (and account) per process; Windows Hello approves the sign-in.
export async function desktopClient(accountId) {
  requireThat(!account || account === accountId, 'One DesktopAuth account per process');
  account = accountId;
  const sdk = await sdkModule();
  clientPromise ??= sdk.createClient({ auth: new sdk.DesktopAuth(accountId), integrationName: 'Agent Eye attended signing', integrationVersion: '1.0.0' });
  return clientPromise;
}
// Tags a failure with the stage it happened in so callers can report it without losing the cause.
export async function atStage(name, action) { try { return await action(); } catch (error) { if (!error?.stage) try { error.stage = name; } catch { /* frozen error */ } throw error; } }
// `injected` ({ client, sdk }) lets tests supply a fake SDK; production uses the DesktopAuth client.
export async function openVault(config, injected) {
  for (const field of ['accountId', 'vaultId', 'documentTitle', 'passwordTitle']) requireThat(typeof config[field] === 'string' && config[field].length > 0 && !config[field].startsWith('OWNER_'), `Configure ${field}`);
  requireThat(config.documentTitle.startsWith('Agent Eye ') && config.passwordTitle.startsWith('Agent Eye '), 'Dedicated Agent Eye items required');
  requireThat(config.documentTitle !== config.passwordTitle, 'Key Document and password need distinct titles');
  const client = injected?.client ?? await desktopClient(config.accountId);
  const sdk = injected?.sdk ?? await sdkModule();
  async function find(title) {
    const matches = (await client.items.list(config.vaultId, { type: 'ByState', content: { active: true, archived: true } })).filter(item => item.title === title);
    requireThat(matches.length <= 1, 'Duplicate Agent Eye vault item');
    return matches[0];
  }
  // The SDK serializes with JSON.stringify and a replacer that only converts `instanceof Uint8Array`; a Node Buffer
  // defines toJSON() ({type:'Buffer',data:[...]}) which the replacer never sees as bytes, so document content is copied
  // into a plain Uint8Array (see docs/GOTCHAS.md).
  return {
    config,
    // Read-only overview of the configured vault's items (no values).
    async overview() {
      return (await client.items.list(config.vaultId, { type: 'ByState', content: { active: true, archived: true } }))
        .map(item => ({ id: item.id, title: item.title, category: item.category, state: item.state, createdAt: item.createdAt }));
    },
    async deleteItem(id) { await client.items.delete(config.vaultId, id); },
    async assertAbsent() { requireThat(!(await find(config.documentTitle)) && !(await find(config.passwordTitle)), 'Agent Eye key/items already exist; recover, never overwrite'); },
    async store(bytes, password) {
      await this.assertAbsent();
      await atStage('store-password', () => client.items.create({ vaultId: config.vaultId, title: config.passwordTitle, category: sdk.ItemCategory.Password, fields: [{ id: 'password', title: 'password', fieldType: sdk.ItemFieldType.Concealed, value: password }] }));
      await atStage('store-document', () => client.items.create({ vaultId: config.vaultId, title: config.documentTitle, category: sdk.ItemCategory.Document, document: { name: 'agent-eye.p12', content: new Uint8Array(bytes) } }));
    },
    async recover() {
      const doc = await find(config.documentTitle), pass = await find(config.passwordTitle);
      requireThat(doc && pass, 'Missing Agent Eye key/password item; inspect partial provisioning');
      const document = await client.items.get(config.vaultId, doc.id);
      const item = await client.items.get(config.vaultId, pass.id);
      const password = item.fields.find(field => field.id === 'password')?.value;
      requireThat(document.document && password, 'Invalid Agent Eye vault items');
      return { bytes: Buffer.from(await client.items.files.read(config.vaultId, doc.id, document.document)), password };
    },
  };
}
