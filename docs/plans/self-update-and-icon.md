## 1. Goal

Add user-initiated, GitHub-backed Android self-updates and a distinctive, reproducibly generated eye icon, preserving pairing, window insets and LAN-only camera operation. Updates must retain `local.agenteye`, its signing certificate and application data, with Android confirmation before replacement.

Target the completed pairing branch. Recommend folding unpublished pairing work into **1.1.0**, followed by a later signed release that demonstrates in-app updating.

## 2. Placement

Paths below are repository-relative. `K` means `android/app/src/main/java/local/agenteye`; `T` means `android/app/src/test/java/local/agenteye`.

Add explicit `STRUCTURE.md` Layout bullets for `selfupdate` responsibilities and `src/android/icons/`. Keep every code file below approximately 800 lines.

| Files | Responsibility and placement |
|---|---|
| `K/AgentEyeApplication.kt` | Application composition only; lazily owns the updater runtime across Activity recreation. Initialization performs no networking. |
| `K/selfupdate/UpdateRuntime.kt` | Wires Android adapters, worker executor and main-thread delivery. Holds application context, never an Activity. |
| `K/selfupdate/UpdateCoordinator.kt` | Testable check/download/verify/install state machine; owns single-operation enforcement and cancellation. |
| `K/selfupdate/UpdateState.kt` | Immutable states, candidate identity and actionable failure categories. |
| `K/selfupdate/release/ReleaseManifest.kt` | Bounded schema-1 decoding and checksum-document parsing. |
| `K/selfupdate/release/ReleasePolicy.kt` | Pure identity, version, SDK, pin and asset-set validation. |
| `K/selfupdate/release/GitHubReleaseSource.kt` | Stable-release discovery and exact-candidate revalidation. |
| `K/selfupdate/transport/UpdateHttpClient.kt`, `UpdateUrlPolicy.kt` | Bounded HTTPS transport and independently testable endpoint/redirect rules. Kept separate from LAN networking. |
| `K/selfupdate/download/ApkDownloader.kt` | Streaming transfer, progress and cancellation. |
| `K/selfupdate/download/ApkVerifier.kt`, `AndroidApkInspector.kt` | Pure verification decisions separated from PackageManager archive/installed-package inspection. |
| `K/selfupdate/storage/UpdateStore.kt` | Private partial/verified files and atomic, credential-free transfer records. |
| `K/selfupdate/install/PackageInstallerGateway.kt` | Android session creation, byte transfer, permission handling and commit. |
| `K/selfupdate/install/InstallRecovery.kt`, `InstallResultReceiver.kt` | Durable session reconciliation and narrowly validated installer callbacks. |
| `K/ui/CameraScreen.kt` | **Extract** existing screen construction from `MainActivity`: preview, controls, status, pairing panel and inset-aware layout. |
| `K/ui/update/UpdatePanel.kt`, `UpdatePresenter.kt` | Update controls/disclosure and pure state-to-presentation/action mapping. |
| `K/ui/MainActivity.kt` | Retains Activity permissions, service binding and preview lifecycle; delegates presentation and connects installer handoff to existing `CaptureService.stopSession()`. |
| `android/app/src/main/AndroidManifest.xml` | Application class, nonexported installer receiver, install-request permission, network configuration and launcher references. |
| `android/app/src/main/res/xml/network_security_config.xml`; `res/values/strings.xml` | Explicit HTTPS client policy; update UI and privacy wording. |
| `android/app/build.gradle.kts` | Package the validated public certificate pin from its existing source; retain privacy generation; add test-only JSON support if necessary. |
| `src/android/releases/artifacts/fixtures/update-schema1/` | Shared synthetic `release.json` and `SHA256SUMS` fixtures consumed by Node and JVM contract tests. |
| `src/android/releases/artifacts/contract.test.mjs`, `artifacts.test.mjs` | Prove fixture compatibility with the existing publisher. Production publication format remains schema 1. |
| `src/android/icons/agent-eye.svg` | Canonical, restricted vector artwork. |
| `src/android/icons/icon-assets.mjs`, `generate-icons.mjs`, `icon-assets.test.mjs` | Artwork conversion/rendering, standard-wrapper CLI and reproducibility tests. |
| `src/android/icons/play-store-512.png` | Committed generated store asset beside its source and generator. |
| `package.json`, `package-lock.json`; `src/script-run/entry-points.test.mjs` | Exactly pinned build-time rasterizer, icon command and arbitrary-working-directory coverage. |
| `.github/workflows/ci.yml`; `android/version.properties` | Generated-asset drift gate and release identity. |
| Documentation listed in section 6 | Product, operational and structural contracts. |

