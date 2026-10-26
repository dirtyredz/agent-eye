---
id: dk-bed2beb9
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: android
priority: P2
rank: zy
parent:
fixes: []
blocked_by: []
relates: []
---
# One CaptureLimits for throttle interval, timeout and port

Baseline review 2026-10-26 (both Android lenses). One-second capture spacing is hard-coded in camera/CameraSession.kt:162 while http/StillServer.kt:31 has a configurable intervalMs=1000 and its own busy/lastStart gate (155-169); port 8765 is in service/CaptureService.kt:170 and the StillServer.kt:24 default. The camera-side gate is intentional (ARCHITECTURE: survives listener replacement) but its value is not tied to the server's.

Direction: one CaptureLimits constant set used at both sites; one throttle owner if possible.
