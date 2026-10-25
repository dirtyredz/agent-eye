import test from 'node:test';
import assert from 'node:assert/strict';
import { parseBundleManifest, jarReport, bundleTools } from './bundle-signing.mjs';
test('AAB XML identity and debuggable state are parsed', () => {
  const xml = '<manifest package="local.agenteye" android:versionName="1.0.0" android:versionCode="1" android:compileSdkVersion="36"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><application android:debuggable="false"/></manifest>';
  assert.equal(parseBundleManifest(xml).applicationId, 'local.agenteye'); assert.equal(parseBundleManifest(xml).debuggable, false);
  assert.equal(parseBundleManifest(xml.replace('debuggable="false"', 'debuggable="true"')).debuggable, true);
});
test('JAR verification accepts only expected trust warnings, rejects mixed signatures', () => {
  jarReport({ code: 4, stdout: 'jar verified.\nWarning:\nThis jar contains entries whose signer certificate is self-signed.', stderr: '' });
  for (const result of [{ code: 16, stdout: 'jar verified.' }, { code: 4, stdout: 'jar verified.\nWarning:\nThis jar contains unsigned entries.' }, { code: 4, stdout: 'jar verified.\nWarning:\nUnknown warning' }, { code: 0, stdout: 'jar is unsigned.' }]) assert.throws(() => jarReport({ stderr: '', ...result }));
});
test('JDK 21 strict self-signed report is accepted with only trust bit 4', () => {
  const stdout = 'jar verified, with signer errors.\nError:\nThis jar contains entries whose certificate chain is invalid. Reason: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target\nThis jar contains entries whose signer certificate is self-signed.\nWarning:\nThis jar contains signatures that do not include a timestamp. Without a timestamp, users may not be able to validate this jar after any of the signer certificates expire (as early as 2056-10-17).\nRe-run with the -verbose and -certs options for more details.';
  jarReport({ code: 4, stdout, stderr: '' });
  assert.throws(() => jarReport({ code: 12, stdout, stderr: '' }));
});
test('AAB uses jarsigner with password environment references, never apksigner', async () => {
  const calls = [];
  const tools = bundleTools('missing', async (file, args, opts) => { calls.push({ file, args, opts }); throw new Error('stop after signing command'); });
  await assert.rejects(tools.sign('in.aab', 'out.aab', { path: 'key', password: 'secret' }, { alias: 'agent-eye' }, {}), /stop/);
  assert.equal(calls[0].file, 'jarsigner'); assert.ok(calls[0].args.includes('-storepass:env')); assert.ok(!calls[0].args.includes('secret'));
  assert.equal(calls[0].opts.env.AGENT_EYE_KEY_PASSWORD, 'secret');
});
