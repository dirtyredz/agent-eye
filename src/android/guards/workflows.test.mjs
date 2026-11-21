import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';

test('workflows never invoke sdkmanager through PATH', async () => {
  const directory = new URL('../../../.github/workflows/', import.meta.url);
  const workflows = (await readdir(directory)).filter(name => /\.ya?ml$/.test(name));
  assert.ok(workflows.length > 0);
  for (const name of workflows) {
    const source = await readFile(new URL(name, directory), 'utf8');
    assert.doesNotMatch(source, /(?<![\w/-])sdkmanager\b/,
      `${name}: use the explicit Android SDK cmdline-tools path, not bare sdkmanager`);
  }
});
