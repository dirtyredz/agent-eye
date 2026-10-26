---
id: dk-ae1cbb13
type: bug
created: 2026-10-26
status: dropped
since: 2026-10-26
area: release
priority: P2
rank: zw
parent:
fixes: []
blocked_by: []
relates: []
---
# Release draft fails: 'Draft not found after creation' (lookup races GitHub)

v1.2.0 Android release run 40413735326 (2026-10-26) failed in the draft job: src/android/releases/cli/ci-commands.mjs:27 looks the release up by tag immediately after 'gh release create --draft' and gets nothing, although the draft did exist (isDraft true, 0 assets). Likely eventual consistency of the releases-by-tag listing for drafts. Rerun of the failed job recovers because the existing-draft path is taken.
Direction: retry gh.releaseByTag a few times with short backoff after create (or use the id/URL returned by create). Add a unit test in ci-commands.test.mjs for a first-lookup-miss.
