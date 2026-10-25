# Features and verification status

Agent Eye implements a sideloaded Android camera foreground service with live preview,
Start/Stop and session credentials; fresh, bounded JPEG capture; exact-subnet Wi-Fi binding;
authenticated status and snapshot endpoints; and a Node CLI with status, shot, smoke and clean.

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
Real GitHub CI/draft creation, real key provisioning, public installation, minified CameraX
device checks, offline policy display and a second in-place signed update remain **not exercised**.
