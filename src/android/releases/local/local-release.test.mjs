import test from 'node:test';
import assert from 'node:assert/strict';
import { checkout, localRelease } from './local-release.mjs';
import { releaseSign } from './release-sign.mjs';
import { mkdir, mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { SDK, WORKFLOW, UNSIGNED } from '../artifacts/contract.mjs';
import { digest } from '../artifacts/artifacts.mjs';
test('checkout preflight rejects changed commit, dirty files and wrong repository', async () => {
  for (const mode of ['dirty', 'origin', 'commit']) {
    await assert.rejects(checkout(async (_file, args) => {
      if (args[0] === 'status') return { stdout: mode === 'dirty' ? ' M source' : '' };
      if (args[0] === 'remote') return { stdout: mode === 'origin' ? 'https://github.com/other/repo.git' : 'https://github.com/dirtyredz/agent-eye.git' };
      if (args[0] === 'rev-parse') return { stdout: args[1] === 'HEAD' ? 'a'.repeat(40) : 'b'.repeat(40) };
      throw new Error('Unexpected command');
    }, '.', 'v1.0.0'), /Clean checkout|Unexpected origin|does not match tag/);
  }
});
async function transactionFixture() {
  await mkdir('.tmp', { recursive: true }); const root = await mkdtemp(resolve('.tmp/transaction-test-'));
  const commit = 'a'.repeat(40), tag = 'v1.0.0';
  const files = new Map(UNSIGNED.map(name => [name, Buffer.from(`unsigned:${name}`)]));
  const m = { schema: 1, repository: 'dirtyredz/agent-eye', applicationId: 'local.agenteye', ...SDK, versionName: '1.0.0', versionCode: 1, tag, commit, runId: 12, runAttempt: 1, workflow: WORKFLOW, artifacts: Object.fromEntries([...files].map(([name, bytes]) => [name, digest(bytes)])) };
  files.set('release-unsigned.json', Buffer.from(JSON.stringify(m)));
  const events = [];
  let failUpload = false;
  const gh = {
    release: async () => ({ id: 1, draft: true, prerelease: false, tag_name: tag }),
    assets: async () => [...files.keys()].map((name, i) => ({ name, id: i + 1 })),
    api: async route => route.startsWith('git/ref') ? { object: { type: 'commit', sha: commit } } : { status: 'identical' },
    download: async (_tag, name, dir) => { const bytes = files.get(name); await writeFile(join(dir, name), bytes); return bytes; },
    provenance: async (manifest, dir) => {
      if (manifest.runId !== 12) throw new Error('Changed run');
      for (const name of UNSIGNED) await writeFile(join(dir, name), `unsigned:${name}`);
    },
    history: async () => { events.push('history'); },
    upload: async (_id, _tag, path, name) => {
      events.push(`upload:${name}`);
      if (failUpload && name === 'release.json') throw new Error('Interrupted upload');
      const bytes = await readFile(path);
      if (files.has(name)) assert.deepEqual(files.get(name), bytes);
      files.set(name, bytes);
    },
    removeUnsigned: async () => { events.push('remove'); for (const name of ['agent-eye-unsigned.apk', 'agent-eye-unsigned.aab', 'release-unsigned.json']) files.delete(name); },
    publish: async () => { events.push('publish'); },
  };
  const run = async (_file, args) => ({ stdout: args[0] === 'status' ? '' : args[0] === 'remote' ? 'https://github.com/dirtyredz/agent-eye.git' : commit });
  const artifactTool = { unsigned: async () => { events.push('unsigned'); }, verify: async () => { events.push('verify'); }, sign: async (_input, output) => { await writeFile(output, 'signed fixture'); } };
  const deps = localRelease({ root, run, gh, apk: artifactTool, bundle: artifactTool, vault: () => assert.fail('real vault'), localVersion: { versionName: '1.0.0', versionCode: 1 }, pin: { applicationId: 'local.agenteye', alias: 'agent-eye', sha256: 'b'.repeat(64), firstVersionCode: 1 } });
  deps.withKey = async (_pin, callback) => { events.push('vault'); return callback({}); };
  return { root, files, m, events, deps, interrupt: () => { failUpload = true; }, resume: () => { failUpload = false; }, options: { tag, releaseId: 1, publish: true }, cleanup: () => rm(root, { recursive: true, force: true }) };
}
test('local transaction resumes interrupted uploads with identical cached files and no second vault access', async () => {
  const f = await transactionFixture();
  try {
    f.interrupt(); await assert.rejects(releaseSign(f.deps, f.options), /Interrupted upload/);
    assert.equal(f.events.includes('publish'), false); assert.equal(f.events.includes('remove'), false);
    f.resume(); await releaseSign(f.deps, f.options);
    assert.equal(f.events.filter(e => e === 'vault').length, 1);
    assert.equal(f.events.at(-1), 'publish');
    assert.deepEqual([...f.files.keys()].sort(), ['SHA256SUMS', 'agent-eye.aab', 'agent-eye.apk', 'mapping.txt', 'release.json'].sort());
  } finally { await f.cleanup(); }
});
for (const changed of ['commit', 'run', 'hash']) test(`local ${changed} tampering fails before vault access`, async () => {
  const f = await transactionFixture();
  try {
    if (changed === 'commit') f.m.commit = 'c'.repeat(40);
    if (changed === 'run') f.m.runId = 13;
    if (changed === 'hash') f.files.set('agent-eye-unsigned.apk', Buffer.from('tampered'));
    f.files.set('release-unsigned.json', Buffer.from(JSON.stringify(f.m)));
    await assert.rejects(releaseSign(f.deps, f.options), /mismatch|Changed run/);
    assert.equal(f.events.includes('vault'), false); assert.equal(f.events.includes('publish'), false);
  } finally { await f.cleanup(); }
});
test('unexpected assets added during signing prevent publication', async () => {
  const f = await transactionFixture();
  const verify = f.deps.verifyDownloads;
  f.deps.verifyDownloads = async (...args) => { await verify(...args); f.files.set('unexpected.txt', Buffer.from('unexpected')); };
  try {
    await assert.rejects(releaseSign(f.deps, f.options), /Unexpected or duplicate draft assets/);
    assert.equal(f.events.includes('publish'), false);
  } finally { await f.cleanup(); }
});
