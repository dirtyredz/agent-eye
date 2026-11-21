import test from 'node:test';
import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';

const androidRoot = new URL('../', import.meta.url);

// Directory dependency rules for the Node tooling: `from` must never import a path under `forbidden`.
const BOUNDARIES = [
  { from: 'signing', forbidden: 'releases' },
  { from: 'shared', forbidden: 'releases' },
  { from: 'shared', forbidden: 'signing' },
];

/** Relative import specifiers in the source that resolve into the forbidden top-level directory. */
export function importsInto(source, forbidden) {
  const specifiers = [...source.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g)].map(m => m[1]);
  return specifiers.filter(s => new RegExp(`(^|/)${forbidden}/`).test(s));
}

test('importsInto flags an import of the forbidden directory', () => {
  assert.deepEqual(importsInto("import { x } from '../../releases/artifacts/contract.mjs';", 'releases'), ['../../releases/artifacts/contract.mjs']);
  assert.deepEqual(importsInto("const m = await import('../releases/cli/cli.mjs');", 'releases'), ['../releases/cli/cli.mjs']);
});

test('importsInto accepts unrelated imports', () => {
  assert.deepEqual(importsInto("import { y } from '../../shared/assert.mjs';\nimport z from 'node:path';", 'releases'), []);
});

for (const { from, forbidden } of BOUNDARIES) {
  test(`${from}/ never imports ${forbidden}/`, async () => {
    const files = (await readdir(new URL(`${from}/`, androidRoot), { recursive: true })).filter(name => name.endsWith('.mjs'));
    assert.ok(files.length > 0, `no .mjs files found under ${from}/`);
    for (const name of files) {
      const source = await readFile(new URL(`${from}/${name.replaceAll('\\', '/')}`, androidRoot), 'utf8');
      assert.deepEqual(importsInto(source, forbidden), [], `${from}/${name}: must not import ${forbidden}/ (directory dependency cycle)`);
    }
  });
}
