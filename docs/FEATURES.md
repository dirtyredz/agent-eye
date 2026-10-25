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
