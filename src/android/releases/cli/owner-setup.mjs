import { REPOSITORY, requireThat } from '../artifacts/contract.mjs';
export const setupPlan = [
  'Review all tracked files and git history for private material before making them public.',
  `gh repo create ${REPOSITORY} --public --description "Local-only authenticated LAN camera"`,
  `git remote add origin https://github.com/${REPOSITORY}.git`,
  'git push -u origin HEAD:main',
  `gh api repos/${REPOSITORY} --method PATCH -f default_branch=main -F has_issues=true`,
  `gh api repos/${REPOSITORY}/actions/permissions/workflow --method PUT -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false`,
  'Keep Actions default token permissions read-only; allow workflow draft job contents:write. No custom GitHub secrets.',
];
export async function setup(run) {
  const status = (await run('git', ['status', '--porcelain'])).stdout;
  requireThat(!status.trim(), 'Setup requires a clean reviewed checkout');
  const remotes = (await run('git', ['remote'])).stdout.trim().split(/\r?\n/);
  requireThat(!remotes.includes('origin'), 'Existing origin refused; inspect manually');
  const probe = await run('gh', ['api', `repos/${REPOSITORY}`], { allowed: [0, 1] });
  requireThat(probe.code === 1 && /HTTP 404/.test(probe.stderr), 'Existing repository or ambiguous lookup refused');
  await run('gh', ['repo', 'create', REPOSITORY, '--public', '--description', 'Local-only authenticated LAN camera']);
  await run('git', ['remote', 'add', 'origin', `https://github.com/${REPOSITORY}.git`]);
  await run('git', ['push', '-u', 'origin', 'HEAD:main']);
  await run('gh', ['api', `repos/${REPOSITORY}`, '--method', 'PATCH', '-f', 'default_branch=main', '-F', 'has_issues=true']);
  await run('gh', ['api', `repos/${REPOSITORY}/actions/permissions/workflow`, '--method', 'PUT', '-f', 'default_workflow_permissions=read', '-F', 'can_approve_pull_request_reviews=false']);
}
