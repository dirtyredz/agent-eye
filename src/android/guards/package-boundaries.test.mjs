import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';

const sourceRoot = new URL('../../../android/app/src/main/java/local/agenteye/', import.meta.url);

const BOUNDARIES = [
  { from: 'service', forbidden: 'local.agenteye.ui' },
];

/** True when the source mentions the forbidden package: imports and fully qualified usages alike. */
export function referencesPackage(source, forbidden) {
  const escaped = forbidden.replaceAll('.', '\\.');
  return new RegExp(`(?<![\\w.])${escaped}(?![\\w])`).test(source);
}

test('referencesPackage rejects an import of the forbidden package', () => {
  assert.ok(referencesPackage('import local.agenteye.ui.MainActivity\n', 'local.agenteye.ui'));
});

test('referencesPackage rejects a fully qualified reference', () => {
  assert.ok(referencesPackage('val c = local.agenteye.ui.MainActivity::class.java', 'local.agenteye.ui'));
});

test('referencesPackage accepts service code without ui references', () => {
  const source = 'import local.agenteye.service.Foo\nimport local.agenteye.uiextra.Bar\nval x = Foo()';
  assert.ok(!referencesPackage(source, 'local.agenteye.ui'));
});

for (const { from, forbidden } of BOUNDARIES) {
  test(`package ${from} never references ${forbidden}`, async () => {
    const files = await readdir(new URL(`${from}/`, sourceRoot), { recursive: true });
    const kotlin = files.filter(name => name.endsWith('.kt'));

    assert.ok(kotlin.length > 0, `no Kotlin files found under ${from}/`);
    for (const name of kotlin) {
      const relative = name.replaceAll('\\', '/');
      const source = await readFile(new URL(from + '/' + relative, sourceRoot), 'utf8');
      assert.ok(!referencesPackage(source, forbidden),
        `${from}/${name}: must not reference ${forbidden} (package dependency cycle)`);
    }
  });
}
