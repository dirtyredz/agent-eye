# Gotchas

- 1Password SDK (`@1password/sdk` 0.5.0) serializes requests with `JSON.stringify` and a replacer that only
  converts `instanceof Uint8Array` to a number array. A Node `Buffer` has `toJSON()` (`{type:'Buffer',data}`), so a
  Document item created with `content: Buffer` is rejected. Always pass `new Uint8Array(buffer)` (as command-deck does).
  A failed provision can leave the password item behind (it is created first): use `vault-repair.ps1 -Inspect` /
  `-CleanupOrphans`; never regenerate blindly.

- GitHub's Android SDK command-line tools are not necessarily on PATH. Workflows use
  `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"` and accept licenses before installation.
  With Bash `pipefail`, use `(yes || true)` so the producer's SIGPIPE does not fail a successful
  license check; SDK manager failures still propagate. The Node workflow guard rejects bare calls.

- DHCP can change the URL. Wi-Fi loss/address/prefix changes close old connections. Read the
  current URL in the app and retry after reconnect. Client isolation can prevent same-LAN access.
- Start requires a visible Activity with camera permission. Notification denial does not grant
  camera access and does not disable Android's foreground-service requirements. With notifications
  denied, use Stop in the Activity or Android's active-app controls.
- Screen-off while plugged in is the baseline to verify. Wake locks and foreground services do
  not defeat Doze or OEM termination. Wi-Fi high-performance locks are used only before API 34;
  newer behavior does not offer a universal screen-off guarantee.
- Camera privacy toggles, another camera app, force-stop and reboot can interrupt capture.
  Reopen Agent Eye and Start explicitly. No hardware availability claim is made without testing.
- HTTP exposes frames and tokens on the trusted LAN. Do not port-forward or add a remote proxy.
  A proxy/router rewriting peer addresses can conceal external origin. Do not treat network
  security config or the INTERNET permission as a destination firewall.
- Edge-to-edge is enforced at targetSdk 35+: the framework Activity no longer insets content, so the
  Compose camera root applies safeDrawing insets, and Settings uses Scaffold insets. Do not add a second Activity/View inset listener. Both Activities call enableEdgeToEdge on creation and handled configuration changes.
- Pairing codes are only 20 bits: security rests on the 2-minute life, 5 attempts per code and the
  per-client rate limit, on top of subnet admission. Never log codes or tokens; the `--code` argv value
  and 64-hex tokens are redacted by pattern in `snapshot.mjs` logs. Re-pair after every phone Start.
- The stored token file's Windows ACL is set with `icacls`; if that fails the CLI warns and the file
  keeps inherited permissions.
- Only one unambiguous RFC1918 IPv4 Wi-Fi interface is supported. VPNs, IPv6-only networks,
  hotspots, Ethernet and routed clients outside the phone's exact prefix are unsupported.
- Failed snapshots exit nonzero and may leave an older `latest.jpg`. Only a successful command's
  returned path/metadata represents the new capture. Always clean after inspection.
- Capturing an image does not authorize any physical/device-control action. Consumer workflows
  must preserve their own owner-presence and authorization requirements.
- Before targeting SDK 37+, implement the local-network runtime permission and denial flow.
- Debug-to-release migration needs a deliberate uninstall because the certificates differ.
  Subsequent release updates must retain package/certificate and increase versionCode without uninstalling.
- AAB upload signing is not Play app signing. Different Play/GitHub app-signing keys create
  incompatible update tracks. Never replace the sideload key after a Play upload-key reset.
- After forced signer termination, verify its PID is gone before removing the stale local lock
  or its exact protected temp-key directory. Preserve the public `.tmp/releases/` transaction
  cache until publication to resume partial uploads without clobbering signatures.
- A fresh tag checkout lacks the ignored `android/signing.local.json` and `node_modules`. `sign-release.ps1` runs
  `npm ci` and the CLI falls back to the main worktree's config (`signing-config.mjs`); run `provision.ps1` once in
  the main checkout so that file exists.
- Expired Actions artifacts, missing historical release metadata or conflicting assets stop
  signing. A checksum beside a draft download alone is not trusted build provenance.
- Privacy/Data safety disclosures must include off-device LAN transfers and plain HTTP.
  Local-only does not imply encrypted transport or automatically imply no Play-defined collection.
- Escape the Windows drive colon in `local.properties` (`sdk.dir=C\:/...`); otherwise release
  lint reports `PropertyEscape` even when the build itself can locate the SDK.
