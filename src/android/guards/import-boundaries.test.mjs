import test from 'node:test';
import assert from 'node:assert/strict';
import { posix } from 'node:path';
import { readdir, readFile } from 'node:fs/promises';

const androidRoot = new URL('../', import.meta.url);

// Directories that must never import a path under another directory.
const FORBIDDEN = [{ from: 'signing', forbidden: 'releases' }];
// Directories restricted to Node built-ins and files inside themselves.
const SELF_CONTAINED = ['shared'];
// Directories whose modules must not re-export from shared/ (callers import the owner).
const NO_SHARED_REEXPORT = ['releases/artifacts'];

const specifiersOf = source => [...source.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g)].map(m => m[1]);

function importsInto(source, forbidden) {
  return specifiersOf(source).filter(s => new RegExp(`(^|/)${forbidden}/`).test(s));
}

/** Specifiers that are neither Node built-ins nor relative paths staying inside `dir` (given the file's dir-relative path). */
function outwardImports(source, dir, file) {
  return specifiersOf(source).filter(s => {
    if (s.startsWith('node:')) return false;
    if (!s.startsWith('.')) return true;
    return posix.normalize(posix.join(posix.dirname(`${dir}/${file}`), s)).split('/')[0] !== dir;
  });
}

/** Re-exports of shared/ names: direct export-from, or an export of a binding imported from shared/. */
function sharedReexports(source) {
  const direct = [...source.matchAll(/export\s*(?:\{[^}]*\}|\*)\s*from\s*['"]([^'"]*shared\/[^'"]*)['"]/g)].map(m => m[1]);
  const bound = new Set();
  for (const [, clause] of source.matchAll(/import\s+([^;'"]+?)\s+from\s*['"][^'"]*shared\/[^'"]*['"]/g)) {
    const named = /\{([^}]*)\}/.exec(clause)?.[1] ?? '';
    for (const part of named.split(',')) { const name = part.trim().split(/\s+as\s+/).pop(); if (name) bound.add(name); }
    const rest = clause.replace(/\{[^}]*\}/, '');
    for (const part of rest.split(',')) { const name = part.trim().replace(/^\*\s*as\s+/, ''); if (name) bound.add(name); }
  }
  const exported = [];
  for (const [, list] of source.matchAll(/export\s*\{([^}]*)\}(?!\s*from)/g)) {
    for (const part of list.split(',')) { const local = part.trim().split(/\s+as\s+/)[0]; if (bound.has(local)) exported.push(local); }
  }
  for (const [, name] of source.matchAll(/export\s+default\s+([A-Za-z_$][\w$]*)\s*;?/g)) if (bound.has(name)) exported.push(name);
  return [...direct, ...exported];
}

async function mjsFiles(dir) {
  const names = (await readdir(new URL(`${dir}/`, androidRoot), { recursive: true })).filter(name => name.endsWith('.mjs'));
  assert.ok(names.length > 0, `no .mjs files found under ${dir}/`);
  return Promise.all(names.map(async name => {
    const file = name.replaceAll('\\', '/');
    return { file, source: await readFile(new URL(`${dir}/${file}`, androidRoot), 'utf8') };
  }));
}

test('importsInto flags an import of the forbidden directory', () => {
  assert.deepEqual(importsInto("import { x } from '../../releases/artifacts/contract.mjs';", 'releases'), ['../../releases/artifacts/contract.mjs']);
  assert.deepEqual(importsInto("const m = await import('../releases/cli/cli.mjs');", 'releases'), ['../releases/cli/cli.mjs']);
  assert.deepEqual(importsInto("import { y } from '../../shared/assert.mjs';\nimport z from 'node:path';", 'releases'), []);
});

test('outwardImports rejects a hop through another directory and bare packages', () => {
  assert.deepEqual(outwardImports("import '../tooling/android-tools.mjs';", 'shared', 'x.mjs'), ['../tooling/android-tools.mjs']);
  assert.deepEqual(outwardImports("import 'left-pad';", 'shared', 'x.mjs'), ['left-pad']);
});

test('outwardImports accepts built-ins and files inside the directory', () => {
  assert.deepEqual(outwardImports("import { createHash } from 'node:crypto';\nimport { a } from './assert.mjs';", 'shared', 'x.mjs'), []);
});

test('sharedReexports flags every way of re-exporting a shared binding', () => {
  assert.deepEqual(sharedReexports("export { digest } from '../../shared/digest.mjs';"), ['../../shared/digest.mjs']);
  assert.deepEqual(sharedReexports("import { digest } from '../../shared/digest.mjs';\nexport { digest };"), ['digest']);
  assert.deepEqual(sharedReexports("import { digest as d } from '../../shared/digest.mjs';\nexport { d as digest };"), ['d']);
  assert.deepEqual(sharedReexports("import {\n  a,\n  b as c,\n} from '../../shared/assert.mjs';\nexport { c };"), ['c']);
  assert.deepEqual(sharedReexports("import * as ns from '../../shared/assert.mjs';\nexport default ns;"), ['ns']);
});

test('sharedReexports accepts a shared import used only locally', () => {
  assert.deepEqual(sharedReexports("import { digest } from '../../shared/digest.mjs';\nexport const x = digest(1);\nexport { other };"), []);
});

for (const { from, forbidden } of FORBIDDEN) {
  test(`${from}/ never imports ${forbidden}/`, async () => {
    for (const { file, source } of await mjsFiles(from)) {
      assert.deepEqual(importsInto(source, forbidden), [], `${from}/${file}: must not import ${forbidden}/ (directory dependency cycle)`);
    }
  });
}

for (const dir of SELF_CONTAINED) {
  test(`${dir}/ imports only Node built-ins and itself`, async () => {
    for (const { file, source } of await mjsFiles(dir)) {
      assert.deepEqual(outwardImports(source, dir, file), [], `${dir}/${file}: must stay a leaf`);
    }
  });
}

for (const dir of NO_SHARED_REEXPORT) {
  test(`${dir}/ does not re-export from shared/`, async () => {
    for (const { file, source } of await mjsFiles(dir)) {
      assert.deepEqual(sharedReexports(source), [], `${dir}/${file}: import from shared/ directly instead of re-exporting`);
    }
  });
}
