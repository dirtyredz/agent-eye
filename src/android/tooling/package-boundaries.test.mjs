import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';

const sourceRoot = new URL('../../../android/app/src/main/java/local/agenteye/', import.meta.url);

const BOUNDARIES = [
  { from: 'service', forbidden: 'local.agenteye.ui.' },
];

for (const { from, forbidden } of BOUNDARIES) {
  test(`package ${from} never imports ${forbidden}`, async () => {
    const files = await readdir(new URL(`${from}/`, sourceRoot), { recursive: true });
    const kotlin = files.filter(name => name.endsWith('.kt'));

    assert.ok(kotlin.length > 0, `no Kotlin files found under ${from}/`);
    for (const name of kotlin) {
      const relative = name.replaceAll('\\', '/');
      const source = await readFile(new URL(from + '/' + relative, sourceRoot), 'utf8');
      assert.ok(!source.includes(`import ${forbidden}`),
        `${from}/${name}: must not import ${forbidden}* (package dependency cycle)`);
    }
  });
}
