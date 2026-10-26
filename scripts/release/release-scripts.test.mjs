// Runs the plain-PowerShell test for the owner scripts under every PowerShell found (pwsh on CI, powershell + pwsh locally).
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ps1 = fileURLToPath(new URL('./release-scripts.test.ps1', import.meta.url));
const shells = ['pwsh', 'powershell'].filter(name => spawnSync(name, ['-NoProfile', '-Command', 'exit 0'], { stdio: 'ignore' }).status === 0);

test('a PowerShell is available (CI must have pwsh)', t => {
  if (!shells.length && !process.env.CI) return t.skip('no PowerShell on this machine');
  assert.ok(shells.length, 'pwsh missing on CI');
});
for (const shell of shells) {
  test(`owner scripts dry-run under ${shell}`, () => {
    const run = spawnSync(shell, ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1], { encoding: 'utf8' });
    assert.equal(run.status, 0, `${run.stdout}\n${run.stderr}`);
  });
}
