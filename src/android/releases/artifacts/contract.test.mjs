import test from 'node:test';
import assert from 'node:assert/strict';
import { readVersion, version, identity, manifest, certificate, SDK, UNSIGNED, WORKFLOW } from './contract.mjs';
export const fixture = () => ({ schema: 1, repository: 'dirtyredz/agent-eye', applicationId: 'local.agenteye', ...SDK, versionName: '1.0.0', versionCode: 1, tag: 'v1.0.0', commit: 'a'.repeat(40), runId: 12, runAttempt: 1, workflow: WORKFLOW, artifacts: Object.fromEntries(UNSIGNED.map(n => [n, { size: 1, sha256: 'b'.repeat(64) }])) });
test('version is canonical, positive and exactly tag-bound', () => {
  assert.deepEqual(readVersion('versionName=1.0.0\nversionCode=1\n'), { versionName: '1.0.0', versionCode: 1 });
  for (const name of ['1.0', '01.0.0', '1.0.0-beta', '../x', '1.0.0\n']) assert.throws(() => version({ versionName: name, versionCode: 1 }));
  for (const code of [0, -1, 1.2, '1', 2100000001]) assert.throws(() => version({ versionName: '1.0.0', versionCode: code }));
  assert.throws(() => version(fixture(), 'v2.0.0'));
});
test('manifest rejects malformed schema, identity, provenance and assets', () => {
  manifest(fixture());
  for (const [key, value] of Object.entries({ schema: 2, repository: 'other/repo', applicationId: 'other.app', commit: 'HEAD', runId: 0, runAttempt: 0, workflow: 'evil.yml', minSdk: 1, artifacts: {} })) assert.throws(() => manifest({ ...fixture(), [key]: value }));
  const m = fixture(); m.artifacts['agent-eye.apk'] = m.artifacts[UNSIGNED[0]];
  assert.throws(() => manifest(m));
  assert.throws(() => certificate({ applicationId: 'local.agenteye', sha256: null }));
});
test('embedded package/version/sdk and debuggable status must match', () => {
  const expected = { versionName: '1.0.0', versionCode: 1 };
  const actual = { ...expected, ...SDK, applicationId: 'local.agenteye', debuggable: false };
  identity(actual, expected);
  for (const [key, value] of Object.entries({ applicationId: 'wrong', versionCode: 2, versionName: '2.0.0', minSdk: 23, targetSdk: 35, compileSdk: 35, debuggable: true })) assert.throws(() => identity({ ...actual, [key]: value }, expected));
});
