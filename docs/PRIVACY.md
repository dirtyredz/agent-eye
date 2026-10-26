# Agent Eye privacy policy

Effective October 26, 2026.

Agent Eye collects no data for its developer. The app has no accounts, analytics,
advertising, crash reporting or cloud uploads of camera data. Camera permission enables a live
preview and photographs requested by an authenticated client on the supported
private Wi-Fi LAN. Photographs do leave the phone when that client requests them.

The phone handles photographs in memory and does not create a gallery or archive.
The companion PC client retains its latest photograph until replacement or explicit
cleanup (`node src/camera/snapshot.mjs clean`). Downstream consumers may copy,
retain or transmit photographs under their own policies; review those separately.

Each Start creates a session token, handed to the PC only in exchange for the short pairing code shown on the phone. Stop ends the camera session, closes connections
and clears the token. Tokens and photographs travel over plain HTTP, without
encryption in transit, and can be observed on the LAN. Use only a trusted private
network; never expose the port to the internet. Stop the session and delete PC
snapshots when finished. Revoking camera permission prevents further captures.

Updates: the app contacts the internet only when you tap Check for updates or Update. It never
checks automatically (not at launch, in the background, on a timer or at boot). Those requests
go to GitHub and its download servers, which receive your IP address and ordinary request
information under GitHub's own policy. Photographs, pairing codes, session tokens and camera
data are never sent. They use whatever network the phone is on, so mobile data may be used.

Privacy inquiries: open an issue at https://github.com/dirtyredz/agent-eye/issues
or use the public contact mechanisms on https://github.com/dirtyredz. Do not post
photographs, session tokens or other private information in public issues.
GitHub hosts this policy and downloads and processes visits under its own policy:
https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement.
The bundled copy of this policy is available without network access.
