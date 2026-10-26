> Historical implementation plan. The 2026-10-25 structure review superseded the flat
> release paths below; see [STRUCTURE.md](../../STRUCTURE.md) for current placement.

## 1. Goal

Create a public GitHub release pipeline for `dirtyredz/agent-eye`: CI produces unsigned, minified APK and AAB artifacts in a draft release; the owner signs them locally using 1Password DesktopAuth/Windows Hello and publishes the verified APK for installation from a plain link.

Completion requires a successful phone installation from GitHub Releases and a subsequent signed release updating that installation in place. Play submission remains future work.

## 2. Placement

Use the family-command-center workflow and signing implementation as the reference, adapting their contract/orchestration/vault separation. Do not import product catalogs, Cloudflare, Access, Firebase, deployment credentials, or unattended signing.

Agent Eye currently has no root `package.json`, release tooling, or workflows. Introduce the following responsibility-based tree:

| Path | Responsibility and placement |
|---|---|
| `.github/workflows/ci.yml` | PR/push fast gate; callable by the release workflow. |
| `.github/workflows/android-release.yml` | Build unsigned release artifacts and create/update a draft. |
| `package.json`, `package-lock.json` | Minimal private Node 22 tooling package. Scripts: `test`, `release`, `secrets`. Pin the development-only 1Password SDK; preserve dependency-free camera runtime. |
| `src/android/releases/cli.mjs` | Thin argument parsing and command dispatch; both npm command aliases enter here. |
| `src/android/releases/contract.mjs` | Repository/package identity, version/tag validation, asset names, manifest schema and pinned verification-tool metadata. |
| `src/android/releases/artifacts.mjs` | Stage build outputs, calculate hashes, and create/validate unsigned and signed manifests. |
| `src/android/releases/github.mjs` | GitHub release/run inspection, pagination, downloads, uploads and publication. |
| `src/android/releases/release-sign.mjs` | Local signing transaction and recovery ordering, with injected dependencies for tests. |
| `src/android/releases/apk-signing.mjs` | APK inspection, alignment, signing and certificate verification. |
| `src/android/releases/bundle-signing.mjs` | AAB inspection, JAR signing and signature verification. |
| `src/android/releases/owner-setup.mjs` | Reviewable owner bootstrap plan and explicit execution for public repository creation/configuration. |
| `src/android/releases/signing-certificate.json` | Public package identity, certificate SHA-256, alias and first release version code. Never private key material. |
| `src/android/signing/vault/vault.mjs` | Lazy-loaded 1Password SDK DesktopAuth adapter. |
| `src/android/signing/keys/key-provisioning.mjs` | Dedicated Agent Eye key generation, vault storage and recovery verification. |
| `src/android/signing/key-material.mjs` | Windows-protected temporary key storage and cleanup. |
| `src/android/tooling/process.mjs` | Shared subprocess execution with bounded output and secret redaction. |
| Adjacent `*.test.mjs` files | Tests for release contracts, transaction failures, signing adapters and owner commands. |
| `android/version.properties` | Authoritative `versionName` and `versionCode`, consumed by Gradle and release tooling. |
| `android/signing.example.json` | Placeholder local configuration containing account/vault/item references only. Actual `android/signing.local.json` is ignored. |
| `android/build.gradle.kts` | Pinned ktlint plugin configuration. |
| `android/app/build.gradle.kts` | Version loading, unsigned release build type, R8/resource shrinking and generated privacy asset registration. |
| `android/app/proguard-rules.pro` | Documented location for narrowly justified application rules; initially no blanket keep rules. |
| `.editorconfig` | Kotlin/Kotlin-script formatting policy. |
| `android/app/src/main/java/local/agenteye/ui/PrivacyPolicyDialog.kt` | Display bundled privacy text without network access or another rendering dependency. |
| `android/app/src/main/java/local/agenteye/ui/MainActivity.kt` | Add the Privacy policy entry point. |
| `android/app/src/main/res/values/strings.xml` | Privacy UI labels. |
| `docs/PRIVACY.md` | Canonical public privacy policy, also copied into generated Android assets during builds. |
| `docs/ANDROID-DISTRIBUTION.md` | Owner setup, signing, publication, recovery and installation runbook. |
| `docs/PLAY-LISTING.md` | Future Play enrollment/submission checklist and unresolved policy requirements. |
| `.gitignore` | Ignore Node dependencies, AABs, keystores, signing-local configuration and generated signing artifacts. |

