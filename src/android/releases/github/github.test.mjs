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
const listed = (releases, seen = []) => github(async (file, args) => {
  seen.push([file, ...args]);
  const route = args[1];
  if (route.includes('/releases/tags/')) throw new Error('gh: Not Found (HTTP 404)');
  return { stdout: JSON.stringify(route.includes('page=1') ? releases : []) };
});
test('releaseByTag finds a draft that releases/tags/<tag> 404s on, null when absent, fails closed on duplicates', async () => {
  const draft = { id: 423852989, tag_name: 'v1.0.0', draft: true };
  const other = { id: 5, tag_name: 'v1.0.01', draft: false };
  const seen = [];
  assert.deepEqual(await listed([other, draft], seen).releaseByTag('v1.0.0'), draft);
  assert.ok(seen.every(c => !c[2].includes('/releases/tags/')));
  assert.equal(await listed([other]).releaseByTag('v1.0.0'), null);
  await assert.rejects(listed([draft, { ...draft, id: 6 }]).releaseByTag('v1.0.0'), /Multiple releases match/);
});
test('draftId resolves the numeric ID of a draft by tag and refuses published, missing or invalid releases', async () => {
  assert.equal(await listed([{ id: 987, tag_name: 'v1.0.0', draft: true }]).draftId('v1.0.0'), '987');
  await assert.rejects(listed([{ id: 987, tag_name: 'v1.0.0', draft: false }]).draftId('v1.0.0'), /not a draft/);
  await assert.rejects(listed([{ id: 'x', tag_name: 'v1.0.0', draft: true }]).draftId('v1.0.0'), /Invalid draft release ID/);
  await assert.rejects(listed([]).draftId('v9.9.9'), /not found/);
});
const gh40 = 'b'.repeat(40);
function createRun(releases, calls, visibleAfter) {
  let lists = 0;
  return async (file, args) => {
    calls.push([file, ...args]);
    if (args[0] === 'release') return { stdout: '', code: 0 };
    if (args[1].includes('/releases?')) return { stdout: JSON.stringify(args[1].includes('page=1') && ++lists >= visibleAfter ? releases : []) };
    throw new Error('unexpected ' + args.join(' '));
  };
}
test('createDraft creates a verified-tag draft at the commit and retries the lookup with backoff', async () => {
  const draft = { id: 9, tag_name: 'v1.0.0', draft: true };
  const calls = [], delays = [];
  const rel = await github(createRun([draft], calls, 3)).createDraft('v1.0.0', { commit: gh40, title: 'T', notes: 'N', sleep: async d => { delays.push(d); } });
  assert.deepEqual(rel, draft);
  assert.deepEqual(delays, [1_000, 2_000]);
  const create = calls[0];
  assert.deepEqual(create.slice(1, 4), ['release', 'create', 'v1.0.0']);
  for (const flag of ['--draft', '--verify-tag']) assert.ok(create.includes(flag));
  assert.equal(create[create.indexOf('--target') + 1], gh40);
  assert.equal(create[create.indexOf('--title') + 1], 'T');
  assert.equal(create[create.indexOf('--notes') + 1], 'N');
});
test('createDraft returns undefined after the lookup retries are exhausted', async () => {
  const delays = [];
  assert.equal(await github(createRun([], [], 1)).createDraft('v1.0.0', { commit: gh40, title: 'T', notes: 'N', sleep: async d => { delays.push(d); } }), undefined);
  assert.deepEqual(delays, [1_000, 2_000, 4_000, 8_000]);
});
test('tagCommit peels lightweight and annotated tags; non-commit objects yield undefined', async () => {
  const refs = { light: { object: { type: 'commit', sha: gh40 } }, ann: { object: { type: 'tag', sha: 't1' } }, tree: { object: { type: 'tree', sha: 'x' } } };
  const gh = github(async (_f, args) => {
    const route = args[1].split('/').slice(3).join('/');
    if (route.startsWith('git/tags/')) return { stdout: JSON.stringify({ object: { type: 'commit', sha: gh40 } }) };
    return { stdout: JSON.stringify(refs[route.split('/').pop()]) };
  });
  assert.equal(await gh.tagCommit('light'), gh40);
  assert.equal(await gh.tagCommit('ann'), gh40);
  assert.equal(await gh.tagCommit('tree'), undefined);
});
test('isOnMain accepts ahead/identical and refuses diverged or behind', async () => {
  for (const [status, expected] of [['ahead', true], ['identical', true], ['diverged', false], ['behind', false]]) {
    assert.equal(await github(async () => ({ stdout: JSON.stringify({ status }) })).isOnMain(gh40), expected, status);
  }
});
