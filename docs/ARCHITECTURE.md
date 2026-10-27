# Architecture

Visible Activity + camera permission → camera foreground service → service-owned CameraX
ImageCapture → authenticated private Wi-Fi HTTP → transient PC JPEG → agent inspection.
Any action taken after inspection belongs to the consuming application and its authorization rules.

The camera Activity owns no capture lifecycle. It binds to the service to attach/detach Preview and pushes the display
rotation and the FIT preview ViewPort through `ui/preview/PreviewFeed` (started-only DisplayManager and layout listeners). Preview and ImageCapture share one UseCaseGroup. FIT retains the shared camera frame, independent of screen aspect ratio; the owner sees the full delivered frame letterboxed. JPEG crop metadata handling is retained for shared-use-case alignment, never a screen-shaped crop. The Activity declares
`configChanges` and is not recreated on rotation. Preview detachment can rebind to still-only capture, preserving the service-owned session and last framing. The service starts only on explicit Start,
posts an ongoing notification with Stop, acquires session power locks, creates a fresh token,
and observes Wi-Fi. Stop tears down listener/connections, pending captures, camera, token and locks.
The service is not sticky and no boot receiver is present.

Wi-Fi selection uses LinkProperties' actual IPv4 prefix. Multiple Wi-Fi networks or multiple
IPv4 addresses fail closed. Only RFC1918 non-VPN Wi-Fi is eligible. A listener binds to the
selected address, never a wildcard. Each accepted socket must pass the exact subnet policy
before HTTP parsing; every endpoint then requires the session bearer token. A network/address/
prefix change closes all old connections before rebinding. The camera path makes no outbound
connections; the only outbound traffic is the separate update transport below.

Pairing: the service owns a `PairingCodes` state machine (security package) next to the session
token. It holds one six-digit SecureRandom code valid two minutes and usable once; five wrong
guesses burn it, a per-client sliding window (10 requests/minute) throttles guessing, and the
comparison is constant-time. `POST /pair` is the only unauthenticated route and sits behind the
same subnet admission and connection limits; `StillServer` routes it to `PairHandler` (body checks, code-for-token exchange) and it returns the token. Main polls the live code once per second while started; `PairingState.from` (pure companion mapping in `ui/pairing/PairingState.kt`) calculates the display countdown. Stop discards the gate with the token. The PC
`pair` command stores the token in an owner-only `.tmp/agent-eye/token.json`.

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

## Camera controls and settings

The bound CaptureService discovers CameraX logical lens directions even while stopped.
The pure camera/LensSelection unit chooses a saved direction, otherwise back (or the first
available direction on a device without back), cycles only available directions, and persists
through an injected writer (`LensPreferences`: the `camera` SharedPreferences file, `lens` key). A lens change reuses CameraSession and its HTTP
server closure: it preserves rotation, ViewPort, token, pairing code and capture sequence.
Rebinding waits for an in-flight still callback to finish; readiness is false until the new
camera opens. JPEG encoding applies CameraX rotation/crop without horizontal mirroring.

The Compose main screen has one factory-owned PreviewView, Start/Stop, lens switching, pairing (Copy/New code) and typed status. A credential-free CaptureSnapshot feeds pure CameraStateHolder; binding differs from stopped, and Stop survives camera/network failures. Permission returns recheck visibility, camera grant and the latest install phase. Pairing digits are Activity-owned, never saved, and cleared on stop/disconnect. Stable control bars reserve space around the whole-frame preview; status/countdown cannot resize it.
PairingClipboard resolves Copy at tap time, rejects inactive/expired codes, and marks ClipData
sensitive. Only API <33 gets an app toast; Android 13+ provides its own clipboard confirmation.
SettingsActivity under ui/settings owns a scrollable update/privacy/guidance screen and has
a Material Scaffold applying insets once and its existing configChanges declaration. Both ComponentActivities forward configuration to Compose and refresh system-bar styling; Main also refreshes settled preview geometry. Static amber light/dark schemes follow the system. Both activities attach the updater on resume
and detach on pause, avoiding overlapping owners during navigation. Settings binds the service
only to stop capture before install. Rotation does not recreate either activity; leaving the
camera screen detaches preview while the service keeps still capture and its last geometry.

## Self-update (outbound, user-initiated)

Inbound camera networking (above) and outbound update transport are separate: the camera HTTP
server has no updater routes, and the updater does not bind the process to any network. Updates
use the ordinary system network (Wi-Fi, mobile data or VPN). Camera admission is unchanged.

- **Lifecycle.** Only Settings' Check for updates and Update buttons start network work
  (no launch, foreground, timer, boot or connectivity check; no automatic retry). `UpdateCoordinator`
  is the state machine; backgrounding cancels network work, rotation keeps it.
- **Transport.** Unauthenticated HTTPS/443 to the GitHub releases API (never redirected) and
  exact-tag asset URLs (redirects only to GitHub's asset CDN hosts, max 5 hops). No cookies, auth
  headers or camera credentials; signed CDN query strings are never logged or persisted.
  Cleartext is denied and there is no TLS pinning. Responses are size-bounded; 403/429 persist a
  backoff shown as distinct from "up to date".
- **Trust.** `release.json` and `SHA256SUMS` are not independently signed. HTTPS and GitHub protect
  discovery; hashes detect inconsistency; the signer pin (packaged from
  `signing-certificate.json`) plus Android's installer verification protect executable
  replacement. There is no freshness protection against release suppression. PackageManager
  archive inspection is a preflight; the installer is the authoritative signature check.
- **Download and install.** One verified candidate in `noBackupFilesDir/self-update/`, no Range
  resume. Before staging, the camera session is stopped; a `PackageInstaller` session id and nonce are persisted
  before commit and only the matching `InstallResultReceiver` result is accepted. Launch and
  foreground reconcile locally (installed version at or above target with the pinned signer proves
  replacement). A failed or cancelled install leaves the old app and data; recovery is only a
  higher version code, never a downgrade or uninstall.

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
dialog. There is no policy fetch, WebView or added runtime dependency. Launcher icon resources
are generated from `src/android/icons/agent-eye.svg` and checked in CI. Node's development-only
1Password SDK is not part of the phone or camera client.

## Entry points and logs

Every runnable entry point (`cli.mjs`, `signing-smoke.mjs`, `snapshot.mjs`) runs inside
`runScript` from `src/script-run/`, so paths resolve from the file location rather than the
caller's directory. Each run writes a secret-redacted log to `.tmp/logs/` (or `.tmp/owner-runs/`
for owner commands, listed in `releases/cli/command-names.mjs`) and ends with an `OK`/`FAILED`
line naming the log. `src/script-run/entry-points.test.mjs` runs every entry point with `--dry-run`
from `os.tmpdir()` to guard this.
