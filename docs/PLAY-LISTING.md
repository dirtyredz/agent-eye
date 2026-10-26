# Future Play submission checklist

A signed AAB and this checklist are preparation, not Play approval. Owner submission is deferred.

- Verify the developer account, legal/listing identity, package `local.agenteye` and actual
  contact details; do not invent an email address. Check current [account/testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
- Decide app-signing key continuity **before** [Play App Signing enrollment](https://developer.android.com/studio/publish/app-signing).
  The current dedicated key signs GitHub APKs and AAB uploads. A different Google app-signing
  key creates a separate update track; an upload-key reset cannot change the sideload signer.
- Upload the verified AAB with a new positive versionCode. Check current
  [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
  and [16 KB page-size/native-library requirements](https://developer.android.com/guide/practices/page-sizes).
  Do not assume CameraX native dependencies satisfy future requirements without testing.
- Launcher icon: use the generated [512px asset](../src/android/icons/play-store-512.png). Prepare screenshots, feature graphic, accurate camera/LAN description,
  content rating, audience declarations and reviewer instructions with a test phone and supported
  private LAN. Explain permission denial, explicit Start/Stop and session authentication.
- Supply the public [privacy policy](https://github.com/dirtyredz/agent-eye/blob/main/docs/PRIVACY.md)
  and owner contact mechanism. Verify public access and offline in-app text; review the current
  [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
- Assess LAN photograph transfers explicitly under [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469).
  Off-device transfers are relevant even though the developer receives nothing. Document PC
  retention/deletion and downstream consumer behavior. Do not automatically answer “no collection”
  because traffic is local. Do not claim encryption in transit: tokens/photos use plain HTTP.
  Complete a policy/security review of that transport before submission; redesign is outside this task.
- Declare the camera foreground-service type accurately, including user initiation, visible
  notification, ongoing camera purpose and Stop controls. Supply required demonstration material
  under current [foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821).
- Complete applicable closed testing, reviewer access and device acceptance. Record actual
  evidence before changing any “not exercised” status in FEATURES or the distribution runbook.
- Disclose update networking: the in-app updater contacts GitHub (and its download servers) only
  on user request, sending IP address and request information, possibly over mobile data. Camera
  data and pairing credentials are never sent. Include it in the privacy policy and Data safety review.
- **Play policy review required before any Play submission.** Apps distributed through Play may not
  update themselves outside Play. A Play build must have the in-app updater (and
  `REQUEST_INSTALL_PACKAGES`) disabled or removed; review the current policy separately first.
