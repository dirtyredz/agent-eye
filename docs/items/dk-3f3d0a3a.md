---
id: dk-3f3d0a3a
type: task
created: 2026-11-21
status: todo
since: 2026-11-21
area:
priority: P3
rank: zzt
parent:
fixes: []
blocked_by: []
relates: []
---
# Device-check self-update lifecycle and capture-split behaviour on a signed test install

Deferred from docs/plans/self-update-split.md Chosen scope: run once on a signed test install at the release install step.

Also run these owner device checks from capture-split (dk-e3c7da36, dk-afb1348d, dk-33998a40; merged 6bdc830) on the same install:
- Preview geometry delivered before Start is kept when the session starts.
- Stop then Start with the preview attached keeps framing; detach/reconnect replays the viewport.
- Notification open and notification Stop work (Activity Stop still works if notification permission is denied).
- Rotation during capture (portrait, landscape, 180 degrees) keeps FIT framing; no extra rebinds.
- GET /status still returns the seven-field response (note: boundAddress is no longer escaped as `http:\/\/`; same decoded JSON) and stills are valid.
- Behaviour change to eyeball: a session started with no preview attached (e.g. from the notification) frames stills with the last retained window.
