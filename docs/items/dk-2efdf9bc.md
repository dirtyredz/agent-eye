---
id: dk-2efdf9bc
type: bug
created: 2026-10-25
status: todo
since: 2026-10-25
area:
priority: P2
rank: t
parent:
fixes: []
blocked_by: []
relates: []
---
# draftRelease: retry the list lookup after creating a draft


Release run 40269939117 (v1.0.0, 2026-10-26): `draft` job created draft 423864903, then failed "Draft v1.0.0 not found after creation" — GitHub's release list is eventually consistent for a just-created draft. Rerunning the failed job reused the draft and succeeded (safe by design: lookup-before-create).

Fix: in src/android/releases/cli/ci-commands.mjs draftRelease, after `gh release create`, retry releaseByTag a few times with short backoff (e.g. 5 attempts, 2 s) before failing; unit test with a fake list that returns empty on the first N calls.
