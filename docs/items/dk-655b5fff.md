---
id: dk-655b5fff
type: bug
created: 2026-10-25
status: done
since: 2026-10-25
area:
priority: P1
rank: y
parent:
fixes: []
blocked_by: []
relates: []
---
# Fix SDK manager invocation in CI workflows

Fix CI run 40233068646 exit 127 by using the explicit ANDROID_HOME command-line tool path in both workflows. Accept licenses non-interactively with pipefail-safe input and preserve action SHA pins.

Verification: regression guard failed before the fix; node --test passed 91 tests afterward. Both workflow YAML files parsed successfully using installed js-yaml. No downloads or Gradle runs.
