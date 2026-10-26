// Explicit, offline integration check. Never loaded by node --test; no owner vault.
// All disposable inputs stay in the worktree and are removed, including on failure.
// Runs from any directory; --dry-run only resolves paths and reads the version.
import { mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { onTermination, run as bareRun } from '../tooling/process.mjs';
import { runScript } from '../../script-run/script-run.mjs';
import { tooling } from '../tooling/android-tools.mjs';
import { readVersion, PASS_ENV } from '../releases/artifacts/contract.mjs';
import { keyCertificate } from './keys/key-provisioning.mjs';
import { main } from '../releases/cli/cli.mjs';

async function smoke(log) {
  const { dryRun } = log;
  const root = log.root;
  const run = log.wrapRun(bareRun);
  const version = readVersion(await readFile(join(root, 'android/version.properties'), 'utf8'));
  if (dryRun) {
    log.step('dry-run: no key, build or signing performed');
    console.log(JSON.stringify({ mode: 'dry-run', root, version: version.versionName }));
    return;
  }
  await mkdir(join(root, '.tmp'), { recursive: true });
  const dir = await mkdtemp(join(root, '.tmp/signing-smoke-'));
  const cleanup = () => rm(dir, { recursive: true, force: true });
  const removeCleanup = onTermination(cleanup);
  try {
    const password = randomBytes(32).toString('hex');
    log.addSecrets(password);
    const key = { path: join(dir, 'throwaway.p12'), password };
    await run('keytool', ['-genkeypair', '-alias', 'agent-eye', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10950', '-dname', 'CN=Agent Eye DISPOSABLE TEST ONLY', '-storetype', 'PKCS12', '-keystore', key.path, '-storepass:env', PASS_ENV], { env: { [PASS_ENV]: password }, secrets: [password] });
    const pin = { alias: 'agent-eye', sha256: await keyCertificate(run, key.path, password) };
    const { apk, bundle } = tooling(root, run);
    const unsignedApk = join(root, 'android/app/build/outputs/apk/release/app-release-unsigned.apk');
    const unsignedAab = join(root, 'android/app/build/outputs/bundle/release/app-release.aab');
    log.step('inspect unsigned outputs');
    await apk.unsigned(unsignedApk, version); await bundle.unsigned(unsignedAab, version);
    const signedApk = join(dir, 'agent-eye.apk'), signedAab = join(dir, 'agent-eye.aab');
    log.step('sign and verify');
    await apk.sign(unsignedApk, signedApk, key, pin, version);
    await bundle.sign(unsignedAab, signedAab, key, pin, version);
    const policy = await readFile(join(root, 'docs/PRIVACY.md'));
    for (const [archive, entry] of [[signedApk, 'assets/PRIVACY.md'], [signedAab, 'base/assets/PRIVACY.md']]) {
      await run('jar', ['xf', archive, entry], { cwd: dir });
      const bundled = await readFile(join(dir, entry));
      if (!bundled.equals(policy)) throw new Error(`Bundled privacy policy differs from source: ${entry}`);
    }
    // The updater's packaged signer pin must be the tracked public pin (not the disposable key).
    const tracked = JSON.parse(await readFile(join(root, 'src/android/releases/signing-certificate.json'), 'utf8')).sha256;
    for (const [archive, entry] of [[signedApk, 'assets/update-signer.txt'], [signedAab, 'base/assets/update-signer.txt']]) {
      await run('jar', ['xf', archive, entry], { cwd: dir });
      if ((await readFile(join(dir, entry), 'utf8')).trim() !== tracked) throw new Error(`Bundled update pin differs from signing-certificate.json: ${entry}`);
    }
    await main(['release-sign', '--tag', `v${version.versionName}`, '--release-id', '1', '--dry-run', '--publish'], { log, output: () => {}, execute: () => { throw new Error('Dry-run attempted execution'); } });
    console.log('PASS: real unsigned inspection, bundled privacy and update-pin equality, APK/AAB signing and verification with disposable RSA-4096 key; dry-run has no execution.');
  } finally {
    try { await cleanup(); } finally { removeCleanup(); }
  }
}

await runScript({ name: 'signing-smoke', from: import.meta.url }, smoke);
