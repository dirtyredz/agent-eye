import { randomBytes } from 'node:crypto';
import { readFile, writeFile, access } from 'node:fs/promises';
import { APPLICATION_ID, certificate, requireThat } from '../releases/artifacts/contract.mjs';
import { PASS_ENV } from './apk-signing.mjs';
import { withKeyDirectory } from './key-material.mjs';

export async function keyCertificate(run, path, password, alias = 'agent-eye') {
  const report = (await run('keytool', ['-list', '-v', '-storetype', 'PKCS12', '-keystore', path, '-storepass:env', PASS_ENV, '-J-Duser.language=en'], { env: { [PASS_ENV]: password }, secrets: [password] })).stdout;
  const hashes = [...report.matchAll(/SHA256: ([A-F0-9:]+)/g)];
  const lines = report.split(/\r?\n/).map(line => line.trim());
  requireThat(lines.includes('Your keystore contains 1 entry') && lines.includes(`Alias name: ${alias}`) && lines.includes('Entry type: PrivateKeyEntry') && /4096-bit RSA/.test(report) && hashes.length === 1, 'Unexpected key identity/type');
  return hashes[0][1].replaceAll(':', '').toLowerCase();
}
export async function provision(deps, { pinPath, firstVersionCode }) {
  const io = deps.io ?? { readFile, writeFile, access };
  const originalPin = await io.readFile(pinPath, 'utf8');
  const old = JSON.parse(originalPin);
  requireThat(old.sha256 === null && old.applicationId === APPLICATION_ID && old.alias === 'agent-eye', 'Refusing existing or invalid certificate pin');
  const vault = await deps.vault();
  await vault.assertAbsent();
  return (deps.withKeyDirectory ?? withKeyDirectory)(deps, async key => {
    try { await io.access(key.path); throw new Error('Key already exists'); } catch (e) { if (e.code !== 'ENOENT') throw e; }
    const password = randomBytes(48).toString('base64url');
    await deps.run('keytool', ['-genkeypair', '-alias', 'agent-eye', '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA', '-validity', '10950', '-dname', 'CN=Agent Eye', '-storetype', 'PKCS12', '-keystore', key.path, '-storepass:env', PASS_ENV, '-keypass:env', PASS_ENV], { env: { [PASS_ENV]: password }, secrets: [password] });
    const sha256 = await keyCertificate(deps.run, key.path, password);
    try {
      const bytes = await io.readFile(key.path);
      try { await vault.store(bytes, password); } finally { bytes.fill(0); }
      const recovered = await vault.recover();
      const recoveryPath = key.path + '.recovered';
      try { await io.writeFile(recoveryPath, recovered.bytes, { flag: 'wx' }); } finally { recovered.bytes.fill(0); }
      requireThat(await keyCertificate(deps.run, recoveryPath, recovered.password) === sha256, 'Recovery certificate mismatch');
      const pin = certificate({ applicationId: APPLICATION_ID, alias: 'agent-eye', firstVersionCode, sha256 });
      requireThat((await io.readFile(pinPath, 'utf8')) === originalPin, 'Pin changed during provisioning');
      await io.writeFile(pinPath, JSON.stringify(pin, null, 2) + '\n');
      return pin;
    } catch { throw new Error('Partial provisioning: inspect both vault items and recover the existing key; do not regenerate. Recovery verification or public pin write did not complete.'); }
  });
}
export async function recoverKey(deps, pin, action) {
  certificate(pin);
  return (deps.withKeyDirectory ?? withKeyDirectory)(deps, async key => {
    const recovered = await (await deps.vault()).recover();
    try { await (deps.io?.writeFile ?? writeFile)(key.path, recovered.bytes, { flag: 'wx' }); }
    finally { recovered.bytes.fill(0); }
    requireThat(await keyCertificate(deps.run, key.path, recovered.password, pin.alias) === pin.sha256, 'Recovered certificate does not match public pin');
    return action({ ...key, password: recovered.password });
  });
}
