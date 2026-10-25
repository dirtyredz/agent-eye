# Agent Eye

**Goal:** a reusable, local-only "eyes for an agent" phone camera. General purpose — nothing
project-specific (TVs etc.) belongs here; consumers live in their own repos.

## Rules
- Local only is a hard requirement: no cloud endpoints, analytics, crash reporters, ads or
  third-party SDKs. Accept clients from the private LAN subnet only.
- Sideload builds only (no Play Store). Debug-signed is fine for now.
- Plan of record: `docs/plans/agent-eye-app.md` (written as "TV Eye" inside tv-tuneup; names and
  paths are superseded by this repo — see the note at its top).
