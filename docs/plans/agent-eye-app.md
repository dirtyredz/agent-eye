> **Superseded naming (2026-10-25):** written as "TV Eye" for tv-tuneup. This is now the standalone, general-purpose repo `agent-eye`: app name "Agent Eye", Android project `android/`, Kotlin package `local.agenteye`, PC client `src/camera/`, runbook `docs/AGENT-EYE.md`. Drop all tv-tuneup doc edits; nothing TV-specific belongs here.

## 1. Goal

Build **TV Eye**, a sideloaded Kotlin Android app that provides fresh JPEG photographs of the TV over the household LAN while the phone is plugged in with its screen off. A small Node client saves each photograph locally so the agent can inspect it before using the existing Samsung remote helper.

## 2. Placement

Use `android/tv-eye/` for the standalone Android project and `src/camera/` for PC tooling. The repository currently has no application code to extract; the scratch helper at `~/.tv-tuneup/tv.mjs` remains unchanged.

Proposed tree, with Kotlin package root `local.tveye`:

```text
android/tv-eye/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  gradle/wrapper/
    gradle-wrapper.jar
    gradle-wrapper.properties
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/local/tveye/
        ui/MainActivity.kt
        service/CaptureService.kt
        service/SessionPower.kt
        camera/CameraSession.kt
        camera/JpegEncoder.kt
        network/LanBinding.kt
        network/SubnetPolicy.kt
        http/StillServer.kt
        http/HttpRequest.kt
        security/SessionToken.kt
      res/
        layout/activity_main.xml
        values/strings.xml
        values/themes.xml
        drawable/ic_camera_notification.xml
    src/test/java/local/tveye/
      network/SubnetPolicyTest.kt
      http/HttpRequestTest.kt
      http/StillServerTest.kt

src/camera/
  snapshot.mjs
  client.mjs
  snapshot-store.mjs
  client.test.mjs
  snapshot-store.test.mjs

docs/TV-EYE.md
```

Responsibilities:

| File or group | Responsibility and placement rationale |
|---|---|
| Android Gradle files and wrapper | Reproducible standalone Android build; no global Gradle requirement. |
| `AndroidManifest.xml` | Permissions, launcher Activity, private camera foreground service, backup disabled. |
| `MainActivity.kt` and resources | Preview, Start/Stop, address, session token and actionable status. No networking or capture ownership. |
| `CaptureService.kt` | Session lifecycle and component coordination; owns the CameraX lifecycle independently of the Activity. |
| `SessionPower.kt` | Acquire and release session wake/Wi-Fi locks. Keeps power-management details out of service orchestration. |
| `CameraSession.kt` | Rear-camera selection, CameraX use cases, preview attachment and serialized still capture. |
| `JpegEncoder.kt` | Produce bounded, correctly oriented ordinary JPEG bytes in memory. |
| `LanBinding.kt` | Observe eligible Wi-Fi networks and obtain their actual IPv4 address and prefix. |
| `SubnetPolicy.kt` | Pure IPv4 admission logic, independent of Android APIs and directly unit-testable. |
| `StillServer.kt` | Listener, connection limits, endpoint routing and responses. |
| `HttpRequest.kt` | Strict, bounded HTTP request parsing. |
| `SessionToken.kt` | Generate and validate an unpredictable token for each serving session. |
| `snapshot.mjs` | CLI arguments, orchestration and exit codes. |
| `client.mjs` | Bounded HTTP requests and response validation. |
| `snapshot-store.mjs` | Atomic replacement and cleanup of transient snapshot files. |
| Tests | Colocated with their platform’s implementation; exercise policy and observable failure behavior. |
| `docs/TV-EYE.md` | Build, installation, operation, endpoint contract and device verification runbook. |

Also change `.gitignore`, `README.md`, `STRUCTURE.md`, and the living docs listed in section 6. Ignore Android build/cache directories, `local.properties`, and snapshot artifacts; preserve the existing `.tmp/` exclusion.

Add these exact responsibility bullets to `STRUCTURE.md`’s existing Layout:

- `android/tv-eye/` — native Android camera app and its Gradle wrapper
- `src/camera/` — PC client for fetching and managing transient TV photographs

