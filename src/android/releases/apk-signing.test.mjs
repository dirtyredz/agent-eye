import test from 'node:test';
import assert from 'node:assert/strict';
import { parseBadging, parseSigner, apkTools } from './apk-signing.mjs';
const expected = { versionName: '1.0.0', versionCode: 1 };
const badging = "package: name='local.agenteye' versionCode='1' versionName='1.0.0' compileSdkVersion='36'\nsdkVersion:'24'\ntargetSdkVersion:'36'";
test('APK badging and exactly one supported signer are required', () => {
  assert.equal(parseBadging(badging).debuggable, false);
  assert.equal(parseBadging(badging + '\napplication-debuggable').debuggable, true);
  const report = `Number of signers: 1\nVerified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: ${'a'.repeat(64)}\n`;
  assert.equal(parseSigner(report), 'a'.repeat(64));
  assert.throws(() => parseSigner(report.replace('true', 'false')));
  assert.throws(() => parseSigner(report + `Signer #2 certificate SHA-256 digest: ${'b'.repeat(64)}\n`));
});
test('APK aligns before signing and supplies password only in child env', async () => {
  const calls = [];
  const api = apkTools('tools', async (file, args, options) => {
    calls.push({ file, args, options });
    return { stdout: args.includes('badging') ? badging : `Number of signers: 1\nVerified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: ${'a'.repeat(64)}\n` };
  });
  await api.sign('in.apk', 'out.apk', { path: 'key.p12', password: 'secret' }, { alias: 'agent-eye', sha256: 'a'.repeat(64) }, expected);
  assert.match(calls[0].file, /zipalign/); assert.ok(calls[1].args.includes('sign'));
  assert.ok(calls.every(c => !c.args.includes('secret')));
  assert.equal(calls[1].options.env.AGENT_EYE_KEY_PASSWORD, 'secret');
  assert.equal(calls.some(c => c.file === 'jarsigner'), false);
});
