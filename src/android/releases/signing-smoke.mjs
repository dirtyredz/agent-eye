// Explicit, offline integration check. Never loaded by node --test; no owner vault.
// All disposable inputs stay in the worktree and are removed, including on failure.
import { mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';
import { run, onTermination } from '../tooling/process.mjs';
import { tooling } from './commands.mjs';
import { readVersion } from './contract.mjs';
import { keyCertificate } from '../signing/key-provisioning.mjs';
import { main } from './cli.mjs';

const root = resolve('.');
await mkdir('.tmp', { recursive: true });
const dir = await mkdtemp(join(root, '.tmp/signing-smoke-'));
const cleanup = () => rm(dir, { recursive: true, force: true });
const removeCleanup = onTermination(cleanup);
try {
  const password = randomBytes(32).toString('hex');
  const key = { path: join(dir, 'throwaway.p12'), password };
  await run('keytool', ['-genkeypair', '-alias', 'agent-eye', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10950', '-dname', 'CN=Agent Eye DISPOSABLE TEST ONLY', '-storetype', 'PKCS12', '-keystore', key.path, '-storepass:env', 'AGENT_EYE_KEY_PASSWORD'], { env: { AGENT_EYE_KEY_PASSWORD: password }, secrets: [password] });
  const pin = { alias: 'agent-eye', sha256: await keyCertificate(run, key.path, password) };
  const version = readVersion(await readFile('android/version.properties', 'utf8'));
  const { apk, bundle } = tooling(root);
  const unsignedApk = join(root, 'android/app/build/outputs/apk/release/app-release-unsigned.apk');
  const unsignedAab = join(root, 'android/app/build/outputs/bundle/release/app-release.aab');
  await apk.unsigned(unsignedApk, version); await bundle.unsigned(unsignedAab, version);
  await apk.sign(unsignedApk, join(dir, 'agent-eye.apk'), key, pin, version);
  await bundle.sign(unsignedAab, join(dir, 'agent-eye.aab'), key, pin, version);
  await main(['release-sign', '--tag', `v${version.versionName}`, '--release-id', '1', '--dry-run', '--publish'], { execute: () => { throw new Error('Dry-run attempted execution'); } });
  console.log('PASS: real unsigned inspection, APK/AAB signing and verification with disposable RSA-4096 key; dry-run has no execution.');
} finally {
  removeCleanup();
  await cleanup();
}
