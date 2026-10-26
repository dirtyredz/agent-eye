// Every runnable script must work from any directory and leave a log, proven with cwd = os.tmpdir().
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';

const src = relative => fileURLToPath(new URL(`../${relative}`, import.meta.url));
const cli = src('android/releases/cli/cli.mjs');
const cases = [
  [cli, 'setup', '--dry-run'], [cli, 'setup-vault', '--dry-run'], [cli, 'provision', '--dry-run'],
  [cli, 'generate-key', '--dry-run'], [cli, 'verify-key', '--dry-run'],
  [cli, 'release-sign', '--tag', 'v1.0.0', '--release-id', '1', '--dry-run', '--publish'],
  [cli, 'validate-tag', '--dry-run'], [cli, 'stage', '--dry-run'], [cli, 'draft', '--dry-run'],
  [src('android/signing/signing-smoke.mjs'), '--dry-run'],
  [src('camera/snapshot.mjs'), 'clean', '--dry-run'],
  [src('camera/snapshot.mjs'), 'status', '--url', 'http://192.168.1.2:8765', '--dry-run'],
  [src('camera/snapshot.mjs'), 'pair', '--url', 'http://192.168.1.2:8765', '--code', '123456', '--dry-run'],
  [src('camera/snapshot.mjs'), 'unpair', '--dry-run'],
];
for (const args of cases) {
  test(`runs from os.tmpdir(): ${args.slice(1).join(' ')}`, () => {
    const out = execFileSync(process.execPath, args, { cwd: tmpdir(), encoding: 'utf8' });
    const last = out.trim().split(/\r?\n/).at(-1);
    const match = /^OK \(exit 0\) log: (.+)$/.exec(last);
    assert.ok(match, last);
    assert.ok(existsSync(match[1]));
    assert.match(readFileSync(match[1], 'utf8'), /end .* exit=0/);
  });
}
test('owner commands log under .tmp/owner-runs', () => {
  const out = execFileSync(process.execPath, [cli, 'verify-key', '--dry-run'], { cwd: tmpdir(), encoding: 'utf8' });
  assert.match(out, /owner-runs/);
});
