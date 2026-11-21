// `provision` = create/verify the signing vault, then generate the key into it.
import { setupVault } from '../vault/vault-setup.mjs';
import { provision } from './key-provisioning.mjs';

export const reportVault = (say, result) => say(`Signing vault: ${result.status}.`);
export const reportPin = (say, pin) => say(`Vault recovery verified. Commit only this public certificate: ${JSON.stringify(pin)}`);

export async function provisionFlow({ root, run, vault, account, log, pinPath, firstVersionCode, say = console.log }) {
  reportVault(say, await setupVault({ root, account, log }));
  const pin = await provision({ root, run, vault }, { pinPath, firstVersionCode });
  reportPin(say, pin);
  return pin;
}
