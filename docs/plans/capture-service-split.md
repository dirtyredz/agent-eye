## Chosen scope (lead, 2026-11-21)

Weighed against the plan-trimmer report. Build three slices, each a compiling commit:
1. **Keep:** notification extraction (`SessionNotification`) + `ACTION_START`/`ACTION_STOP` constants (dk-e3c7da36 part 1). Gate: ktlintCheck + assembleDebug (no full gate).
2. **Keep:** `SessionStatusJson` + `CaptureSnapshot` timing fields + compact `SessionStatusJsonTest` (7-key shape/types, sessionReady vs cameraReady, explicit nulls, Long uptime > 2^31). Full gate.
3. **Keep, merged:** `PreviewViewport` holder (dk-afb1348d) and `PreviewSink` port (dk-33998a40) as ONE commit (they change the same `attach` signature), including the `ui/preview` -> `service` rule in `package-boundaries.test.mjs`. Keep: holder retains no surface, single update when a camera exists, `startForeground` ordering. Full gate.
- **Cut:** per-error-code and escaping test loops, debugger-observation check, docket claim/release ceremony, separate ARCHITECTURE/GOTCHAS rewrites (fold one sentence into DECISIONS; GOTCHAS only if a note already exists).
- **Docs:** STRUCTURE.md layout + a DECISIONS.md entry superseding the shared-viewport note, in the matching commits.
- **Device checks:** owner-run, shortened to: geometry before Start, Stop/Start with preview attached, notification open/Stop, rotation during capture, status response. Record as the post-merge check; not automatable here.
- **Deferred:** `PreviewTarget` value type (no fourth field). dk-e3c7da36 part 1 (typed errors, snapshot, display renderer) is already satisfied; close with a note.
- Validation: none (refactor).

---

## Goal

Refactor capture notification construction, status serialization, and preview delivery into their appropriate owners while preserving session lifecycle, HTTP responses, and camera framing. Keep `CaptureService` responsible for start/stop, binding, and orchestration; reuse the existing `CaptureSnapshot`, `SessionError`, and pure UI renderer.

## Placement

Kotlin paths below are relative to `android/app/src/main/java/local/agenteye/`; JVM test paths are relative to `android/app/src/test/java/local/agenteye/`.

| File | Responsibility and rationale |
|---|---|
| **New** `service/SessionNotification.kt` | Extract notification channel, notification construction, and both PendingIntents from `CaptureService`. Foreground lifecycle remains in the service. |
| **New** `service/SessionStatusJson.kt` | Pure `CaptureSnapshot` → HTTP status JSON renderer, using existing `json/StrictJson.write`. No Android APIs, clocks, or service references. |
| `service/CaptureSnapshot.kt` | Add credential-free `uptimeMs: Long` and `lastCaptureTime: Long?`, with defaults preserving existing constructors. Reuse this snapshot rather than introducing `SessionStatus`. |
| `service/CaptureService.kt` | Delegate notification and rendering; define public `ACTION_START`/`ACTION_STOP` companion constants; coordinate preview delivery and implement `PreviewSink`. |
| `ui/capture/SessionStarter.kt` | Use `CaptureService.ACTION_START`. Permission and visibility gates remain here. |
| **New** `camera/PreviewViewport.kt` | Own the single retained ViewPort and its retention/equality policy. Contains no surface, Activity, or service reference. |
| `camera/CameraSession.kt` | Consume the retained viewport holder; preserve rotation updates and rebind decisions. Remove its separate ViewPort storage and move viewport comparison into the holder. |
| **New** `camera/PreviewSink.kt` | One-method preview delivery contract shared by UI and service. `camera/` avoids the forbidden service → UI dependency. |
| `ui/preview/PreviewFeed.kt` | Depend on `() -> PreviewSink?`; retain Android display/layout listener responsibilities. |
| `src/android/guards/package-boundaries.test.mjs` | Add `ui/preview` → `local.agenteye.service` prohibition to the existing table. |
| **New test** `service/SessionStatusJsonTest.kt` | Verify the status wire contract on the JVM. |

