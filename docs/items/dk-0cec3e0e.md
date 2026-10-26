---
id: dk-0cec3e0e
type: task
created: 2026-10-26
status: todo
since: 2026-10-26
area: android
priority: P2
rank: zt
parent:
fixes: []
blocked_by: []
relates: []
---
# Narrow MainActivity configChanges to rotation categories

From the 2026-10-26 v1.1.1 sign-off (Sol high, PASS): AndroidManifest.xml declares density, uiMode, keyboard and navigation configChanges for MainActivity, but onConfigurationChanged only handles orientation. Restrict to orientation|screenSize|screenLayout|smallestScreenSize or handle every declared category.
