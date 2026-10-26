---
id: dk-25aafecb
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: camera-client
priority: P2
rank: zzy
parent:
fixes: []
blocked_by: []
relates: []
---
# Camera client: split client.mjs into transport, jpeg validation and destination policy

Baseline review 2026-10-26 (componentization-lens). src/camera/client.mjs:4 mixes destination policy (RFC1918 check), validateJpeg (pure) and the bounded HTTP transport plus pair/status/shot protocol (45-line fetch closure).

Direction: jpeg.mjs and destination.mjs; client.mjs keeps transport and protocol. Relates dk-661cbd46.
