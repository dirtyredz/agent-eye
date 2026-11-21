import { hex, requireThat } from '../../shared/assert.mjs';
import { APPLICATION_ID, SDK, version } from '../../shared/release-identity.mjs';

export const REPOSITORY = 'dirtyredz/agent-eye';
export const UNSIGNED = ['agent-eye-unsigned.apk', 'agent-eye-unsigned.aab', 'mapping.txt'];
export const FINAL = ['agent-eye.apk', 'agent-eye.aab', 'mapping.txt'];
export const UNSIGNED_MANIFEST = 'release-unsigned.json';
// Unsigned assets deleted after signing; mapping.txt is kept because the final set shares it.
export const UNSIGNED_REMOVABLE = [...UNSIGNED.filter(name => name !== 'mapping.txt'), UNSIGNED_MANIFEST];
export const finalNames = [...FINAL, 'release.json', 'SHA256SUMS'];
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
export const WORKFLOW = '.github/workflows/android-release.yml';
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