Formatting-only changes may touch every existing `.kt` file under Android main/test sources and the three Gradle `.kts` files. These files are currently small; no existing application extraction is warranted. Keep every new code file below roughly 800 lines.

The new release infrastructure belongs under `src/android/`, separate from `src/camera/`. Update `STRUCTURE.md`’s enforced Layout bullets before introducing it. Do not extend the camera CLI with release commands.

## 3. Sequence

1. **Establish the work item and boundaries.**  
   Before implementation writes, follow `docket guide`, find or create the appropriate item through `dk`, and claim it in the implementation worktree. Do not invent an ID or treat open notes as approved requirements. Update the stale “sideload/debug signing only” policy in `CLAUDE.md` and `docs/DECISIONS.md`; this owner brief supersedes it.

2. **Introduce deterministic versioning and release builds.**  
   Move the current hard-coded version into `android/version.properties`. Start with `versionName=1.0.0`, `versionCode=1`; require tags of exactly `v<versionName>`. Each subsequently published build gets a strictly greater positive version code, including future Play uploads. Do not derive codes from workflow run numbers or timestamps.

   Configure release builds with R8 optimization, resource shrinking and `proguard-android-optimize.txt`. Release signing configuration must remain absent; never fall back to the debug key. Preserve package `local.agenteye`, SDK levels 24/36/36, JDK 21, JVM bytecode 17 and the existing Android dependency pins.

   No application caller uses version constants or reflection. Default Android/AndroidX consumer rules are the starting point; add targeted rules only when verification demonstrates a need.

