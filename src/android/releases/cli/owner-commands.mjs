import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { certificate } from '../artifacts/contract.mjs';
import { setup } from './owner-setup.mjs';
import { releaseSign } from '../local/release-sign.mjs';
import { localRelease } from '../local/local-release.mjs';
import { openVault } from '../../signing/vault.mjs';
import { provision, recoverKey } from '../../signing/key-provisioning.mjs';
import { tooling } from '../../tooling/android-tools.mjs';

export async function ownerCommand({ root, run, gh, localVersion }, options) {
  const pinPath = join(root, 'src/android/releases/signing-certificate.json');
  const vault = async () => openVault(JSON.parse(await readFile(join(root, 'android/signing.local.json'), 'utf8')));
  if (options.command === 'setup') return setup(run);
  if (options.command === 'generate-key') {
    const pin = await provision({ root, run, vault }, { pinPath, firstVersionCode: localVersion.versionCode });
    console.log(`Vault recovery verified. Commit only this public certificate: ${JSON.stringify(pin)}`);
    return pin;
  }
  if (options.command === 'verify-key') return recoverKey({ root, run, vault }, certificate(JSON.parse(await readFile(pinPath, 'utf8'))), async () => console.log('Recovery verified against committed public certificate.'));
  const { apk, bundle } = tooling(root);
  if (options.command === 'release-sign') {
    const outputs = await releaseSign(localRelease({ root, run, gh, apk, bundle, vault, localVersion, pin: JSON.parse(await readFile(pinPath, 'utf8')) }), options);
    console.log(`${options.publish ? 'Published' : 'Verified signed draft'} ${options.tag}, release ${options.releaseId}. Public retry cache: ${outputs}`);
    return outputs;
  }
}
