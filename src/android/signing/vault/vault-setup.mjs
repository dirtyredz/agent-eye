// One-command owner setup of the dedicated signing vault and android/signing.local.json.
// Creates (never modifies) the "Agent Eye Signing" vault; refuses ambiguity and never overwrites config.
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { requireThat } from '../../releases/artifacts/contract.mjs';
import { desktopClient } from './vault.mjs';
import { DEFAULT_ACCOUNT } from './owner-account.mjs';

export const VAULT_TITLE = 'Agent Eye Signing';
export const CONFIG_FILE = 'android/signing.local.json';
export const vaultPlan = [
  `Resolve the 1Password account (--account, OP_ACCOUNT_ID, existing ${CONFIG_FILE}, else the owner default) and sign in with DesktopAuth (Windows Hello).`,
  `Create the dedicated vault "${VAULT_TITLE}" if absent; refuse duplicates.`,
  `Write ${CONFIG_FILE} (git-ignored) with account, vault ID and item titles; never overwrite an existing different config.`,
];

export async function setupVault({ root, account, env = process.env, client: injected, io = { readFile, writeFile }, log }) {
  const path = join(root, CONFIG_FILE);
  let existing;
  try { existing = JSON.parse(await io.readFile(path, 'utf8')); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  const accountId = account ?? env.OP_ACCOUNT_ID ?? existing?.accountId ?? DEFAULT_ACCOUNT;
  requireThat(!accountId.startsWith('OWNER_'), 'Pass --account with the real 1Password account ID');
  log?.step(`vault setup for account ${accountId}`);
  const client = injected ?? await desktopClient(accountId);
  const matches = (await client.vaults.list()).filter(v => v.title === VAULT_TITLE);
  requireThat(matches.length <= 1, 'Ambiguous Agent Eye Signing vault; resolve in 1Password');
  let vault = matches[0], created = false;
  if (!vault) {
    requireThat(typeof client.vaults.create === 'function', `The installed @1password/sdk cannot create vaults; create "${VAULT_TITLE}" once in 1Password, then rerun`);
    vault = await client.vaults.create({ title: VAULT_TITLE }); created = true;
  }
  const config = { accountId, vaultId: vault.id, documentTitle: 'Agent Eye release key', passwordTitle: 'Agent Eye release key password' };
  if (existing) {
    const same = existing.accountId === config.accountId && existing.vaultId === config.vaultId;
    requireThat(same, `${CONFIG_FILE} already points elsewhere; inspect it, it is never overwritten`);
    return { status: 'already set up', created, config };
  }
  await io.writeFile(path, JSON.stringify(config, null, 2) + '\n', { flag: 'wx' });
  return { status: created ? 'vault created, config written' : 'existing vault, config written', created, config };
}