3. **Add ktlint and the fast gate.**  
   Use ktlint rather than detekt: this task needs formatting enforcement without a second analysis configuration. Pin the Gradle plugin and engine independently; begin with plugin `14.2.0` and stable ktlint `1.8.0`, then verify compatibility with the existing toolchain. The plugin supports check/format tasks and recommends explicitly pinning the engine. [Plugin documentation](https://github.com/JLLeitschuh/ktlint-gradle), [ktlint releases](https://github.com/ktlint/ktlint/releases).

   Format existing Kotlin sources once in a mechanical commit. Cover production sources, tests and Gradle scripts; exclude generated/build directories. Do not introduce a baseline or `ignoreFailures`.

   **Workflow YAML outline:**

   | Workflow | Keys and execution outline |
   |---|---|
   | `ci.yml` | `on`: pull requests, pushes to `main`, `workflow_call`. `permissions`: contents read. Concurrency cancels superseded PR/branch runs. Use pinned action revisions, Node 22, JDK 21, SDK platform 36/build-tools 35.0.0 and the checked-in wrapper. Run `npm ci`, `node --test`, then `ktlintCheck assembleDebug testDebugUnitTest`. Upload failure reports. No secrets or privileged PR trigger. |
   | `android-release.yml` | `on`: pushed `v*` tags and manual retry with an existing tag. Repository-wide release concurrency; do not cancel an active release. Validate tag syntax, version agreement and commit ancestry from `main`. Call the fast gate, then build `assembleRelease bundleRelease lintRelease`. Stage outputs and manifest. A separate draft job receives artifacts and gets contents-write permission. It creates an explicitly draft release and attaches unsigned artifacts; it never signs or publishes. |

   Only the draft-writing job receives write access. No 1Password configuration or signing credentials belong in CI. Disable persisted checkout credentials in build jobs.

4. **Define and enforce the artifact contract.**  
   Draft assets are `agent-eye-unsigned.apk`, `agent-eye-unsigned.aab`, `release-unsigned.json` and `mapping.txt`. Final assets are `agent-eye.apk`, `agent-eye.aab`, `release.json`, `SHA256SUMS` and `mapping.txt`.

   The manifest records schema version, repository, tag, exact commit, workflow run identity, application ID, version name/code, SDK levels, artifact lengths and SHA-256 values. Final metadata additionally records the signer certificate and original unsigned hashes.

   Verify the identity embedded in both artifacts against the contract. Reject signed inputs, debuggable release artifacts, unexpected names, missing outputs or version mismatches. Pin the bundletool version and download checksum used for AAB validation; do not resolve “latest.”

   CI retries may resume only the same draft/tag/commit. Published releases and conflicting existing assets are immutable to this tooling.

5. **Implement owner key provisioning and the local signing contract.**

   **Command contract:**

   | Command | Behavior |
   |---|---|
   | `npm run release -- setup --dry-run` | Print the public-repository bootstrap commands and required settings. |
   | `npm run secrets -- generate-key --dry-run` | Describe dedicated key creation, vault writes and public certificate output. |
   | `npm run secrets -- verify-key --dry-run` | Describe vault recovery and certificate verification. |
   | `npm run secrets -- release-sign --tag <tag> --release-id <id> --dry-run --publish` | Describe validation, signing, asset replacement and publication. |
   | Corresponding `--execute` commands | Perform the owner-attended operation. `--publish` explicitly requests publication after successful verification. |

   Default to dry-run; reject conflicting flags. Dry-run may read public repository configuration, but must not initialize the SDK, read secrets, launch tools, access the network or write files. Test that boundary.

   Reuse family-command-center’s `@1password/sdk` DesktopAuth approach, initially pinning its demonstrated `0.5.0` version exactly. Use dedicated Agent Eye vault items and one SDK client per process. Never copy the other project’s account/key references or introduce service-account fallback.

   Owner key generation creates an RSA-4096 PKCS12 key with approximately 30-year validity, stored as a 1Password Document plus password item. Read it back and verify the certificate before reporting success. Refuse existing keys/items/pins; report partial provisioning for owner recovery rather than regenerating silently.

   The release-sign transaction must:

   - Validate a clean local checkout matching the tagged commit, repository, draft ID/tag, successful trusted workflow run, expected assets and hashes **before accessing 1Password**. Compare draft artifacts with the corresponding Actions artifact record; a checksum downloaded beside an artifact alone is not provenance.
   - Paginate release history and enforce version monotonicity. Missing or malformed metadata in an existing Agent Eye release fails closed.
   - Recover the key through DesktopAuth and match it to the committed public certificate pin.
   - Materialize it outside the checkout in a unique directory whose Windows ACL has been restricted and verified **before writing key bytes**. Do not copy the reference’s `chmod` assumption.
   - Align the APK before signing with `apksigner`; verify alignment, supported signatures, exactly one pinned signer, package/version and non-debuggable status. Altering an APK after signing invalidates its signature. [Android apksigner documentation](https://developer.android.com/tools/apksigner).
   - Sign the AAB with `jarsigner`, then verify its signature, pinned signer and bundle identity. Handle expected self-signed-certificate trust warnings explicitly; do not accept unsigned entries or unexplained verification failures. AAB signing uses `jarsigner`, not `apksigner`. [Android command-line build documentation](https://developer.android.com/build/building-cmdline).
   - Pass passwords through child-only environment references, never command arguments, logs or persisted environment files.
   - Upload final artifacts and metadata, download them again and verify their hashes. Remove only the exact unsigned assets, then publish last when requested.

   Hold a local signing lock and recheck release/version state immediately before publication. Retries may reuse matching uploaded assets; conflicting assets must fail without `--clobber`. Any failure leaves the release unpublished. Cleanup runs on normal completion, errors and catchable termination; document recovery after forced termination.

6. **Add privacy disclosure and future Play preparation.**  
   Make `docs/PRIVACY.md` the single policy source. Gradle copies it into generated assets; `PrivacyPolicyDialog` displays it locally. `MainActivity` only launches the dialog.

   Initially use the public rendered repository page as the policy URL: `https://github.com/dirtyredz/agent-eye/blob/main/docs/PRIVACY.md`. This avoids introducing another hosting workflow.

   State precisely: Agent Eye collects no data for the developer, has no analytics/cloud upload/accounts/ads, and sends requested photographs only to an authenticated client on the supported LAN. Explain camera access, in-memory phone handling, PC snapshot retention until replacement/cleanup, session tokens, plain HTTP, deletion controls and the separate behavior of downstream consumers. Identify Agent Eye and a real privacy contact mechanism. Do not claim photographs never leave the phone or that GitHub itself collects no access information.

   Google requires public privacy disclosure and an in-app policy link or text. Its Data safety definition includes off-device transfers; therefore “local-only” cannot automatically become “no collection” in the Play form. The checklist must assess LAN photo transfer explicitly and must not claim encryption in transit. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

7. **Provide the owner-run setup and release runbook.**

   | Stage | Owner action |
   |---|---|
   | Local prerequisites | Confirm JDK/Android tools, Node 22, authenticated `gh`, 1Password Desktop integration and Windows Hello. Run `npm ci`. |
   | Key setup | Review dry-run, execute `generate-key`, execute `verify-key`, retain a recovery backup, then commit only the public certificate pin. |
   | Public repository | Review tracked files/history and the setup dry-run. Execute setup to create `dirtyredz/agent-eye` publicly, set the intended origin and push the reviewed branch. Fail rather than replace an unexpected existing origin/repository. |
   | Secret setup | Configure only local 1Password item references. GitHub needs no custom signing secrets, PAT, service-account token or keystore; the workflow uses its automatic `GITHUB_TOKEN`. |
   | First release | Commit version metadata and pass the gate; owner runs `git tag v1.0.0` and `git push origin v1.0.0`. Record the resulting draft release ID. |
   | Sign and publish | Review `release-sign --dry-run --publish`, then run the equivalent `--execute --publish` command with the explicit tag and release ID. |
   | Phone installation | Open `https://github.com/dirtyredz/agent-eye/releases/latest`, download `agent-eye.apk`, authorize installation for that browser when Android requests it, and install. Explain that the AAB is for Play upload. |
   | Update acceptance | Commit `1.0.1` with version code `2`, repeat release/sign/publish, and install over `1.0.0` without uninstalling. |

   Agents may execute the offline dry-runs and fixture tests; real owner provisioning, repository publication and signing commands are clearly separated in the runbook.

## 4. Tests

Add adjacent Node tests with injected vault, process, filesystem and GitHub adapters:

- **`contract.test.mjs` / `artifacts.test.mjs`:** tag/version agreement, malformed versions, package mismatch, artifact identity, hashes, schema validation and missing artifacts.
- **`github.test.mjs`:** pagination beyond 100 releases, missing release metadata, published-release refusal, conflicting assets and exact unsigned-asset removal.
- **`release-sign.test.mjs`:** validation precedes vault access; changed commit/run/hash/certificate is rejected; no publication after signing, upload or download-verification failure; publish occurs last; interrupted upload retries safely.
- **`apk-signing.test.mjs` / `bundle-signing.test.mjs`:** correct distinct tool chains, signer/version checks, password-free arguments and unsigned/mixed-signature rejection.
- **`key-provisioning.test.mjs` / `key-material.test.mjs`:** refusal to overwrite, recovery verification, partial provisioning, ACL failure before key writes and cleanup on failure.
- **`cli.test.mjs` / `owner-setup.test.mjs`:** all dry-runs have zero external effects; invalid execution flags and unexpected repository identities fail closed.
- **`process.test.mjs`:** secret redaction from both successful output and subprocess failures.

Run a separate integration check with a disposable test key and real Android/JDK tools. Never use the owner’s key in automated tests.

Retain existing JVM and camera-client tests. Formatting needs no new behavioral tests; privacy presentation and R8/CameraX behavior require the device checks below.

## 5. Verification

This planning session read source and documentation only; no builds, tests, signing or deployment were performed.

The implementer must run:

| Purpose | Command or check |
|---|---|
| Node installation/tests | `npm ci`, then `node --test` from the repository root. |
| Windows fast gate | `.\android\gradlew.bat -p android ktlintCheck assembleDebug testDebugUnitTest` |
| Linux fast gate | `./gradlew ktlintCheck assembleDebug testDebugUnitTest` from `android/`. |
| Release build | `.\android\gradlew.bat -p android assembleRelease bundleRelease lintRelease` |
| Formatting maintenance | `.\android\gradlew.bat -p android ktlintFormat`, followed by the fast gate. |
| Work tracking | `dk check` before pushing; release the work-item claim when finished. |

Use the wrapper, normal user Gradle cache and one heavyweight local Gradle build at a time, as the existing runbook requires. Inspect minified outputs, the merged release manifest and R8 mapping. Confirm both unsigned artifacts are actually unsigned.

Acceptance evidence must include:

- A real CI run creates a draft with both unsigned artifacts, correct versions and commit-bound metadata.
- Local owner signing verifies the pinned certificate and publishes only after uploaded assets pass verification.
- An unauthenticated phone browser can open the published release and download/install the APK.
- The minified app passes the existing `docs/AGENT-EYE.md` device procedure, especially preview, authenticated fresh capture, rotation, screen-off operation and Stop.
- The privacy policy is publicly readable and available inside the app without connectivity.
- A second release installs over the first without uninstalling; package, certificate and increased version code are recorded.

Drafts are not the public download destination. Published releases must be complete before publication, including when GitHub release immutability is enabled. [GitHub release guidance](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## 6. Docs

| Document | Update |
|---|---|
| `CLAUDE.md` | Replace sideload/debug-only policy with unsigned CI, attended local signing and future Play preparation. |
| `STRUCTURE.md` | Add `src/android/`, workflows and distribution/privacy documents to Layout; retain responsibility and size boundaries. |
| `README.md` | Add the public release download link, brief installation instructions and distribution runbook link. |
| `docs/ARCHITECTURE.md` | Describe the build/sign/publish boundary separately from the local-only camera runtime. |
| `docs/DECISIONS.md` | Record key custody, APK/AAB signing, version scheme, ktlint, R8 and Play certificate constraints. |
| `docs/FEATURES.md` | Track pipeline implementation and actual CI/device acceptance separately; preserve unverified status until demonstrated. |
| `docs/GOTCHAS.md` | Explain debug-to-release migration, upload-key reset implications, channel incompatibility, interrupted signing recovery and plain-HTTP disclosure. |
| `docs/AGENT-EYE.md` | Extend the fast gate and point installation instructions to signed releases while retaining the existing physical acceptance procedure. |
| `docs/ANDROID-DISTRIBUTION.md` | Include the owner commands, artifact contract, dry-run boundary, retry/recovery steps and two-version acceptance record. |
| `docs/PRIVACY.md` | Provide accurate developer-no-collection disclosure with LAN transfer, retention and contact details. |
| `docs/PLAY-LISTING.md` | Checklist: account/package verification, signing enrollment, AAB upload, current target/API/native-library requirements, listing assets and launcher icon, privacy URL/contact, Data safety, content rating, camera foreground-service declarations, reviewer instructions and applicable testing requirements. |

The Play checklist must link current official requirements rather than freeze future submission deadlines. The camera foreground service needs an accurate declaration and supporting explanation. [Play foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en-GB).

## 7. Open questions / assumptions

- **Play signing and update compatibility:** Implement the requested shared local key for sideload APK signing and AAB upload signing. With a different Google-managed Play app-signing key, those installations cannot cross-update. Matching upload signatures do not change that. If cross-channel updates become mandatory, decide before Play enrollment whether to supply the existing sideload key as Play’s app-signing key and introduce a separate upload key. Alternatively, later distribute Play-signed APKs obtained from Play. [Android app signing](https://developer.android.com/studio/publish/app-signing), [Android distribution guidance](https://android-developers.googleblog.com/2021/06/the-future-of-android-app-bundles-is.html).

- **Recommended initial policy:** Keep one application ID and document separate GitHub/Play update tracks. Switching between differently signed tracks requires uninstall/reinstall and may discard local state. This accepts that limitation without introducing flavors now. An upload-key reset must not silently replace the established sideload signer.

- **Existing debug installation:** Its certificate differs from the release certificate. The first migration requires a deliberate uninstall; subsequent release-to-release acceptance must not uninstall.

- **“Play-ready” scope:** Deliver a valid signed AAB and an actionable checklist, not a promise of Play approval. Plain HTTP camera transfers need policy/security review before submission; transport redesign is outside this pipeline change.

- **Privacy contact and listing identity:** Use Agent Eye and the owner’s public GitHub inquiry mechanism initially. The owner supplies the final Play contact details before submission; do not invent an email address.

- **Hosting and branch:** Assume `main` and the public repository-hosted privacy page. GitHub Pages can be added later without changing the signing architecture.
