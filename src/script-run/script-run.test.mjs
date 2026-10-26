import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { findRoot, runScript } from './script-run.mjs';
import { run as bareRun } from '../android/tooling/process.mjs';

async function fakeRepo() {
  const root = await mkdtemp(join(tmpdir(), 'script-run-'));
  await mkdir(join(root, 'src/deep'), { recursive: true });
  await writeFile(join(root, 'package.json'), '{}');
  return root;
}
const from = root => pathToFileURL(join(root, 'src/x.mjs')).href;
const logPath = lines => lines.at(-1).split('log: ')[1];

test('root is found by walking up from the caller, not from cwd', async () => {
  const root = await fakeRepo();
  assert.equal(findRoot(pathToFileURL(join(root, 'src/deep/x.mjs')).href), root);
  await rm(root, { recursive: true, force: true });
});

test('success writes a log under .tmp/logs and ends with an OK line', async () => {
  const root = await fakeRepo(), lines = [];
  const code = await runScript({ name: 'demo', from: from(root), argv: ['--a'], echo: l => lines.push(l) }, async log => {
    log.step('hello');
    const result = await log.wrapRun(bareRun)(process.execPath, ['-e', 'console.log("hi")']);
    assert.equal(result.stdout.trim(), 'hi');
  });
  assert.equal(code, 0);
  assert.match(lines.at(-1), /^OK \(exit 0\) log: .*[\\/]\.tmp[\\/]logs[\\/]demo-.*\.log$/);
  assert.match(lines[0], /^log: /);
  const text = await readFile(logPath(lines), 'utf8');
  for (const needle of ['start demo', 'argv: ["--a"]', 'step: hello', 'exit: 0', 'end demo exit=0']) assert.ok(text.includes(needle), needle);
  await rm(root, { recursive: true, force: true });
});

test('failure and owner runs: FAILED line, exit code 1, owner-runs folder', async () => {
  const root = await fakeRepo(), lines = [];
  const previous = process.exitCode;
  const code = await runScript({ name: 'owner', kind: 'owner', from: from(root), echo: l => lines.push(l) }, async log => {
    await log.wrapRun(bareRun)(process.execPath, ['-e', 'process.exit(3)']);
  });
  assert.equal(code, 1);
  assert.match(lines.at(-1), /^FAILED \(exit 1\) log: .*owner-runs[\\/]owner-.*\.log$/);
  assert.match(await readFile(logPath(lines), 'utf8'), /Subprocess failed \(3\)/);
  process.exitCode = previous;
  await rm(root, { recursive: true, force: true });
});

test('secret env values, added secrets and token shapes never reach the log', async () => {
  const root = await fakeRepo(), lines = [];
  const previous = process.exitCode;
  const env = { ...process.env, MY_SECRET_VALUE: 'sup3r-s3cret-value', OTHER: 'named-secret-xyz' };
  await runScript({ name: 'redact', secrets: ['OTHER'], env, from: from(root), argv: ['--x', 'sup3r-s3cret-value'], echo: l => lines.push(l) }, async log => {
    log.addSecrets('runtime-secret-1');
    log.step('using sup3r-s3cret-value and runtime-secret-1 and named-secret-xyz and ghp_abcdefghijklmnopqrstuvwxyz0123456789');
    await log.wrapRun(bareRun)(process.execPath, ['-e', 'console.log(process.env.CHILD_PW)'], { env: { CHILD_PW: 'child-only-pw-123' }, secrets: ['child-only-pw-123'] });
    throw new Error('boom child-only-pw-123 Bearer abc.def-ghi');
  });
  const text = await readFile(logPath(lines), 'utf8');
  for (const secret of ['sup3r-s3cret-value', 'runtime-secret-1', 'named-secret-xyz', 'child-only-pw-123', 'ghp_abcdefghij', 'abc.def-ghi']) assert.ok(!text.includes(secret), secret);
  assert.ok(text.includes('[REDACTED]'));
  process.exitCode = previous;
  await rm(root, { recursive: true, force: true });
});

test('the log stays clean even when the base runner does not redact', async () => {
  const root = await fakeRepo(), lines = [];
  const previous = process.exitCode;
  const leaky = async () => { throw new Error('leak runtime-secret-9 and ghp_abcdefghijklmnopqrstuvwxyz0123456789\nsecond line'); };
  await runScript({ name: 'leaky', from: from(root), echo: l => lines.push(l) }, async log => {
    log.addSecrets('runtime-secret-9');
    await assert.rejects(log.wrapRun(leaky)('tool', ['runtime-secret-9']), /runtime-secret-9/);
    throw new Error('final runtime-secret-9');
  });
  const text = await readFile(logPath(lines), 'utf8');
  assert.ok(!text.includes('runtime-secret-9') && !text.includes('ghp_abcdefghij'));
  assert.ok(text.includes('failed: tool'));
  process.exitCode = previous;
  await rm(root, { recursive: true, force: true });
});

test('options: custom redaction, logDir, exitCode and dry-run argv', async () => {
  const root = await fakeRepo(), lines = [];
  const previous = process.exitCode;
  const code = await runScript({ name: 'opts', from: from(root), logDir: 'out/logs', redaction: { shapes: /corp-\d+/ }, argv: ['a', '--dry-run', 'b'], echo: l => lines.push(l) }, async log => {
    assert.deepEqual(log.args, ['a', 'b']); assert.equal(log.dryRun, true);
    log.step('id corp-12345');
    throw Object.assign(new Error('chosen'), { exitCode: 7 });
  });
  assert.equal(code, 7);
  assert.match(lines.at(-1), /^FAILED \(exit 7\) log: .*out[\\/]logs[\\/]opts-/);
  assert.ok(!(await readFile(logPath(lines), 'utf8')).includes('corp-12345'));
  process.exitCode = previous;
  await rm(root, { recursive: true, force: true });
});