`MainActivity` remains the composition point: its existing `PreviewFeed(this) { service }` expression can remain unchanged because `CaptureService` implements the port. `CameraStateHolder`, `CameraStatus`, `CaptureScreenModel`, `CaptureServiceLink`, and `StillServer` need no architectural changes.

Tradeoff: a small viewport holder survives individual `CameraSession` instances so pre-Start geometry and restart replay remain available. The service retains its reference, but neither stores another ViewPort nor implements retention rules. This avoids introducing the deferred three-field `PreviewTarget` abstraction.

## Sequence

1. **essential — Commit 1: extract notification construction and centralize actions.**
   - Add `ACTION_START = "START"` and `ACTION_STOP = "STOP"` to the service companion; update dispatch, `SessionStarter`, and notification Stop.
   - Extract `SessionNotification` with notification ID and a construction method accepting an Android context.
   - Keep `startForeground` in `CaptureService`, at its current position before session resources are started.
   - Preserve channel `"capture"`, low importance, notification ID `1`, request codes `1`/`2`, immutable flags, icon, copy, ongoing flag, and Stop action.
   - Preserve `getLaunchIntentForPackage` for opening the app; do not introduce a UI import.

2. **essential — Commit 2: extract pure HTTP status rendering.**
   - Extend `CaptureSnapshot` with timing facts. `CaptureService.snapshot()` supplies elapsed realtime minus `startedAt`, and the last capture’s epoch timestamp.
   - Add `SessionStatusJson`; serialize only the existing seven wire fields.
   - Replace `statusJson` construction with a thin delegation from the existing `StillServer` callback.
   - Preserve `sessionReady = running && address != null`, independent `cameraReady`, explicit JSON nulls, protocol version `1`, and existing `SessionError.code` values.
   - Leave `CameraStateHolder` as the pure display renderer: `displayStatus` has already been removed.
   - Add renderer tests in this commit. Do not expand this into a session state-machine or concurrency rewrite.

3. **essential — Commit 3: give retained viewport state one owner.**
   - Introduce `PreviewViewport`, containing one nullable ViewPort with a read-only accessor and an update operation.
   - Move the existing rule into it: replace the retained viewport only when both surface and supplied port are non-null. Null surface or null port preserves it.
   - Preserve semantic comparison of aspect ratio, rotation, scale type, and layout direction.
   - `CaptureService` retains one holder for its lifetime and passes it into each new `CameraSession`.
   - When no camera exists, service preview delivery forwards to that holder so geometry arriving before Start is retained. With a camera, forward only to `CameraSession.attach`; do not update the holder twice.
   - `CameraSession.attach` updates rotation as today, applies the holder update, and rebinds only for a changed surface identity or changed viewport geometry. `bind()` reads the holder.
   - Keep the service’s existing surface and rotation replay fields. Stop closes camera resources without clearing the holder; the next session receives it. The holder must never retain a surface.
   - Preserve deferred rebinding during captures and lens-switch behavior.

4. **essential — Commit 4: narrow preview delivery to its port.**
   - Define one method: `attach(surface: Preview.SurfaceProvider?, displayRotation: Int? = null, port: ViewPort? = null)`.
   - Null surface means detach; omitted rotation means retain the current rotation. No second detach method is needed.
   - Implement the port in `CaptureService`, resolving omitted rotation against its replay field.
   - Change `PreviewFeed`’s dependency and its three delivery sites: publish, stop, and release. Keep refresh scheduling, cancellation, and service-arrival behavior intact.
   - Add the package-boundary rule. Leave the Node-only `import-boundaries.test.mjs` unchanged.
   - Verify the existing `MainActivity` wiring compiles without an adapter.

Include the relevant documentation changes with each commit. Every commit must compile and pass the scoped gate.

## Tests

Add `service/SessionStatusJsonTest.kt` covering:

