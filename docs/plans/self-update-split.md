## Chosen scope

(Lead, after plan-trimmer. Build slice by slice as separate commits; all three slices KEPT.)
- Slice 1 InstallPrompter: KEEP, with the ordering rules and the `selfupdate -> ui` boundary row.
- Slice 2 UpdateController: KEEP, incl. UpdateControllerTest (phase table, live guard, per-action effect, swallowed permission exception, bound/unbound stop) and the `ui/capture -> selfupdate` boundary. CUT the separate "changed service availability" case (merge into bound/unbound).
- Slice 3 vocabulary split: KEEP as verbatim move, no new tests; compile plus final gate only.
- Gate: `node --test` + `gradlew --no-daemon ktlintCheck assembleDebug testDebugUnitTest` after slices 1 and 2 and the final state. CUT: separate full Gradle gate for slice 3, per-slice proof runs. NEVER `gradlew --stop` (cut the trimmer's suggestion; shared machine).
- Docs: STRUCTURE.md (new files, fix stale vocabulary line) and one DECISIONS.md entry; ARCHITECTURE/GOTCHAS only where they name a moved owner.
- DEFER: signed-device lifecycle checks (Start blocked after camera-permission return; capture stopped on verified install) to the release install step; add as a Docket item.

## Goal

Refactor dk-7948b352 into three independently buildable slices: extract installer prompting, centralize UI update orchestration, and separate updater vocabulary. Preserve networking, lifecycle, installation, persistence, and camera Start behavior.

## Placement

Paths below are relative to `android/app/src/main/java/local/agenteye/`, except where stated.

| Slice | File | Responsibility and placement |
|---|---|---|
| 1 | **New** `selfupdate/install/InstallPrompter.kt` | Android installer UI bridge: confirmation intent, visible launcher, attachment, result handling, continuation, and permission intent. Extract from runtime into the installer package. |
| 1 | `selfupdate/UpdateRuntime.kt` | Construct and expose `coordinator` and `prompter`; remove prompting state and methods, including forwarding wrappers. Retain adapter composition. |
| 1 | `selfupdate/install/InstallResultReceiver.kt` | Forward results directly to `updates.prompter`. |
| 1–2 | `ui/CaptureServiceLink.kt` | Slice 1 redirects attachment to the prompter. Slice 2 removes updater attachment entirely, leaving service binding. |
| 1–2 | `ui/MainActivity.kt`, `ui/settings/SettingsActivity.kt` | Wire lifecycle and dependencies to extracted owners. Settings delegates action dispatch; Main supplies the controller’s live Start guard. |
| 2 | **New** `ui/update/UpdateController.kt` | Own `UpdateAction` dispatch, `stopForInstall()`, and `startBlocked()`. These are UI orchestration responsibilities, separate from presentation and the updater state machine. |
| 2 | `ui/capture/CaptureScreenModel.kt` | Replace the phase supplier with a live Start-blocked boolean supplier. |
| 2 | `ui/capture/CameraStateHolder.kt` | Consume that boolean for display and Start eligibility; remove updater phase knowledge. |
| 3 | **New** `selfupdate/Candidate.kt` | Frozen candidate value, extracted verbatim with its existing package. |
| 3 | **New** `selfupdate/UpdateFailure.kt` | Failure taxonomy and exceptions: `UpdateProblem`, `UpdateFailure`, `UpdateCancelled`. |
| 3 | `selfupdate/UpdateState.kt` | Retain only `UpdatePhase` and `UpdateState`. |
| 2 | **New** `android/app/src/test/java/local/agenteye/ui/update/UpdateControllerTest.kt` | JVM coverage of orchestration and the Start guard. |
| 2 | `android/app/src/test/java/local/agenteye/ui/capture/CameraStateHolderTest.kt` | Adapt existing presentation tests to the boolean input. |
| 1–2 | `src/android/guards/package-boundaries.test.mjs` | Extend its existing boundary table; retain the shared scanner. |

Living-document changes are specified under Docs. No file should approach the approximately 800-line cap.

## Sequence

1. **essential — Slice 1: extract installer prompting.**

   Move the existing runtime bridge into `InstallPrompter`, constructed with application context and the coordinator. Update every caller listed above together.

   Preserve these ordering rules:
   - Attach installs the launcher and camera handoff before foreground reconciliation.
   - Detach clears both callbacks, retains confirmation, and backgrounds only when not changing configurations.
   - Installer results pass coordinator session/nonce acceptance before changing confirmation.
   - Pending confirmation launches only through an attached launcher. Successful launch retains confirmation.
   - Missing confirmation or a throwing launcher reconciles to lost-confirmation recovery only while attached. Without a launcher, continuation does nothing.
   - Reattachment reconciles locally without automatically launching confirmation.
   - Permission intents retain the API 26+ per-app setting and API 24–25 security-setting behavior.

   Add the table boundary `selfupdate → local.agenteye.ui`.

   **Acceptance:** runtime contains composition only; all bridge callers use the prompter; resume/pause ownership, cancellation, recovery, and explicit Install-after-permission behavior remain unchanged.

   **Risks:** retaining an Activity after detach, launching from the background, or changing rejected-result handling. Preserve existing method ordering rather than redesigning recovery.

2. **essential — Slice 2: centralize UI update orchestration.**

   Extract from the current owners: `SettingsActivity.act`, `CaptureServiceLink.attachUpdates`, and the phase predicate in `CameraStateHolder`.

   Make the controller testable with narrow function dependencies for current phase, coordinator commands, permission/confirmation effects, and a supplier of the currently bound service’s stop operation. Avoid new mocking frameworks or a general coordinator abstraction.

   Preserve dispatch exactly:

   | Action | Effect |
   |---|---|
   | Check | `coordinator.check()` |
   | Update | `coordinator.update()` |
   | Cancel | `coordinator.cancel()` |
   | Install | `coordinator.install()` |
   | GrantPermission | Launch permission settings, preserving swallowed launch exceptions |
   | Continue | Continue installer confirmation |
   | AbandonRetry | `coordinator.abandonAndRetry()` |

   `stopForInstall()` obtains the current stop operation when invoked, returns false if unbound, otherwise stops synchronously and returns true—even if capture was already stopped. Keep this callback at the coordinator’s existing verified-install handoff; do not stop capture at download or permission entry.

   `startBlocked()` rereads the phase and returns true **only** for `Installing` and `AwaitingConfirmation`. Feed its boolean through `CaptureScreenModel.refresh()` into `CameraStateHolder`. Keep `SessionStarter`’s refresh on permission return.

   Both Activities retain attachment on resume, detachment on pause, and current observer ordering. Construct controllers lazily to avoid accessing `application` during Activity field initialization. Remove `CaptureServiceLink.attachUpdates`.

   Add the table boundary `ui/capture → local.agenteye.selfupdate`.

   **Acceptance:** controller owns all three policies; capture presentation has no updater dependency; all seven actions retain their effects; Start and stop-before-install behavior remain unchanged.

   **Risks:** caching a phase or unbound service, replacing the guard with the inequivalent `UpdateState.busy`, or moving lifecycle attachment to start/stop.

3. **essential — Slice 3: separate updater vocabulary.**

   Move declarations verbatim into `Candidate.kt` and `UpdateFailure.kt`, retaining `local.agenteye.selfupdate`. Existing callers and imports remain unchanged.

   Preserve Candidate’s twelve constructor fields and order, enum names/order, exception messages, defaults, and `UpdateState.busy`.

   **Acceptance:** unchanged persisted records, candidate equality, error classification, and displayed messages; existing vocabulary consumers compile unchanged.

   **Risk/tradeoff:** file separation deliberately leaves the documented root/subpackage dependency cycle unresolved. Moving Candidate into `release/` would violate the existing installer boundary; broader package migration is outside this slice.

Each slice must pass independently against the baseline. Slices 1 and 2 share wiring files but have no semantic prerequisite: controller callbacks bind to runtime methods before slice 1, or prompter methods afterward. Do not introduce compatibility wrappers solely to sequence the work.

## Tests

- **Slice 1:** retain coordinator, installer gateway, and recovery coverage. The project has JUnit but no Android intent-testing framework; verify Activity/Intent behavior through the device checks below rather than adding infrastructure for this extraction.
- **Slice 2:** add `UpdateControllerTest` covering:
  - Every `UpdatePhase.entries` value, with exactly two blocked phases.
  - Phase changes between calls, proving the guard is live.
  - Every action invoking exactly its corresponding effect.
  - Permission-launch exceptions remaining swallowed.
  - Unbound, bound, and changed service availability; synchronous stop before successful return.
- Adapt `CameraStateHolderTest` without losing visibility, binding, running, notice, Installing-status, or lens assertions. Retain `SessionStarterTest`’s permission checks.
- **Slice 3:** use existing persistence round-trip, frozen-candidate, downloader, transport-failure, and presenter tests. Add no tests that merely repeat declarations.
- Preserve existing package-boundary rules and add new rules through `BOUNDARIES`.

## Verification

The implementer must run; no build or tests were run during planning.

From the repository root, after each slice:

- `node --test src/android/guards/package-boundaries.test.mjs`
- `.\android\gradlew.bat -p android ktlintCheck assembleDebug testDebugUnitTest --no-daemon`

Before integration, also run the documented Node gate: `npm ci`, then `node --test`. Run formatting separately if needed; stop Gradle afterward with `.\android\gradlew.bat -p android --stop`.

On a suitable signed test installation, confirm:

- Opening Main/Settings starts no network work; navigation and rotation preserve existing updater ownership.
- Permission settings return requires another Install tap.
- Verified installation stops capture; cancellation leaves it stopped until explicit Start.
- Start is unavailable during installation/confirmation, including after a camera-permission return.
- Background confirmation is retained without launching; lost confirmation offers existing recovery.

## Validation

Validation: none (behavior-preserving refactor).

## Docs

- `STRUCTURE.md`: document InstallPrompter, UpdateController, binding-only CaptureServiceLink, and separated vocabulary files. Correct the statement that vocabulary remains in `UpdateState.kt`; explicitly retain unresolved package-cycle debt.
- `docs/ARCHITECTURE.md`: describe runtime composition, installer prompting, UI orchestration, and the boolean guard supplied to capture presentation.
- `docs/DECISIONS.md`: record the deliberate same-package vocabulary split and deferred package migration.
- `docs/GOTCHAS.md`: update ownership references while retaining resume/pause, explicit permission retry, and camera-stop rules.
- `docs/FEATURES.md`: no capability change; leave unchanged.

## Open questions / assumptions

- The item’s source locations are stale: action dispatch now lives in Settings, install preflight in CaptureServiceLink, and the phase guard in CameraStateHolder. This plan follows the current tree.
- `dk show dk-7948b352 --json` failed with `DOCKET_NOT_A_REPO`; the checked-in item body was read directly. No item was changed or claimed.
- Assume the requested vocabulary split does not authorize broader dependency-cycle removal. Do not mark that structural debt resolved.
- Implementation requires normal worktree access: follow `docket guide` before the first item write, claim/release through `dk`, and run `dk check` before pushing.