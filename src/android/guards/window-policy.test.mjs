import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';

test('Android UI allows screenshots and recents thumbnails', async () => {
  const sourceRoot = new URL('../../../android/app/src/main/java/', import.meta.url);
  const files = await readdir(sourceRoot, { recursive: true });
  const kotlin = files.filter(name => name.endsWith('.kt'));

  assert.ok(kotlin.length > 0);
  for (const name of kotlin) {
    const source = await readFile(new URL(name.replaceAll('\\', '/'), sourceRoot), 'utf8');
    assert.doesNotMatch(source, /FLAG_SECURE|SecureFlagPolicy\.SecureOn/,
      `${name}: screenshots and recents thumbnails must remain allowed`);
  }
});
