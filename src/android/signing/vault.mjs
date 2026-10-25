import { requireThat } from '../releases/contract.mjs';
let clientPromise;
let account;
export async function openVault(config) {
  for (const field of ['accountId', 'vaultId', 'documentTitle', 'passwordTitle']) requireThat(typeof config[field] === 'string' && config[field].length > 0 && !config[field].startsWith('OWNER_'), `Configure ${field}`);
  requireThat(config.documentTitle.startsWith('Agent Eye ') && config.passwordTitle.startsWith('Agent Eye '), 'Dedicated Agent Eye items required');
  requireThat(config.documentTitle !== config.passwordTitle, 'Key Document and password need distinct titles');
  requireThat(!account || account === config.accountId, 'One DesktopAuth account per process');
  account = config.accountId;
  const imported = await import('@1password/sdk');
  const sdk = imported.default ?? imported;
  clientPromise ??= sdk.createClient({ auth: new sdk.DesktopAuth(config.accountId), integrationName: 'Agent Eye attended signing', integrationVersion: '1.0.0' });
  const client = await clientPromise;
  async function find(title) {
    const matches = (await client.items.list(config.vaultId, { type: 'ByState', content: { active: true, archived: true } })).filter(item => item.title === title);
    requireThat(matches.length <= 1, 'Duplicate Agent Eye vault item');
    return matches[0];
  }
  return {
    async assertAbsent() { requireThat(!(await find(config.documentTitle)) && !(await find(config.passwordTitle)), 'Agent Eye key/items already exist; recover, never overwrite'); },
    async store(bytes, password) {
      await this.assertAbsent();
      await client.items.create({ vaultId: config.vaultId, title: config.passwordTitle, category: sdk.ItemCategory.Password, fields: [{ id: 'password', title: 'password', fieldType: sdk.ItemFieldType.Concealed, value: password }] });
      await client.items.create({ vaultId: config.vaultId, title: config.documentTitle, category: sdk.ItemCategory.Document, document: { name: 'agent-eye.p12', content: bytes } });
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
