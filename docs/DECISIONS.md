# Decisions

- **2026-10-25 — General-purpose, local-only.** App Agent Eye, package `local.agenteye`,
  project `android/`, client `src/camera/`. No consumer-specific camera or control logic.
  `AGENT_EYE_TOKEN` and `.tmp/agent-eye/` replace the historical TV-specific names too.
- **Pinned toolchain.** AGP 8.13.2, Gradle 8.13, Kotlin 2.3.10, CameraX 1.5.3;
  min/compile/target SDK 24/36/36, Java and Kotlin bytecode 17, build JDK 21.
  Core KTX 1.16.0, lifecycle-service 2.8.1, JUnit 4.13.2. Node 22 as directed by the
  implementation brief (supersedes the plan's Node 24 preparation line).
  Gradle 8.13's published binary ZIP SHA-256 is pinned in the wrapper properties.
  Kotlin uses the typed `compilerOptions` DSL to retain JVM 17 compatibility on Kotlin 2.3.10.
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
  `OP_ACCOUNT_ID`, existing config, then the owner default in `signing/owner-account.mjs`.