Generated Android icon resources:

```text
android/app/src/main/res/
  drawable/
    ic_launcher_background.xml
    ic_launcher_foreground.xml
    ic_launcher_monochrome.xml
  mipmap-anydpi-v26/
    ic_launcher.xml
    ic_launcher_round.xml
  mipmap-anydpi-v33/
    ic_launcher.xml
    ic_launcher_round.xml
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
    ic_launcher.png
    ic_launcher_round.png
```

Tests mirror the new Kotlin packages under `T`; test names and coverage appear below.

Do not put updater code in `CaptureService`, `LanBinding`, `SubnetPolicy`, `StillServer` or the PC client. Preserve the notification-specific `ic_camera_notification.xml`.

## 3. Sequence

1. **Establish the integration base and release identity.**

   Start from completed pairing work, preserving concurrent edits. The inspected pairing commit is `5b2422d`; its working tree already contains uncommitted `1.1.0 / 2` versioning and documentation changes. Do not overwrite them or assume code 2 remains unpublished.

   Before implementation work-item writes, run `docket guide`, locate the relevant item through filtered `dk list --json`/`dk show --json`, and claim it in the implementation worktree.

   Extract `CameraScreen` first, changing only its caller, `MainActivity`. Preserve `FLAG_SECURE`, inset handling, pairing countdown, Start/Stop, preview attachment and privacy access. No updater behavior is introduced in this step.

