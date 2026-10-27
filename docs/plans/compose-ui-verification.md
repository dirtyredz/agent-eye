# Compose UI implementation verification

Implementation commit: 9a5d56d. Follows compose-ui-redesign.md including section 8. Version 1.3.0 / code 5.

## Scope and assumptions

- Same worktree/branch throughout: feat/compose-ui. Sibling apps read only. No downloads, device tests, emulator, long suites, signing, publishing, push or merge.
- Stable control bars reserve frame space (bottom when narrow, 320dp trailing when wide). This adapts the full-bleed layout to section 8: controls cannot hide frame edges. Status/countdown do not alter preview bounds.
- PreviewView uses COMPATIBLE + FIT_CENTER and delivers its FIT ViewPort. CameraSession.attach, shared UseCaseGroup, deferred in-flight rebind and JpegEncoder are unchanged. FIT removes screen-aspect cropping; full frame means the shared camera stream frame, not every sensor pixel. Front preview remains mirrored; delivered JPEG remains unmirrored.
- API 24 remains the minimum. No new runtime SDKs beyond AndroidX/Compose. No extended icons, custom fonts, navigation library, image loader or blanket R8 rules.
- dk-d5e272e1 remains wip pending its original physical edge-to-edge acceptance. Its open agent note is not treated as proof or an owner decision. FIT addresses the screen-ratio cause; hardware equivalence is not claimed. Existing open note/body are unchanged. dk-afb1348d remains deferred.

## Commands and results

All Gradle commands run with --offline and ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk (no local.properties). npm_config_offline=true and npm_config_audit=false enforced the no-download constraint for npm ci. Processes were checked before heavyweight builds.

- npm ci: exit 0, added 10 packages in 4s.
- node --test: exit 0; 155 tests, 155 pass, 0 fail/cancelled/skipped, 39136.23 ms.
- node src/android/icons/generate-icons.mjs --check: OK (exit 0).
- Baseline: .\android\gradlew.bat -p android --offline assembleRelease: BUILD SUCCESSFUL in 3m 13s; 50 actionable tasks: 50 executed. HEAD a96f5d2 differs from 7ac5538 only by the plan; baseline app/build inputs are identical. Saved before implementation build as .tmp/baseline-7ac5538.apk, 1,074,018 bytes.
- .\android\gradlew.bat -p android --offline ktlintFormat initially failed with:

```text
CameraStateHolderTest.kt:6:1 Wildcard import (cannot be auto-corrected)
PairingStateHolderTest.kt:4:1 Wildcard import (cannot be auto-corrected)
Execution failed for task ':app:ktlintTestSourceSetFormat'.
BUILD FAILED in 2m 12s
```

Replaced the two wildcard imports with explicit assertions. The same command then passed: BUILD SUCCESSFUL in 59s; 10 actionable tasks: 4 executed, 6 up-to-date.

First requested Android gate failed during debug/release compilation (BUILD FAILED in 5m 17s; 79 actionable tasks: 76 executed, 3 up-to-date). Diagnostics:

```text
Type checking has run into a recursive problem. Easiest workaround: specify the types of your declarations explicitly.
Unresolved reference 'service'.
'onRequestPermissionsResult' overrides nothing. Potential signatures for overriding:
fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Unit
fun onRequestPermissionsResult(p0: Int, p1: Array<(out) String!>, p2: IntArray, p3: Int): Unit
Argument type mismatch: actual type is 'Array<CapturedType(out String)>', but 'Array<String>' was expected.
```

Fixed explicit link/service types and the ComponentActivity callback signature. Also corrected camera-canvas system-bar contrast and reserved status height for large text.

Final formatting pass: BUILD SUCCESSFUL in 1m; 10 actionable tasks: 2 executed, 8 up-to-date.

Final requested Android gate:

```powershell
.\android\gradlew.bat -p android --offline ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest lintRelease
```

Exit 0: BUILD SUCCESSFUL in 6m 27s; 118 actionable tasks: 43 executed, 75 up-to-date. Debug/release APKs and release AAB produced. 116 JVM tests, zero failures/errors/skips. Release lint: 0 errors, 19 warnings. Merged debug and release manifests both retain minSdkVersion 24; dependency/manifest checks passed. Existing inlined sensitive-clipboard string, pinned CameraX versions, platform/icon guidance and native-symbol stripping warnings remain; no suppressions or SDK override were added.

## APK cost

Equivalent unsigned R8/resource-shrunk release APKs on the same machine/toolchain:

