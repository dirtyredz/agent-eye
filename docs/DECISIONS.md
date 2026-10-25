# Decisions

- **2026-10-25 — General-purpose, local-only.** App Agent Eye, package `local.agenteye`,
  project `android/`, client `src/camera/`. No consumer-specific camera or control logic.
  `AGENT_EYE_TOKEN` and `.tmp/agent-eye/` replace the historical TV-specific names too.
- **Pinned toolchain.** AGP 8.13.2, Gradle 8.13, Kotlin 2.3.10, CameraX 1.5.3;
  min/compile/target SDK 24/36/36, Java and Kotlin bytecode 17, build JDK 21.
  Core KTX 1.16.0, lifecycle-service 2.8.1, JUnit 4.13.2. Node 22 as directed by the
  implementation brief (supersedes the plan's Node 24 preparation line).
  Gradle 8.13's published binary ZIP SHA-256 is pinned in the wrapper properties.
  Kotlin uses the typed `compilerOptions` DSL to retain JVM 17 compatibility on Kotlin 2.3.10.
- **Dependencies.** Kotlin and required AndroidX/CameraX are permitted platform components.
  No cloud, advertising, analytics, crash reporting or unrelated SDKs. Sideload/debug signing only.
- **Foreground service owns capture.** Activity recreation and preview loss do not own the
  still-capture lifecycle. No automatic restart after process death, force-stop or reboot.
- **IPv4 Wi-Fi only.** Exact LinkProperties subnet, private phone and peer addresses, no VPN,
  no wildcard listeners. IPv6, Ethernet, cellular, hotspots and ambiguous topology are excluded.
- **Session authentication.** 256 random bits per Start, required on both endpoints, cleared on
  Stop. No token in URLs, notifications or logs. The app blocks screenshots/recents thumbnails.
- **Plain HTTP.** Simple local interoperability at the cost of LAN observers seeing frames and
  tokens. No port forwarding or remote proxies. Source rewriting prevents proof of physical LAN
  membership. Android INTERNET permission and cleartext policy do not implement a firewall.
- **Bounded custom server.** No runtime web-server dependency; narrow GET-only API, strict limits,
  no request bodies or keep-alive, and JVM tests for observable behavior.
- **Transient images.** No phone archive or gallery writes, only on-demand JPEGs. One PC image,
  explicit cleanup after consumption; atomic replacement preserves the old image on failure.
