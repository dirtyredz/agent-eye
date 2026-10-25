import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { digest } from './artifacts.mjs';
import { identity, requireThat } from './contract.mjs';
import { PASS_ENV } from './apk-signing.mjs';

export const BUNDLETOOL = Object.freeze({ version: '1.18.1', sha256: 'a73341a7945abcb0e6b8971c7b1b2801bd765006447ca0d2437a4260d572ceac', url: 'https://dl.google.com/dl/android/maven2/com/android/tools/build/bundletool/1.18.1/bundletool-1.18.1.jar' });
export function parseBundleManifest(xml) {
  const attr = name => xml.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1];
  return { applicationId: attr('package'), versionName: attr('android:versionName'), versionCode: Number(attr('android:versionCode')), minSdk: Number(attr('android:minSdkVersion')), targetSdk: Number(attr('android:targetSdkVersion')), compileSdk: Number(attr('android:compileSdkVersion')), debuggable: attr('android:debuggable') !== undefined && attr('android:debuggable') !== 'false' };
}
export function jarReport(result) {
  // JDK -strict bits: 4 = self-signed/invalid trust chain; all other bits are fatal.
  requireThat([0, 4].includes(result.code) && /jar verified(?:, with signer errors)?\./.test(result.stdout), 'AAB signature verification failed');
  const text = result.stdout + result.stderr;
  requireThat(!/unsigned entries|signatures? (?:are|is) invalid|disabled|weak algorithm/i.test(text), 'Unsigned or invalid AAB entries');
  const warnings = text.split(/Warning:|Error:/).slice(1).join('\n');
  for (const line of warnings.split(/\r?\n/).map(s => s.trim()).filter(Boolean)) {
    requireThat(/^(This jar contains entries whose certificate chain is invalid\. Reason: PKIX path building failed:|This jar contains entries whose signer certificate is self-signed\.|This jar contains signatures that do not include a timestamp\.|Re-run with the -verbose|The signer certificate will expire on|The signer certificate's timestamp)/.test(line), `Unexplained jarsigner warning: ${line}`);
  }
}
export function bundleTools(toolDir, run) {
  async function bundle(args) {
    requireThat(digest(await readFile(join(toolDir, `bundletool-${BUNDLETOOL.version}.jar`))).sha256 === BUNDLETOOL.sha256, 'bundletool checksum mismatch');
    return run('java', ['-cp', join(toolDir, '*'), 'com.android.tools.build.bundletool.BundleToolMain', ...args]);
  }
  async function inspect(path, expected) {
    await bundle(['validate', `--bundle=${path}`]);
    identity(parseBundleManifest((await bundle(['dump', 'manifest', `--bundle=${path}`, '--module=base'])).stdout), expected);
  }
  async function verify(path, pin, expected) {
    jarReport(await run('jarsigner', ['-verify', '-strict', path], { allowed: [0, 4] }));
    const report = (await run('keytool', ['-printcert', '-jarfile', path, '-J-Duser.language=en'])).stdout;
    const certs = [...report.matchAll(/SHA256: ([A-F0-9:]+)/g)];
    requireThat(certs.length === 1 && certs[0][1].replaceAll(':', '').toLowerCase() === pin.sha256, 'AAB signer does not match pin');
    await inspect(path, expected);
  }
  return {
    inspect, verify,
    async unsigned(path, expected) {
      await inspect(path, expected);
      const entries = (await run('jar', ['tf', path])).stdout;
      requireThat(!/^META-INF\/[^\r\n]+\.(SF|RSA|DSA|EC)$/im.test(entries), 'Signed AAB input refused');
    },
    async sign(input, output, key, pin, expected) {
      await run('jarsigner', ['-keystore', key.path, '-storetype', 'PKCS12', '-storepass:env', PASS_ENV, '-keypass:env', PASS_ENV, '-sigalg', 'SHA256withRSA', '-digestalg', 'SHA-256', '-signedjar', output, input, pin.alias], { env: { [PASS_ENV]: key.password }, secrets: [key.password] });
      await verify(output, pin, expected);
    },
  };
}
