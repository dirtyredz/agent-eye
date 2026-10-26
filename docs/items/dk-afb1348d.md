---
id: dk-afb1348d
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: camera
priority: P2
rank: y
parent:
fixes: []
blocked_by: []
relates: []
---
# Single owner for the preview ViewPort (CaptureService vs CameraSession)

From the 2026-10-26 v1.1.1 push review (componentization + abstraction lenses): both CaptureService.attach and CameraSession.attach keep the last ViewPort when the surface is null, each with its own rule. Make one owner (CameraSession holds it; the service only replays it into a new session). Consider a PreviewTarget(surface, rotation, viewPort) value type if a fourth field appears.
