---
id: dk-1a0ea30f
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: android
priority: P2
rank: zz
parent:
fixes: []
blocked_by: []
relates: []
---
# PairHandler returns a PairOutcome; StillServer maps it to HTTP

Baseline review 2026-10-26 (abstraction-lens). security/PairHandler.kt:9 returns HTTP status codes and JSON bodies from the security layer; StillServer.pair (StillServer.kt:151) rethrows them as HttpFailure.

Direction: sealed PairOutcome (Token, BadRequest, Rejected, RateLimited, Unavailable); StillServer maps to status and body.
