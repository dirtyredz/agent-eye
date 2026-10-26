# Agent Eye

Turns an old Android phone into a **local-only camera for an AI agent**: the phone serves fresh
JPEG snapshots over the home LAN (screen off, plugged in), and a small PC client saves them so an
agent can look at something physical — a TV screen, a device display — and act on it.

- No cloud, no accounts, no ads, no third-party SDKs. LAN subnet only, per-session token.
- Sideloaded APK (`android/`), PC client in Node (`src/camera/`).

Signed downloads will be available at [GitHub Releases](https://github.com/dirtyredz/agent-eye/releases/latest).
On Android, download `agent-eye.apk`, allow installs from your browser when prompted, and install.
The public repository and first release are owner-run steps and are not yet published.
See [Android distribution](docs/ANDROID-DISTRIBUTION.md) for signing, updates and recovery,
and [Privacy](docs/PRIVACY.md) for LAN transfer and snapshot retention details.

Owner quick-start (signing; each script takes `-DryRun`, works from any directory, logs to `.tmp\owner-runs\`):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\provision.ps1 [-DryRun]
powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\sign-release.ps1 -Tag v1.0.0 [-Publish] [-DryRun]
```

See [the Agent Eye runbook](docs/AGENT-EYE.md) for builds, sideloading, the HTTP contract,
PC commands and the physical-device acceptance checklist. Component map: [STRUCTURE.md](STRUCTURE.md).

The PC client requires Node 22 and uses `AGENT_EYE_TOKEN`. Snapshots are transient and must be
cleaned after inspection. Any device-control actions belong to the consumer's own authorized workflow.
