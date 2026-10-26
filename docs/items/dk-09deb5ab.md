---
id: dk-09deb5ab
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area:
priority: P3
rank: zw
parent:
fixes: []
blocked_by: []
relates: []
---
# Move SHA-256/hex encoding out of ReleasePolicy into a neutral integrity utility


From the 1.1.0 sign-off (Sol, 2026-10-26, P2). android/.../selfupdate/release/ReleasePolicy.kt:~28 holds generic digest/hex encoding that download/ and install/ import, making lower-level integrity code depend on release policy. Move it into a neutral integrity utility (e.g. selfupdate/contract/ or a small integrity/ package) shared by release, download and install.
