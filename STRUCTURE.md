# Agent Eye structure

## Layout

Android dependency pins live in `android/gradle/libs.versions.toml`; Compose tooling is debug-only.

- `android/` — native Android camera app and its official Gradle wrapper.
- `src/camera/` — Node PC client for fetching and managing transient photographs.
- `src/android/releases/` — public signing certificate pin and release responsibilities below.
- `src/android/releases/artifacts/` — release contract, manifests, hashes and artifact staging.
- `src/android/releases/cli/` — side-effect-free argument parsing and CI/owner command dispatch.
- `src/android/releases/github/` — GitHub release assets, history, draft lookup by tag (`releaseByTag`; the tags endpoint hides drafts) and Actions provenance.
- `src/android/releases/local/` — owner transaction ordering, checkout, verification, retry cache and lock.
- `src/android/signing/` — signing responsibilities below; imports only `shared/` and `tooling/`, never `releases/` (guarded by `guards/import-boundaries.test.mjs`).
- `src/android/shared/` — leaf modules: `assert.mjs` (`requireThat`, `hex`), `release-identity.mjs` (application id, SDK, version shape, artifact identity), `signing-policy.mjs` (`PASS_ENV`, certificate pin check, unsigned-archive check), `digest.mjs`. Imports only Node built-ins and itself (guarded).
- `src/android/signing/artifacts/` — APK (`apk-signing.mjs`) and AAB (`bundle-signing.mjs`) inspection, signing and verification.
- `src/android/signing/vault/` — 1Password DesktopAuth adapter (`vault.mjs`, documents the `error.stage` contract), vault creation (`vault-setup.mjs`), config lookup (`signing-config.mjs`: locates `signing.local.json`, checkout then main worktree), repair (`vault-maintenance.mjs`) and the default account (`owner-account.mjs`).
- `src/android/signing/keys/` — key custody: isolated key directories (`key-material.mjs`), generation and recovery (`key-provisioning.mjs`), keytool inspection (`key-inspection.mjs`: `keyCertificate`) and `provision-flow.mjs` (vault then key).
- `src/android/tooling/` — Android SDK/tool discovery, the Gradle `releaseTools` runner (`release-tools.mjs`) and bounded secret-redacting subprocess execution (`process.mjs`).
- `src/android/guards/` — repo guard tests: `workflows.test.mjs` (no bare `sdkmanager` in `.github/workflows/`), `window-policy.test.mjs` (Kotlin sources never re-add FLAG_SECURE/SecureOn) and `package-boundaries.test.mjs` (table-driven Kotlin package-dependency rules; add a rule per boundary in its table).
- `android/app/src/main/java/local/agenteye/selfupdate/` — user-initiated GitHub self-update: `contract/` (leaf release vocabulary), `release/` (policy, discovery), `transport/` (URL policy, bounded HTTPS), `download/` (transfer, verification), `storage/` (private files and records), `install/` (PackageInstaller sessions and recovery); details below.
- `android/app/src/main/java/local/agenteye/json/` — neutral strict JSON: `StrictJson` parser/writer and the typed `JsonObject` view (one decode error, `StrictJson.Invalid`).
- `android/app/src/main/java/local/agenteye/security/` — session token, `PairingCodes` state machine and `PairHandler` (the `POST /pair` body checks and code-for-token exchange, returning a sealed `PairOutcome` that `StillServer` maps to HTTP).
- `android/app/src/main/java/local/agenteye/ui/` — `MainActivity` (lifecycle, service link, preview wiring) and `CaptureServiceLink` (binding and updater install preflight); the rest lives in the responsibility folders below.
- `android/app/src/main/java/local/agenteye/ui/capture/` — capture screen: `CameraScreen` shell, `CameraBody` content-measured preview/panel placement, unified `CameraPanel` and `CameraControls` (Compose), pure `CameraLayout` geometry (JVM-tested by `CameraLayoutTest`), `CaptureScreenModel` (the one owner of `CameraState`/`PairingState` and the 1 s countdown), `SessionStarter` (permission and Start flow), pure `CameraStateHolder` (snapshot to display state) and `CameraStatus` (status enum resolved from string resources).
- `android/app/src/main/java/local/agenteye/ui/preview/` — `CameraPreview` (stable AndroidView), `RoundedPreviewFrame` (fail-safe clipping to CameraX's transformed FIT implementation bounds) and `PreviewFeed`, a non-Compose geometry feed (display and layout listeners pushing rotation and ViewPort to the service while started).
- `android/app/src/main/java/local/agenteye/ui/pairing/` — `PairingCard`, pure `PairingState` (countdown), `PairingCopy` (tap-time copy policy) and the sensitive `PairingClipboard`.
- `android/app/src/main/java/local/agenteye/ui/privacy/` — local privacy-policy dialog.
- `android/app/src/main/java/local/agenteye/ui/theme/` — `Theme` provides the static amber Material 3 light/dark theme (no dynamic color); `DesignTokens` owns the shared 16dp corner-radius, shape, spacing and elevation tokens, and `SettingsTypography` owns the Settings card text roles.
- `android/app/src/main/java/local/agenteye/ui/settings/` — `SettingsActivity` (updater lifecycle and install handoff) and `SettingsScreen` (update panel, usage guidance and privacy-policy entry).
- `android/app/src/main/java/local/agenteye/ui/update/` — `UpdatePanel` view, its pure `UpdatePresenter`, `UpdateController` and `UpdateLifecycle` (the Activities' resume/pause attach and detach; `Context.updates` is the single runtime accessor).
- `android/app/src/main/java/local/agenteye/camera/` — CameraX session, capture geometry (crop maths), JPEG encoding; `CapturedFrame` (the captured JPEG value, consumed by `http`); `CaptureError` (camera failures only: camera_unavailable, capture_failed); `LensSelection` (pure, JVM-tested lens policy), `LensPreferences` (`camera`/`lens` SharedPreferences factory) and `LensCameraSelector` (CameraX adapter and provider discovery; the experimental external-lens opt-in lives only here).
- `android/app/src/main/java/local/agenteye/http/` — bounded request parser and `StillServer` routes.
- `android/app/src/main/java/local/agenteye/network/` — Wi-Fi/LAN binding and pure subnet admission.
- `android/app/src/main/java/local/agenteye/service/` — `CaptureService` session coordination, `SessionError` (the closed session-wide failure set, wrapping camera failures), credential-free immutable `CaptureSnapshot` (carrying a typed `SessionError?`), and `SessionPower` locks.
- `src/android/icons/` — canonical `agent-eye.svg`, `icon-assets.mjs` (restricted SVG to VectorDrawable plus sharp rendering), `generate-icons.mjs` (standard `runScript` CLI: writes by default, `--dry-run`, `--check` compares XML bytes and PNG pixels within 2/255), `icon-assets.test.mjs` and `play-store-512.png`. Generated, not hand-edited: `android/app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, `mipmap-anydpi-v26` and `-v33` (adds monochrome) and legacy `mipmap-*dpi` PNGs for API 24-25. `ic_camera_notification.xml` is unchanged.
- `src/android/releases/artifacts/fixtures/update-schema1/` — shared schema-1 update fixtures generated by `update-fixtures.mjs` from the real publisher; a Node test proves they match and the JVM tests consume them.
- `src/script-run/` — `script-run.mjs`, the standard script wrapper (repo-root resolution, per-run redacted log, OK/FAILED line, v1.1.0). Deliberately ONE dependency-free file: the canonical copy is copied verbatim to other repos and extended through options only. Holds its tests and the repo-wide all-entry-points-from-tmpdir test.
- `scripts/release/` — owner-run PowerShell entry points (`provision.ps1`, `sign-release.ps1`, `vault-repair.ps1`), the verbatim harness template `owner-run.ps1` (v1.0.0), repo specifics `agent-eye.ps1` (CLI path, `Install-AgentEyeDependencies` running `npm ci` when `node_modules` is missing, CLI pass-through, final line pointing at the CLI log) and the dry-run tests (`release-scripts.test.ps1`, run by `release-scripts.test.mjs` under pwsh/powershell). They call `cli.mjs` and hold no release logic.
- `.github/workflows/` — read-only verification/builds and separately privileged draft creation.
- `docs/ANDROID-DISTRIBUTION.md`, `docs/PRIVACY.md`, `docs/PLAY-LISTING.md` — owner distribution, bundled privacy and future Play checklist.
- `docs/AGENT-EYE.md` — build, operation, API and physical acceptance runbook.
- `docs/ARCHITECTURE.md` — runtime and release flow boundaries.
- `docs/DECISIONS.md` — toolchain and product decisions with rationale.
- `docs/FEATURES.md` — implemented capabilities and verification status.
- `docs/GOTCHAS.md` — operational traps and recovery constraints.
- `docs/plans/` — implementation plans (`agent-eye-app.md` is the original specification; its superseded-naming note applies).
- `docs/items/` — Docket work items, edited through `dk` only.
- `.tmp/` — ignored build inputs, verification artifacts and transient snapshots.

The Kotlin package root is `android/app/src/main/java/local/agenteye/`.
`ui` owns presentation, the Start permission flow and the display state (`ui/capture/`); `service` owns session coordination, `CaptureSnapshot` (the UI read model) and
power locks; `camera` owns CameraX and JPEG encoding; `network` owns Wi-Fi selection and
pure subnet admission; `http` owns bounded parsing and serving; `security` owns tokens and the pairing-code state machine (`PairingCodes`); `ui/pairing` presents pairing. `camera` owns `CaptureError` (camera failures) and `service` owns `SessionError`, the closed failure set carried by the snapshot and mapped to the `/status` wire code only in `CaptureService.statusJson`. `POST /pair` is the only route with a request body: `StillServer` supplies the route policy (`bodyLimit`) to the generic `HttpRequest.read` and routes to `PairHandler`, which owns the body shape (ASCII, `{"code":"dddddd"}`) and returns a `PairOutcome` (`StillServer` maps it to HTTP status and body).
Local JVM tests mirror these packages under `app/src/test/java/`.

Self-update (user-initiated, outbound) lives under `selfupdate/`: `UpdateRuntime` (wiring, lazily
owned by `AgentEyeApplication`), `UpdateCoordinator` (state machine), `UpdateState.kt` (`UpdatePhase`, `UpdateState`), `Candidate.kt` (frozen release candidate) and `UpdateFailure.kt` (`UpdateProblem`, `UpdateFailure`, `UpdateCancelled`);
`contract/` (`HttpPorts.kt`: the `HttpExchange`, `HttpConnector` and `Backoff` ports shared by transport and storage; `ReleaseContract`: repository, asset names, tag/version/SHA-256 shapes; `Integrity`: the one SHA-256 and hex implementation used by release, download and install; imports nothing but the JDK. The shared updater vocabulary (`Candidate.kt`, `UpdateFailure.kt`, `UpdateState.kt`) is now split by file but still in the root package, so the subpackages import the root package and the cycle remains; package migration is tracked as debt in dk-7948b352);
`release/` (`ReleaseManifest` decode and SHA256SUMS parse via `JsonObject`,
`ReleasePolicy`, `GitHubReleaseSource`); `transport/` (`UpdateUrlPolicy`, `UpdateHttpClient`,
`HttpsConnector`); `download/` (`ApkDownloader`, pure `ApkVerifier`, `AndroidApkInspector`);
`storage/` (`UpdateStore`: paths and cleanup; `RecordFiles`: atomic schema-tagged record read/write; `BackoffStore`: the persisted `Backoff` in `backoff.json`; `RecordCodec`: record <-> map); `install/` (`PackageInstallerGateway` policy, `AndroidInstallerPort`,
pure `InstallRecovery`, `InstallPrompter` (Activity-facing confirmation/permission intents and launcher attach/detach), `InstallResultReceiver`). `ui/settings/` and `ui/update/` (Compose panel, pure presenter, `UpdateController`: action dispatch, stop-for-install, live Start-blocked guard) present updates. Tests mirror under `app/src/test/java/local/agenteye/selfupdate/**`
and `ui/update/`, with fakes in `selfupdate/UpdateFakes.kt`. The signer pin is not hand-written in
Kotlin: Gradle task `generateUpdatePinAsset` packages `signing-certificate.json` as `update-signer.txt`.

The PC CLI is `snapshot.mjs` (argument parsing and token resolution in `snapshot-args.mjs`); `client.mjs` owns the bounded HTTP transport, protocol and `pair`, with `destination.mjs` (private-LAN IPv4 policy) and `jpeg.mjs` (structural JPEG check) split out;
`agent-eye-dir.mjs` owns private-directory validation and atomic replacement for both stores; `snapshot-store.mjs` keeps snapshot naming, durability and cleanup; `token-store.mjs` owns the owner-only paired-token file. Tests are adjacent `.test.mjs` files (`client`, `destination`, `jpeg`, `token-store`, `snapshot-args` incl. log redaction, `snapshot-store`, `agent-eye-dir`).
There is no device-control integration in this repository.

```text
src/
├── camera/          PC client (+ destination, jpeg), snapshot and token stores, agent-eye-dir (shared private dir + atomic write)
├── script-run/      standard script wrapper (copied verbatim across repos)
└── android/
    ├── icons/           canonical SVG, generator, play-store-512.png
    ├── releases/
    │   ├── artifacts/   contract, manifests and staging
    │   │   └── fixtures/  shared update-schema1 fixtures (Node and JVM tests)
    │   ├── cli/         CLI parsing and CI/owner commands
    │   ├── github/      release API, draft lookup and Actions provenance
    │   ├── local/       owner transaction, local checks/cache/lock
    │   ├── smoke/       signing-smoke.mjs, the disposable-key integration check
    │   └── signing-certificate.json
    ├── signing/
    │   ├── artifacts/   APK and AAB signing
    │   ├── vault/       DesktopAuth adapter, vault setup, config lookup and repair
    │   └── keys/        key material, inspection, provisioning, provision flow
    ├── shared/          leaf modules used by releases/ and signing/ (assert, release identity, signing policy, digest)
    ├── guards/          repo guard tests (workflows, window policy, package and import boundaries)
    └── tooling/         Android tools, Gradle releaseTools runner, subprocess execution
scripts/release/        owner-run .ps1 pass-throughs, harness template, dry-run tests
android/                Gradle project (Kotlin app, version.properties, signing.example.json)
.github/workflows/      ci.yml (verification), android-release.yml (unsigned artifacts, draft)
docs/                   living docs, runbooks, plans/, items/
```

```text
android/app/src/main/java/local/agenteye/
├── AgentEyeApplication.kt   owns the lazy UpdateRuntime
├── camera/          CameraX session, CaptureLimits (port, still spacing and timeouts shared with http and service), capture geometry, JPEG encoding, CapturedFrame (captured JPEG value), CaptureError (camera failures), LensSelection (policy), LensPreferences, LensCameraSelector (CameraX adapter, experimental opt-in)
├── http/            HttpRequest (bounded parser), StillServer (routes, POST /pair)
├── json/            StrictJson, JsonObject
├── network/         LAN binding, subnet policy
├── security/        SessionToken, PairingCodes, PairHandler, PairOutcome
├── selfupdate/
│   ├── contract/    ReleaseContract, Integrity (leaf)
│   ├── release/     ReleaseManifest, ReleasePolicy, GitHubReleaseSource
│   ├── transport/   UpdateUrlPolicy, UpdateHttpClient, HttpsConnector
│   ├── download/    ApkDownloader, ApkVerifier, AndroidApkInspector
│   ├── storage/     UpdateStore, RecordCodec
│   └── install/     PackageInstaller gateway, recovery, result receiver
├── service/         CaptureService, CaptureSnapshot (UI read model), SessionPower
└── ui/              MainActivity, CaptureServiceLink; capture/, preview/, pairing/, settings/, update/, privacy/, theme/
```

Release `cli/cli.mjs` parses flags without loading execution adapters on dry-run;
`cli/commands.mjs` dispatches to `cli/ci-commands.mjs` (tag validation, staging and drafts)
or `cli/owner-commands.mjs` (setup, provisioning, recovery and signing).
`local/release-sign.mjs` owns transaction ordering; `local/local-release.mjs` assembles
`checkout.mjs`, `draft-verification.mjs`, `retry-cache.mjs` (resume only), `signed-bundle.mjs` (sign and verify) and `release-lock.mjs`.
`artifacts/contract.mjs` owns release names, phase-specific asset sets and the manifest schema.
APK and AAB verification remain separate under `signing/artifacts/`; `tooling/android-tools.mjs`
owns SDK discovery and the build-tools version used by both workflows and signing;
`guards/workflows.test.mjs` guards both workflows against PATH-based `sdkmanager` calls; `guards/window-policy.test.mjs` guards the screenshot policy.
`signing/vault/vault-setup.mjs` creates the dedicated signing vault and writes `android/signing.local.json` in the checkout only (`setup-vault`); `signing/vault/signing-config.mjs` owns `CONFIG_FILE` and `resolveSigningConfigPath`, which every read-only lookup (release-sign, inspect, cleanup, key commands) uses to find it in the checkout, else the main worktree; `signing/keys/provision-flow.mjs` runs `provision` = vault then key; the default 1Password account lives alone in `signing/vault/owner-account.mjs`. `cli/command-names.mjs` is the single owner/CI command set. Every entry point (`cli.mjs`, `signing-smoke.mjs`, `snapshot.mjs`) runs inside `runScript`, so it works from any directory and logs to `.tmp/logs/` or, for owner commands, `.tmp/owner-runs/`.
`releases/smoke/signing-smoke.mjs` is an explicitly invoked disposable-key integration check, not a unit test.
`android/version.properties` is the version source; only the public certificate pin is tracked.

Structural review: 2026-10-25 (findings applied 2026-10-26: typed errors, status resources, ui/capture state owner). No code file exceeds 800 lines.
Last full review: 2026-10-26

## Structural debt

From the 2026-10-26 baseline review: componentization and abstraction lenses for each area, a
topology lens, and a Sol cross-model pass. Verdict PASS, no P0. The Docket items hold the details.

- **Dependency cycles (P1):** `selfupdate` root <-> subpackages through the
  shared vocabulary (dk-7948b352).
- **Wide coordinators:** `CaptureService` (dk-e3c7da36), the release CLI command spread (dk-0e25e219).
- **Smaller seams (P2/P3):** dk-8e8110f3.
- `src/android/releases/github/` is a single-module folder on purpose.
- `tooling/android-tools.mjs` imports `signing/artifacts/*` while `signing/` imports `tooling/`: a cycle; move it to a composition layer and add a `tooling` boundary rule when nearby.
