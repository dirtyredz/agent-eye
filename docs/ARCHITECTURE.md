# Architecture

Visible Activity + camera permission → camera foreground service → service-owned CameraX
ImageCapture → authenticated private Wi-Fi HTTP → transient PC JPEG → agent inspection.
Any action taken after inspection belongs to the consuming application and its authorization rules.

The Activity owns no capture lifecycle. It binds to the service to attach/detach Preview;
ImageCapture remains bound when the Activity stops. The service starts only on explicit Start,
posts an ongoing notification with Stop, acquires session power locks, creates a fresh token,
and observes Wi-Fi. Stop tears down listener/connections, pending captures, camera, token and locks.
The service is not sticky and no boot receiver is present.

Wi-Fi selection uses LinkProperties' actual IPv4 prefix. Multiple Wi-Fi networks or multiple
IPv4 addresses fail closed. Only RFC1918 non-VPN Wi-Fi is eligible. A listener binds to the
selected address, never a wildcard. Each accepted socket must pass the exact subnet policy
before HTTP parsing; every endpoint then requires the session bearer token. A network/address/
prefix change closes all old connections before rebinding. No outbound connections are made.

The HTTP server has four connection slots, an 8 KiB strict header parser and a scheduled socket
close at ten seconds, covering slow reads and blocked writes. A single fresh capture has a
five-second deadline and a one-second start interval. The camera also enforces serialization
and spacing across listener replacement. Timed-out hardware callbacks close their ImageProxy
without returning a stale image. JPEG decode dimensions and output size are bounded.

The Node client validates a canonical RFC1918 IPv4 HTTP URL before direct networking; it does
not follow redirects or use proxy settings. Response lengths, MIME, JPEG structure and metadata
are checked before an atomic sibling-file replacement of `.tmp/agent-eye/latest.jpg`.
Failed saves clean temporary files and report failure; an older `latest.jpg` can remain and must
never be interpreted as a new capture. Cleanup touches only the client's names and rejects
redirected snapshot directories.
