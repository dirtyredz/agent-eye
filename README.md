# Agent Eye

Turns an old Android phone into a **local-only camera for an AI agent**: the phone serves fresh
JPEG snapshots over the home LAN (screen off, plugged in), and a small PC client saves them so an
agent can look at something physical — a TV screen, a device display — and act on it.

- No cloud, no accounts, no ads, no third-party SDKs. LAN subnet only, per-session token.
- Sideloaded APK (`android/`), PC client in Node (`src/`).

First consumer: `../tv-tuneup` (driving Samsung TV menus).
