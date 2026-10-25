# Agent Eye

Turns an old Android phone into a **local-only camera for an AI agent**: the phone serves fresh
JPEG snapshots over the home LAN (screen off, plugged in), and a small PC client saves them so an
agent can look at something physical — a TV screen, a device display — and act on it.

- No cloud, no accounts, no ads, no third-party SDKs. LAN subnet only, per-session token.
- Sideloaded APK (`android/`), PC client in Node (`src/`).

See [the Agent Eye runbook](docs/AGENT-EYE.md) for builds, sideloading, the HTTP contract,
PC commands and the physical-device acceptance checklist. Component map: [STRUCTURE.md](STRUCTURE.md).

The PC client requires Node 22 and uses `AGENT_EYE_TOKEN`. Snapshots are transient and must be
cleaned after inspection. Any device-control actions belong to the consumer's own authorized workflow.
