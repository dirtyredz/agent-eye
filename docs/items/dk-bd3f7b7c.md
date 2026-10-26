---
id: dk-bd3f7b7c
type: task
created: 2026-10-25
status: todo
since: 2026-10-25
area:
priority: P3
rank: t
parent:
fixes: []
blocked_by: []
relates: []
---
# Bump GitHub Actions off deprecated Node 20 / setup-java v4


CI run 40234636388 (2026-10-25) passed but warned: actions/checkout, setup-java, setup-node and upload-artifact (pinned SHAs) target Node 20, now forced onto Node 24; setup-java v4 is deprecated in favour of v5. Applies to .github/workflows/ci.yml and android-release.yml.

Direction: re-pin each action to the current major's commit SHA (keep SHA pinning + version comment), run CI and one android-release dry run, confirm no warnings.
