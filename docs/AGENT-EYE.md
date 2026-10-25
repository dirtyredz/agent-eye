# Agent Eye

A sideloaded Android 7+ rear-camera app for a trusted private Wi-Fi LAN. It produces photographs
on demand; it does not stream, upload, archive or control the observed device. Keep the phone
plugged in for screen-off operation. Kotlin and required AndroidX components are the only runtime
dependencies beyond Android.

## Build and fast gate

Use JDK 21, Node 22 and Android SDK platform 36/build-tools 35.0.0. Dependencies are pinned in
`android/build.gradle.kts` and `android/app/build.gradle.kts`. Use the wrapper only.
The implementation environment prohibits downloading missing inputs without owner approval.

From the repository root in PowerShell:

```powershell
java -version
node --version
# SDK location is machine-local and must stay ignored.
$sdkPath = "$env:LOCALAPPDATA\Android\Sdk".Replace('\', '/')
Set-Content android/local.properties "sdk.dir=$sdkPath"
# Keep writable Gradle state inside this worktree. Seed caches from local inputs if offline.
$env:GRADLE_USER_HOME = "$PWD\.tmp\gradle-home"
Push-Location android
try { .\gradlew.bat --offline --no-daemon assembleDebug testDebugUnitTest }
finally { .\gradlew.bat --stop; Pop-Location }
node --test src/camera/client.test.mjs src/camera/snapshot-store.test.mjs
dk check
```

On a shell supporting the Unix wrapper, the equivalent Android gate is
`./gradlew --offline --no-daemon assembleDebug testDebugUnitTest` in `android/`.
Before starting Gradle, inspect `Get-CimInstance Win32_Process` for another Gradle/Java build;
only one heavyweight build runs at a time. Do not install system-wide tools. If required caches
are absent, obtain owner permission before removing `--offline`.

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`.
JVM reports: `android/app/build/reports/tests/testDebugUnitTest/index.html`.
The gate tests policy/server/client behavior; it cannot establish CameraX hardware behavior.

### Implementation verification record (2026-10-25)

- `java -version`: OpenJDK 21.0.12.1; `node --version`: v22.20.0.
- Wrapper generation from the already-installed Gradle 8.13 distribution, offline:
  `BUILD SUCCESSFUL in 3m 51s`, `1 actionable task: 1 executed`.
- `.\android\gradlew.bat -p android --offline --no-daemon assembleDebug testDebugUnitTest`:
  **failed before compilation**, `BUILD FAILED in 1m 7s`. Relevant output verbatim:

  ```text
  Plugin [id: 'org.jetbrains.kotlin.android', version: '2.3.10', apply: false] was not found in any of the following sources:

  - Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
  - Included Builds (No included builds contain this plugin)
  - Plugin Repositories (could not resolve plugin artifact 'org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:2.3.10')
  ```

- `node --test src/camera/client.test.mjs src/camera/snapshot-store.test.mjs`:
  `# tests 17`, `# pass 17`, `# fail 0`, `# skipped 0`.
- `dk check`: `1 item file(s), 0 error(s), 0 warning(s)`.
- Android compilation/JVM tests, APK generation and device checks: **not completed**. No phone
  is attached. The Android gate requires Kotlin 2.3.10 and CameraX 1.5.3, while local caches
  contain 2.3.21 and 1.6.2. Required pins were preserved; no dependency downloads were performed.
- The official wrapper scripts/JAR are present. Distribution SHA-256 pinning is **deferred**:
  the original ZIP/checksum is not cached, and network download permission is pending. Obtain
  the official Gradle 8.13 binary ZIP SHA-256 and add `distributionSha256Sum` to
  `android/gradle/wrapper/gradle-wrapper.properties` before declaring the build complete.

The Docket implementation item remains open until the pinned Android gate passes. No full test
tiers or device/emulator suites were run.

## Sideload and operate

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s SERIAL shell getprop ro.build.version.sdk
& $adb -s SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Record phone model, Android version and serial in your device acceptance record. SDK must be at
least 24. Open **Agent Eye**, grant camera access, choose notification permission, and tap
**Start** while the app is visible. The app shows the preview, camera status, current base URL
and token. Notification denial is handled separately from camera authorization. Stop from the
Activity or ongoing notification. Force-stop/reboot require reopening and pressing Start.

On the PC, use the displayed URL, not a guessed prefix. The fixture `192.168.68.0/24` is not
configuration. Set the token in the process environment, never in a command URL:

```powershell
$env:AGENT_EYE_TOKEN = 'COPY_CURRENT_TOKEN_FROM_APP'
node src/camera/snapshot.mjs status --url http://192.168.68.20:8765
node src/camera/snapshot.mjs smoke --url http://192.168.68.20:8765
node src/camera/snapshot.mjs shot --url http://192.168.68.20:8765
node src/camera/snapshot.mjs clean
Remove-Item Env:AGENT_EYE_TOKEN
```

`status` prints readiness; `shot` prints JSON containing absolute saved path, capture timestamp
and sequence. `smoke` requires ready status then fetches/saves one frame. Exit 0 means success;
errors exit nonzero with no success path. Inspect the returned JPEG for orientation and readability.
The client always uses direct HTTP, rejects redirects and noncanonical/nonprivate destinations,
and applies a ten-second deadline, 16 KiB status limit and 8 MiB JPEG limit.

Only `.tmp/agent-eye/latest.jpg` is retained. Saving uses a unique temporary sibling and atomic
replacement. On failure an older file may remain; never treat it as a new success. `clean` removes
only `latest.jpg` and the client's `.agent-eye-<UUID>.partial` names. It rejects directory links.
Clean after inspection or session teardown. No timestamped history is kept.

