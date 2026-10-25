# Agent Eye structure

## Layout

- `android/` — native Android camera app and its official Gradle wrapper.
- `src/camera/` — Node PC client for fetching and managing transient photographs.
- `src/android/releases/` — release contract, artifact verification, GitHub transaction and owner CLI.
- `src/android/signing/` — attended DesktopAuth, key provisioning and Windows key isolation.
- `src/android/tooling/` — bounded, secret-redacting subprocess execution.
- `.github/workflows/` — read-only verification/builds and separately privileged draft creation.
- `docs/ANDROID-DISTRIBUTION.md`, `docs/PRIVACY.md`, `docs/PLAY-LISTING.md` — owner distribution, bundled privacy and future Play checklist.
- `docs/AGENT-EYE.md` — build, operation, API and physical acceptance runbook.
- `docs/plans/` — original implementation specification; its superseded-naming note applies.
- `docs/items/` — Docket work items, edited through `dk` only.
- `.tmp/` — ignored build inputs, verification artifacts and transient snapshots.

The Kotlin package root is `android/app/src/main/java/local/agenteye/`.
`ui` owns preview presentation and permissions; `service` owns session coordination and
power locks; `camera` owns CameraX and JPEG encoding; `network` owns Wi-Fi selection and
pure subnet admission; `http` owns bounded parsing and serving; `security` owns tokens.
Local JVM tests mirror these packages under `app/src/test/java/`.

The PC CLI is `snapshot.mjs`; `client.mjs` owns direct HTTP validation;
`snapshot-store.mjs` owns atomic replacement and cleanup. Tests are adjacent `.test.mjs` files.
There is no device-control integration in this repository.

Release `cli.mjs` parses flags without loading execution adapters on dry-run; `commands.mjs`
binds the adapters. `release-sign.mjs` owns transaction ordering, while `local-release.mjs`
owns checkout checks and the public retry cache. APK and AAB verification remain separate.
`signing-smoke.mjs` is an explicitly invoked disposable-key integration check, not a unit test.
`android/version.properties` is the version source; only the public certificate pin is tracked.

Structural review: 2026-10-25. No code file exceeds 800 lines.