- Exactly seven keys, expected types, and protocol version `1`.
- The running/address combinations governing `sessionReady`; camera readiness remains independent.
- Explicit null address, last-capture timestamp, and error.
- Every `SessionError` wire code.
- Fixed uptime and capture timestamps, including values exceeding 32-bit integer range.
- Correct JSON escaping.
- Exclusion of lens fields and all other snapshot-only data.

Assert decoded JSON semantics using `StrictJson.parse`; do not require a particular object-key order. Existing `SessionErrorTest` and `CameraStateHolderTest` already cover error vocabulary and display precedence.

The new package rule provides the automated preview dependency regression check.

Do not add Robolectric, mocking libraries, or a generic retention framework solely for these extractions. `PreviewFeed` still depends on Android `Activity`, `Handler`, `DisplayManager`, and `PreviewView`; a fake sink alone does not make it JVM-testable. Viewport and notification integration require the device checks below.

## Verification

No builds or tests were run during planning.

Use the documented JDK 21, Node 22, Android SDK 36/build-tools 35.0.0, and Gradle wrapper. Ensure only one heavyweight build runs at a time.

For every commit, from the repository root:

- `node --test`
- `.\android\gradlew.bat -p android --no-daemon ktlintCheck assembleDebug testDebugUnitTest`

Run `npm ci` if dependencies need installation. If formatting is needed, run `ktlintFormat` separately before the gate. Stop Gradle afterward with `.\android\gradlew.bat --stop`. Run `dk check` before pushing.

On a device, verify:

- Start, notification open, notification Stop, and Activity Stop; notification permission denial still leaves Activity Stop available.
- Preview geometry delivered before Start, and service connection arriving after preview creation.
- Stop/Start with the preview attached, plus detach/reconnect and restart replay.
- Settings → Back and screen-off capture preserve framing and session credentials.
- Portrait, landscape, and 180° rotation, including rotation during capture; readiness recovers and FIT framing remains intact.
- Lens changes preserve the listener and session; front JPEG text remains unmirrored.
- Repeated identical geometry does not cause rebinds; surface or viewport changes do. Use debugger observation of `CameraSession.bind` if necessary.
- The existing pair/status/shot procedure still returns the seven-field status response and valid images.

## Validation

Validation: none (internal refactors; no claimed performance improvement or intentional user-visible behavior change).

## Docs

- `STRUCTURE.md`: list the extracted notification/JSON responsibilities, camera-owned viewport holder, and preview port; replace references to `CaptureService.statusJson` as the serialization owner.
- `docs/ARCHITECTURE.md`: describe preview delivery through `PreviewSink` and retained geometry surviving individual camera sessions.
- `docs/DECISIONS.md`: record the ownership resolution, holder-lifetime tradeoff, and reuse of the existing snapshot/display renderer; supersede the note retaining shared viewport ownership pending `dk-afb1348d`.
- `docs/GOTCHAS.md`: clarify that pre-Start geometry, null-surface retention, and rotation without unnecessary rebinding must survive future changes.
- `docs/FEATURES.md`: no update unless actual device acceptance evidence is added; these changes introduce no feature.

## Open questions / assumptions

- `dk show <id> --json` was attempted for all three items but returned `DOCKET_NOT_A_REPO`. Their Markdown bodies were read directly; no items or files were modified. The implementer must re-read through Docket in a functioning worktree, run `docket guide` before the first item write, claim the work, and release it when finished.
- `dk-e3c7da36` is partly stale: typed errors, the snapshot, and pure display rendering already exist; Start dispatch lives in `SessionStarter`. Treat those portions as satisfied.
- The older preview item suggests introducing the port when JVM feed tests are wanted. This brief explicitly requests the port; proceed with the narrow dependency change without expanding Android test infrastructure.
- “CameraSession holds it; service only replays it” is interpreted as one camera-owned retained viewport holder, retained by the service across camera instances. This preserves pre-Start delivery without duplicate mutable viewport storage.
- Assume `a0a3010` is the intended baseline supplied by the orchestrator; Git identity was not independently verified.