2. **Define the update experience against the references.**

   | Reference | Useful benchmark | Agent Eye decision |
   |---|---|---|
   | Command Deck’s `selfupdate/` and `feature/phoneupdate/` | Candidate-bound download, hash/certificate checks, installer sessions and recovery. Its found-update path is Update → Android Install. | Reuse these boundaries. Omit foreground automatic checks, Worker authentication and pairing dependencies. |
   | [F-Droid client](https://f-droid.org/en/docs/FAQ_-_Client/) | Distinguishes recommended updates from other available versions; documents signed repository metadata. | Offer only the stable release and show installed/available versions clearly. Do not claim equivalent signed-metadata protection. |
   | [Obtainium](https://obtainium.imranr.dev/) | Updates directly from release sources, including GitHub. | Use the fixed public repository directly; require no account or source configuration. |

   These are source/documentation comparisons, not measured competitor timings.

   Place installed version, a **Check for updates** button and disclosure directly on the main screen. A check shows current/checking/update-available/error states. An available release shows version and download size, with **Update** and progress/cancel controls. State that installation stops the camera session.

   **Felt target:** with install permission already granted, at most **three taps** from the main screen—Check, Update, Android Install—and installation completed within **60 seconds** on the acceptance phone with ≥10 Mbps connectivity, an APK ≤15 MiB and prompt confirmation. Target discovery within five seconds. Record actual results; network and OS delays are not guarantees. Measure first-use permission setup separately.

3. **Implement the existing manifest contract and public GitHub discovery.**

   Use unauthenticated `GET https://api.github.com/repos/dirtyredz/agent-eye/releases/latest`. Require a published, non-prerelease release with an exact stable semantic-version tag. GitHub documents public release access without credentials. [GitHub release API](https://docs.github.com/en/rest/releases/releases)

   Require the final assets already published by this repository: `agent-eye.apk`, `agent-eye.aab`, `mapping.txt`, `release.json`, `SHA256SUMS`. Reject duplicates, unsigned/incomplete release sets and malformed metadata. Download only metadata and the APK.

   | Contract element | Required behavior |
   |---|---|
   | Manifest identity | `schema: 1`, `repository: "dirtyredz/agent-eye"`, `applicationId: "local.agenteye"`. |
   | Version | Positive bounded integer `versionCode`; stable `versionName`; `tag == "v" + versionName`; agreement with discovered release. |
   | Provenance fields | Validate existing `commit`, `runId`, `runAttempt`, `workflow`, SDK fields and `unsignedHashes` shapes. They are release metadata, not independently authenticated on-device provenance. |
   | Artifacts | Preserve existing `artifacts[name].size` and `.sha256` format and final artifact names. |
   | Signer | `signerSha256` equals the packaged public pin. Never accept a pin supplied only by the network. |
   | Checksums | Parse the existing two-space format. Require unique exact filenames; verify raw `release.json` bytes against its checksum and APK checksum agreement with the manifest. |
   | Availability | Compare numeric version codes. Equal means current; lower means no upgrade. An incompatible higher release gets an explicit compatibility explanation. |
   | Frozen candidate | Retain release ID, tag, relevant asset IDs, manifest digest and validated APK identity. Never mix assets fetched through a moving “latest” URL. |

   Fetch assets through exact-tag GitHub download URLs, validating API-provided URLs against the expected repository/tag/name. Before download, revalidate the exact release ID and metadata; changed candidates require a fresh check.

   No Cloudflare Worker or Access credentials are needed: this is a public repository, with publicly downloadable releases and device-side signer enforcement. A proxy would add infrastructure and another data recipient without supplying a required access boundary.

   **Tradeoff:** `release.json` and `SHA256SUMS` are not independently signed. HTTPS and GitHub protect discovery; hash checks detect inconsistency; the pinned APK signer and Android installation verification protect executable replacement. This does not provide TUF-style freshness or protection against release suppression.

4. **Implement the narrowly scoped internet exception.**

   Keep camera admission exactly as it is: private IPv4 Wi-Fi, actual subnet, address-bound listener, no internet-validation requirement. Do not change process-wide network binding.

   Updater HTTPS uses the ordinary system network, which may be Wi-Fi, cellular or VPN. Disclose possible mobile-data use. Add `network_security_config.xml` with cleartext denied and system trust anchors; no trust-all certificates or GitHub TLS-certificate pinning. This configuration governs supporting client APIs, not the raw inbound LAN server and not destination authorization. [Android network security configuration](https://developer.android.com/privacy-and-security/security-config)

   `UpdateUrlPolicy` must enforce:

   - Fixed API repository paths and exact-tag asset paths.
   - HTTPS, port 443, no userinfo or fragments.
   - Redirects disabled by default and manually checked, at most five hops.
   - Asset redirects only to exact approved GitHub asset hosts, initially `release-assets.githubusercontent.com` and `objects.githubusercontent.com`; no wildcard host allowance.
   - No HTTPS downgrade, arbitrary host, cookies, authentication headers or camera credentials.
   - Signed CDN query strings remain transient and are never logged or persisted.

   Bound release JSON to 1 MiB, manifest to 64 KiB, checksums to 8 KiB and APK to 64 MiB. Use connection/read timeouts plus overall metadata/download deadlines; reject truncation and excess bytes even without trustworthy `Content-Length`.

   No launch, foreground, service, timer, boot or connectivity event initiates a network request. Repeated manual checks respect persisted server backoff; 403/429 are distinguishable from “up to date.” No automatic retry loop.

   Suggested visible wording:

   > Camera and pairing stay on your local network. Only Check for updates and Update contact GitHub and its download servers. They receive your IP address and request information; photos and pairing credentials are never sent. Mobile data may be used.

5. **Implement download and verification independently of UI.**

   Use `noBackupFilesDir/self-update/`, one candidate and one transfer at a time. Stream into a private partial file, enforce declared size, hash the completed bytes, then atomically mark the candidate verified. Check storage capacity for both downloaded APK and installer staging, plus a safety margin.

   Cancel network work on explicit cancellation or real backgrounding; configuration recreation retains the operation. For this small APK, restart interrupted partial transfers rather than implementing Range resume. Retain a completed verified APK for installation retry, with a bounded expiry and candidate-bound record.

   **Tradeoff:** restarting partial downloads sacrifices bandwidth on poor connections but avoids Command Deck’s substantially larger Range/validator protocol.

   Package the pin from `src/android/releases/signing-certificate.json` through a validated Gradle-generated asset. The inspected pin is:

   `f0d99c5825fbc240e7fefb68bb791d47cb61c37f13c42a5f9f81730168d6d6e2`

   Validate its package, format and first-version enrollment; do not maintain a second hand-written pin. Missing or malformed pin fails the build.

   Before staging, require:

   - Exact downloaded length and SHA-256.
   - Installed package and exactly one current signer matching the compiled pin.
   - Archive package, version name/code and SDK identity matching the candidate.
   - Exactly one archive signer matching both the pin and installed signer.
   - Strictly increasing version code, compatible minimum SDK and non-debuggable APK.
   - API 24–27 inspection compatibility, with signing-info APIs used on API 28+.

   Debug or differently signed installations show an incompatible-channel explanation; never offer uninstall as the updater’s recovery action.

   **Verification boundary:** follow Command Deck’s hash plus PackageManager signer/identity preflight. Archive signer inspection is not an independent verification of every APK signature byte. Android’s installer performs authoritative cryptographic verification before replacement. Do not describe checksum metadata or certificate extraction as stronger than this, and do not embed a custom signature implementation. The upstream `apksig` project describes its library as intended for use outside Android devices. [apksig documentation](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/README.md)

6. **Implement installation and durable recovery.**

   Declare `REQUEST_INSTALL_PACKAGES`. On API 26+, check per-app installation permission and open the package-specific settings page when needed; on API 24–25 handle the legacy unknown-sources setting/block. Returning from settings must not trigger a new download automatically.

   Immediately before staging, reverify the retained APK and current installed identity. Through the foreground Activity’s service binding, call the existing `stopSession()`; gate camera Start until handoff finishes. Check/download failures must not stop capture. Installation cancellation leaves capture stopped until explicit Start.

   Create a `MODE_FULL_INSTALL` session for `local.agenteye`. Stream `base.apk`, hash/count the exact bytes written, compare again, and fsync before commit. Request user action explicitly on API 31+, and handle Android confirmation on every supported version. [PackageInstaller user-action contract](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))

   Persist the candidate, session ID, random callback nonce and staging/committing state atomically before the corresponding irreversible transition. Use an explicit nonexported receiver and appropriately mutable callback PendingIntent. Accept callbacks only for the stored session and nonce.

   Handle `STATUS_PENDING_USER_ACTION` through the visible Activity. Never launch confirmation unexpectedly from the background. If process death loses the confirmation intent, expose an explicit recovery action; do not claim installation succeeded.

   Reconcile locally on launch/foreground:

   - Matching installed package/pin with version code at least the target proves replacement; clear update files and records.
   - Uncommitted orphan sessions are abandoned.
   - A live committed session is observed without creating a second installation.
   - A missing/terminal session with unchanged installed version becomes an actionable interrupted/failed state.
   - Lost confirmation permits explicit abandon/retry only when the existing session can safely be abandoned; otherwise report that Android is still completing it.
   - A success callback alone is insufficient evidence of installed-version advancement.

   Map cancellation, blocked installation, insufficient storage, incompatible package/signature, corrupt APK and unavailable network separately. Delete corrupt bytes; retain valid bytes for bounded retry. Recovery itself performs no network access.

   **Rollback:** failed or cancelled installation leaves the existing app/data installed. Do not downgrade or uninstall automatically. Recover from a bad published version through a corrected release with a higher version code.

7. **Generate the icon and integrate the resources.**

   Use an **aperture eye** with no lettering, camera-body outline or gradients:

   - SVG viewport: 108 × 108.
   - Full-bleed background: deep navy `#101B2D`.
   - Eye: off-white `#F4F7FA` almond ring, centered at `(54,54)`, outer tips `(24,54)` and `(84,54)`, top/bottom extrema around `y=37/71`; inner cutout spans approximately `x=32..76`, `y=43..65`.
   - Iris: teal `#2DD4BF`, radius 11.
   - Pupil: navy, radius 5.
   - Small off-white highlight centered near `(57,50)`, radius 2.
   - Monochrome: one filled-color eye ring and iris silhouette with transparent pupil; omit the highlight.

   Encode circles and outlines as filled paths with explicit winding so the same geometry converts directly to VectorDrawable. Keep meaningful foreground geometry inside the central safe region; background fills the adaptive canvas. Supply v26 adaptive and v33 monochrome resources, plus legacy density PNGs for API 24–25. [Adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

   The Play asset is a full-square, opaque-background 512 × 512 RGBA PNG, with no baked rounded corners or drop shadow. [Google Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)

   Follow the owner’s canonical-artwork → generated-output pattern. Use an exactly pinned development-only rasterizer, preferably the inspected family repository’s `sharp` version, with its lockfile. Restrict SVG input to the geometry needed here; reject unsupported elements, external references, fonts, filters and transforms instead of building a general SVG converter.

   `generate-icons.mjs` uses the existing unmodified `runScript` wrapper and supports generation, `--dry-run` and `--check`. Resolve from `import.meta.url`, write standard redacted logs and verdicts, and work from arbitrary directories. `--check` compares generated outputs without replacing them. Specify Node/renderer/native-library versions as the reproducibility environment.

8. **Integrate disclosure, gates and release preparation.**

   Wire `UpdatePanel` through `MainActivity`; expose no updater routes on the camera HTTP server. Keep `PrivacyPolicyDialog` reading the existing bundled policy source.

   Update documentation and CI drift checks, then perform the gates below. Release signing remains the documented owner-run process. Publishing a complete signed release remains the final release-pipeline mutation; no new signing secret, Worker or release asset format is introduced.

## 4. Tests

All required automated coverage runs without a device or public GitHub access.

| Test location | Required coverage |
|---|---|
| `T/selfupdate/release/ReleaseManifestTest.kt`, `ReleasePolicyTest.kt` | Shared schema-1 fixtures; malformed/duplicate/missing fields, overflow, wrong package/repository/tag/pin, unsigned assets, checksum disagreement, equal/older versions, incompatible SDK and future SDK values without hard-coding target 36 forever. |
| `T/selfupdate/release/GitHubReleaseSourceTest.kt` | Stable selection, drafts/prereleases, incomplete releases, asset collisions, exact-candidate revalidation, changed manifests/assets, 404 versus current, rate limits and backoff. |
| `T/selfupdate/transport/UpdateUrlPolicyTest.kt`, `UpdateHttpClientTest.kt` | Redirect chains, forbidden hosts/ports/userinfo/downgrades, lookalike domains, oversize/slow/truncated bodies, cancellation and header isolation, using fake connections. |
| `T/selfupdate/download/ApkDownloaderTest.kt`, `ApkVerifierTest.kt` | Bounded streaming, disk failure, partial cleanup, byte corruption, forged matching checksum metadata with wrong signer, multiple signers, debug signer, package/version/SDK mismatch and expired cache. |
| `T/selfupdate/storage/UpdateStoreTest.kt` | Atomic record recovery, corrupt/old-schema records, orphan files, candidate mismatch and cleanup confined to updater-owned files. |
| `T/selfupdate/install/InstallRecoveryTest.kt`, `PackageInstallerGatewayTest.kt` | Permission denial/return, byte changes between verification and session write, fsync/commit failure, duplicate commit prevention, forged callbacks, cancellation, process death around commit, lost confirmation, missing sessions and installed-version reconciliation. Exercise gateway policy through a fake platform session port. |
| `T/selfupdate/UpdateCoordinatorTest.kt` | Full happy path and failure transitions; duplicate taps; rotation; background cancellation; zero network calls during launch/foreground/recovery; camera stop only at verified install handoff; explicit restart after cancellation. |
| `T/ui/update/UpdatePresenterTest.kt` | Visible versions/disclosure, button availability, permission flow, progress and actionable errors. |
| Existing Node artifact tests | Publisher-generated manifest/checksum bytes agree with the shared JVM fixtures. Test pin packaging and fail-closed invalid pin behavior. |
| `src/android/icons/icon-assets.test.mjs` | Deterministic regeneration, PNG dimensions/color mode, supported resource variants, safe geometry, rejected unsupported SVG and drift detection. |
| `src/script-run/entry-points.test.mjs` | Icon CLI dry-run from `os.tmpdir()`, standard verdict/log and no generated-asset mutations. |

Use injected clock, executor, transport, archive inspector, installer and filesystem boundaries where failure/lifecycle behavior warrants them. Do not enable Android stub “default values” to make tests pass. If parsing uses Android’s `org.json`, provide a pinned JVM test implementation.

Retain pairing, server, subnet and PC-client tests as regression coverage. Extend the existing disposable-key signing smoke to inspect the bundled pin/privacy/icon resources; it remains separate from the fast JVM gate.

## 5. Verification

No implementation, builds or tests were performed for this specification.

**Per-change fast gate**, matching the existing CI tier and adding generated-artifact verification:

- `npm ci`
- `node src/android/icons/generate-icons.mjs --check`
- `node --test`
- `.\android\gradlew.bat -p android ktlintCheck assembleDebug testDebugUnitTest`
- `dk check`

**Before release**, run the documented broader gate, serializing heavyweight Gradle work:

- `.\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest`
- `.\android\gradlew.bat -p android lintRelease releaseTools`
- `node src/android/signing/signing-smoke.mjs`
- `.\android\gradlew.bat --stop`
- `git diff --check`
- `dk check`

Verify icon regeneration twice produces no tracked changes under the declared toolchain. Inspect minified release packaging and the explicit receiver/session configuration; JVM fakes do not establish Android/OEM installation behavior.

**Owner-visible phone acceptance:**

1. Retain a manually installed, officially signed `1.0.x`. Install the first updater-bearing release over it without uninstalling. Verify package, certificate continuity, permissions, pairing and insets.
2. Publish a later complete signed release with a higher version code. From the installed updater-bearing app, Check → Update → Android Install must replace the app without browser download or uninstall.
3. Record phone/API, old/new versions and codes, APK size, network conditions, taps and elapsed time. Reopen and verify the new version and icon; Start explicitly, pair again and take a fresh photograph.
4. Cancel installation and deny install-source permission in separate attempts. The previous app remains usable; retry is understandable.
5. Exercise rotation, backgrounding and process interruption during download and installation. Confirm no duplicate sessions or false success.
6. Disconnect WAN while retaining LAN. Pairing/capture and bundled privacy still work; manual update checks fail clearly. Observe that launch, Start and ordinary foregrounding cause no updater requests.
7. Inspect launcher masks, themed monochrome icon, small-icon legibility and update controls with gesture/three-button navigation and enlarged text.

**Bootstrap limitation:** published `1.0.0` has no updater. It cannot perform its first updater installation internally. The honest default acceptance requires one manual in-place bootstrap upgrade, followed by an entirely in-app upgrade. A literal “install 1.0.x manually once, then update inside the app” test requires that initial `1.0.x` itself contain the updater.

## 6. Docs

| Document | Update |
|---|---|
| `CLAUDE.md` | Replace blanket prohibition on cloud endpoints with the narrow, user-initiated GitHub update exception; retain LAN-only camera requirements. |
| `STRUCTURE.md` | Add updater and icon Layout responsibilities, generated-resource locations and mirrored tests. |
| `docs/ARCHITECTURE.md` | Separate inbound camera networking from outbound update transport; document trust, lifecycle and installation boundaries. |
| `docs/ANDROID-DISTRIBUTION.md` | Document endpoint/schema contract, bootstrap installation, in-app updates, higher-code recovery, version sequencing and acceptance evidence. Fix mixed `v1.0.1`/`v1.1.0` examples. |
| `docs/PRIVACY.md` | Disclose explicit GitHub/CDN requests, IP/request metadata, cellular use and absence of automatic checks or camera-data uploads. Update effective date. |
| `docs/AGENT-EYE.md` | Qualify “no outbound connections” as camera behavior; add update controls, failures and phone procedure. |
| `docs/DECISIONS.md` | Record direct public GitHub, manual-only checks, signer/platform verification boundary, no partial resume, icon generation and release choice. |
| `docs/FEATURES.md` | Separate implemented updater/icon capabilities, automated verification and actual phone acceptance. |
| `docs/GOTCHAS.md` | Cover bootstrap, unknown-source permission, offline/rate-limited checks, lost installer confirmation, signer incompatibility and forward-only recovery. |
| `docs/PLAY-LISTING.md` | Link the generated 512px asset; disclose update networking and flag a separate Play distribution-policy review before submission. |

Existing distribution/features text still says publication is unexercised, contrary to the owner’s brief. Correct that specific status using the owner-confirmed publication; do not infer that all phone checks passed. Command Deck also contains stale deployment-status wording, so use its code as the implementation reference.

## 7. Open questions / assumptions

- **Release choice:** assume pairing and updater work can ship together as **1.1.0 / 2**, provided code 2 remains unpublished. Then use a legitimate later release, ordinarily **1.1.1 / 3**, for in-app acceptance. If `1.0.1 / 2` publishes first, use **1.1.0 / 3**. If pairing alone publishes as `1.1.0 / 2`, release this feature under a new minor version and higher code; never replace published artifacts.
- **Bootstrap interpretation:** assume one additional manual in-place installation for existing users is acceptable. If exactly one lifetime manual installation is mandatory, the first manually installed release must be an updater-bearing bridge; that changes release sequencing.
- **Signature requirement:** plan uses Command Deck’s certificate/hash preflight plus Android’s cryptographic install verification. Independent cryptographic verification before session creation would be additional scope requiring an Android-supported verifier decision.
- **Privacy:** assume manual checks/downloads over the system-selected network are acceptable when plainly disclosed. No background-update setting is included.
- **Signer continuity:** one current signer, no key rotation or separate Play-signing track. Such changes require an explicit migration design.
- **Artwork:** the specified navy/teal aperture eye is the implementation design; acceptance judges legibility, adaptive masking and distinction on the phone.
- **External behavior:** GitHub/CDN redirect behavior and OEM installer UX must be confirmed during acceptance. Host changes should fail clearly, not silently broaden outbound destinations.