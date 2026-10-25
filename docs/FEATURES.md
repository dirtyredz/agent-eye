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
Real GitHub CI/draft creation, real key provisioning, public installation, minified CameraX
device checks, offline policy display and a second in-place signed update remain **not exercised**.
