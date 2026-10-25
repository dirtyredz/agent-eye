# Android distribution

CI builds unsigned optimized APK/AAB artifacts and creates a **draft**. The owner signs
locally using 1Password DesktopAuth/Windows Hello. No custom GitHub secrets, PAT,
service-account token, keystore or password belongs in CI. Camera runtime remains local-only.

## Owner steps, in order

Agents must not execute provisioning, setup, real signing, pushes or publication.
All CLI commands default to dry-run. Dry-run performs no subprocess calls, network access,
SDK initialization, secret reads or writes. `--execute` is explicit; conflicting flags fail.

1. Review the complete tracked tree and history before publication. Install JDK 21, Node 22,
   Android SDK platform 36/build-tools 35.0.0, authenticated `gh`, and 1Password Desktop
   with developer SDK integration and Windows Hello enabled. Use the normal Gradle cache.
   Configure `ANDROID_HOME` or `android/local.properties`. Run:

   ```powershell
   npm ci
   Copy-Item android/signing.example.json android/signing.local.json
   ```

   Edit only your account ID, dedicated vault ID and Agent Eye item titles in the ignored
   local JSON. Do not copy references from another application. No secret values go in it.

2. Review then provision exactly once. This creates an RSA-4096 PKCS12 key (10950 days),
   stores a Document and separate password item, reads them back and verifies recovery:

   ```powershell
   npm run secrets -- generate-key --dry-run
   npm run secrets -- generate-key --execute
   npm run secrets -- verify-key --dry-run
   npm run secrets -- verify-key --execute
   git add src/android/releases/signing-certificate.json
   git commit -m "Pin Agent Eye release certificate"
   ```

   Retain an owner-managed encrypted recovery backup and test recovery. Commit only the
   public pin (package, alias, SHA-256 and first version code). Never commit the key/password.
   Existing items or a nonempty pin are refused. Partial provisioning is not permission to
   regenerate: inspect the items, recover the existing key and reconcile its public pin.

3. Review repository setup. It refuses any existing origin or repository, including ambiguous
   lookup failures. Execute once from the reviewed branch; it creates the public repository,
   adds the intended origin, pushes HEAD to main and configures read-only default workflow permissions:

   ```powershell
   npm run release -- setup --dry-run
   npm run release -- setup --execute
   git fetch origin main
   ```

   Equivalent printed commands are reviewable in the dry-run. Enable Actions and issues;
   permit the release draft job's explicit contents-write grant. Consider branch/tag protections
   and immutable published releases. **Create no signing secrets in GitHub.** If setup stops
   after creating the repository, inspect and complete the printed steps manually; it will
   not overwrite an existing repository/origin on retry.

4. Confirm `android/version.properties` is `1.0.0` / `1`. Commit all source and pin changes.
   Run the gate below, then tag the reviewed main commit:

   ```powershell
   git tag v1.0.0
   git push origin v1.0.0
   gh run list --repo dirtyredz/agent-eye --workflow android-release.yml
   gh release view v1.0.0 --repo dirtyredz/agent-eye --json databaseId,isDraft,targetCommitish
   ```

   Wait for the complete workflow to succeed. Record the draft database ID. A manual retry
   must run at the same tag: `gh workflow run android-release.yml --ref v1.0.0 -f tag=v1.0.0`.
   Prefer `gh run rerun RUN_ID --failed` after a draft-job failure; it retains the successful
   build's original artifact name/run attempt. Signing checks that build job and the eventual
   successful workflow run separately.
   Existing assets are never clobbered; changed build bytes or metadata require owner review.

5. Check out the exact clean tagged commit, with the committed public pin and an up-to-date
   origin/main. Resolve pinned verification tools, then review and execute signing:

   ```powershell
   git fetch origin main --tags
   git switch --detach v1.0.0
   .\android\gradlew.bat -p android releaseTools
   .\android\gradlew.bat --stop
   npm run secrets -- release-sign --tag v1.0.0 --release-id DRAFT_ID --dry-run --publish
   npm run secrets -- release-sign --tag v1.0.0 --release-id DRAFT_ID --execute --publish
   ```

   Replace `DRAFT_ID` with the positive numeric ID. Omitting `--publish` leaves a complete,
   signed draft; rerunning with `--publish` verifies it again and publishes last.

6. On the phone, open https://github.com/dirtyredz/agent-eye/releases/latest without signing
   in. Download **agent-eye.apk**, allow installation from that browser when Android asks,
   and install. The AAB is for future Play upload, not direct phone installation. A previous
   debug-signed installation must be deliberately uninstalled once because its signer differs.
   Run the complete physical procedure in [AGENT-EYE.md](AGENT-EYE.md), including the local
   Privacy policy dialog with WAN disconnected.

