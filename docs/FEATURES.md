# Features and verification status

Agent Eye implements a sideloaded Android camera foreground service with live preview,
Start/Stop and session credentials; fresh, bounded JPEG capture; exact-subnet Wi-Fi binding;
authenticated status and snapshot endpoints; and a Node CLI with status, shot, smoke and clean.

Node 22 verification: 17 tests passed on 2026-10-25. Android assemble/JVM gate failed during
configuration: Kotlin 2.3.10 is not cached and downloads were not authorized. CameraX 1.5.3 is
also absent locally. Android code and JVM tests have not compiled or run; no APK was produced.
The wrapper scripts/JAR were generated from the installed Gradle 8.13 distribution, but its
official distribution checksum still needs to be obtained and pinned. See the runbook.

Physical-device verification: **not run — no phone attached**. Orientation, real camera freshness,
Activity recreation, screen-off availability, lock release and Wi-Fi reconnect require the device
acceptance procedure in [AGENT-EYE.md](AGENT-EYE.md). No unattended availability promise is made.
