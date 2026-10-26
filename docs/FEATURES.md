# Features and verification status

v1.2.0 / 4 adds available-lens switching on the camera screen (back/front/external logical
directions, saved locally across restarts), sensitive clipboard Copy for the active pairing
code, and a separate Settings screen for updates, privacy and operating guidance. Switching
while stopped selects the next Start lens; switching live preserves the server, token and code.
A pending still finishes before the camera rebinds; requests during rebind may report unavailable.
Front stills are unmirrored; the front preview retains CameraX's familiar mirrored presentation.
JVM tests cover selection, fallback, persistence and clipboard eligibility/confirmation policy.
Local fast gate on 2026-10-26 passed offline: npm ci, 155 Node tests, icon check, ktlint,
debug/release APKs, release AAB, 108 JVM tests and release lint (0 errors, 36 warnings).
The final Android gate took 5m 2s; Gradle was stopped afterward.
Phone acceptance remains pending: both orientations, live/stopped switching, restart persistence,
front text orientation, Copy on API <33/33+, and Settings/install lifecycle.

Agent Eye implements a sideloaded Android camera foreground service with live preview,
Start/Stop and session credentials; fresh, bounded JPEG capture; exact-subnet Wi-Fi binding;
authenticated status and snapshot endpoints; and a Node CLI with pair, status, shot, smoke, unpair and clean.

v1.1.0 adds short-code pairing (six-digit, two-minute, single-use code with countdown and New code
button; `snapshot.mjs pair` stores the token owner-only) and system-bar/cutout inset handling so the
navigation bar no longer overlaps content. Phone-side behaviour of both is **not yet confirmed on
a device**; the JVM and Node tests cover the state machine, server route and client/storage.

v1.1.0 (versionCode 2, unpublished) also adds a user-initiated in-app updater (Check for updates /
Update, verified download, Android installer hand-off) and a new launcher icon generated from a
canonical SVG.

| Capability | Status |
|---|---|
| In-app updater | Implemented. JVM and Node automated coverage (shared schema-1 fixtures). |
| New launcher icon (adaptive, themed monochrome, legacy PNGs, Play 512px) | Implemented; CI `--check` guard. |
| Phone acceptance of pairing, insets, updater and icon | **Pending: not yet run.** Procedure in [AGENT-EYE.md](AGENT-EYE.md). |

v1.0.0 is the only published release (publication owner-confirmed). In-app update acceptance
needs a later signed release (normally 1.1.1 / 3).

Verification on 2026-10-25: Node 22 passed 17 tests; Android assembleDebug/testDebugUnitTest
passed all 12 JVM tests and produced the debug APK (4,708,052 bytes). Original dependency pins
resolved with owner-authorized downloads. Gradle 8.13's official distribution checksum is pinned.
The Kotlin build script now uses typed compilerOptions with JVM target 17. See the runbook.

Physical-device verification: **not run — no phone attached**. Orientation, real camera freshness,
Activity recreation, screen-off availability, lock release and Wi-Fi reconnect require the device
acceptance procedure in [AGENT-EYE.md](AGENT-EYE.md). No unattended availability promise is made.

Release implementation adds deterministic versions, unsigned optimized APK/AAB builds, ktlint,
commit/run-bound draft workflows, attended local signing/provisioning commands, pinned certificate
verification, retry-safe publication ordering and a bundled privacy policy. See
[distribution](ANDROID-DISTRIBUTION.md) and [future Play checklist](PLAY-LISTING.md).
Release verification on 2026-10-25: 71 Node tests and 12 JVM tests pass; ktlint, debug/release
APK, release AAB and release lint pass (0 errors, 16 warnings). Real disposable-key signing
verifies both artifacts and exact bundled privacy bytes. Both workflows pass actionlint locally.
See the distribution runbook for commands, recovery fixtures and corrected initial failures.
Publication of v1.0.0 is owner-confirmed. Public installation on a phone, minified CameraX
device checks, offline policy display and a second in-place signed update remain **not exercised**.

The 2026-10-25 structure review separates CI/owner commands, artifact contracts, GitHub access,
local checkout/draft/cache/lock components and signing tools. Its gate passed 90 Node tests,
all owner dry-runs, offline release APK/AAB builds, disposable-key signing and workflow YAML
parsing. These checks preserve the acceptance limits above; see the distribution record.