| Build | Bytes |
|---|---:|
| 7ac5538 app inputs (pre-redesign) | 1,074,018 |
| v1.3.0 Compose implementation | 2,121,596 |
| Increase | 1,047,578 (97.54%, about 1.00 MiB) |

Growth is principally DEX/resources, consistent with Compose/Material and aligned AndroidX dependencies. Largest compressed entry: classes.dex 1,313,140 bytes; resources.arsc 247,360 bytes. No extended icon pack, fonts or release-tool libraries were introduced into the app; tooling stays debug-only.

Docket: implementation dk-52c39ab4, PreviewFeed extraction dk-6839f9b8 and configuration handling dk-0cec3e0e completed after the gate. dk-d5e272e1 remains wip pending phone acceptance; dk-afb1348d remains todo. Cleanup/check results:

- .\android\gradlew.bat -p android --stop: exit 0; "Stopping Daemon(s)" / "1 Daemon stopped".
- dk check: exit 0; 41 item file(s), 0 error(s), 2 warning(s). Both warnings predate this work:

```text
warning: dk-4e20c7d1.md: status wip but no claim in this clone
warning: dk-d5e272e1.md: status wip but no claim in this clone
```

- git diff --check: exit 0, no output.
- Final process inspection found no remaining Gradle/worker/test process started by this task. Existing owner/orchestrator processes were left alone. No server/editor/device process was started.
- No device or long-suite checks run, as instructed. Fixture previews were added and compiled; they were not rendered in an editor/device.

## Phone acceptance

Not run, as instructed. The Compose acceptance checklist in ../AGENT-EYE.md covers full-frame edges/orientation; rotation during a shot; stable preview during ticks; live/stopped lens selection; permission denial and delayed returns; leading-zero Copy/renewal; Settings/back and screen-off session survival; explicit updater actions; both themes, large text, cutouts and navigation modes; secure offline privacy.

## Changed implementation files

- `.editorconfig`
- `android/app/build.gradle.kts`
- `android/app/src/main/java/local/agenteye/service/CaptureService.kt`
- `android/app/src/main/java/local/agenteye/service/CaptureSnapshot.kt`
- `android/app/src/main/java/local/agenteye/ui/CameraScreen.kt`
- `android/app/src/main/java/local/agenteye/ui/MainActivity.kt`
- `android/app/src/main/java/local/agenteye/ui/PairingPanel.kt`
- `android/app/src/main/java/local/agenteye/ui/PrivacyPolicyDialog.kt`
- `android/app/src/main/java/local/agenteye/ui/capture/CameraControls.kt`
- `android/app/src/main/java/local/agenteye/ui/capture/CameraScreen.kt`
- `android/app/src/main/java/local/agenteye/ui/capture/CameraStateHolder.kt`
- `android/app/src/main/java/local/agenteye/ui/pairing/PairingCard.kt`
- `android/app/src/main/java/local/agenteye/ui/pairing/PairingClipboard.kt`
- `android/app/src/main/java/local/agenteye/ui/pairing/PairingCopy.kt`
- `android/app/src/main/java/local/agenteye/ui/pairing/PairingStateHolder.kt`
- `android/app/src/main/java/local/agenteye/ui/preview/CameraPreview.kt`
- `android/app/src/main/java/local/agenteye/ui/preview/PreviewFeed.kt`
- `android/app/src/main/java/local/agenteye/ui/privacy/PrivacyPolicyDialog.kt`
- `android/app/src/main/java/local/agenteye/ui/settings/SettingsActivity.kt`
- `android/app/src/main/java/local/agenteye/ui/settings/SettingsScreen.kt`
- `android/app/src/main/java/local/agenteye/ui/theme/Theme.kt`
- `android/app/src/main/java/local/agenteye/ui/update/UpdatePanel.kt`
- `android/app/src/main/res/values-night/themes.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/test/java/local/agenteye/ui/capture/CameraStateHolderTest.kt`
- `android/app/src/test/java/local/agenteye/ui/pairing/PairingCopyTest.kt`
- `android/app/src/test/java/local/agenteye/ui/pairing/PairingStateHolderTest.kt`
- `android/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `android/version.properties`

Living documentation updated: CLAUDE.md, STRUCTURE.md, docs/AGENT-EYE.md, docs/ARCHITECTURE.md, docs/DECISIONS.md, docs/FEATURES.md and docs/GOTCHAS.md. This verification record and the three completed Docket items accompany the implementation.
