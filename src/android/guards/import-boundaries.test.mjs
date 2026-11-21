import test from 'node:test';
import assert from 'node:assert/strict';
import { posix } from 'node:path';
import { readdir, readFile } from 'node:fs/promises';

const androidRoot = new URL('../', import.meta.url);

// Allowlists: a directory's modules may import Node built-ins, files inside the directory, the
// listed sibling directories and the listed packages; anything else is rejected.
const ALLOWED = [
  { dir: 'shared', siblings: [], packages: [] },
  { dir: 'signing', siblings: ['shared', 'tooling'], packages: ['@1password/sdk'] },
];
// Directories whose modules must not re-export from shared/ (callers import the owner).
const NO_SHARED_REEXPORT = ['releases/artifacts'];

const specifiersOf = source => [...source.matchAll(/(?:\bfrom\s*|\bimport\s*\(?\s*)['"]([^'"]+)['"]/g)].map(m => m[1]);

/** Imports that are not Node built-ins, not inside dir, and not in the allowed siblings or packages. */
function disallowedImports(source, dir, file, { siblings, packages }) {
  return specifiersOf(source).filter(s => {
    if (s.startsWith('node:')) return false;
    if (!s.startsWith('.')) return !packages.includes(s);
    return ![dir, ...siblings].includes(posix.normalize(posix.join(posix.dirname(dir + '/' + file), s)).split('/')[0]);
  });
}

/** Re-exports of shared/ names: any export ... from a shared/ module, or an export of a binding imported from shared/. */
function sharedReexports(source) {
  const direct = [...source.matchAll(/\bexport\b[^;]*?\bfrom\s*['"]([^'"]+)['"]/gs)].map(m => m[1]).filter(s => /(^|\/)shared\//.test(s));
  const bound = new Set();
  for (const [, clause] of source.matchAll(/import\s+([^;'"]+?)\s+from\s*['"][^'"]*shared\/[^'"]*['"]/g)) {
    const named = /\{([^}]*)\}/.exec(clause)?.[1] ?? '';
    for (const part of named.split(',')) { const name = part.trim().split(/\s+as\s+/).pop(); if (name) bound.add(name); }
    for (const part of clause.replace(/\{[^}]*\}/, '').split(',')) { const name = part.trim().replace(/^\*\s*as\s+/, ''); if (name) bound.add(name); }
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

test('disallowedImports rejects a sibling directory outside the allowlist', () => {
  const signing = ALLOWED.find(rule => rule.dir === 'signing');
  assert.deepEqual(disallowedImports("import { x } from '../../icons/icon-assets.mjs';", 'signing', 'keys/a.mjs', signing), ['../../icons/icon-assets.mjs']);
  assert.deepEqual(disallowedImports("import { x } from '../../releases/cli/cli.mjs';", 'signing', 'keys/a.mjs', signing), ['../../releases/cli/cli.mjs']);
  assert.deepEqual(disallowedImports("import 'left-pad';", 'signing', 'a.mjs', signing), ['left-pad']);
});

test('disallowedImports accepts built-ins, own files, allowed siblings and packages', () => {
  const signing = ALLOWED.find(rule => rule.dir === 'signing');
  const source = "import fs from 'node:fs';\nimport a from './b.mjs';\nimport c from '../vault/v.mjs';\nimport d from '../../shared/assert.mjs';\nimport e from '../../tooling/process.mjs';\nimport f from '@1password/sdk';";
  assert.deepEqual(disallowedImports(source, 'signing', 'keys/a.mjs', signing), []);
});

test('disallowedImports keeps shared a leaf', () => {
  const shared = ALLOWED.find(rule => rule.dir === 'shared');
  assert.deepEqual(disallowedImports("import '../tooling/android-tools.mjs';", 'shared', 'x.mjs', shared), ['../tooling/android-tools.mjs']);
  assert.deepEqual(disallowedImports("import { a } from './assert.mjs';", 'shared', 'x.mjs', shared), []);
});

test('sharedReexports flags every export-from of a shared module', () => {
  for (const source of [
    "export { digest } from '../../shared/digest.mjs';",
    "export * from '../../shared/digest.mjs';",
    "export * as policy from '../../shared/signing-policy.mjs';",
    "export {\n  a,\n  b as c,\n} from '../../shared/assert.mjs';",
  ]) assert.equal(sharedReexports(source).length, 1, source);
});

test('sharedReexports flags an export of a binding imported from shared', () => {
  assert.deepEqual(sharedReexports("import { digest } from '../../shared/digest.mjs';\nexport { digest };"), ['digest']);
  assert.deepEqual(sharedReexports("import { digest as d } from '../../shared/digest.mjs';\nexport { d as digest };"), ['d']);
  assert.deepEqual(sharedReexports("import {\n  a,\n  b as c,\n} from '../../shared/assert.mjs';\nexport { c };"), ['c']);
  assert.deepEqual(sharedReexports("import * as ns from '../../shared/assert.mjs';\nexport default ns;"), ['ns']);
});

test('sharedReexports accepts a shared import used only locally and unrelated export-from', () => {
  assert.deepEqual(sharedReexports("import { digest } from '../../shared/digest.mjs';\nexport const x = digest(1);\nexport { other };"), []);
  assert.deepEqual(sharedReexports("export { a } from './contract.mjs';"), []);
});

for (const rule of ALLOWED) {
  test(rule.dir + '/ imports only what its allowlist permits', async () => {
    for (const { file, source } of await mjsFiles(rule.dir)) {
      assert.deepEqual(disallowedImports(source, rule.dir, file, rule), [], rule.dir + '/' + file + ': import outside the allowlist');
    }
  });
}

for (const dir of NO_SHARED_REEXPORT) {
  test(dir + '/ does not re-export from shared/', async () => {
    for (const { file, source } of await mjsFiles(dir)) {
      assert.deepEqual(sharedReexports(source), [], dir + '/' + file + ': import from shared/ directly instead of re-exporting');
    }
  });
}