- Run `ktlintFormat` separately before the gate. Combining format/check task selectors can
  schedule a subproject's check before its formatter and report stale formatting errors.

## GitHub's get-release-by-tag never returns drafts
`GET repos/<repo>/releases/tags/<tag>` (and so `gh api` on it) returns 404 for DRAFT releases. Look drafts up with
`github.releaseByTag(tag)` (`src/android/releases/github/github.mjs`), which lists releases and matches `tag_name`
exactly (null if none, fails closed on more than one). The list is eventually consistent immediately after creation:
release runs 40413735326 (v1.2.0) and 40436158413 (v1.3.0) created a draft, then failed to find it; rerunning reused
the now-visible draft. `draftRelease` therefore retries only its post-create lookup with bounded backoff. `draftId`
uses the non-retrying lookup. Never reintroduce `releases/tags/` for release-pipeline lookups.

## Self-update traps
- **Bootstrap.** 1.0.0 has no updater: install 1.1.0 manually once over 1.0.0 (no uninstall; same
  package and signer keeps data). Later updates can run in the app.
- **Unknown-source permission.** Install needs the per-app "install unknown apps" setting (API 26+;
  legacy unknown-sources on 24-25). Returning from settings starts nothing; tap Install again.
- **Offline or rate-limited checks.** Check fails clearly without internet. GitHub 403/429 persists a
  backoff (shown as distinct from "up to date"); 404 on latest means no published release.
- **Lost installer confirmation.** If the process dies while Android's confirm screen is up, the app
  offers "Retry installation" (abandons the waiting session); reopening reconciles locally.
- **Commit intent stage.** The install record is saved as `Committing` before `commit()` and upgraded to
  `Committed` only after it returns. Recovery abandons any record whose platform session is still `Open`
  (death before commit() took effect) and returns the app to idle/retry; a permanent "Installing" (Start
  disabled) was the old failure. A `Committed` platform session is left to Android to finish or abort.
- **Signer incompatibility.** Debug or differently signed installs cannot self-update; the app shows an
  incompatible-channel message and never offers uninstall.
- **Forward-only recovery.** A bad release is fixed only by a higher version code; never downgrade.
- **Installing stops the camera.** The session is stopped before staging and stays stopped after a
  cancel until Start is pressed.
- **Node 22:** `node --test src/dir/` fails on a directory argument; use globs or plain `node --test`.
- **Windows:** `File.renameTo` will not replace an existing file; tests inject a rename.
- **Rotation (v1.1.1).** `MainActivity` declares `configChanges`, so turning the phone never recreates
  it or touches the service-held session (token, pairing code, camera). Rotation reaches CameraX only
  as `targetRotation`, pushed by PreviewFeed from a started-only `DisplayManager` listener; never read it from `PreviewView.display`
  (null before the view is attached, which made stills portrait-only). Preview and still share one
  `UseCaseGroup` FIT `ViewPort` taken from the `PreviewView`. FIT_CENTER is required in v1.3.0: changing it to FILL reintroduces screen-aspect snapshot cropping;
  `JpegEncoder` applies `cropRect` itself if CameraX leaves the crop as metadata. Rebind only when the
  surface or view port changes: needless rebinds race `takePicture` and leave `cameraReady:false`.
  Physical-device acceptance (portrait, landscape, 180 flip, rotate mid-session) is still owed.

- **Lens switches (v1.2.0).** An in-flight still can finish from the old lens; new requests during
  rebind can briefly return camera unavailable. Retry after Camera ready. Switches preserve the
  pairing code/token and HTTP listener. Front stills are unmirrored even though the default front
  preview is mirrored. Check readable text, portrait/landscape and 180-degree flips on hardware.
- **Settings lifecycle.** Updater attachment uses resume/pause on both Activities: start/stop
  overlap during navigation and can otherwise let the outgoing screen clear the new launcher.
  Navigating away cancels an active check/download; simply opening Settings never uses the network.
- CameraX 1.5.3 marks external lens facing experimental. The opt-in is confined to
  `camera/LensCameraSelector.kt`; back/front selection uses the stable API. Do not suppress lint
  globally when changing this adapter.

- **Compose pairing:** never save digits or copy rendered text. PairingClipboard rereads the service at tap time. Clear presentation on Activity stop/disconnect; countdown is not an accessibility live region. Screens and dialogs no longer block screenshots (DECISIONS 2026-10-27); `tooling/window-policy.test.mjs` fails the build if FLAG_SECURE or SecureOn returns.
