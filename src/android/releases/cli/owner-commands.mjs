import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { certificate } from '../artifacts/contract.mjs';
import { setup } from './owner-setup.mjs';
import { releaseSign } from '../local/release-sign.mjs';
import { resolveReleaseTools } from '../local/release-tools.mjs';
import { localRelease } from '../local/local-release.mjs';
import { setupVault } from '../../signing/vault/vault-setup.mjs';
import { provisionFlow } from '../../signing/keys/provision-flow.mjs';
import { openVault } from '../../signing/vault/vault.mjs';
import { resolveSigningConfigPath } from '../../signing/vault/signing-config.mjs';
import { provision, recoverKey } from '../../signing/keys/key-provisioning.mjs';
import { inspectVault, cleanupOrphans } from '../../signing/vault/vault-maintenance.mjs';
import { tooling } from '../../tooling/android-tools.mjs';

export async function ownerCommand({ root, run, gh, localVersion, log }, options) {
  const pinPath = join(root, 'src/android/releases/signing-certificate.json');
  const vault = async () => openVault(JSON.parse(await readFile(await resolveSigningConfigPath(root), 'utf8')));
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
  if (options.command === 'inspect-vault') return inspectVault({ vault: await vault() });
  if (options.command === 'cleanup-orphans') return cleanupOrphans({ vault: await vault(), pin: JSON.parse(await readFile(pinPath, 'utf8')), gh, execute: options.execute });
  const { apk, bundle } = tooling(root, run);
  if (options.command === 'release-sign') {
    const releaseId = options.releaseId ?? await gh.draftId(options.tag);
    if (!options.skipReleaseTools) await resolveReleaseTools({ root, run });
    const outputs = await releaseSign(localRelease({ root, run, gh, apk, bundle, vault, localVersion, pin: JSON.parse(await readFile(pinPath, 'utf8')) }), { ...options, releaseId });
    console.log(`${options.publish ? 'Published' : 'Verified signed draft'} ${options.tag}, release ${releaseId}. Public retry cache: ${outputs}`);
    return outputs;
  }
}
