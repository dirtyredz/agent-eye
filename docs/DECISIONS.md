# Decisions

- **2026-10-26 ? Compose Material 3, whole-frame FIT (1.3.0 / 5).** Follow Command Deck's static amber system light/dark theme and default typography; no dynamic color, navigation framework, extended icon pack or custom fonts. Keep two Activities and service-owned capture. ComponentActivity propagates handled configuration changes to Compose; PreviewFeed owns started-only display/layout listeners. Pure typed capture/pairing presentation keeps credentials out of saved state. Retain shared viewport ownership pending dk-afb1348d.
  Owner section 8 overrides the plan's full-bleed assumption: FIT_CENTER produces ViewPort.FIT, preserving the shared camera frame instead of cropping to screen aspect. Stable bottom/trailing control bars keep the frame visible, with secondary content scrolling at large text sizes. CameraSession.attach and JPEG encoding are unchanged. Front preview remains mirrored and front JPEG unmirrored, as before; matching refers to field of view. Measured unsigned minified APK cost: 1,074,018 ? 2,121,596 bytes (+1,047,578; +97.54%). R8/resource shrinking remain enabled; no blanket keep rules.


- **2026-10-26 — Split Settings from capture controls; persist logical lens direction (1.2.0 / 4).**
  A separate non-exported SettingsActivity keeps update/privacy/guidance out of the capture screen.
  It uses the same in-place configChanges policy and a scrolling inset-aware layout. Updater
  ownership follows resume/pause, so an outgoing Activity cannot detach the incoming owner's
  callbacks. Main still reconciles install state locally; network actions exist only in Settings.
  LensSelection persists BACK/FRONT/EXTERNAL in private preferences, not unstable camera IDs;
  unavailable preferences fall back to back, or the first available direction if back is absent.
  This cycles logical directions, not every physical wide/telephoto sensor. Live changes defer
  rebind until an active capture completes and keep the same server/session/rotation/viewport.
  Front stills are unmirrored so agents see readable text and real scene handedness; CameraX's
  default mirrored front preview is retained. No remote camera-switch API is added.
  Pairing Copy uses sensitive ClipData and only shows an app toast below API 33.

- **2026-10-26 — Handle rotation in place (`configChanges`), not by Activity recreation.** Recreating
  the Activity unbound and rebound the camera mid-capture (capture_failed after rotating). The Activity
  now handles orientation itself, re-lays out, and pushes rotation and a shared ViewPort to the service.
  Rejected: state-restoring recreation (still rebinds CameraX on every turn) and locking to portrait
  (stills of a wide TV crop the sides).
- **2026-10-26 — Signing config falls back to the main worktree; sign-release auto-installs deps.**
  Read-only lookups (release-sign, inspect, cleanup, key commands) resolve the git-ignored
  `android/signing.local.json` in the checkout, then in the main worktree, so a fresh tag checkout
  can sign; `setup-vault`/`provision` read and write the checkout-local file only. `sign-release`
  runs `npm ci` when `node_modules` is missing, including under `-DryRun`: the one documented
  exception to "dry run changes nothing", because the CLI cannot load without `@1password/sdk`
  (`provision -DryRun` only reports it). Rejected: copying the config into each checkout.
- **2026-10-26 — In-app updater: direct public GitHub, manual only.** The app queries the public
  `dirtyredz/agent-eye` releases API and exact-tag asset URLs directly. Rejected: a Worker/proxy
  (repo is public; it adds infrastructure and another data recipient). Checks happen only when
  the user taps; rejected: launch/background/timer checks (privacy, battery, surprise network use).
- **2026-10-26 — Verification boundary: pinned signer plus the platform.** `release.json` and
  `SHA256SUMS` are not independently signed, so they only detect inconsistency. Executable
  replacement is protected by the pinned signer (packaged from `signing-certificate.json`, no
  second hand-written pin), a PackageManager archive preflight and Android's installer check.
  Not provided: TUF-style freshness or protection against release suppression.
- **2026-10-26 — No partial-download resume.** Interrupted transfers restart from zero (APK is
  small; no Range protocol). Tradeoff: bandwidth versus the larger Range/validator protocol
  Command Deck carries.
- **2026-10-26 — Own strict JSON parser, not `org.json`.** Android's `org.json` silently keeps
  duplicate keys and differs from the JVM test implementation, so the contract could be tested on
  one parser and run on another. `StrictJson` rejects duplicates and is identical in app and tests.
- **2026-10-26 — Icon generated from a canonical SVG with pinned sharp.** `agent-eye.svg` is the
  source; VectorDrawables and legacy PNGs are generated (`sharp` 0.35.5 exact; reproducibility
  environment Node 22.20.0, libvips 8.18.7, librsvg 2.63.2) and CI runs `--check` (XML bytes,
  PNG pixels within 2/255). Rejected: hand-edited resources that drift from the Play asset.
- **2026-10-26 — Release choice: 1.1.0 / 2, then 1.1.1 / 3.** Pairing, insets, updater and icon ship
  as 1.1.0 / 2 (unpublished). 1.0.0 has no updater, so it is installed over manually once;
  in-app update acceptance needs a later signed release, normally 1.1.1 / 3.
- **2026-10-25 — General-purpose, local-only.** App Agent Eye, package `local.agenteye`,
  project `android/`, client `src/camera/`. No consumer-specific camera or control logic.
  `AGENT_EYE_TOKEN` and `.tmp/agent-eye/` replace the historical TV-specific names too.
