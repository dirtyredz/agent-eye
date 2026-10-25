import { REPOSITORY, requireThat } from '../artifacts/contract.mjs';

export async function tagIdentity(run, root, tag, mismatch = 'Tag commit mismatch') {
  const commit = (await run('git', ['rev-parse', 'HEAD'], { cwd: root })).stdout.trim();
  requireThat((await run('git', ['rev-parse', `${tag}^{commit}`], { cwd: root })).stdout.trim() === commit, mismatch);
  await run('git', ['merge-base', '--is-ancestor', commit, 'origin/main'], { cwd: root });
  return commit;
}
export async function checkout(run, root, tag) {
  requireThat(/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(tag), 'Invalid tag');
  requireThat(!(await run('git', ['status', '--porcelain', '--untracked-files=all'], { cwd: root })).stdout.trim(), 'Clean checkout required');
  const origin = (await run('git', ['remote', 'get-url', 'origin'], { cwd: root })).stdout.trim();
  requireThat([`https://github.com/${REPOSITORY}.git`, `git@github.com:${REPOSITORY}.git`].includes(origin), 'Unexpected origin');
  return tagIdentity(run, root, tag, 'Checkout does not match tag');
}
