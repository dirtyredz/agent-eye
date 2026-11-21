---
id: dk-e3c7da36
type: task
created: 2026-10-25
status: done
since: 2026-11-21
area:
priority: P2
rank: n
parent:
fixes: []
blocked_by: []
relates: []
---
# CaptureService: extract SessionStatus/ErrorCode and SessionNotification


From the first-push structure review (componentization + abstraction lenses, 2026-10-25).

android/app/src/main/java/local/agenteye/service/CaptureService.kt mixes session lifecycle with notification building (showNotification/PendingIntents), UI text (displayStatus), protocol JSON (statusJson) and server wiring (rebind). Session state is scattered across @Volatile fields; error codes are bare strings ("wifi_unavailable", "capture_failed" in CaptureService and CameraSession); Intent actions "START"/"STOP" are literals in CaptureService and MainActivity.

Direction: a SessionStatus snapshot + ErrorCode enum owning state, with statusJson/displayStatus as pure, unit-tested renderers; SessionNotification in service/; Intent actions in the service companion. CaptureService keeps only start/stop/rebind orchestration.

## Notes

### 2026-11-21T09:51:54.085Z · open · agent

Closed 2026-11-21: typed errors, snapshot and display renderer were already done (dk-4f28c010); remaining notification/status-JSON/actions extracted in capture-split. PreviewTarget value type deferred (no fourth preview field yet).
