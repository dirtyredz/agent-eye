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