- **Pinned toolchain.** AGP 8.13.2, Gradle 8.13, Kotlin 2.3.21, CameraX 1.5.3;
  min/compile/target SDK 24/36/36, Java and Kotlin bytecode 17, build JDK 21.
  Compose BOM 2026.03.01, Activity Compose 1.12.4, Core KTX 1.17.0, lifecycle-service 2.10.0, JUnit 4.13.2. Node 22 as directed by the
  implementation brief (supersedes the plan's Node 24 preparation line).
  Gradle 8.13's published binary ZIP SHA-256 is pinned in the wrapper properties.
  Kotlin uses the typed `compilerOptions` DSL to retain JVM 17 compatibility on Kotlin 2.3.21.
- **Dependencies.** Kotlin and required AndroidX/CameraX are permitted platform components.
  No cloud, advertising, analytics, crash reporting or unrelated runtime SDKs.
  Unsigned CI artifacts, attended local signing and future Play preparation supersede debug-only distribution.
- **Foreground service owns capture.** Activity recreation and preview loss do not own the
  still-capture lifecycle. No automatic restart after process death, force-stop or reboot.
- **IPv4 Wi-Fi only.** Exact LinkProperties subnet, private phone and peer addresses, no VPN,
  no wildcard listeners. IPv6, Ethernet, cellular, hotspots and ambiguous topology are excluded.
- **Session authentication.** 256 random bits per Start, required on both endpoints, cleared on
  Stop. No token in URLs, notifications or logs. The app blocks screenshots/recents thumbnails.
- **2026-10-26 — Short-code pairing (v1.1.0).** The 64-hex token was unreadable by hand and the app
  blocks screenshots (FLAG_SECURE, kept). The phone shows a six-digit code (two minutes, single use,
  5 attempts, per-client rate limit) that `POST /pair` exchanges for the token. The token is no longer
  shown at all (no "show token" toggle: less exposure, and `pair` makes it unnecessary);
  `AGENT_EYE_TOKEN`/`--token` remain for scripts. Rejected: QR code (needs a second device screenshot
  path), longer codes (typing cost), unlimited retries. Pairing is HTTP like the rest, so a LAN
  observer could sniff the token exactly as before.
- **Plain HTTP.** Simple local interoperability at the cost of LAN observers seeing frames and
  tokens. No port forwarding or remote proxies. Source rewriting prevents proof of physical LAN
  membership. Android INTERNET permission and cleartext policy do not implement a firewall.
- **Bounded custom server.** No runtime web-server dependency; narrow GET-only API, strict limits,
  no request bodies (except the 64-byte pairing body) or keep-alive, and JVM tests for observable behavior.
- **Transient images.** No phone archive or gallery writes, only on-demand JPEGs. One PC image,
  explicit cleanup after consumption; atomic replacement preserves the old image on failure.
- **2026-10-25 — Distribution boundary.** CI produces unsigned R8/resource-shrunk APK/AAB
  artifacts; only its draft-writing job has contents-write permission. Local DesktopAuth
  retains key custody. APK uses zipalign then apksigner; AAB uses jarsigner. No debug-key fallback.
- **Explicit versions.** `android/version.properties` owns semantic version and positive
  monotonically increasing versionCode; tags are exactly `v<versionName>`. No timestamp/run codes.
- **Formatting.** ktlint Gradle plugin 14.2.0 and engine 1.8.0; no baseline or ignored failures.
- **Key continuity.** One dedicated RSA-4096 key initially signs sideload APKs and AAB uploads.
  A different Play app-signing key cannot update GitHub installs. Decide cross-channel signing
  before Play enrollment; an upload-key reset never changes the established sideload signer.
- **Privacy source.** `docs/PRIVACY.md` is bundled at build time and displayed as local text.
  Plain HTTP LAN transfer is disclosed and must be assessed explicitly in future Data safety forms.
- **2026-10-25 — Standard script wrapper.** `src/script-run/script-run.mjs` is the canonical,
  dependency-free script wrapper (repo-root resolution, redacted per-run log, OK/FAILED line). It is
  copied VERBATIM into other repos and never edited per repo: variation goes through options
  (`kind`, `logDir`, `redaction`, `error.exitCode`, `log.dryRun`), and any API change bumps its
  version string (now 1.1.0). One runner only: `log.wrapRun(base)`; the log redacts regardless of
  whether `base` does. Rejected: a shared package (no cross-repo dependency mechanism) and a second
  built-in runner (two redaction paths).
- **2026-10-26 — Owner `.ps1` scripts are pass-throughs; the Node CLI owns release logic and the verdict.** Draft-ID lookup
  (release list lookup by tag, draft-only), `gradlew releaseTools` + `--stop`, and tag/ID validation live in the CLI
  (`github.draftId`, `local/release-tools.mjs`, `parse`); `release-sign --tag` resolves the draft and `--release-id` only
  overrides. The CLI's `runScript` log is authoritative. `.ps1` scripts keep a short wrapper log (harness template
  `owner-run.ps1` copied verbatim, v1.0.0; Agent Eye specifics in `agent-eye.ps1`), forward the CLI's exit code, never echo
  the CLI's verdict, and end with one line `OK|FAILED (exit N) log: <CLI log>` after a `Wrapper log:` line.
  Rejected: a second PowerShell verdict (two competing logs) and duplicating tag/ID regexes in PowerShell.
- **2026-10-25 — `provision` = vault then key.** One owner command creates the dedicated signing
  vault and `android/signing.local.json`, then generates the key (`signing/provision-flow.mjs`);
  `setup-vault` and `generate-key` remain for retries. Account precedence: `--account`,
  `OP_ACCOUNT_ID`, existing config, then the owner default in `signing/vault/owner-account.mjs`.
