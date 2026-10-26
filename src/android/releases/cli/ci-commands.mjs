import { readFile, mkdir, mkdtemp, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { version, REPOSITORY, requireThat, UNSIGNED, UNSIGNED_MANIFEST, manifest, assertAssetSet } from '../artifacts/contract.mjs';
import { stage, verifyFiles } from '../artifacts/artifacts.mjs';
import { assertDraft } from '../github/github.mjs';
import { tagIdentity } from '../local/checkout.mjs';
import { tooling } from '../../tooling/android-tools.mjs';

export async function validateTag({ run, root, tag }) {
  console.log(await tagIdentity(run, root, tag));
}
export async function stageRelease({ root, dir, localVersion, tag, commit, runId, runAttempt, apk, bundle }) {
  return stage({ root, dir, version: localVersion, tag, commit, runId, runAttempt, inspect: async (directory, expected) => {
    await apk.unsigned(join(directory, UNSIGNED[0]), expected);
    await bundle.unsigned(join(directory, UNSIGNED[1]), expected);
  } });
}
export async function draftRelease({ root, dir, run, gh, localVersion, tag, commit, runId, runAttempt }) {
  const m = manifest(JSON.parse(await readFile(join(dir, UNSIGNED_MANIFEST), 'utf8')));
  requireThat(m.tag === tag && m.commit === commit && m.runId === runId && m.runAttempt <= runAttempt, 'Draft staging identity mismatch');
  await verifyFiles(dir, m);
  if (!await gh.releaseByTag(tag)) {
    await run('gh', ['release', 'create', tag, '--repo', REPOSITORY, '--draft', '--verify-tag', '--target', commit, '--title', `Agent Eye ${localVersion.versionName}`, '--notes', `Unsigned build of ${commit}. Owner-attended local signing required.`]);
  }
  const rel = await gh.releaseByTag(tag);
  requireThat(rel, `Draft ${tag} not found after creation`);
  assertDraft(rel, tag, rel.id);
  requireThat(rel.target_commitish === commit, 'Existing draft commit differs');
  const assets = await gh.assets(rel.id);
  assertAssetSet(assets, 'ci');
  await mkdir(join(root, '.tmp'), { recursive: true });
  const scratch = await mkdtemp(join(root, '.tmp/draft-'));
  try { for (const name of [...UNSIGNED, UNSIGNED_MANIFEST]) await gh.upload(rel.id, tag, join(dir, name), name, scratch); }
  finally { await rm(scratch, { recursive: true, force: true }); }
  console.log(`Draft ${rel.id}: ${tag}`);
}
export async function ciCommand(deps, options) {
  const { root, run, localVersion } = deps;
  const tag = options.tag ?? process.env.RELEASE_TAG;
  version(localVersion, tag);
  if (options.command === 'validate-tag') return validateTag({ ...deps, tag });
  const commit = (await run('git', ['rev-parse', 'HEAD'], { cwd: root })).stdout.trim();
  const context = { ...deps, tag, commit, dir: join(root, '.tmp/release-stage'),
    runId: Number(process.env.GITHUB_RUN_ID), runAttempt: Number(process.env.GITHUB_RUN_ATTEMPT) };
  if (options.command === 'stage') return stageRelease({ ...context, ...tooling(root, run) });
  if (options.command === 'draft') return draftRelease(context);
}
