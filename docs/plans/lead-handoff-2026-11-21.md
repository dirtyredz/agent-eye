# Agent Eye lead handoff, 2026-11-21

Written by the outgoing lead at Skippy's request (context past 200k). Main is pushed at `c942d9c`; working tree clean.

## Done this session (all on main, Codex sign-off PASS each)
- dk-6844e342 service -> ui cycle (CaptureService uses getLaunchIntentForPackage)
- dk-c6943e21 Shot -> camera/CapturedFrame
- dk-6eb63a65 shared `src/camera/agent-eye-dir.mjs` (private dir + atomic write)
- dk-09deb5ab `selfupdate/contract/Integrity.kt` (SHA-256/hex)
- dk-28656ae1 signing <-> releases cycle: new `src/android/shared/` (assert, digest, release-identity, signing-policy); contract.mjs re-exports removed
- dk-7251f934 closed as shipped (repo is at v1.3.3)
- Guards: `src/android/guards/package-boundaries.test.mjs` (Kotlin, table-driven) and `import-boundaries.test.mjs` (mjs, allowlists for signing/ and shared/, re-export check). Add a table row per new boundary.

## Remaining queue
P1 (not started):
- dk-67c7a246 github() adapter owns all GitHub knowledge (createDraft, tagCommit, isOnMain, artifact name; also move the post-create lookup retry into gh.createDraft). Pairs with dk-782342ab (P3) and the retry-cache work.
- dk-d31136f0 split signing/verify out of retry-cache.mjs (local/signed-bundle.mjs); one sha256sums() formatter; SIGNED_MANIFEST constant in the contract. Touches the same release/ files as dk-67c7a246: do them sequentially, not in parallel.
- dk-4f28c010 split CaptureError (camera failures vs session-wide under service/). /status wire codes must not change (CaptureErrorTest pins them). Android, Gradle gate.
- dk-7948b352 self-update: InstallPrompter, UpdateController, UpdateState/Candidate split. Android, Gradle gate; largest of the four, plan it as slices.
Not mine: two P1 wip bugs (dk-d5e272e1 snapshot crop, dk-4e20c7d1 rotation breaks capture) belong to other sessions; they touch CameraSession.kt, so keep diffs there minimal.
P2 (all todo): dk-e3c7da36, a3578348, afb1348d, bed2beb9, 1a0ea30f, 7fe1491b, de2486c6, 02d3e16c, 25aafecb, 0e25e219, 48064578, 33998a40.

## Process that worked (and traps)
- Per item: verify it still holds, `Plan:` block, developer-light (Sonnet) in worktree `C:\Users\dirty\projects\.worktrees\agent-eye\<branch>`, fast gate, ff-merge to local main, `gate-cli take`, one `structure-reviewer` (add simplicity-lens only if a change adds/deletes layers; Skippy's ruling), then `review-launch codex-review` sign-off, `gate-cli clear`, push.
- `review-launch structure-reviewer|simplicity-lens` needs `--effort medium`; codex-review does not.
- Sign-off briefs must contain `Plan:` and `Validation: none (<reason>)` lines, plus each earlier finding with its disposition, or it FAILs on intake.
- ff-merge can silently fail if the branch was cut before main moved (an earlier merge hid behind `tail -1`); `git rebase main` in the worktree first, and check `git log` after merging. Sign-off reviews main HEAD, so a missed merge fails with "change not in HEAD".
- Never run `git push` in the same command as `gate-cli clear`: the push hook blocks the whole command before clear runs.
- Never `gradlew --stop` (kills other projects' builds); check for other java.exe first. agent-eye is not enrolled on ANU, so Gradle gates run locally with `ANDROID_HOME=C:/Users/dirty/AppData/Local/Android/Sdk` (no local.properties in worktrees).
- `node --test` showed 3 spurious failures twice when another `node --test` ran concurrently; it passes when run alone.
- Guard tests grew by whack-a-mole under sign-off; write them structurally (allowlist, any `export ... from`) from the start.
- Releases happen only at night via Skippy. Do not cut one.
