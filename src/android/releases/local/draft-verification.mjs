import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { REPOSITORY, UNSIGNED, UNSIGNED_MANIFEST, finalNames, assertAssetSet, version, manifest, certificate, requireThat } from '../artifacts/contract.mjs';
import { digest } from '../artifacts/artifacts.mjs';
import { assertDraft } from '../github/github.mjs';
import { checkout } from './checkout.mjs';

export function draftVerification({ root, run, gh, apk, bundle, localVersion, pin, tempRoot, fresh }) {
  async function check(state) {
    requireThat(await checkout(run, root, state.tag) === state.manifest.commit, 'Checkout changed');
    assertDraft(await gh.release(state.id), state.tag, state.id);
    const assets = await gh.assets(state.id);
    assertAssetSet(assets, state.cleaned ? 'final' : 'draft');
    const tag = await gh.api(`git/ref/tags/${state.tag}`);
    let object = tag.object;
    if (object.type === 'tag') object = (await gh.api(`git/tags/${object.sha}`)).object;
    requireThat(object.type === 'commit' && object.sha === state.manifest.commit, 'Remote tag moved');
    const comparison = await gh.api(`compare/${state.manifest.commit}...main`);
    requireThat(['ahead', 'identical'].includes(comparison.status), 'Tagged commit is not on remote main');
  }
  return {
    check,
    async preflight(options) {
      version(localVersion, options.tag); certificate(pin);
      const commit = await checkout(run, root, options.tag);
      const rel = await gh.release(options.releaseId);
      assertDraft(rel, options.tag, options.releaseId);
      const list = await gh.assets(rel.id);
      assertAssetSet(list, 'draft');
      const downloaded = await fresh('draft-');
      const hasUnsignedManifest = list.some(a => a.name === UNSIGNED_MANIFEST);
      const m = hasUnsignedManifest
        ? manifest(JSON.parse(await gh.download(options.tag, UNSIGNED_MANIFEST, downloaded)))
        : manifest(JSON.parse(await gh.download(options.tag, 'release.json', downloaded)), true);
      requireThat(m.commit === commit && m.versionCode === localVersion.versionCode && m.versionName === localVersion.versionName && m.tag === options.tag, 'Draft version/commit mismatch');
      const unsigned = hasUnsignedManifest ? m : { ...m, artifacts: Object.fromEntries(UNSIGNED.map(name => [name, { size: 1, sha256: m.unsignedHashes[name] }])) };
      // When deletion was interrupted, obtain the original manifest from the trusted run.
      const actions = await fresh('actions-');
      let original = unsigned;
      if (!hasUnsignedManifest) {
        const probe = await fresh('manifest-');
        await run('gh', ['run', 'download', String(m.runId), '--repo', REPOSITORY, '--name', `unsigned-${m.commit}-${m.runAttempt}`, '--dir', probe]);
        original = manifest(JSON.parse(await readFile(join(probe, UNSIGNED_MANIFEST), 'utf8')));
        requireThat(original.commit === commit && original.tag === options.tag && original.runId === m.runId && original.runAttempt === m.runAttempt, 'Recovery provenance mismatch');
        for (const name of UNSIGNED) requireThat(original.artifacts[name].sha256 === m.unsignedHashes[name], 'Recovery unsigned hash mismatch');
      }
      await gh.provenance(original, actions);
      for (const name of UNSIGNED) {
        if (!list.some(a => a.name === name)) {
          requireThat(finalNames.every(n => list.some(a => a.name === n)), `Missing unsigned asset: ${name}`);
          continue;
        }
        const bytes = await gh.download(options.tag, name, downloaded);
        requireThat(JSON.stringify(digest(bytes)) === JSON.stringify(original.artifacts[name]), `Draft hash mismatch: ${name}`);
      }
      await apk.unsigned(join(actions, UNSIGNED[0]), localVersion);
      await bundle.unsigned(join(actions, UNSIGNED[1]), localVersion);
      const state = { id: rel.id, tag: options.tag, manifest: original, pin, input: actions, cache: join(tempRoot, `${options.tag}-${commit}`), list };
      await check(state);
      return state;
    },
  };
}
