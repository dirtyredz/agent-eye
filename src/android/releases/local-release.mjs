import { readFile, writeFile, mkdir, mkdtemp, rm, open, copyFile, access } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { REPOSITORY, UNSIGNED, FINAL, version, manifest, certificate, requireThat } from './contract.mjs';
import { digest, verifyFiles, finalManifest, hashes } from './artifacts.mjs';
import { assertDraft } from './github.mjs';
import { recoverKey } from '../signing/key-provisioning.mjs';
import { onTermination } from '../tooling/process.mjs';

const finalNames = [...FINAL, 'release.json', 'SHA256SUMS'];
export async function checkout(run, root, tag) {
  requireThat(/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(tag), 'Invalid tag');
  requireThat(!(await run('git', ['status', '--porcelain', '--untracked-files=all'], { cwd: root })).stdout.trim(), 'Clean checkout required');
  const origin = (await run('git', ['remote', 'get-url', 'origin'], { cwd: root })).stdout.trim();
  requireThat([`https://github.com/${REPOSITORY}.git`, `git@github.com:${REPOSITORY}.git`].includes(origin), 'Unexpected origin');
  const commit = (await run('git', ['rev-parse', 'HEAD'], { cwd: root })).stdout.trim();
  requireThat((await run('git', ['rev-parse', `${tag}^{commit}`], { cwd: root })).stdout.trim() === commit, 'Checkout does not match tag');
  await run('git', ['merge-base', '--is-ancestor', commit, 'origin/main'], { cwd: root });
  return commit;
}
export function localRelease({ root, run, gh, apk, bundle, vault, localVersion, pin }) {
  const tempRoot = join(root, '.tmp/releases');
  let scratch;
  async function fresh(prefix) { return mkdtemp(join(scratch, prefix)); }
  async function check(state) {
    requireThat(await checkout(run, root, state.tag) === state.manifest.commit, 'Checkout changed');
    assertDraft(await gh.release(state.id), state.tag, state.id);
    const assets = await gh.assets(state.id);
    const allowed = state.cleaned ? finalNames : [...UNSIGNED, 'release-unsigned.json', ...finalNames];
    requireThat(assets.every(a => allowed.includes(a.name)) && new Set(assets.map(a => a.name)).size === assets.length, 'Unexpected or duplicate draft assets');
    if (state.cleaned) requireThat(assets.length === finalNames.length, 'Incomplete final asset set');
    const tag = await gh.api(`git/ref/tags/${state.tag}`);
    let object = tag.object;
    if (object.type === 'tag') object = (await gh.api(`git/tags/${object.sha}`)).object;
    requireThat(object.type === 'commit' && object.sha === state.manifest.commit, 'Remote tag moved');
    const comparison = await gh.api(`compare/${state.manifest.commit}...main`);
    requireThat(['ahead', 'identical'].includes(comparison.status), 'Tagged commit is not on remote main');
  }
  return {
    async lock() {
      await mkdir(tempRoot, { recursive: true });
      const lockPath = join(root, '.release-sign.lock');
      const handle = await open(lockPath, 'wx');
      await handle.writeFile(JSON.stringify({ pid: process.pid, started: new Date().toISOString() }));
      await handle.close();
      scratch = await mkdtemp(join(tempRoot, 'inspection-'));
      const cleanup = async () => { await rm(scratch, { recursive: true, force: true }); await rm(lockPath, { force: true }); };
      const removeCleanup = onTermination(cleanup, 2);
      return async () => { try { await cleanup(); } finally { removeCleanup(); } };
    },
    async preflight(options) {
      version(localVersion, options.tag); certificate(pin);
      const commit = await checkout(run, root, options.tag);
      const rel = await gh.release(options.releaseId);
      assertDraft(rel, options.tag, options.releaseId);
      const list = await gh.assets(rel.id);
      const allowed = [...UNSIGNED, 'release-unsigned.json', ...finalNames];
      requireThat(list.every(a => allowed.includes(a.name)) && new Set(list.map(a => a.name)).size === list.length, 'Unexpected or duplicate draft assets');
      const downloaded = await fresh('draft-');
      const hasUnsignedManifest = list.some(a => a.name === 'release-unsigned.json');
      const m = hasUnsignedManifest
        ? manifest(JSON.parse(await gh.download(options.tag, 'release-unsigned.json', downloaded)))
        : manifest(JSON.parse(await gh.download(options.tag, 'release.json', downloaded)), true);
      requireThat(m.commit === commit && m.versionCode === localVersion.versionCode && m.versionName === localVersion.versionName && m.tag === options.tag, 'Draft version/commit mismatch');
      const unsigned = hasUnsignedManifest ? m : { ...m, artifacts: Object.fromEntries(UNSIGNED.map(name => [name, { size: 1, sha256: m.unsignedHashes[name] }])) };
      // When deletion was interrupted, obtain the original manifest from the trusted run.
      const actions = await fresh('actions-');
      let original = unsigned;
      if (!hasUnsignedManifest) {
        const probe = await fresh('manifest-');
        await run('gh', ['run', 'download', String(m.runId), '--repo', REPOSITORY, '--name', `unsigned-${m.commit}-${m.runAttempt}`, '--dir', probe]);
        original = manifest(JSON.parse(await readFile(join(probe, 'release-unsigned.json'), 'utf8')));
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
    history: m => gh.history(m),
    async resume(state) {
      try { await access(join(state.cache, 'release.json')); return state.cache; }
      catch (e) { if (e.code !== 'ENOENT') throw e; }
      const existing = state.list.filter(a => finalNames.includes(a.name) && a.name !== 'mapping.txt');
      if (!existing.length) return null;
      requireThat(finalNames.every(name => state.list.some(a => a.name === name)), 'Partial upload requires retained local .tmp/releases cache; restore it before retry');
      await mkdir(state.cache, { recursive: true });
      for (const name of finalNames) await gh.download(state.tag, name, state.cache);
      return state.cache;
    },
    withKey: (publicPin, action) => recoverKey({ root, run, vault }, publicPin, action),
    async sign(state, key) {
      // Stage separately, and persist a complete transaction before the first upload.
      const dir = await fresh('signed-');
      await apk.sign(join(state.input, UNSIGNED[0]), join(dir, FINAL[0]), key, pin, localVersion);
      await bundle.sign(join(state.input, UNSIGNED[1]), join(dir, FINAL[1]), key, pin, localVersion);
      await copyFile(join(state.input, 'mapping.txt'), join(dir, 'mapping.txt'));
      await finalManifest(dir, state.manifest, pin.sha256);
      await mkdir(state.cache, { recursive: true });
      for (const name of finalNames) await copyFile(join(dir, name), join(state.cache, name));
      return state.cache;
    },
    async verify(state, dir) {
      const m = manifest(JSON.parse(await readFile(join(dir, 'release.json'), 'utf8')), true);
      for (const field of ['tag', 'commit', 'versionName', 'versionCode', 'runId', 'runAttempt']) requireThat(m[field] === state.manifest[field], `Cached ${field} mismatch`);
      requireThat(m.signerSha256 === pin.sha256, 'Cached signer mismatch');
      for (const name of UNSIGNED) requireThat(m.unsignedHashes[name] === state.manifest.artifacts[name].sha256, 'Cached provenance mismatch');
      requireThat(m.artifacts['mapping.txt'].sha256 === state.manifest.artifacts['mapping.txt'].sha256, 'Mapping changed');
      await verifyFiles(dir, m, true);
      await apk.verify(join(dir, FINAL[0]), pin, localVersion);
      await bundle.verify(join(dir, FINAL[1]), pin, localVersion);
      const sums = await hashes(dir, [...FINAL, 'release.json']);
      requireThat(await readFile(join(dir, 'SHA256SUMS'), 'utf8') === Object.entries(sums).map(([name, a]) => `${a.sha256}  ${name}\n`).join(''), 'SHA256SUMS mismatch');
    },
    recheck: check,
    async upload(state, dir) { for (const name of finalNames) await gh.upload(state.id, state.tag, join(dir, name), name, scratch); },
    async verifyDownloads(state, dir) {
      const checkDir = await fresh('uploaded-');
      for (const name of finalNames) requireThat(digest(await gh.download(state.tag, name, checkDir)).sha256 === digest(await readFile(join(dir, name))).sha256, `Uploaded hash mismatch: ${name}`);
    },
    async removeUnsigned(state) { await gh.removeUnsigned(state.id, state.tag); state.cleaned = true; },
    publish: state => gh.publish(state.id, state.tag),
  };
}
