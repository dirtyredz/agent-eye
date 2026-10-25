# Agent Eye structure

## Layout

- `android/` — native Android camera app and its official Gradle wrapper.
- `src/camera/` — Node PC client for fetching and managing transient photographs.
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

Structural review: 2026-10-25. No code file exceeds 800 lines.
