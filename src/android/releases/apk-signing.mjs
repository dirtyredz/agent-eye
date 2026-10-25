import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { identity, requireThat } from './contract.mjs';

export const PASS_ENV = 'AGENT_EYE_KEY_PASSWORD';
export function parseBadging(text) {
  const attr = name => text.match(new RegExp(`${name}='([^']*)'`))?.[1];
  return { applicationId: attr('name'), versionCode: Number(attr('versionCode')), versionName: attr('versionName'), minSdk: Number(text.match(/(?:^|\n)(?:minSdkVersion|sdkVersion):'(\d+)'/)?.[1]), targetSdk: Number(text.match(/targetSdkVersion:'(\d+)'/)?.[1]), compileSdk: Number(attr('compileSdkVersion') ?? attr('platformBuildVersionCode')), debuggable: /application-debuggable/.test(text) };
}
export function parseSigner(text) {
  const signers = [...text.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\s*$/gmi)];
  requireThat(signers.length === 1 && /Number of signers: 1\b/.test(text), 'Expected exactly one APK signer');
  requireThat(/Verified using v2 scheme .*: true/.test(text), 'APK v2 signature missing');
  return signers[0][1].toLowerCase();
}
export function apkTools(buildTools, run) {
  const exe = name => join(buildTools, name + (process.platform === 'win32' ? '.exe' : ''));
  // Invoke the JAR directly to avoid cmd.exe/.bat shell quoting and inherited password arguments.
  const signer = args => run('java', ['-jar', join(buildTools, 'lib/apksigner.jar'), ...args]);
  async function inspect(path, expected) {
    identity(parseBadging((await run(exe('aapt2'), ['dump', 'badging', path])).stdout), expected);
  }
  async function verify(path, pin, expected) {
    await run(exe('zipalign'), ['-c', '-P', '16', '4', path]);
    const report = (await signer(['verify', '--verbose', '--print-certs', path])).stdout;
    requireThat(parseSigner(report) === pin.sha256, 'APK signer does not match pin');
    await inspect(path, expected);
  }
  return {
    inspect, verify,
    async unsigned(path, expected) {
      await inspect(path, expected);
      const bytes = await readFile(path);
      requireThat(!bytes.includes(Buffer.from('APK Sig Block 42')), 'Signed APK input refused');
      const entries = (await run('jar', ['tf', path])).stdout;
      requireThat(!/^META-INF\/[^\r\n]+\.(SF|RSA|DSA|EC)$/im.test(entries), 'JAR-signed APK input refused');
      const result = await run('java', ['-jar', join(buildTools, 'lib/apksigner.jar'), 'verify', path], { allowed: [1] });
      requireThat(/DOES NOT VERIFY/.test(result.stderr + result.stdout), 'Unexpected unsigned APK verification result');
    },
    async sign(input, output, key, pin, expected) {
      const aligned = output + '.aligned';
      await run(exe('zipalign'), ['-P', '16', '-f', '4', input, aligned]);
      await run('java', ['-jar', join(buildTools, 'lib/apksigner.jar'), 'sign', '--ks', key.path, '--ks-key-alias', pin.alias, '--ks-pass', `env:${PASS_ENV}`, '--key-pass', `env:${PASS_ENV}`, '--out', output, aligned], { env: { [PASS_ENV]: key.password }, secrets: [key.password] });
      await verify(output, pin, expected);
    },
  };
}
