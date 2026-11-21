import { requireThat } from '../../shared/assert.mjs';
import { PASS_ENV } from '../../shared/signing-policy.mjs';

// keytool inspection of a PKCS12 key: one RSA-4096 private-key entry with the pinned alias.
export async function keyCertificate(run, path, password, alias = 'agent-eye') {
  const report = (await run('keytool', ['-list', '-v', '-storetype', 'PKCS12', '-keystore', path, '-storepass:env', PASS_ENV, '-J-Duser.language=en'], { env: { [PASS_ENV]: password }, secrets: [password] })).stdout;
  const hashes = [...report.matchAll(/SHA256: ([A-F0-9:]+)/g)];
  const lines = report.split(/\r?\n/).map(line => line.trim());
  requireThat(lines.includes('Your keystore contains 1 entry') && lines.includes(`Alias name: ${alias}`) && lines.includes('Entry type: PrivateKeyEntry') && /4096-bit RSA/.test(report) && hashes.length === 1, 'Unexpected key identity/type');
  return hashes[0][1].replaceAll(':', '').toLowerCase();
}
