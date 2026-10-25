import { readFile, mkdir, mkdtemp, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { run } from '../tooling/process.mjs';
import { readVersion, version, certificate, REPOSITORY, requireThat, UNSIGNED, manifest } from './contract.mjs';
import { apkTools } from './apk-signing.mjs';
import { bundleTools } from './bundle-signing.mjs';
import { stage, verifyFiles } from './artifacts.mjs';
import { github, assertDraft } from './github.mjs';
import { setup } from './owner-setup.mjs';
import { releaseSign } from './release-sign.mjs';
import { localRelease } from './local-release.mjs';
import { openVault } from '../signing/vault.mjs';
import { provision, recoverKey } from '../signing/key-provisioning.mjs';

export function tooling(root) {
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? join(process.env.LOCALAPPDATA ?? '', 'Android/Sdk');
  return { apk: apkTools(join(sdk, 'build-tools/35.0.0'), run), bundle: bundleTools(join(root, 'android/build/release-tools'), run) };
}
export async function execute(options) {
  const root = resolve('.');
  const localVersion = readVersion(await readFile(join(root, 'android/version.properties'), 'utf8'));
  const pinPath = join(root, 'src/android/releases/signing-certificate.json');
  const vault = async () => openVault(JSON.parse(await readFile(join(root, 'android/signing.local.json'), 'utf8')));
  const gh = github(run);
  const tag = options.tag ?? process.env.RELEASE_TAG;
  if (options.command === 'setup') return setup(run);
  if (options.command === 'generate-key') return provision({ root, run, vault }, { pinPath, firstVersionCode: localVersion.versionCode });
  if (options.command === 'verify-key') return recoverKey({ root, run, vault }, certificate(JSON.parse(await readFile(pinPath, 'utf8'))), async () => console.log('Recovery verified against committed public certificate.'));
  const { apk, bundle } = tooling(root);
  if (options.command === 'release-sign') return releaseSign(localRelease({ root, run, gh, apk, bundle, vault, localVersion, pin: JSON.parse(await readFile(pinPath, 'utf8')) }), options);
  version(localVersion, tag);
  const commit = (await run('git', ['rev-parse', 'HEAD'])).stdout.trim();
  if (options.command === 'validate-tag') {
    requireThat((await run('git', ['rev-parse', `${tag}^{commit}`])).stdout.trim() === commit, 'Tag commit mismatch');
    await run('git', ['merge-base', '--is-ancestor', commit, 'origin/main']);
    console.log(commit); return;
  }
  const dir = join(root, '.tmp/release-stage');
  if (options.command === 'stage') return stage({ root, dir, version: localVersion, tag, commit, runId: Number(process.env.GITHUB_RUN_ID), runAttempt: Number(process.env.GITHUB_RUN_ATTEMPT), inspect: async (directory, expected) => { await apk.unsigned(join(directory, UNSIGNED[0]), expected); await bundle.unsigned(join(directory, UNSIGNED[1]), expected); } });
  if (options.command === 'draft') {
    const m = manifest(JSON.parse(await readFile(join(dir, 'release-unsigned.json'), 'utf8')));
    requireThat(m.tag === tag && m.commit === commit && m.runId === Number(process.env.GITHUB_RUN_ID) && m.runAttempt <= Number(process.env.GITHUB_RUN_ATTEMPT), 'Draft staging identity mismatch');
    await verifyFiles(dir, m);
    const existing = await run('gh', ['api', `repos/${REPOSITORY}/releases/tags/${tag}`], { allowed: [0, 1] });
    if (existing.code === 1) {
      requireThat(/HTTP 404/.test(existing.stderr), 'Ambiguous release lookup');
      await run('gh', ['release', 'create', tag, '--repo', REPOSITORY, '--draft', '--verify-tag', '--target', commit, '--title', `Agent Eye ${localVersion.versionName}`, '--notes', `Unsigned build of ${commit}. Owner-attended local signing required.`]);
    }
    const rel = await gh.api(`releases/tags/${tag}`);
    assertDraft(rel, tag, rel.id);
    requireThat(rel.target_commitish === commit, 'Existing draft commit differs');
    const assets = await gh.assets(rel.id);
    requireThat(assets.every(a => [...UNSIGNED, 'release-unsigned.json'].includes(a.name)), 'Signing already started; CI cannot modify this draft');
    await mkdir(join(root, '.tmp'), { recursive: true });
    const scratch = await mkdtemp(join(root, '.tmp/draft-'));
    try { for (const name of [...UNSIGNED, 'release-unsigned.json']) await gh.upload(rel.id, tag, join(dir, name), name, scratch); }
    finally { await rm(scratch, { recursive: true, force: true }); }
    console.log(`Draft ${rel.id}: ${tag}`);
  }
}
