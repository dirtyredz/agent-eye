# Agent Eye structure

## Layout

- `android/` — native Android camera app and its official Gradle wrapper.
- `src/camera/` — Node PC client for fetching and managing transient photographs.
- `src/android/releases/` — public signing certificate pin and release responsibilities below.
- `src/android/releases/artifacts/` — release contract, manifests, hashes and artifact staging.
- `src/android/releases/cli/` — side-effect-free argument parsing and CI/owner command dispatch.
- `src/android/releases/github/` — GitHub release assets, history, draft lookup by tag (`releaseByTag`; the tags endpoint hides drafts) and Actions provenance.
- `src/android/releases/local/` — owner transaction ordering, checkout, verification, retry cache and lock.
- `src/android/signing/` — signing responsibilities below plus `signing-smoke.mjs`, the explicitly invoked disposable-key integration check.
- `src/android/signing/artifacts/` — APK (`apk-signing.mjs`) and AAB (`bundle-signing.mjs`) inspection, signing and verification.
- `src/android/signing/vault/` — 1Password DesktopAuth adapter (`vault.mjs`, documents the `error.stage` contract), vault creation (`vault-setup.mjs`), repair (`vault-maintenance.mjs`) and the default account (`owner-account.mjs`).
- `src/android/signing/keys/` — key custody: isolated key directories (`key-material.mjs`), generation and recovery (`key-provisioning.mjs`) and `provision-flow.mjs` (vault then key).
- `src/android/tooling/` — Android SDK/tool discovery, bounded secret-redacting subprocess execution (`process.mjs`) and the workflow guard test (no bare `sdkmanager` in `.github/workflows/`).
- `src/script-run/` — `script-run.mjs`, the standard script wrapper (repo-root resolution, per-run redacted log, OK/FAILED line, v1.1.0). Deliberately ONE dependency-free file: the canonical copy is copied verbatim to other repos and extended through options only. Holds its tests and the repo-wide all-entry-points-from-tmpdir test.
- `scripts/release/` — owner-run PowerShell entry points (`provision.ps1`, `sign-release.ps1`, `vault-repair.ps1`), the verbatim harness template `owner-run.ps1` (v1.0.0), repo specifics `agent-eye.ps1` (CLI path, CLI pass-through, final line pointing at the CLI log) and the dry-run tests (`release-scripts.test.ps1`, run by `release-scripts.test.mjs` under pwsh/powershell). They call `cli.mjs` and hold no release logic.
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
src/
├── camera/          PC client and snapshot store
├── script-run/      standard script wrapper (copied verbatim across repos)
└── android/
    ├── releases/
    │   ├── artifacts/   contract, manifests and staging
    │   ├── cli/         CLI parsing and CI/owner commands
    │   ├── github/      release API, draft lookup and Actions provenance
    │   ├── local/       owner transaction, release tools, local checks/cache/lock
    │   └── signing-certificate.json
    ├── signing/
    │   ├── artifacts/   APK and AAB signing
    │   ├── vault/       DesktopAuth adapter, vault setup and repair
    │   ├── keys/        key material, provisioning, provision flow
    │   └── signing-smoke.mjs
    └── tooling/         Android tools and subprocess execution
scripts/release/        owner-run .ps1 pass-throughs, harness template, dry-run tests
android/                Gradle project (Kotlin app, version.properties, signing.example.json)
.github/workflows/      ci.yml (verification), android-release.yml (unsigned artifacts, draft)
docs/                   living docs, runbooks, plans/, items/
```

Release `cli/cli.mjs` parses flags without loading execution adapters on dry-run;
`cli/commands.mjs` dispatches to `cli/ci-commands.mjs` (tag validation, staging and drafts)
or `cli/owner-commands.mjs` (setup, provisioning, recovery and signing).
`local/release-sign.mjs` owns transaction ordering; `local/local-release.mjs` assembles
`checkout.mjs`, `draft-verification.mjs`, `retry-cache.mjs` and `release-lock.mjs`.
`artifacts/contract.mjs` owns phase-specific asset sets and shared unsigned-JAR checks.
APK and AAB verification remain separate under `signing/artifacts/`; `tooling/android-tools.mjs`
owns SDK discovery and the build-tools version used by both workflows and signing;
`tooling/workflows.test.mjs` guards both workflows against PATH-based `sdkmanager` calls.
`signing/vault/vault-setup.mjs` creates the dedicated signing vault and writes `android/signing.local.json` (`setup-vault`); `signing/keys/provision-flow.mjs` runs `provision` = vault then key; the default 1Password account lives alone in `signing/vault/owner-account.mjs`. `cli/command-names.mjs` is the single owner/CI command set. Every entry point (`cli.mjs`, `signing-smoke.mjs`, `snapshot.mjs`) runs inside `runScript`, so it works from any directory and logs to `.tmp/logs/` or, for owner commands, `.tmp/owner-runs/`.
`signing-smoke.mjs` is an explicitly invoked disposable-key integration check, not a unit test.
`android/version.properties` is the version source; only the public certificate pin is tracked.

Structural review: 2026-10-25. No code file exceeds 800 lines.
