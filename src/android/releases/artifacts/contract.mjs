export const REPOSITORY = 'dirtyredz/agent-eye';
export const APPLICATION_ID = 'local.agenteye';
export const SDK = Object.freeze({ minSdk: 24, targetSdk: 36, compileSdk: 36 });
export const UNSIGNED = ['agent-eye-unsigned.apk', 'agent-eye-unsigned.aab', 'mapping.txt'];
export const FINAL = ['agent-eye.apk', 'agent-eye.aab', 'mapping.txt'];
export const UNSIGNED_MANIFEST = 'release-unsigned.json';
export const finalNames = [...FINAL, 'release.json', 'SHA256SUMS'];
export const PASS_ENV = 'AGENT_EYE_KEY_PASSWORD';
// CI and signing retries may have partial sets; only the cleaned final set is exact.
export function assertAssetSet(assets, phase) {
  const allowed = phase === 'ci' ? [...UNSIGNED, UNSIGNED_MANIFEST]
    : phase === 'draft' ? [...UNSIGNED, UNSIGNED_MANIFEST, ...finalNames]
      : phase === 'final' ? finalNames : null;
  requireThat(allowed, 'Unknown asset phase');
  requireThat(assets.every(a => allowed.includes(a.name)), phase === 'ci'
    ? 'Signing already started; CI cannot modify this draft' : 'Unexpected or duplicate draft assets');
  requireThat(new Set(assets.map(a => a.name)).size === assets.length, 'Unexpected or duplicate draft assets');
  if (phase === 'final') requireThat(assets.length === finalNames.length, 'Incomplete final asset set');
}
export function assertJarUnsigned(entries, message = 'Signed JAR input refused') {
  requireThat(!/^META-INF\/[^\r\n]+\.(SF|RSA|DSA|EC)$/im.test(entries), message);
}
export const WORKFLOW = '.github/workflows/android-release.yml';
export const hex = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
export function requireThat(condition, message) {
  if (!condition) throw new Error(message);
}
export function version(value, tag = `v${value?.versionName}`) {
  requireThat(value && /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(value.versionName), 'Invalid versionName');
  requireThat(Number.isInteger(value.versionCode) && value.versionCode > 0 && value.versionCode <= 2100000000, 'Invalid versionCode');
  requireThat(tag === `v${value.versionName}`, 'Tag/version mismatch');
  return value;
}
export function readVersion(text) {
  const lines = text.trim().split(/\r?\n/);
  requireThat(lines.length === 2 && lines[0].startsWith('versionName=') && /^versionCode=[1-9]\d*$/.test(lines[1]), 'Invalid version.properties');
  return version({ versionName: lines[0].slice(12), versionCode: Number(lines[1].slice(12)) });
}
export function identity(actual, expected) {
  for (const [key, val] of Object.entries({ applicationId: APPLICATION_ID, ...SDK, ...version(expected) })) {
    requireThat(actual[key] === val, `Artifact identity mismatch: ${key}`);
  }
  requireThat(actual.debuggable === false, 'Debuggable artifact refused');
}
export function certificate(pin) {
  requireThat(pin.applicationId === APPLICATION_ID && hex(pin.sha256) && pin.alias === 'agent-eye' && Number.isInteger(pin.firstVersionCode) && pin.firstVersionCode > 0, 'Public certificate pin is not provisioned or invalid');
  return pin;
}
export function manifest(m, signed = false) {
  requireThat(m?.schema === 1 && m.repository === REPOSITORY && m.applicationId === APPLICATION_ID, 'Invalid manifest identity/schema');
  version(m, m.tag);
  requireThat(/^[a-f0-9]{40}$/.test(m.commit), 'Invalid commit');
  requireThat(Number.isSafeInteger(m.runId) && m.runId > 0 && Number.isSafeInteger(m.runAttempt) && m.runAttempt > 0 && m.workflow === WORKFLOW, 'Invalid workflow identity');
  for (const [key, val] of Object.entries(SDK)) requireThat(m[key] === val, `Invalid ${key}`);
  const names = signed ? FINAL : UNSIGNED;
  requireThat(m.artifacts && Object.keys(m.artifacts).sort().join() === [...names].sort().join(), 'Unexpected artifact names');
  for (const name of names) {
    const a = m.artifacts[name];
    requireThat(Number.isSafeInteger(a?.size) && a.size > 0 && hex(a.sha256), `Invalid artifact: ${name}`);
  }
  if (signed) {
    requireThat(hex(m.signerSha256), 'Invalid signer');
    requireThat(m.unsignedHashes && Object.keys(m.unsignedHashes).sort().join() === [...UNSIGNED].sort().join(), 'Invalid unsigned hashes');
    for (const name of UNSIGNED) requireThat(hex(m.unsignedHashes[name]), 'Invalid unsigned hash');
  } else requireThat(!('signerSha256' in m), 'Signed input manifest refused');
  return m;
}
