## 1. Goal

Redesign Agent Eye as a polished Compose + Material 3 camera utility for **v1.3.0**, preserving its service-owned capture, pairing, lens selection and manual updater behavior.

The felt target: after permissions are granted, **start or stop in one tap, frame the scene immediately, read and copy the pairing code without navigating, and reach Settings in one tap**. Rotation, theme changes and countdown updates must preserve the active session.

Comparable products provide two useful patterns:

| Benchmark | Main and Settings organization | Application to Agent Eye |
|---|---|---|
| DroidCam | Its documented connection screen exposes the phone’s IP/port, then camera output. Phone Settings groups Video and Camera controls, with advanced options separated. [Connection guide](https://www.dev47apps.com/droidcam/connect/), [Settings documentation](https://droidcam.app/help/) | Keep connection information visible beside pairing; put occasional configuration outside the camera controls. |
| AlfredCamera | Its documented camera screen places Settings and power controls at opposite bottom corners. Settings contains operational switches such as automatic power saving. [Camera-screen and Settings guide](https://support.alfred.camera/hc/en-us/articles/360039690872-Power-Saving-Mode) | Give the preview prominence and keep a small set of persistent controls around its edges. |

These are documentation-based layout references, not hands-on evaluations. Agent Eye retains its existing capture and networking model.

## 2. Placement

Paths below are relative to `android/app/src/main/java/local/agenteye/` unless stated otherwise.

| File | Responsibility and placement |
|---|---|
| `ui/MainActivity.kt` — change | `ComponentActivity`; Android lifecycle, permission requests, foreground-service commands and navigation. Extract preview tracking and presentation state. Retain its package/class so notification navigation remains stable. |
| `ui/capture/CameraScreen.kt` — move/rewrite | Stateless Compose camera layout, replacing `ui/CameraScreen.kt`. Owns preview and overlay placement. |
| `ui/capture/CameraControls.kt` — new | Start/Stop, lens switch and readable session status. Keeps the screen layout separate from control rendering. |
| `ui/capture/CameraStateHolder.kt` — new | Android-free presentation state and Start eligibility, derived from service snapshots, visibility, permissions and updater phase. Associated immutable UI models live here. |
| `ui/preview/CameraPreview.kt` — new | Hosts CameraX `PreviewView` through `AndroidView`; owns its creation and release hooks. |
| `ui/preview/PreviewFeed.kt` — new extraction | Display/layout listeners, Activity-derived rotation and surface/viewport delivery, extracted from MainActivity. Addresses `dk-6839f9b8`. |
| `ui/pairing/PairingCard.kt` — new replacement | Compose code/countdown/Copy/New code presentation; replaces `ui/PairingPanel.kt`. |
| `ui/pairing/PairingStateHolder.kt` — new | Pure live-code display and countdown calculation with injected time. |
| `ui/pairing/PairingCopy.kt` — move unchanged | Existing pure copy eligibility and confirmation policy. |
| `ui/pairing/PairingClipboard.kt` — new extraction | Tap-time code lookup, sensitive `ClipData` and platform confirmation behavior, extracted from PairingPanel. |
| `ui/settings/SettingsActivity.kt` — change | `ComponentActivity`; retain updater ownership, service binding and Android install/permission launches. |
| `ui/settings/SettingsScreen.kt` — rewrite | Compose Settings layout, scrolling, back navigation and local privacy-dialog visibility. |
| `ui/update/UpdatePanel.kt` — rewrite | Stateless Material rendering of existing `UpdateView` and `UpdateAction`. |
| `ui/privacy/PrivacyPolicyDialog.kt` — move/rewrite | Secure Material dialog displaying bundled, selectable policy text. Replaces the root-level View dialog. |
| `ui/theme/Theme.kt` — new | Static light/dark Material schemes, default typography and root Surface, following Command Deck’s theme structure. |
| `service/CaptureSnapshot.kt` — new | Immutable, Android-free capture facts: running, camera readiness, address, error and available/selected lenses. Contains no credentials. |
| `service/CaptureService.kt` — narrow change | Expose that snapshot; remove `displayStatus()` after migrating its sole caller, MainActivity. Preserve session orchestration. |

Retain `ui/CaptureServiceLink.kt`, `ui/update/UpdatePresenter.kt`, `camera/LensSelection.kt`, `CameraSession.kt` and `JpegEncoder.kt` behavior. Do not introduce another lens policy, updater state machine or capture owner.

The resulting `ui/` has two files and seven responsibility folders: nine immediate entries. Keep each folder near ten entries and every code file below approximately 800 lines. No generic `components/` or `utils/` catch-all is needed.

Additional changed/new files:

- `android/gradle/libs.versions.toml`: introduce a catalog following the siblings; migrate existing dependency pins without unrelated upgrades.
- `android/build.gradle.kts`, `android/app/build.gradle.kts`: catalog aliases, Compose compiler and dependencies.
- `.editorconfig`: narrow Compose naming allowance.
- `android/app/src/main/res/values/themes.xml` and new `values-night/themes.xml`: coherent light/dark bootstrap window colors.
- `android/app/src/main/res/values/strings.xml`: new UI labels and accessibility descriptions.
- `android/version.properties`: `1.3.0`, versionCode `5`, subject to intervening releases.
- JVM test files listed in section 4; living documentation listed in section 6.

Keep both manifest Activity entries and their existing security/export settings. No manifest change is required under the configuration-handling decision below. Retain existing ProGuard rules unless minified verification demonstrates a specific missing rule.

## 3. Sequence

1. **Establish the design and dependency baseline.**

   Reuse these sibling references:

   - Command Deck: `src/mobile/android/gradle/libs.versions.toml` and `app/src/main/java/com/dirtyredz/commanddeck/ui/theme/Theme.kt`.
   - Family Command Center: `src/android/gradle/libs.versions.toml` and `core/designsystem/src/main/kotlin/com/dirtyredz/familyhq/core/designsystem/FamilyTheme.kt`.
   - Reuse Command Deck’s Scaffold/top-app-bar pattern and Family’s readable Settings rows, spacing and scrolling. Their drawers, tabs and larger navigation architecture are unnecessary here.

   Both siblings use Compose BOM **`2026.03.01`**, Kotlin/Compose plugin **`2.3.21`**, Activity Compose **`1.12.4`**, lifecycle **`2.10.0`**, core KTX **`1.17.0`**, default Material typography and static light/dark schemes. Neither enables dynamic color.

   Align those dependencies while retaining AGP `8.13.2`, Gradle `8.13`, CameraX `1.5.3`, SDK **24/36/36**, JVM 17 and build JDK 21. Align existing `lifecycle-service` to `2.10.0`; verify resolved dependencies still support API 24. The siblings’ minimum SDK is 26, so their builds do not establish that compatibility.

   Apply `org.jetbrains.kotlin.plugin.compose` at the Kotlin version and enable Compose. Add BOM-managed UI, foundation, Material 3, material-icons-core and tooling-preview; keep UI tooling debug-only. Add Activity Compose separately. The compiler follows Kotlin rather than the BOM. [Official compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [BOM guidance](https://developer.android.com/develop/ui/compose/bom).

   Use core icons with text labels; avoid extended-icons, custom fonts, Navigation Compose and a new image-loading library. Preserve generated privacy/signer assets and fixture configuration.

   Add `ktlint_function_naming_ignore_when_annotated_with = Composable` scoped to Kotlin source, verifying support in the pinned ktlint engine. Keep strict lint/ktlint behavior and existing generated-file exclusions. Do not copy sibling advisory suppressions without a demonstrated need.

2. **Implement the theme and screen layouts with fixture state.**

   Follow Command Deck’s single-file theme and root `Surface(background, onBackground)`. Reuse its amber palette: light primary `#B4621C`; dark primary `#E08A3C`, background `#101418`, surface `#161B21`, foreground `#E8E4DC`, with the remaining scheme roles taken from that theme. Use default Material typography; monospace only for pairing digits.

   Follow system light/dark mode. Keep dynamic color disabled for sibling consistency and predictable camera-overlay contrast. No appearance preference is added in this release.

   **Main, portrait:**

   - Full-bleed preview as the stable background.
   - Safe-inset top row: concise status at the leading edge, Settings icon at the trailing edge.
   - Bottom pairing card: “Pairing code,” six large digits preserving leading zeros, labeled Copy action, countdown/single-use explanation and New code.
   - Persistent bottom control row: one prominent labeled Start/Stop button and labeled camera-switch action showing the selected direction.
   - Display the current LAN URL as selectable text in the pairing/status region. Show a clear Wi-Fi explanation when unavailable.
   - Before Start, show an intentional neutral camera placeholder with brief guidance. Do not activate the camera to decorate the idle screen.

   **Main, landscape:**

   - Keep the same preview node filling the background.
   - Move pairing and controls into a trailing panel approximately 280–320dp wide, constrained to leave most of the preview unobscured.
   - Use a short-height layout with scrolling secondary information and a persistent Start/Stop action.
   - Choose layout using available dimensions, not orientation alone. Narrow multi-window layouts use the stacked arrangement.

   Controls use opaque or sufficiently strong Material surfaces; edge icons use dark scrims with reliable contrast. Preview pixels can extend under system bars, but interactive content must respect bars and cutouts. Apply insets once.

   **Settings, both orientations:**

   - Material top app bar with Back.
   - Updates card first: installed version/code, status, progress and existing phase-specific actions.
   - Keep the complete existing network disclosure visible within the scrolling page.
   - Privacy policy row opens selectable local text with Close.
   - About/usage section explains Agent Eye, trusted Wi-Fi, plain HTTP, plugged-in screen-off use and explicit Stop.
   - Use a centered, width-constrained scrolling column; landscape does not require another navigation model.

   Use 16dp outer spacing, roughly 12dp section gaps, at least 48dp touch targets and accessible icon descriptions. Support large text without clipping codes or actions. Countdown changes must not trigger a TalkBack announcement every second.

   Tradeoff: full-bleed framing can crop differently from the old split layout on tall screens. The actual PreviewView viewport remains authoritative for both preview and JPEG; controls must not resize it as status text changes.

3. **Introduce typed capture presentation before replacing Main.**

   Add `CaptureSnapshot` and expose it through the existing service observer path. Update MainActivity’s current rendering to consume `CameraStateHolder`, then remove the string-based `displayStatus()` path.

   Distinguish service binding/unavailable from confirmed stopped state. Never infer running from Wi-Fi availability or camera readiness. A running session must retain Stop even when the camera or network fails.

   Derive Start eligibility from the latest updater phase and visibility. Preserve camera-permission requests and optional notification permission. Recheck eligibility immediately before starting the service after a permission result: backgrounding or an intervening install must prevent Start.

   Hold immutable presentation state in Activity-owned Compose state. Pure holders have no Activity, service, View or Compose dependency. No ViewModel is required for service-owned session survival; recreation reconnects and refreshes from the service.

4. **Extract preview delivery and connect the Compose Main screen.**

   Create `PreviewView` in the `AndroidView` factory with `ImplementationMode.COMPATIBLE`. Keep one stable composition position across orientation, theme, status and pairing updates. Change overlay placement around it; never put separate AndroidViews inside portrait/landscape branches. This follows AndroidView’s factory ownership model. [View interoperability guidance](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose).

   `PreviewFeed` must:

   - Register DisplayManager and preview layout listeners only while Main is started; unregister them on stop.
   - Handle service connection occurring before or after preview creation.
   - Read rotation from Activity `display.rotation` on API 30+, otherwise `windowManager.defaultDisplay.rotation`; never from `PreviewView.display`.
   - Deliver the stable surface provider, current display rotation and `preview.getViewPort(rotation)` together.
   - Refresh on connection, display changes and completed layout/configuration changes. Do not publish a zero-size viewport; refresh geometry after layout settles.
   - Remove listeners and release references when the view is released.

   Preserve `CameraSession.attach` exactly: both target rotations update; rebind occurs only for a changed surface identity or viewport aspect ratio, rotation, scale type or layout direction. Preserve the shared `UseCaseGroup`, deferred rebinding during an in-flight still and JPEG crop handling.

   A genuine viewport change can cause a rebind. The guarantee is **no Activity/session recreation from handled configuration changes**, not zero CameraX rebinds.

   Retain all existing `configChanges` flags and handle them fully: call `super.onConfigurationChanged`, let Compose configuration update layout, density, typography and system theme, update system-bar styling, and request refreshed preview geometry after layout. Remove the old orientation-only View reparenting. This is the chosen resolution for `dk-0cec3e0e`.

   Keep the existing service/session viewport retention for this migration. `dk-afb1348d` remains open: consolidating that ownership would change capture internals alongside the UI migration. Do not add a new `PreviewTarget` abstraction merely to package the existing three arguments.

5. **Replace pairing presentation and preserve its security behavior.**

   Poll the live service code once per second while Main is started and refresh immediately on service events and actions. The service remains responsible for renewal after expiry, successful pairing or exhausted attempts.

   `PairingStateHolder` preserves leading zeros and rounds remaining time upward to whole seconds. Clear displayed credentials on Stop, disconnect and Activity stop; refresh immediately on return. Disable Copy and New code without a live session.

   `PairingClipboard` rereads the live code at tap time through `PairingCopy.current`; it must never copy cached rendered digits. Preserve sensitive clipboard metadata and app confirmation only below API 33.

   Keep credentials out of saved state, logs and notifications. Only harmless UI state such as scroll position or dialog visibility may use `rememberSaveable`. Delete the old PairingPanel after its callers migrate.

6. **Convert Settings, updater and privacy presentation.**

   Rewrite SettingsScreen and UpdatePanel while reusing `UpdatePresenter`, `UpdateView` and every `UpdateAction`. Keep action dispatch in SettingsActivity.

   Preserve version and candidate-size text, cancellation, download percentage, camera-stop warning, permission handoff, explicit Install after returning, Continue installation and abandoned-confirmation recovery. Opening Settings performs local reconciliation only.

   Preserve both Activities’ lifecycle ordering:

   - `onStart/onStop`: bind/unbind `CaptureServiceLink`.
   - `onResume/onPause`: attach/detach updater.
   - Settings observes after attach and forgets before detach; Main also observes updater changes needed for Start eligibility.
   - Main removes its capture observer, detaches preview and unbinds on stop.
   - Installation still stops bound capture through `CaptureServiceLink`; an unbound link rejects handoff.

   Do not move updater ownership into composition disposal or start/stop effects. Preserve `FLAG_SECURE` before content creation in both Activities; privacy dialogs inherit the secure-window policy.

   Load bundled `PRIVACY.md` once per dialog/content lifetime, retaining offline, scrollable, selectable text.

7. **Finish release integration and measure cost.**

   Remove replaced View classes/imports, complete documentation and bump `android/version.properties`.

   Keep R8 and resource shrinking enabled. Use AndroidX consumer rules; add no blanket Compose keep rules or warning suppressions.

   Expect additional Compose runtime/Material code to increase APK size, potentially by several MiB; that is an estimate, not a measurement. Compare equivalent unsigned minified release APKs from `7ac5538` and the implementation, recording absolute bytes, delta and percentage. Investigate unexpected growth, especially extended icons, bundled fonts or release tooling dependencies.

   Run the verification below before considering the redesign complete.

## 4. Tests

Use ordinary JVM tests under `android/app/src/test/java/local/agenteye/`.

| Test file | Coverage |
|---|---|
| `ui/capture/CameraStateHolderTest.kt` — new | Binding versus stopped; starting/ready/error states; running without Wi-Fi remains stoppable; lens visibility; install-phase blocking; camera denial; notification-only denial; permission return after backgrounding or an intervening install. |
| `ui/pairing/PairingStateHolderTest.kt` — new | Missing/expired code, leading zeros, exact expiry, ceiling countdown boundaries, replacement code, and clearing on Stop/disconnect. |
| `ui/pairing/PairingCopyTest.kt` — move existing | Retain expiry and API confirmation tests; add a fresh-source-versus-rendered-code case if not already covered. |
| `ui/update/UpdatePresenterTest.kt` — retain | Preserve existing action, recovery, disclosure-related status and failure distinctions; add cases only if presentation behavior changes. |

Retain existing LensSelection, CaptureGeometry, PairingCodes, UpdateCoordinator and InstallRecovery tests.

There is no instrumentation foundation today. Do not add emulator startup, screenshot infrastructure or Robolectric to the fast gate. Use fixture-based Compose previews for layout iteration; device acceptance verifies the actual CameraX/lifecycle integration. Optional instrumentation smoke tests belong in a separate task if an existing local device makes them inexpensive.

## 5. Verification

No builds or tests were run for this plan.

The implementer must run the documented gate from the repository root:

1. `npm ci`
2. `node --test`
3. `node src/android/icons/generate-icons.mjs --check`
4. `.\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest`
5. `.\android\gradlew.bat -p android lintRelease releaseTools`
6. `.\android\gradlew.bat -p android --stop`

If formatting is needed, run `ktlintFormat` separately before the gate. Verify API-24 dependency compatibility, resolved version alignment, generated assets and minified artifact identity. Record gate duration; introduce no device-test requirement into it.

Confirm on the phone, including a minified build:

- Start/Stop, camera denial/retry and notification denial behave correctly.
- Portrait, landscape, both 180-degree flips, live theme changes and increased font/display size keep controls reachable.
- Rotation during capture preserves the server, token and pairing session; readiness recovers after legitimate geometry rebinding.
- JPEG orientation and framing match the preview viewport; front-camera JPEG text remains unmirrored.
- Countdown/status recompositions do not recreate the PreviewView or cause readiness drops.
- Lens switching while stopped/live preserves persistence and session behavior.
- Copy preserves leading zeros, rejects stale state and confirms correctly below/above API 33; renewal and Stop update immediately.
- Main → Settings → Back retains the capture session and restores preview; background/screen-off capture follows the existing runbook.
- Opening Settings produces no network request; explicit update operations, cancellation, permission return and install handoff retain their behavior.
- Privacy works offline; screenshots/recents remain blocked, including dialogs.
- Gesture navigation, three-button navigation and cutouts do not obscure controls in either theme.

Use the existing `docs/AGENT-EYE.md` pairing/status/shot procedure. Record unavailable hardware cases as pending rather than passed.

## 6. Docs

- **`STRUCTURE.md`** — replace the old flat UI inventory with the responsibility folders and mirrored test locations.
- **`docs/ARCHITECTURE.md`** — describe Compose presentation, typed capture snapshots, PreviewFeed and unchanged service/updater ownership.
- **`docs/DECISIONS.md`** — record sibling dependency/theme reuse, static colors, configuration handling, full-bleed framing and measured APK tradeoff.
- **`docs/FEATURES.md`** — document v1.3.0 behavior, actual gate results, APK-size change and remaining phone acceptance.
- **`docs/GOTCHAS.md`** — explain stable AndroidView identity, post-layout viewport updates, configuration propagation and single application of insets.
- **`docs/AGENT-EYE.md`** — update navigation instructions and the physical acceptance matrix.
- **`CLAUDE.md`** — reference the redesign plan if saved as the new UI plan of record; retain the fast gate.
- **`docs/PRIVACY.md`** — no content change solely for this migration.

Correct two existing descriptions: inset application currently lives in CameraScreen rather than MainActivity itself; “ImageCapture remains bound when the Activity stops” overstates the implementation. Preview detachment can trigger still-only rebinding while preserving the service-owned session and last framing.

For implementation tracking, run `docket guide` before the first item write, claim through `dk`, and release when finished. Update `dk-6839f9b8` and `dk-0cec3e0e` only after their acceptance is verified; leave `dk-afb1348d` open with the stated deferral. Resolve discussion notes with the owner before changing authoritative bodies. Run `dk check` before pushing.

## 7. Open questions / assumptions

- **Theme:** proceed with Command Deck’s amber palette, system light/dark and no dynamic color. This intentionally replaces Agent Eye’s current teal accent.
- **Framing:** proceed with full-bleed preview and viewport-matched JPEG cropping. Phone acceptance must confirm that tall-screen cropping still serves the owner’s framing needs.
- **Configuration:** preserve the broad manifest configuration policy and implement complete Compose propagation. Narrowing it would reintroduce recreation for theme/density changes.
- **Architecture tradeoff:** retain two Activities and existing viewport-retention duplication to limit lifecycle changes. No navigation framework or capture-engine refactor is required.
- **Version:** assume versionCode `5` remains available; otherwise use the next valid monotonic code.
- **Acceptance:** previous rotation/lens/settings hardware acceptance is still pending. Passing JVM tests does not discharge those checks.
- **Primary risks:** accidental preview recreation, configuration changes that fail to reach Compose, updater ownership races, API-24 dependency incompatibility and excessive release-size growth. The sequence and verification above explicitly cover each.
## 8. Owner decisions (2026-10-26)

- **Framing: show the whole frame.** Overrides the full-bleed assumption in section 7. The preview
  uses FIT (letterboxed): it shows the full camera frame exactly as the agent receives it, with dark
  bars where the frame does not fill the screen; overlaid controls sit over the bars where possible.
  Snapshots are the full frame, not cropped to the screen. What the owner sees is what the agent gets.
  Check this against open bug dk-d5e272e1 (snapshot narrower than preview); it should be resolved
  or made moot, so record which.
- **Theme:** Command Deck's amber palette, system light/dark, no dynamic colour (as planned).
