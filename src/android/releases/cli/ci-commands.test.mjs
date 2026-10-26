import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, writeFile, readFile, readdir, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { draftRelease, stageRelease, validateTag } from './ci-commands.mjs';
import { SDK, WORKFLOW, REPOSITORY, UNSIGNED, UNSIGNED_MANIFEST } from '../artifacts/contract.mjs';
import { github } from '../github/github.mjs';
import { hashes } from '../artifacts/artifacts.mjs';

async function fixture(t) {
  await mkdir('.tmp', { recursive: true });
  const root = await mkdtemp(resolve('.tmp/ci-command-test-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const dir = join(root, 'stage');
  await mkdir(dir);
  for (const name of UNSIGNED) await writeFile(join(dir, name), name);
  const m = { schema: 1, repository: REPOSITORY, applicationId: 'local.agenteye', ...SDK,
    versionName: '1.0.0', versionCode: 1, tag: 'v1.0.0', commit: 'a'.repeat(40), runId: 12,
    runAttempt: 1, workflow: WORKFLOW, artifacts: await hashes(dir, UNSIGNED) };
  await writeFile(join(dir, UNSIGNED_MANIFEST), JSON.stringify(m));
  const events = [];
  const state = { exists: false };
  const release = { id: 7, tag_name: m.tag, target_commitish: m.commit, draft: true, prerelease: false };
  const deps = { root, dir, localVersion: m, tag: m.tag, commit: m.commit, runId: m.runId, runAttempt: 2,
    run: async (_file, args) => {
      events.push('create'); state.exists = true;
      assert.ok(args.includes('--draft')); assert.ok(args.includes('--verify-tag'));
      assert.equal(args[args.indexOf('--target') + 1], m.commit);
      return { code: 0 };
    },
    gh: {
      releaseByTag: async () => { events.push(state.exists ? 'release' : 'lookup'); return state.exists ? release : null; },
      assets: async () => { events.push('assets'); return []; },
      upload: async (id, tag, path, name, scratch) => {
        assert.equal(id, release.id); assert.equal(tag, m.tag);
        assert.equal(path, join(dir, name)); await readdir(scratch);
        events.push(name);
      },
    },
  };
  return { deps, events, release, m, state };
}

test('draftRelease creates only after 404 and uploads the unsigned set, then cleans scratch', async t => {
  const { deps, events } = await fixture(t);
  await draftRelease(deps);
  assert.deepEqual(events, ['lookup', 'create', 'release', 'assets', ...UNSIGNED, UNSIGNED_MANIFEST]);
  assert.deepEqual(await readdir(join(deps.root, '.tmp')), []);
});

test('draftRelease resumes an existing draft without creating it', async t => {
  const { deps, events, state } = await fixture(t);
  state.exists = true;
  deps.gh.assets = async () => [{ name: UNSIGNED[0] }];
  await draftRelease(deps);
  assert.equal(events.includes('create'), false);
  assert.deepEqual(events, ['release', ...UNSIGNED, UNSIGNED_MANIFEST]);
});

test('draftRelease propagates a failed lookup before creating or uploading', async t => {
  const { deps, events } = await fixture(t);
  deps.gh.releaseByTag = async () => { throw new Error('HTTP 403'); };
  await assert.rejects(draftRelease(deps), /HTTP 403/);
  assert.deepEqual(events, []);
});

// Real GitHub: GET releases/tags/<tag> 404s for drafts; only the list endpoint shows them.
function realisticRun(releases, calls) {
  return async (file, args) => {
    calls.push([file, ...args]);
    if (args[0] === 'api') {
      const route = args[1];
      if (route.includes('/releases/tags/')) throw new Error('gh: Not Found (HTTP 404)');
      if (route.includes('/assets?')) return { stdout: '[]' };
      if (route.includes('/releases?')) return { stdout: JSON.stringify(route.includes('page=1') ? releases : []) };
      return { stdout: JSON.stringify(releases[0]) };
    }
    if (args[0] === 'release' && args[1] === 'create') releases.push({ id: 9, tag_name: 'v1.0.0', target_commitish: 'a'.repeat(40), draft: true, prerelease: false });
    return { stdout: '', code: 0 };
  };
}

test('draftRelease reuses an existing empty draft that releases/tags/<tag> cannot see (no duplicate create)', async t => {
  const { deps, m } = await fixture(t);
  const draft = { id: 423852989, tag_name: m.tag, target_commitish: m.commit, draft: true, prerelease: false };
  const calls = [];
  const run = realisticRun([draft], calls);
  deps.run = run; deps.gh = { ...github(run), upload: async () => {} };
  await draftRelease(deps);
  assert.equal(calls.some(c => c[1] === 'release' && c[2] === 'create'), false);
});

test('draftRelease creates a missing draft then finds it via the list endpoint', async t => {
  const { deps } = await fixture(t);
  const calls = [];
  const run = realisticRun([], calls);
  deps.run = run; deps.gh = { ...github(run), upload: async () => {} };
  await draftRelease(deps);
  assert.equal(calls.filter(c => c[1] === 'release' && c[2] === 'create').length, 1);
});

for (const field of ['tag', 'commit', 'runId', 'runAttempt']) test(`draftRelease rejects staged ${field} mismatch before GitHub`, async t => {
  const { deps, events } = await fixture(t);
  deps[field] = field === 'runId' ? 99 : field === 'runAttempt' ? 0 : 'different';
  await assert.rejects(draftRelease(deps), /Draft staging identity mismatch/);
  assert.deepEqual(events, []);
});

test('draftRelease verifies staged bytes before GitHub', async t => {
  const { deps, events } = await fixture(t);
  await writeFile(join(deps.dir, UNSIGNED[0]), 'tampered');
  await assert.rejects(draftRelease(deps), /Hash\/length mismatch/);
  assert.deepEqual(events, []);
});

for (const mode of ['published', 'commit', 'signed', 'duplicate']) test(`draftRelease rejects ${mode} draft before uploads`, async t => {
  const { deps, events, release, state } = await fixture(t);
  state.exists = true;
  if (mode === 'published') release.draft = false;
  if (mode === 'commit') release.target_commitish = 'b'.repeat(40);
  if (mode === 'signed') deps.gh.assets = async () => [{ name: 'agent-eye.apk' }];
  if (mode === 'duplicate') deps.gh.assets = async () => [{ name: 'mapping.txt' }, { name: 'mapping.txt' }];
  await assert.rejects(draftRelease(deps), /expected draft|commit differs|Signing already started|duplicate/);
  assert.equal(events.some(event => UNSIGNED.includes(event)), false);
});

test('draftRelease cleans scratch and propagates upload conflicts', async t => {
  const { deps } = await fixture(t);
  deps.gh.upload = async () => { throw new Error('Conflicting asset'); };
  await assert.rejects(draftRelease(deps), /Conflicting asset/);
  assert.deepEqual(await readdir(join(deps.root, '.tmp')), []);
});

test('stageRelease inspects both copied artifacts and writes commit/run-bound metadata', async t => {
  const { deps, m } = await fixture(t);
  const sources = ['apk/release/app-release-unsigned.apk', 'bundle/release/app-release.aab', 'mapping/release/mapping.txt'];
  for (const source of sources) {
    const path = join(deps.root, 'android/app/build/outputs', source);
    await mkdir(join(path, '..'), { recursive: true }); await writeFile(path, source);
  }
  const inspected = [];
  const tool = { unsigned: async (path, expected) => { assert.equal(expected, m); inspected.push(path); } };
  const result = await stageRelease({ ...deps, apk: tool, bundle: tool });
  assert.deepEqual(inspected, UNSIGNED.slice(0, 2).map(name => join(deps.dir, name)));
  assert.equal(result.commit, m.commit); assert.equal(result.runAttempt, 2);
  assert.deepEqual(JSON.parse(await readFile(join(deps.dir, UNSIGNED_MANIFEST))), result);
});

test('validateTag rejects a mismatched tag before ancestry inspection', async () => {
  const calls = [];
  await assert.rejects(validateTag({ root: '.', tag: 'v1.0.0', run: async (_file, args) => {
    calls.push(args); return { stdout: args[1] === 'HEAD' ? 'a'.repeat(40) : 'b'.repeat(40) };
  } }), /Tag commit mismatch/);
  assert.equal(calls.length, 2);
});
