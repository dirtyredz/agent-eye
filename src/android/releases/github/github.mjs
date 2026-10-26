import { mkdir, readFile, mkdtemp, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { UNSIGNED_MANIFEST, REPOSITORY, WORKFLOW, manifest, requireThat } from '../artifacts/contract.mjs';
import { digest, verifyFiles } from '../artifacts/artifacts.mjs';

export const ACTION_ARTIFACT = m => `unsigned-${m.commit}-${m.runAttempt}`;
export function assertDraft(release, tag, id) {
  requireThat(release.draft === true && release.prerelease === false && release.tag_name === tag && release.id === Number(id), 'Release ID/tag is not the expected draft');
}
export async function pages(get, route) {
  const all = [];
  for (let page = 1; ; page++) {
    const items = await get(`${route}${route.includes('?') ? '&' : '?'}per_page=100&page=${page}`);
    requireThat(Array.isArray(items), 'Invalid paginated response');
    all.push(...items);
    if (items.length < 100) return all;
  }
}
export function github(run) {
  const api = async (route, args = []) => JSON.parse((await run('gh', ['api', `repos/${REPOSITORY}/${route}`, ...args])).stdout || 'null');
  const release = id => api(`releases/${id}`);
  async function assets(id) { return pages(api, `releases/${id}/assets`); }
  async function download(tag, name, dir) {
    await mkdir(dir, { recursive: true });
    await run('gh', ['release', 'download', tag, '--repo', REPOSITORY, '--pattern', name, '--dir', dir]);
    return readFile(join(dir, name));
  }
  // GET releases/tags/<tag> never returns drafts (404), so find a release by listing and matching tag_name exactly.
  // Returns null when absent; fails closed when the tag matches more than one release.
  async function releaseByTag(tag) {
    const matches = (await pages(api, 'releases')).filter(rel => rel.tag_name === tag);
    requireThat(matches.length <= 1, `Multiple releases match tag ${tag}; manual reconciliation required`);
    return matches[0] ?? null;
  }
  // The numeric database ID of the draft release for a tag; refuses a missing or already published release.
  async function draftId(tag) {
    const info = await releaseByTag(tag);
    requireThat(info, `Release ${tag} not found; nothing to sign`);
    requireThat(info.draft === true, `Release ${tag} is not a draft (already published?); nothing to sign`);
    requireThat(Number.isSafeInteger(info.id) && info.id > 0, 'Invalid draft release ID');
    return String(info.id);
  }
  return {
    api, release, releaseByTag, assets, download, draftId,
    async history(current) {
      for (const rel of await pages(api, 'releases')) {
        if (rel.draft) continue;
        requireThat(/^v\d+\.\d+\.\d+$/.test(rel.tag_name), 'Unexpected release history; manual reconciliation required');
        const asset = (await assets(rel.id)).find(a => a.name === 'release.json');
        requireThat(asset, `Published ${rel.tag_name} lacks release.json`);
        const m = manifest(await api(`releases/assets/${asset.id}`, ['-H', 'Accept: application/octet-stream']), true);
        requireThat(m.tag === rel.tag_name && m.versionCode < current.versionCode, 'Published version code is not lower');
      }
    },
    async provenance(m, dir) {
      const runInfo = await api(`actions/runs/${m.runId}`);
      requireThat(runInfo.conclusion === 'success' && runInfo.status === 'completed' && runInfo.run_attempt >= m.runAttempt && runInfo.head_sha === m.commit && runInfo.path === WORKFLOW && runInfo.repository?.full_name === REPOSITORY && runInfo.head_repository?.full_name === REPOSITORY && ['push', 'workflow_dispatch'].includes(runInfo.event), 'Untrusted or unsuccessful workflow run');
      const jobs = await pages(async route => (await api(route)).jobs, `actions/runs/${m.runId}/attempts/${m.runAttempt}/jobs`);
      requireThat(jobs.some(job => job.name === 'build' && job.conclusion === 'success' && job.head_sha === m.commit), 'Original artifact build job did not succeed');
      const records = await pages(async route => (await api(route)).artifacts, `actions/runs/${m.runId}/artifacts`);
      const matches = records.filter(a => a.name === ACTION_ARTIFACT(m));
      requireThat(matches.length === 1 && !matches[0].expired && matches[0].workflow_run?.head_sha === m.commit, 'Missing/expired/conflicting Actions artifact provenance');
      await run('gh', ['run', 'download', String(m.runId), '--repo', REPOSITORY, '--name', ACTION_ARTIFACT(m), '--dir', dir]);
      const original = manifest(JSON.parse(await readFile(join(dir, UNSIGNED_MANIFEST), 'utf8')));
      requireThat(JSON.stringify(original) === JSON.stringify(m), 'Draft manifest differs from Actions artifact');
      await verifyFiles(dir, original);
      return original;
    },
    async upload(id, tag, path, name, tempRoot) {
      assertDraft(await release(id), tag, id);
      const existing = (await assets(id)).filter(a => a.name === name);
      requireThat(existing.length <= 1, 'Duplicate asset');
      if (existing.length) {
        const dir = await mkdtemp(join(tempRoot, 'compare-'));
        try { requireThat(digest(await download(tag, name, dir)).sha256 === digest(await readFile(path)).sha256, `Conflicting asset: ${name}`); }
        finally { await rm(dir, { recursive: true, force: true }); }
      } else await run('gh', ['release', 'upload', tag, path, '--repo', REPOSITORY]);
    },
    async removeUnsigned(id, tag) {
      assertDraft(await release(id), tag, id);
      for (const asset of await assets(id)) {
        if (['agent-eye-unsigned.apk', 'agent-eye-unsigned.aab', UNSIGNED_MANIFEST].includes(asset.name)) await api(`releases/assets/${asset.id}`, ['--method', 'DELETE']);
      }
    },
    async publish(id, tag) {
      assertDraft(await release(id), tag, id);
      await api(`releases/${id}`, ['--method', 'PATCH', '-F', 'draft=false']);
    },
  };
}
