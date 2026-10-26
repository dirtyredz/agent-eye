import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createTokenStore } from './token-store.mjs';
import { parse, redaction, resolveTarget, USAGE } from './snapshot-args.mjs';
import { createScriptLog } from '../script-run/script-run.mjs';

const token = 'ab'.repeat(32);
const base = 'http://192.168.68.20:8765';

const tempRoot = async t => { const dir = await mkdtemp(path.join(tmpdir(), 'agent-eye-')); t.after(() => rm(dir, { recursive: true, force: true })); return dir; };

test('token resolution: --token, then env, then stored; url falls back to the paired phone', async t => {
  const root = await tempRoot(t);
  const tokens = createTokenStore(root, { restrict: async () => true });
  await tokens.save({ token, url: base });
  const other = 'cd'.repeat(32);
  assert.deepEqual(await resolveTarget({}, { env: {}, tokens }), { url: base, token });
  assert.deepEqual(await resolveTarget({ url: 'http://10.0.0.5:8765' }, { env: {}, tokens }), { url: 'http://10.0.0.5:8765', token });
  assert.deepEqual(await resolveTarget({}, { env: { AGENT_EYE_TOKEN: other }, tokens }), { url: undefined, token: other });
  assert.deepEqual(await resolveTarget({ token: 'flag' }, { env: { AGENT_EYE_TOKEN: other }, tokens }), { url: undefined, token: 'flag' });
  await tokens.clear();
  assert.deepEqual(await resolveTarget({}, { env: {}, tokens }), { url: undefined, token: undefined });
});

test('argument parsing is strict', () => {
  assert.deepEqual(parse(['pair', '--url', base, '--code', '123456']), { command: 'pair', url: base, code: '123456' });
  assert.deepEqual(parse(['clean']), { command: 'clean' });
  for (const bad of [['shot', 'extra'], ['shot', '--bogus', 'x'], ['shot', '--url']]) assert.throws(() => parse(bad), new RegExp(USAGE.slice(0, 20)));
});

test('run log redacts the pairing code, the token and the --token argument', async t => {
  const root = await tempRoot(t);
  for (const argv of [['pair', '--url', base, '--code', '482913'], ['pair', '--url', base, '--code=482913'], ['status', '--token', token]]) {
    const log = createScriptLog({ name: 'snapshot', from: new URL('snapshot.mjs', import.meta.url), root, argv, redaction, echo() {} });
    log.write(`argv: ${JSON.stringify(log.argv)}`);
    log.write(`got ${token} and Bearer ${token}`);
    const text = await readFile(log.path, 'utf8');
    assert.ok(!text.includes('482913'), text);
    assert.ok(!text.includes(token), text);
    assert.match(text, /\[REDACTED\]/);
  }
});
