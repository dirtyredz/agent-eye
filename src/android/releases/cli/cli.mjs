import { pathToFileURL } from 'node:url';
import { requireThat } from '../artifacts/contract.mjs';
import { runScript } from '../../../script-run/script-run.mjs';
import { OWNER_COMMANDS, CI_COMMANDS } from './command-names.mjs';
import { setupPlan } from './owner-setup.mjs';
import { vaultPlan } from '../../signing/vault/vault-setup.mjs';

const plans = {
  setup: setupPlan,
  'setup-vault': vaultPlan,
  provision: [...vaultPlan, 'Then run generate-key (below), all with Windows Hello approvals only.'],
  'generate-key': ['Refuse existing public pin or Agent Eye vault items.', 'Create RSA-4096 PKCS12 key (10950 days) in a unique ACL-verified Windows directory.', 'Store dedicated Document and password items through 1Password DesktopAuth/Windows Hello.', 'Read both items back; verify certificate before writing only the public pin. Delete temporary keys.'],
  'verify-key': ['Read committed public certificate pin.', 'Recover dedicated Agent Eye key through DesktopAuth into an ACL-verified Windows directory.', 'Verify one RSA-4096 private key, alias and pinned SHA-256. Delete temporary keys.'],
  'inspect-vault': ['Read-only: sign in through DesktopAuth and list the Agent Eye item titles, categories, states and creation times in the configured vault. Values are never read.'],
  'cleanup-orphans': ['Refuse unless the public pin sha256 is null and gh confirms no GitHub release exists (fail closed).', 'Print the exact two Agent Eye item titles found in the configured vault.', 'Delete only those items, and only with --execute. Then provision can be rerun.'],
  'release-sign': ['Lock local signing; require clean checkout matching tag and repository.', 'Validate draft ID/tag, remote main ancestry, successful trusted workflow run and Actions artifact provenance.', 'Validate unsigned APK/AAB identity, hashes, non-debuggable status and paginated release version history before accessing 1Password.', 'Recover pinned key through DesktopAuth into an ACL-verified directory; align then apksigner-sign APK; jarsigner-sign AAB.', 'Verify signatures/identity; retain complete signed retry cache; upload without clobber; download and verify every final asset.', 'Recheck draft, tag and versions; remove only exact unsigned assets; publish last only with --publish; clean temporary key material.'],
  stage: ['Validate unsigned APK/AAB; stage artifacts and commit/run-bound manifest.'],
  draft: ['Create or resume same draft/tag/commit; refuse published releases and conflicting assets.'],
  'validate-tag': ['Validate tag/version and ancestry from main.'],
};
// Every known command has a plan and vice versa: the sets in command-names.mjs are the single source.
if (Object.keys(plans).sort().join() !== [...OWNER_COMMANDS, ...CI_COMMANDS].sort().join()) throw new Error('plans and command-names.mjs disagree');
export function parse(args) {
  const [command, ...rest] = args;
  requireThat(Object.hasOwn(plans, command), 'Unknown command');
  const options = { command, execute: false, publish: false };
  const seen = new Set();
  for (let i = 0; i < rest.length; i++) {
    const flag = rest[i];
    requireThat(!seen.has(flag), `Duplicate flag: ${flag}`); seen.add(flag);
    if (['--execute', '--dry-run', '--publish'].includes(flag)) options[flag === '--dry-run' ? 'dryRun' : flag.slice(2)] = true;
    else if (['--tag', '--release-id', '--account'].includes(flag)) {
      requireThat(rest[i + 1] && !rest[i + 1].startsWith('--'), `Missing value: ${flag}`);
      options[{ '--release-id': 'releaseId', '--tag': 'tag', '--account': 'account' }[flag]] = rest[++i];
    } else throw new Error(`Unknown flag: ${flag}`);
  }
  requireThat(!(options.execute && options.dryRun), 'Conflicting execution flags');
  requireThat(!options.publish || command === 'release-sign', '--publish requires release-sign');
  requireThat(!options.releaseId || command === 'release-sign', '--release-id requires release-sign');
  requireThat(!options.account || ['setup-vault', 'provision'].includes(command), '--account requires setup-vault or provision');
  requireThat(!options.tag || ['release-sign', 'validate-tag', 'stage', 'draft'].includes(command), 'Unexpected --tag');
  if (command === 'release-sign') requireThat(/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(options.tag) && /^[1-9]\d*$/.test(options.releaseId) && Number.isSafeInteger(Number(options.releaseId)), 'release-sign requires valid --tag and --release-id');
  return options;
}
export async function main(args, { output = console.log, execute = async (options, log) => (await import('./commands.mjs')).execute(options, log), log } = {}) {
  const options = parse(args);
  if (!options.execute) {
    log?.step(`dry-run ${options.command}`);
    output(JSON.stringify({ mode: 'dry-run', ...options, steps: plans[options.command] }, null, 2));
    return;
  }
  log?.step(`execute ${options.command}`);
  return execute(options, log);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const argv = process.argv.slice(2);
  const owner = OWNER_COMMANDS.has(argv[0]);
  await runScript({ name: owner ? argv[0] : `release-${argv[0] ?? 'cli'}`, from: import.meta.url, kind: owner ? 'owner' : 'script', argv }, log => main(argv, { log }));
}
