# Agent Eye lead handoff, 2026-11-21 (second lead)

Main pushed at `3195f3a` (plus a docs commit after this). P1 refactor queue from the first handoff is complete.

## Done (all sign-off PASS, fast gate green)
- dk-67c7a246 github() adapter owns createDraft (with lookup retry), tagCommit, isOnMain
- dk-d31136f0 local/signed-bundle.mjs; SIGNED_MANIFEST/hashedFinalNames; one sha256sums()
- dk-4f28c010 camera/CaptureError (camera only) + service/SessionError (all 5 wire codes)
- dk-7948b352 InstallPrompter, ui/update/UpdateController (+test), Candidate.kt/UpdateFailure.kt (plan: docs/plans/self-update-split.md)
- Deferred: dk-3f3d0a3a (P3) signed-device lifecycle checks at the release install step.

## Remaining
P1 wip bugs belong to other sessions: dk-d5e272e1, dk-4e20c7d1 (CameraSession.kt). P2 todo: dk-e3c7da36, a3578348, afb1348d, bed2beb9, 1a0ea30f, 7fe1491b, de2486c6, 02d3e16c, 25aafecb, 0e25e219, 48064578, 33998a40, dk-782342ab (github provenance). Verify each against code first (all >7 days old).

## Process notes
Same as handoff 2026-11-21. Extra: codex-review sign-off prompt must end with "End your report with a line exactly VERDICT: PASS or VERDICT: FAIL" or `gate-cli clear` refuses. After any fix, `gate-cli retake` and re-run sign-off before clear. Rebase worktree branches onto main before ff-merge. Releases only at night via Skippy.
