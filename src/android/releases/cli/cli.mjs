import { pathToFileURL } from 'node:url';
import { requireThat } from '../artifacts/contract.mjs';
import { setupPlan } from './owner-setup.mjs';

const plans = {
  setup: setupPlan,
  'generate-key': ['Refuse existing public pin or Agent Eye vault items.', 'Create RSA-4096 PKCS12 key (10950 days) in a unique ACL-verified Windows directory.', 'Store dedicated Document and password items through 1Password DesktopAuth/Windows Hello.', 'Read both items back; verify certificate before writing only the public pin. Delete temporary keys.'],
  'verify-key': ['Read committed public certificate pin.', 'Recover dedicated Agent Eye key through DesktopAuth into an ACL-verified Windows directory.', 'Verify one RSA-4096 private key, alias and pinned SHA-256. Delete temporary keys.'],
  'release-sign': ['Lock local signing; require clean checkout matching tag and repository.', 'Validate draft ID/tag, remote main ancestry, successful trusted workflow run and Actions artifact provenance.', 'Validate unsigned APK/AAB identity, hashes, non-debuggable status and paginated release version history before accessing 1Password.', 'Recover pinned key through DesktopAuth into an ACL-verified directory; align then apksigner-sign APK; jarsigner-sign AAB.', 'Verify signatures/identity; retain complete signed retry cache; upload without clobber; download and verify every final asset.', 'Recheck draft, tag and versions; remove only exact unsigned assets; publish last only with --publish; clean temporary key material.'],
  stage: ['Validate unsigned APK/AAB; stage artifacts and commit/run-bound manifest.'],
  draft: ['Create or resume same draft/tag/commit; refuse published releases and conflicting assets.'],
  'validate-tag': ['Validate tag/version and ancestry from main.'],
};
export function parse(args) {
  const [command, ...rest] = args;
  requireThat(Object.hasOwn(plans, command), 'Unknown command');
  const options = { command, execute: false, publish: false };
  const seen = new Set();
  for (let i = 0; i < rest.length; i++) {
    const flag = rest[i];
    requireThat(!seen.has(flag), `Duplicate flag: ${flag}`); seen.add(flag);
    if (['--execute', '--dry-run', '--publish'].includes(flag)) options[flag === '--dry-run' ? 'dryRun' : flag.slice(2)] = true;
    else if (['--tag', '--release-id'].includes(flag)) {
      requireThat(rest[i + 1] && !rest[i + 1].startsWith('--'), `Missing value: ${flag}`);
      options[flag === '--release-id' ? 'releaseId' : 'tag'] = rest[++i];
    } else throw new Error(`Unknown flag: ${flag}`);
  }
  requireThat(!(options.execute && options.dryRun), 'Conflicting execution flags');
  requireThat(!options.publish || command === 'release-sign', '--publish requires release-sign');
  requireThat(!options.releaseId || command === 'release-sign', '--release-id requires release-sign');
  requireThat(!options.tag || ['release-sign', 'validate-tag', 'stage', 'draft'].includes(command), 'Unexpected --tag');
  if (command === 'release-sign') requireThat(/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(options.tag) && /^[1-9]\d*$/.test(options.releaseId) && Number.isSafeInteger(Number(options.releaseId)), 'release-sign requires valid --tag and --release-id');
  return options;
}
export async function main(args, { output = console.log, execute = async options => (await import('./commands.mjs')).execute(options) } = {}) {
  const options = parse(args);
  if (!options.execute) {
    output(JSON.stringify({ mode: 'dry-run', ...options, steps: plans[options.command] }, null, 2));
    return;
  }
  return execute(options);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