Keep existing discovery, remote and docs bullets. Keep each code file below approximately 800 lines; split by responsibility if implementation approaches that limit.

**Tradeoff:** a small, deliberately limited HTTP server avoids another runtime library, but requires explicit parser limits and tests. It must not grow into a general web server.

## 3. Sequence

1. **Establish the build and permission shell.**  
   Use `minSdk 24`, `compileSdk 36`, `targetSdk 36`, application ID `local.tveye`. Pin AGP **8.13.2**, Gradle **8.13**, Kotlin **2.3.10**, and CameraX **1.5.3**. Run Gradle on the installed JDK 21; align Java/Kotlin bytecode targets at 17. Pin other required AndroidX dependencies to compatible stable versions.

   These are conservative version choices, not a request to track latest releases. Their compatibility is supported by the [AGP release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Gradle Java matrix](https://docs.gradle.org/current/userguide/compatibility.html), [Kotlin matrix](https://kotlinlang.org/docs/gradle-configure-project.html), and [CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera).

   Commit the complete official Gradle wrapper, including its JAR and distribution checksum. Bootstrap it using a checksum-verified temporary Gradle distribution if necessary; do not require a global installation.

2. **Implement subnet policy before opening sockets.**  
   Admit a connection only when both addresses are IPv4, the phone and peer are RFC1918 addresses, and the peer belongs to the phone’s actual interface prefix. Reject invalid prefixes, unspecified addresses, loopback, link-local, multicast, and subnet network/broadcast addresses. Never authorize from HTTP headers or a hostname.

   `LanBinding` selects a non-VPN Wi-Fi network with an unambiguous eligible address from `LinkProperties`; do not require internet validation. Bind specifically to that address on TCP **8765**, never `0.0.0.0` or `::`. Initial scope excludes IPv6, cellular, Ethernet and hotspot mode. Unknown or ambiguous topology fails closed.

   The documented `192.168.68.0/24` is an example and test fixture, not configuration.

3. **Implement the foreground capture session.**  
   Start from a visible Activity after granting `CAMERA`. Declare `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_CAMERA`; declare the service type as `camera`. Request notification permission where applicable, handling denial without treating it as camera authorization.

   Post an ongoing notification promptly, with a Stop action. Use a service-owned CameraX lifecycle; Activity recreation or screen-off must not stop capture. These choices follow Android’s [camera foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#camera).

   Start enables preview and serving. Stop closes sockets, cancels pending work, unbinds the camera, clears token/image bytes, releases locks and stops the service. Use `START_NOT_STICKY`; restarting after process death or reboot requires opening the app and pressing Start.

   Hold a partial wake lock during an active session. An API-appropriate Wi-Fi lock may help older devices, but must not be represented as a universal screen-off guarantee. Modern [Wi-Fi lock behavior](https://developer.android.com/reference/android/net/wifi/WifiManager#WIFI_MODE_FULL_HIGH_PERF) has additional restrictions.

4. **Implement fresh still capture and preview attachment.**  
   Use CameraX `Preview` and `ImageCapture`, rear camera, flash disabled, ordinary SDR JPEG. Aim for approximately 1920×1080 with a supported lower-resolution fallback; prioritize readable TV menus over maximum sensor resolution.

   Keep capture bound when the preview surface disappears. Attach/detach the preview without transferring camera ownership to the Activity. Normalize rotation into the returned JPEG, bound decoded dimensions, and always close `ImageProxy`. CameraX’s [in-memory capture callback](https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback) requires explicit image closure and exposes rotation separately.

   Capture only on demand; no video stream, polling capture loop, gallery writes or phone-side image archive. Allow one capture at a time and at most one capture start per second. Return an error rather than an old frame when capture fails.

5. **Expose the two HTTP endpoints.**

   | Request | Result |
   |---|---|
   | `GET /status` | JSON with protocol version, session/camera readiness, bound address, uptime, last successful capture time and a non-sensitive error code. |
   | `GET /shot.jpg` | A fresh `image/jpeg`, explicit length, `Cache-Control: no-store`, and capture timestamp/sequence headers. |

   Require `Authorization: Bearer …` on both endpoints. Generate a new token with at least 128 bits of randomness on each Start; show it in the app, never in URLs, logs or notifications. Authentication supplements the subnet check.

   Limit headers to 8 KiB, concurrent connections to four, capture duration to five seconds, total connection lifetime to ten seconds, and JPEG responses to 8 MiB. Reject request bodies, transfer encoding and unsupported methods. Close connections after responses; no keep-alive or pipelining. Define `400`, `401`, `404`, `405`, `429` and `503` behavior. Enforce wall-clock connection deadlines, including blocked writes.

   On Wi-Fi loss or address/prefix change, close the old listener and its connections immediately. An already-running session may rebind when an eligible Wi-Fi network returns; show the new address and never fall back to another interface.

   **Local-only claim:** the app initiates no outbound network connections and accepts only authenticated peers matching its Wi-Fi subnet. Android’s `INTERNET` permission is nevertheless required. Network-security config and `usesCleartextTraffic` are not destination firewalls, and raw sockets need not obey cleartext policy; do not claim either confines the app to the LAN. See [Android’s manifest documentation](https://developer.android.com/guide/topics/manifest/application-element.html#usesCleartextTraffic).

   **Tradeoff:** HTTP exposes photographs and the token to observers on the trusted LAN. A router/proxy that rewrites source addresses can also conceal an external origin. The application cannot prove physical LAN membership; do not configure port forwarding or remote proxies.

6. **Add the PC client.**  
   Support `status`, `shot`, `smoke`, and `clean` subcommands. Read the token from `TV_EYE_TOKEN`; take the displayed base URL as an argument.

   Accept only a literal RFC1918 IPv4 HTTP destination. Reject redirects, credentials in URLs and non-HTTP schemes; use direct Node networking without proxy configuration. Apply a ten-second deadline, bounded response sizes and MIME/JPEG validation.

   `shot` saves to repository-relative `.tmp/tv-eye/latest.jpg` using a temporary sibling and atomic replacement. Print the absolute path and capture metadata. On failure, exit nonzero, remove partial files, and never report an older image as a new success.

   `smoke` checks readiness, fetches a frame and reports its saved path. `clean` removes only files owned by this client. Retain one photograph, not timestamped history; agent workflows must clean it after consumption or in session cleanup.

   The existing remote helper has no repository callers to update. Document the sequence: snapshot → inspect → owner-authorized key press through the existing helper → fresh snapshot.

## 4. Tests

- **`SubnetPolicyTest.kt`:** same-subnet acceptance; adjacent-subnet rejection; RFC1918 boundaries; `/23`, `/24` and `/25` masks; signed-byte cases; invalid prefixes; network/broadcast addresses; public, loopback, link-local, IPv6 and IPv4-mapped IPv6 rejection.
- **`HttpRequestTest.kt`:** valid requests, truncated/oversized headers, malformed request lines, duplicate authorization headers, unsupported methods, bodies and transfer encoding.
- **`StillServerTest.kt`:** use fake capture and policy inputs to prove unauthorized requests never capture; correct MIME/length; capture timeout; rate/concurrency limits; fresh captures across requests; shutdown closes pending work. Keep this runnable as a local JVM test.
- **`client.test.mjs`:** valid JPEG, authentication failure, unavailable camera, redirect rejection, HTML disguised as JPEG, truncated/oversized responses and timeouts. Use a local fixture transport without weakening production destination validation.
- **`snapshot-store.test.mjs`:** replacement leaves no partial image; failures never claim success; cleanup cannot escape the designated directory.

Hardware-dependent CameraX behavior belongs in physical-device verification, not mocked unit-test claims.

## 5. Verification

No build or device tests were run for this plan. The repository has no existing build commands; document and run the following from its root.

**Preparation and build:**

- `java -version` — confirm JDK 21.
- `node --version` — use Node 24 LTS.
- `& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;35.0.0" "platform-tools"` — install missing packages; accept SDK licenses as required.
- Set `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk`, or provide an ignored `android/tv-eye/local.properties`.
- `.\android\tv-eye\gradlew.bat -p android/tv-eye :app:assembleDebug :app:testDebugUnitTest`
- `node --test src/camera/client.test.mjs src/camera/snapshot-store.test.mjs`

If the SDK installation lacks the `latest` directory alias, use its installed command-line-tools version directory.

**Sideload:**

- `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`
- `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell getprop ro.build.version.sdk` — must be at least 24.
- `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\android\tv-eye\app\build\outputs\apk\debug\app-debug.apk`

Select the intended device with `-s SERIAL` when multiple devices are connected. Open TV Eye, grant permissions and press Start.

**Fast gate:** Android assemble and JVM tests above, then `node src/camera/snapshot.mjs smoke --url http://PHONE_IP:8765` with `TV_EYE_TOKEN` set to the displayed token. Open the saved JPEG and confirm readable TV content; a successful HTTP response alone does not satisfy acceptance.

**Physical acceptance:**

- Phone shows live preview, Start/Stop, its current address and session token.
- PC saves a correctly oriented, readable photograph of the TV.
- Change visible TV content and fetch again; confirm freshness.
- Run a sequential approximately one-request-per-second loop for **15 minutes**, plugged in with the phone locked and screen off. Check failures, responsiveness and image freshness.
- Recreate the Activity and return to preview without losing the active serving session.
- Confirm Stop disables serving and releases camera/locks.
- Disconnect/reconnect Wi-Fi and verify old bindings close and the displayed address updates.
- Verify wrong-token denial and, where a routed test client is available, denial outside the exact subnet.
- Confirm the app works when the LAN has no WAN connection.

Screen-off while charging is the supported baseline. Doze can suspend networking and ignore wake locks when unplugged; a foreground service does not remove those restrictions. If battery operation is required, test forced idle and document any owner-selected battery exemption and OEM settings. Restore normal device state afterward. Follow Android’s [Doze testing guidance](https://developer.android.com/training/monitoring-device-state/doze-standby).

Before pushing, the implementer must run `dk check`.

## 6. Docs

- **`STRUCTURE.md`:** add the two Layout bullets, component ownership and completed structural-review date.
- **`docs/ARCHITECTURE.md`:** phone service → authenticated LAN HTTP → transient PC photograph; Samsung control remains a separate path.
- **`docs/DECISIONS.md`:** record SDK/toolchain pins, service-owned capture, IPv4 Wi-Fi scope, session authentication, plaintext HTTP tradeoff and no automatic background restart.
- **`docs/FEATURES.md`:** add TV Eye with its actual build/device-verification status.
- **`docs/GOTCHAS.md`:** qualify “remote control is blind”: the Samsung API remains blind, while TV Eye supplies external photographs. Add DHCP, Wi-Fi isolation, Doze/OEM behavior and HTTP limitations.
- **`docs/TV-EYE.md`:** commands, API contract, token setup, cleanup, device results and snapshot/remote workflow.
- **`README.md`:** link the TV Eye runbook.

The current no-code documentation matches the tree. Preserve the existing owner-present requirement for TV key presses; camera availability does not automatically change it.

## 7. Open questions / assumptions

- **Phone:** assume Android 7/API 24 or newer with a working rear camera. Record model and Android version during installation; older hardware requires a separate compatibility decision.
- **Dependencies:** interpret “no third-party SDKs” as permitting Kotlin and required AndroidX/CameraX components, while excluding analytics, advertising, cloud and unrelated runtime SDKs.
- **Network:** assume phone and PC share a trusted Wi-Fi subnet without client isolation. Routed VLAN, VPN, IPv6 and hotspot support are deferred.
- **Availability:** guarantee only behavior demonstrated on the actual phone. Forced stop, reboot, camera privacy controls and OEM process termination require explicit recovery; no unattended restart promise.
- **Future targeting:** target SDK 36 is deliberate for this sideloaded version. Before targeting 37+, add the runtime `ACCESS_LOCAL_NETWORK` permission flow, including denial handling for incoming TCP connections. [Android local-network permission requirements](https://developer.android.com/privacy-and-security/local-network-permission)
- **Work tracking:** `dk` was unavailable on this sandbox’s PATH, so work-item bodies could not be reviewed. Before implementation writes, restore access, run `docket guide`, inspect filtered items and claim the appropriate item. Do not infer facts or authorization from open notes.