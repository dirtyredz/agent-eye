// `provision` = create/verify the signing vault, then generate the key into it.
import { setupVault } from '../vault/vault-setup.mjs';
import { provision } from './key-provisioning.mjs';

export async function provisionFlow({ root, run, vault, account, log, pinPath, firstVersionCode, say = console.log }) {
  const result = await setupVault({ root, account, log });
  say(`Signing vault: ${result.status}.`);
  const pin = await provision({ root, run, vault }, { pinPath, firstVersionCode });
  say(`Vault recovery verified. Commit only this public certificate: ${JSON.stringify(pin)}`);
  return pin;
}
