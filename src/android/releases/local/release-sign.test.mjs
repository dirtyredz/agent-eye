import test from 'node:test';
import assert from 'node:assert/strict';
import { releaseSign } from './release-sign.mjs';
function scenario(fail, resume = false) {
  const events = [];
  const state = { manifest: { versionCode: 2 }, pin: { applicationId: 'local.agenteye', alias: 'agent-eye', firstVersionCode: 1, sha256: 'a'.repeat(64) } };
  const action = name => async () => { events.push(name); if (fail === name) throw new Error(name); };
  const deps = Object.fromEntries(['history', 'verify', 'recheck', 'upload', 'verifyDownloads', 'removeUnsigned', 'publish'].map(name => [name, action(name)]));
  deps.lock = async () => { events.push('lock'); return action('unlock'); };
  deps.preflight = async () => { await action('preflight')(); return state; };
  deps.resume = async () => { await action('resume')(); return resume ? 'cached' : null; };
  deps.withKey = async (_pin, callback) => { await action('vault')(); return callback('key'); };
  deps.sign = async () => { await action('sign')(); return 'signed'; };
  return { deps, events };
}
test('validation precedes vault; publication is last mutation, cleanup always follows', async () => {
  const s = scenario(); await releaseSign(s.deps, { publish: true });
  assert.ok(s.events.indexOf('history') < s.events.indexOf('vault'));
  assert.deepEqual(s.events.slice(-2), ['publish', 'unlock']);
  assert.ok(s.events.indexOf('verifyDownloads') < s.events.indexOf('removeUnsigned'));
});
for (const stage of ['preflight', 'history', 'vault', 'sign', 'verify', 'recheck', 'upload', 'verifyDownloads', 'removeUnsigned']) test(`failure at ${stage} never publishes and unlocks`, async () => {
  const s = scenario(stage); await assert.rejects(releaseSign(s.deps, { publish: true }), new RegExp(stage));
  assert.equal(s.events.includes('publish'), false); assert.equal(s.events.at(-1), 'unlock');
  if (['preflight', 'history'].includes(stage)) assert.equal(s.events.includes('vault'), false);
});
test('retry reuses verified outputs without vault and publication is explicit', async () => {
  const s = scenario(undefined, true); await releaseSign(s.deps, { publish: false });
  assert.equal(s.events.includes('vault'), false); assert.equal(s.events.includes('sign'), false); assert.equal(s.events.includes('publish'), false); assert.ok(s.events.includes('verifyDownloads'));
});
