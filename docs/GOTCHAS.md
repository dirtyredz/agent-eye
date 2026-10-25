# Gotchas

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