Consumer workflow: fresh snapshot → inspect → separately owner-authorized action in the
consumer's own tool → fresh snapshot. Camera access does not change authorization for actions.

## HTTP contract (protocol 1)

TCP port **8765**, bound only to the selected Wi-Fi IPv4 address. The socket peer and phone must
both be RFC1918 and usable hosts in the phone's actual prefix (neither network nor broadcast).
Unknown/ambiguous topology fails closed. No outbound connections, IPv6, VPN, cellular, Ethernet,
hotspot support, wildcard binding or internet-validation requirement.

Both routes require `Authorization: Bearer <session-token>` and `Host` using HTTP/1.1:

| Route | Successful response |
| --- | --- |
| `GET /status` | JSON: `protocolVersion`, `sessionReady`, `cameraReady`, `boundAddress`, `uptimeMs`, `lastCaptureTime` (epoch ms or null), `error` (code or null) |
| `GET /shot.jpg` | Fresh ordinary SDR `image/jpeg`, normalized pixel rotation, `Content-Length`, `Cache-Control: no-store`, `X-Capture-Timestamp` (epoch ms), `X-Capture-Sequence` (positive session sequence) |

The 256-bit random token changes each Start and is not returned by HTTP or put in notifications.
Requests have an 8 KiB header limit; no body, transfer encoding, duplicate headers, keep-alive
or pipelining. `Content-Length: 0` is accepted. Connections close after one response.

| Result | Meaning |
| --- | --- |
| 400 | Malformed/oversized/truncated headers, body or unsupported framing |
| 401 | Missing/wrong bearer token; no capture |
| 404 | Authenticated unknown route |
| 405 | Unsupported method; `Allow: GET` |
| 429 | Capture already in flight or less than one second since capture start; `Retry-After: 1` |
| 503 | Camera/capture failure, capture timeout, shutdown or invalid/oversized capture |

Four connection slots maximum. Excess or subnet-denied sockets close without an HTTP response
so admission never blocks on a write. All connections close at ten seconds even during blocked
writes. Capture timeout is five seconds; late images are discarded and proxies closed. One
capture at a time, at most one start/second, no cached-frame fallback. During Wi-Fi loss there
is no listener; reconnect can rebind an existing session and updates the displayed URL.

Error codes are nonsensitive: `wifi_unavailable`, `camera_unavailable`, `bind_failed`,
`capture_failed`, `session_start_failed`. HTTP errors use `http_<status>` or `unavailable`.

HTTP exposes tokens/photographs to LAN observers. Do not use port forwarding or remote proxies.
Source-rewriting routers/proxies can conceal external clients; this app cannot prove physical
LAN membership. Android's INTERNET permission is required; cleartext/network-security settings
are not destination firewalls.

## Physical acceptance — NOT RUN (no phone attached)

Do not claim screen-off support based solely on JVM/client tests. On the chosen phone:

1. Record model, API/version, Wi-Fi IP/prefix, charging state, battery settings and APK version.
2. Deny camera permission and verify Start explains the requirement without opening a listener.
   Grant camera access; separately deny notifications once and confirm Start/Stop remain usable.
3. Start and confirm preview, current address, token and status readiness. Run `smoke`, open the
   saved image and verify correct rotation and readable detail. Rotate phone and repeat.
4. Change the observed scene between two shots (at least one second apart). Inspect both before
   replacement; verify changed content, increasing timestamps/sequences, and no stale fallback.
5. Plug in, lock the phone and keep the screen off. Run the following sequential loop for 15
   minutes; inspect periodic frames before replacement, change the scene several times, and
   record failures, responsiveness, freshness and orientation:

   ```powershell
   $until = (Get-Date).AddMinutes(15)
   while ((Get-Date) -lt $until) {
     node src/camera/snapshot.mjs shot --url http://PHONE_IP:8765
     if ($LASTEXITCODE -ne 0) { Write-Warning 'Snapshot failed' }
     Start-Sleep -Seconds 1
   }
   ```

6. Unlock, rotate/recreate the Activity and return from Home. Verify the same active session
   survives, preview reattaches, and fresh shots work while preview is detached.
7. Stop via notification, then repeat using the Activity. Confirm HTTP cannot connect and the
   camera privacy indicator clears; another camera app can use the rear camera. Inspect
   `adb -s SERIAL shell dumpsys power` and `adb -s SERIAL shell dumpsys wifi` for released
   `AgentEye:session` locks. Start again and confirm the old token is rejected.
8. Disconnect Wi-Fi during a request. Verify old sockets close, status shows unavailable, and
   cellular receives no listener. Reconnect (change DHCP prefix/address if possible), verify
   the new URL and capture. Verify VPN interfaces are never selected and ambiguous Wi-Fi
   topology fails closed (an underlying eligible non-VPN Wi-Fi network may still be selected).
9. Set a wrong token: status and shot must return 401 and cause no capture. From a routed client
   outside the exact prefix, verify connection rejection where such a fixture is available.
10. Disconnect WAN while keeping Wi-Fi LAN intact. Repeat status/shot; internet validation must
    not be required. Confirm client isolation is disabled if clients cannot reach each other.
11. Toggle camera privacy/use another camera app and verify errors rather than an older frame.
    Force-stop and reboot: there must be no automatic camera/listener restart.
12. Run `clean`, clear the PC token, Stop the phone session, and record all results/limitations.

Optional battery-only acceptance is separate: forced idle can suspend networking despite wake
locks/FGS. If testing with `adb shell dumpsys deviceidle force-idle`, restore with
`adb shell dumpsys deviceidle unforce`. Record any owner-selected battery exemptions and OEM
settings; do not silently apply exemptions or promise unattended availability.
