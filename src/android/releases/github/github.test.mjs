import { UNSIGNED_MANIFEST } from '../artifacts/contract.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { pages, assertDraft, github } from './github.mjs';
test('history pagination includes releases beyond 100', async () => {
  const calls = [];
  const results = await pages(async path => { calls.push(path); return path.endsWith('page=1') ? Array(100).fill({}) : [{ id: 101 }]; }, 'releases');
  assert.equal(results.length, 101); assert.equal(calls.length, 2);
});
test('published releases and mismatched IDs/tags are refused', () => {
  const draft = { id: 1, tag_name: 'v1.0.0', draft: true, prerelease: false };
  assertDraft(draft, 'v1.0.0', 1);
  for (const changed of [{ draft: false }, { id: 2 }, { tag_name: 'v2.0.0' }]) assert.throws(() => assertDraft({ ...draft, ...changed }, 'v1.0.0', 1));
});
test('only exact unsigned asset names are removed', async () => {
  const deleted = [];
  const gh = github(async (_file, args) => {
    const route = args[1];
    if (args.includes('DELETE')) { deleted.push(route); return { stdout: '' }; }
    if (route.includes('/assets?')) return { stdout: JSON.stringify(['agent-eye-unsigned.apk', 'agent-eye-unsigned.aab', UNSIGNED_MANIFEST, 'mapping.txt', 'agent-eye.apk', 'custom-unsigned.apk'].map((name, i) => ({ id: i + 1, name }))) };
    return { stdout: JSON.stringify({ id: 1, tag_name: 'v1.0.0', draft: true, prerelease: false }) };
  });
  await gh.removeUnsigned(1, 'v1.0.0'); assert.equal(deleted.length, 3); assert.ok(deleted[2].endsWith('/3'));
});
test('published release with missing metadata fails closed', async () => {
  const gh = github(async (_file, args) => ({ stdout: JSON.stringify(args[1].startsWith('repos/dirtyredz/agent-eye/releases?') ? [{ id: 1, tag_name: 'v1.0.0', draft: false }] : []) }));
  await assert.rejects(gh.history({ versionCode: 2 }), /lacks release.json/);
});
test('conflicting uploads are refused, never clobbered', async () => {
  await mkdir('.tmp', { recursive: true }); const dir = await mkdtemp('.tmp/github-test-'); const file = join(dir, 'agent-eye.apk');
  await writeFile(file, 'wanted');
  const gh = github(async (_file, args) => {
    if (args[0] === 'release') { await writeFile(join(args.at(-1), 'agent-eye.apk'), 'different'); return { stdout: '' }; }
    return { stdout: JSON.stringify(args[1].includes('/assets?') ? [{ name: 'agent-eye.apk', id: 2 }] : { id: 1, tag_name: 'v1.0.0', draft: true, prerelease: false }) };
  });
  try { await assert.rejects(gh.upload(1, 'v1.0.0', file, 'agent-eye.apk', dir), /Conflicting asset/); }
  finally { await rm(dir, { recursive: true, force: true }); }
});
test('changed commit, untrusted workflow and failed runs reject before artifact download', async () => {
  const m = { commit: 'a'.repeat(40), runId: 12, runAttempt: 1 };
  const valid = { conclusion: 'success', status: 'completed', run_attempt: 1, head_sha: m.commit, path: '.github/workflows/android-release.yml', repository: { full_name: 'dirtyredz/agent-eye' }, head_repository: { full_name: 'dirtyredz/agent-eye' }, event: 'push' };
  for (const changed of [{ head_sha: 'b'.repeat(40) }, { path: 'other.yml' }, { conclusion: 'failure' }, { event: 'pull_request' }, { head_repository: { full_name: 'attacker/repo' } }]) {
    const gh = github(async (_file, args) => { assert.equal(args[0], 'api'); return { stdout: JSON.stringify({ ...valid, ...changed }) }; });
    await assert.rejects(gh.provenance(m, 'unused'), /Untrusted/);
  }
});
test('draftId resolves the numeric ID of a draft by tag and refuses published or invalid releases', async () => {
  const seen = [];
  const fake = body => github(async (file, args) => { seen.push([file, ...args]); return { stdout: JSON.stringify(body) }; });
  assert.equal(await fake({ databaseId: 987, isDraft: true }).draftId('v1.0.0'), '987');
  assert.deepEqual(seen[0].slice(0, 4), ['gh', 'release', 'view', 'v1.0.0']);
  await assert.rejects(fake({ databaseId: 987, isDraft: false }).draftId('v1.0.0'), /not a draft/);
  await assert.rejects(fake({ databaseId: 'x', isDraft: true }).draftId('v1.0.0'), /Invalid draft release ID/);
  await assert.rejects(github(async () => { throw new Error('release not found'); }).draftId('v9.9.9'), /not found/);
});
