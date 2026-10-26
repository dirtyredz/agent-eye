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

Release infrastructure is separate from that runtime. Version properties feed Gradle and the
Node contract. CI checks formatting/tests, builds unsigned R8/resource-shrunk APK/AAB outputs,
validates embedded identity and stages a commit/run-bound manifest. Only a separate job can
write draft release assets; it cannot sign or publish.

`src/android/releases/cli/cli.mjs` parses arguments and keeps dry-runs free of adapters.
`commands.mjs` dispatches to separate `ci-commands.mjs` and `owner-commands.mjs` modules.
Release `artifacts/` owns manifests, hashes and the shared asset-set contract; `github/`
owns remote release/history/provenance access. CI drafts allow only unsigned names;
owner retries allow mixed unsigned/final sets until cleanup requires exactly the final set.

The owner CLI first validates a clean tagged checkout, remote main ancestry, draft identity,
trusted Actions artifact provenance and all published version metadata. Only then may the
lazy DesktopAuth adapter recover the pinned key. A Windows ACL is restricted and verified
before key bytes are materialized. APK alignment precedes apksigner; AAB uses jarsigner.
Complete public signed outputs persist for retry; key material does not. Uploads are downloaded
and verified before exact unsigned assets are removed, and publication is the last mutation.
The local signing lock and repeated remote checks prevent accidental overlapping transactions.

Release `local/release-sign.mjs` sequences the transaction; `local-release.mjs` assembles
checkout identity/ancestry, draft verification, public retry cache and lock/scratch ownership.
`src/android/signing/` owns APK/AAB tools and attended key custody; shared Android tool
discovery lives in `src/android/tooling/android-tools.mjs`. Signing and CI tag validation
use the same local tag-to-HEAD and main-ancestry check.

`docs/PRIVACY.md` is copied into generated Android assets and displayed as text by a dedicated
dialog. There is no policy fetch, WebView or added runtime dependency. Node's development-only
1Password SDK is not part of the phone or camera client.

## Entry points and logs

Every runnable entry point (`cli.mjs`, `signing-smoke.mjs`, `snapshot.mjs`) runs inside
`runScript` from `src/script-run/`, so paths resolve from the file location rather than the
caller's directory. Each run writes a secret-redacted log to `.tmp/logs/` (or `.tmp/owner-runs/`
for owner commands, listed in `releases/cli/command-names.mjs`) and ends with an `OK`/`FAILED`
line naming the log. `src/script-run/entry-points.test.mjs` runs every entry point with `--dry-run`
from `os.tmpdir()` to guard this.
