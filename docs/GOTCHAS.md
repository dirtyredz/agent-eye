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
  root view must apply `systemBars()|displayCutout()` insets as padding (MainActivity does). Anything
  new that bypasses the root container must handle insets itself.
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
- Expired Actions artifacts, missing historical release metadata or conflicting assets stop
  signing. A checksum beside a draft download alone is not trusted build provenance.
- Privacy/Data safety disclosures must include off-device LAN transfers and plain HTTP.
  Local-only does not imply encrypted transport or automatically imply no Play-defined collection.
- Escape the Windows drive colon in `local.properties` (`sdk.dir=C\:/...`); otherwise release
  lint reports `PropertyEscape` even when the build itself can locate the SDK.
- Run `ktlintFormat` separately before the gate. Combining format/check task selectors can
  schedule a subproject's check before its formatter and report stale formatting errors.

## GitHub's get-release-by-tag never returns drafts
`GET repos/<repo>/releases/tags/<tag>` (and so `gh api` on it) returns 404 for DRAFT releases, even right after you
create one. The v1.0.0 release run (40267716974) failed on exactly this: the `draft` step created the draft, then its
re-fetch by tag 404'd; a retry would also have created a duplicate. Look drafts up with `github.releaseByTag(tag)`
(`src/android/releases/github/github.mjs`), which lists releases and matches `tag_name` exactly (null if none, fails
closed on more than one). `draftId` uses it too. Never reintroduce `releases/tags/` for release-pipeline lookups.

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
