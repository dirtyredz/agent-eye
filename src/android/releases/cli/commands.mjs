import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { run } from '../../tooling/process.mjs';
import { readVersion } from '../artifacts/contract.mjs';
import { github } from '../github/github.mjs';
import { ciCommand } from './ci-commands.mjs';
import { ownerCommand } from './owner-commands.mjs';

export async function execute(options) {
  const root = resolve('.');
  const localVersion = readVersion(await readFile(join(root, 'android/version.properties'), 'utf8'));
  const deps = { root, run, gh: github(run), localVersion };
  return ['validate-tag', 'stage', 'draft'].includes(options.command)
    ? ciCommand(deps, options) : ownerCommand(deps, options);
}
