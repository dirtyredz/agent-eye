import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { certificate } from '../artifacts/contract.mjs';
import { setup } from './owner-setup.mjs';
import { releaseSign } from '../local/release-sign.mjs';
import { localRelease } from '../local/local-release.mjs';
import { setupVault } from '../../signing/vault-setup.mjs';
import { provisionFlow } from '../../signing/provision-flow.mjs';
import { openVault } from '../../signing/vault.mjs';
import { provision, recoverKey } from '../../signing/key-provisioning.mjs';
import { tooling } from '../../tooling/android-tools.mjs';

export async function ownerCommand({ root, run, gh, localVersion, log }, options) {
  const pinPath = join(root, 'src/android/releases/signing-certificate.json');
  const vault = async () => openVault(JSON.parse(await readFile(join(root, 'android/signing.local.json'), 'utf8')));
  const keyOptions = { pinPath, firstVersionCode: localVersion.versionCode };
  if (options.command === 'setup') return setup(run, root);
  if (options.command === 'setup-vault') {
    const result = await setupVault({ root, account: options.account, log });
    console.log(`Signing vault: ${result.status}.`);
    return result;
  }
  if (options.command === 'provision') return provisionFlow({ root, run, vault, account: options.account, log, ...keyOptions });
  if (options.command === 'generate-key') {
    const pin = await provision({ root, run, vault }, keyOptions);
    console.log(`Vault recovery verified. Commit only this public certificate: ${JSON.stringify(pin)}`);
    return pin;
  }
  if (options.command === 'verify-key') return recoverKey({ root, run, vault }, certificate(JSON.parse(await readFile(pinPath, 'utf8'))), async () => console.log('Recovery verified against committed public certificate.'));
  const { apk, bundle } = tooling(root, run);
  if (options.command === 'release-sign') {
    const outputs = await releaseSign(localRelease({ root, run, gh, apk, bundle, vault, localVersion, pin: JSON.parse(await readFile(pinPath, 'utf8')) }), options);
    console.log(`${options.publish ? 'Published' : 'Verified signed draft'} ${options.tag}, release ${options.releaseId}. Public retry cache: ${outputs}`);
    return outputs;
  }
}