7. Return to the development branch, set `versionName=1.0.1`, `versionCode=2`, commit, pass
   the gate and push the reviewed main update. Tag/push `v1.0.1`; repeat signing with its new
   draft ID. Install over 1.0.0 **without uninstalling**. Record package, certificate, version
   codes, phone model/API and every device check. Every later published code must increase,
   including Play uploads; never reuse a code or derive it from a workflow counter.

   For this implementation worktree, the commands are:

   ```powershell
   git switch release-pipeline
   @('versionName=1.0.1', 'versionCode=2') | Set-Content -Encoding ascii android/version.properties
   git add android/version.properties
   git commit -m "Release Agent Eye 1.0.1"
   node --test
   .\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest lintRelease releaseTools
   node src/android/releases/signing-smoke.mjs
   .\android\gradlew.bat --stop
   git push origin HEAD:main
   git tag v1.0.1
   git push origin v1.0.1
   gh run list --repo dirtyredz/agent-eye --workflow android-release.yml
   gh release view v1.0.1 --repo dirtyredz/agent-eye --json databaseId,isDraft,targetCommitish
   git fetch origin main --tags
   git switch --detach v1.0.1
   npm run secrets -- release-sign --tag v1.0.1 --release-id SECOND_DRAFT_ID --dry-run --publish
   npm run secrets -- release-sign --tag v1.0.1 --release-id SECOND_DRAFT_ID --execute --publish
   ```

   Substitute the second numeric draft ID, wait for CI success before signing, and respect
   any branch protection/review requirements when updating main.

## Gate and artifact contract

Inspect other Gradle processes before starting a build; serialize heavyweight builds. In the
worktree, with the normal Gradle cache and SDK configured:

```powershell
npm ci
node --test
.\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest
.\android\gradlew.bat -p android lintRelease releaseTools
node src/android/releases/signing-smoke.mjs
.\android\gradlew.bat --stop
dk check
```

Use `ktlintFormat` only for intentional formatting. The explicit smoke generates and removes
a throwaway local test key and signs/verifies actual release artifacts; it never uses 1Password.
The CLI dry-run remains side-effect free even when that test key exists. CI runs the read-only
fast gate, then unsigned release builds/lint, then the separately privileged draft job.

Draft: `agent-eye-unsigned.apk`, `agent-eye-unsigned.aab`, `release-unsigned.json`, `mapping.txt`.
Final: `agent-eye.apk`, `agent-eye.aab`, `release.json`, `SHA256SUMS`, `mapping.txt`.
Manifest schema 1 binds repository, exact commit/tag, workflow run/attempt, package,
version, SDK 24/36/36, sizes and SHA-256 values. Final metadata adds signer and unsigned hashes.
The signer compares draft bytes to the corresponding trusted Actions artifact, not merely a
checksum beside the download. Retain Actions artifacts until signing (90-day CI retention).
Missing/expired provenance fails closed. All published history is paginated; missing metadata
or non-increasing version codes fails closed.

Bundletool 1.18.1 is resolved from Google Maven with Gradle; the executable jar SHA-256 is
pinned in `bundle-signing.mjs`. `releaseTools` stages its transitive dependencies. APK signing
uses zipalign before apksigner; AAB uses jarsigner and verifies all entries. Only expected
self-signed trust/timestamp warnings are accepted. See [apksigner](https://developer.android.com/tools/apksigner)
and [command-line signing](https://developer.android.com/build/building-cmdline).

## Recovery and safety boundaries

Real keys are materialized outside the checkout under a unique `agent-eye-key-*` directory.
The current user's SID owns the protected DACL with one explicit full-control ACE, verified
before bytes are written. Passwords are child-only environment references, never argv or logs.
Normal completion, errors and catchable termination clean keys. Forced termination/power loss
cannot run cleanup: inspect and remove only the abandoned directory after confirming the
process is stopped; do not delete unrelated temporary directories.

`.release-sign.lock` prevents concurrent local signers. After a crash inspect its PID/time and
confirm no signer remains before removing it. `.tmp/releases/<tag>-<commit>/` deliberately
retains **public signed outputs only** for interrupted-upload retries. Keep that cache until
publication; a matching existing asset is reused, a conflicting one fails without clobber.
If the cache is lost after a partial upload, restore the original cache rather than generating
different signatures. Complete final sets can be recovered from the draft and reverified.
Unsigned assets are removed only after every final asset has been downloaded and verified.
Published releases are immutable to this tooling. Never fix a released artifact in place.

GitHub and Play are separate update tracks if Play uses another app-signing certificate.
Matching AAB upload signatures do not make those tracks compatible. Decide whether to enroll
the existing sideload key as the Play app-signing key and introduce a separate upload key
before enrollment if cross-channel updates matter. An upload-key reset must not replace the
sideload key. See [Android signing](https://developer.android.com/studio/publish/app-signing).

## Acceptance record

CI draft creation, real owner signing/publication, unauthenticated browser install, minified
CameraX device procedure, offline policy display and 1.0.0-to-1.0.1 in-place update are **not yet
exercised**. No remote is created by implementation. Local verification is recorded separately;
a successful build or disposable-key smoke does not establish phone or GitHub acceptance.
