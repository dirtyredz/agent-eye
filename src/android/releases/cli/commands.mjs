import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { run as bareRun } from '../../tooling/process.mjs';
import { readVersion } from '../artifacts/contract.mjs';
import { github } from '../github/github.mjs';
import { ciCommand } from './ci-commands.mjs';
import { ownerCommand } from './owner-commands.mjs';

export async function execute(options, log) {
  const root = log.root;
  const run = log.wrapRun(bareRun);
  const localVersion = readVersion(await readFile(join(root, 'android/version.properties'), 'utf8'));
  const deps = { root, run, log, gh: github(run), localVersion };
  return ['validate-tag', 'stage', 'draft'].includes(options.command)
    ? ciCommand(deps, options) : ownerCommand(deps, options);
}
