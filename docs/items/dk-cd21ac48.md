---
id: dk-cd21ac48
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: android
priority: P3
rank: zy
parent:
fixes: []
blocked_by: []
relates: []
---
# v1.2.0 sign-off nits: lens prefs constants and isLive test placement

From the v1.2.0 Sol sign-off (PASS, 2026-10-26):
- camera/LensSelection.kt:8 owns the 'camera' prefs file and 'lens' key names; move both into camera/LensPreferences.kt so LensSelection stays pure selection policy.
- ui/PairingCopyTest.kt:16 tests PairingCodes.Code.isLive directly; move that case to security/PairingCodesTest.kt.
