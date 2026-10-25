# Agent Eye structure

## Layout

- `android/` — native Android camera app and its official Gradle wrapper.
- `src/camera/` — Node PC client for fetching and managing transient photographs.
- `src/android/releases/` — public signing certificate pin and release responsibilities below.
- `src/android/releases/artifacts/` — release contract, manifests, hashes and artifact staging.
- `src/android/releases/cli/` — side-effect-free argument parsing and CI/owner command dispatch.
- `src/android/releases/github/` — GitHub release assets, history and Actions provenance.
- `src/android/releases/local/` — owner transaction ordering, checkout, verification, retry cache and lock.
- `src/android/signing/` — APK/AAB signing, disposable smoke, attended DesktopAuth and key isolation.
- `src/android/tooling/` — Android tool discovery and bounded, secret-redacting subprocess execution.
- `.github/workflows/` — read-only verification/builds and separately privileged draft creation.
- `docs/ANDROID-DISTRIBUTION.md`, `docs/PRIVACY.md`, `docs/PLAY-LISTING.md` — owner distribution, bundled privacy and future Play checklist.
- `docs/AGENT-EYE.md` — build, operation, API and physical acceptance runbook.
- `docs/ARCHITECTURE.md` — runtime and release flow boundaries.
- `docs/DECISIONS.md` — toolchain and product decisions with rationale.
- `docs/FEATURES.md` — implemented capabilities and verification status.
- `docs/GOTCHAS.md` — operational traps and recovery constraints.
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

```text
src/android/
├── releases/
│   ├── artifacts/   contract, manifests and staging
│   ├── cli/         CLI parsing and CI/owner commands
│   ├── github/      release API and Actions provenance
│   ├── local/       owner transaction and local checks/cache/lock
│   └── signing-certificate.json
├── signing/         artifact signing and attended key custody
└── tooling/         Android tools and subprocess execution
```

Release `cli/cli.mjs` parses flags without loading execution adapters on dry-run;
`cli/commands.mjs` dispatches CI and owner operations. `local/release-sign.mjs` owns
transaction ordering, while `local/local-release.mjs` assembles checkout checks and the
public retry cache. APK and AAB verification remain separate under `signing/`.
`signing-smoke.mjs` is an explicitly invoked disposable-key integration check, not a unit test.
`android/version.properties` is the version source; only the public certificate pin is tracked.

Structural review: 2026-10-25. No code file exceeds 800 lines.
