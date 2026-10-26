# Android distribution

CI builds unsigned optimized APK/AAB artifacts and creates a **draft**. The owner signs
locally using 1Password DesktopAuth/Windows Hello. No custom GitHub secrets, PAT,
service-account token, keystore or password belongs in CI. Camera runtime remains local-only.

## Release steps, in order

**Owner steps are checked-in PowerShell scripts** in `scripts/release/` (`provision.ps1`,
`sign-release.ps1`), not npm/node commands. They work from any directory (cmd, PowerShell 5.1/7, even
`C:\Windows\System32`), find the repo from their own path and call the Node CLI underneath. Each takes
`-DryRun` (changes nothing) and writes a timestamped, secret-redacted log to `<repo>\.tmp\owner-runs\`,
ending with one line, `OK (exit 0) log: <path>` or `FAILED (exit N) log: <path>`; that path is the Node CLI's own
log (authoritative; the script's short wrapper log is named on the line before). Agents read that log themselves. Exit codes are in each script's help (`Get-Help <script>.ps1 -Full`). The Node CLI
(`node <repo>\src\android\releases\cli\cli.mjs <command> --dry-run`) stays for agents and CI.

The agent runs these scripts itself (`provision.ps1`, `sign-release.ps1`, `vault-repair.ps1`), dry-run
first; the owner only approves the 1Password / Windows Hello prompt that a real run raises. Agents
also push, tag and dispatch releases once the review gate passes.
All CLI commands default to dry-run. Dry-run performs no subprocess calls, network access,
SDK initialization, secret reads or writes. `--execute` is explicit; conflicting flags fail.

1. Review the complete tracked tree and history before publication. Install JDK 21, Node 22,
   Android SDK platform 36/build-tools 35.0.0, authenticated `gh`, and 1Password Desktop
   with developer SDK integration and Windows Hello enabled. Use the normal Gradle cache.
   Configure `ANDROID_HOME` or `android/local.properties`. Run:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\provision.ps1 -DryRun
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\provision.ps1
   ```

   The script runs `npm ci` when `node_modules` is missing, then `provision`, then `verify-key`.
   `provision` is the whole owner flow: `setup-vault` creates the dedicated "Agent Eye Signing"
   vault if absent and writes the ignored `android/signing.local.json` itself (account from
   `--account`, `OP_ACCOUNT_ID`, an existing config, else the owner's default account), then
   `generate-key` runs below. Only Windows Hello approvals are needed; no hand-editing and no
   web-UI steps. `setup-vault` alone can be run first; a correct rerun reports "already set up" and
   a config pointing at another vault is never overwritten. If the installed SDK cannot create
   vaults the command says so and asks for the one vault to be created once.

2. Provisioning (step 1) happens exactly once. It creates an RSA-4096 PKCS12 key (10950 days),
   stores a Document and separate password item, reads them back and verifies recovery
   (`provision.ps1` already ran `generate-key` and `verify-key`). Only the public pin is left to commit:

   ```powershell
   git add src/android/releases/signing-certificate.json
   git commit -m "Pin Agent Eye release certificate"
   ```

   Retain an owner-managed encrypted recovery backup and test recovery. Commit only the
   public pin (package, alias, SHA-256 and first version code). Never commit the key/password.
   Existing items or a nonempty pin are refused. Partial provisioning is not permission to
   regenerate: inspect the items, recover the existing key and reconcile its public pin.
   If the Document was never stored and no public pin/release exists, the owner may explicitly
   remove the orphan password item before a new attempt. Never use that procedure to replace
   an established signer. The tooling does not perform that recovery decision automatically.

3. **Done:** the GitHub repository and origin already exist, so `setup` refusing an existing
   origin is expected and nothing remains here. (Historical text for a fresh clone follows.)
   Repository setup refuses any existing origin or repository, including ambiguous
   lookup failures. It would run once from the reviewed branch; it creates the public repository,
   adds the intended origin, pushes HEAD to main and configures read-only default workflow permissions:

   ```powershell
   node src\android\releases\cli\cli.mjs setup --dry-run
   node src\android\releases\cli\cli.mjs setup --execute
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
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\sign-release.ps1 -Tag v1.0.0 -Publish -DryRun
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\sign-release.ps1 -Tag v1.0.0 -Publish
   ```

   The script is a pass-through: the CLI's `release-sign` looks the numeric draft ID up with `gh` by tag
   (override with `-ReleaseId`; a published release is refused), runs `gradlew releaseTools` then `--stop`
   (`-SkipReleaseTools` skips it), then signs. Omitting
   `-Publish` leaves a complete, signed draft; rerunning with `-Publish` verifies it again and publishes last.

6. On the phone, open https://github.com/dirtyredz/agent-eye/releases/latest without signing
   in. Download **agent-eye.apk**, allow installation from that browser when Android asks,
   and install. The AAB is for future Play upload, not direct phone installation. A previous
   debug-signed installation must be deliberately uninstalled once because its signer differs.
   Run the complete physical procedure in [AGENT-EYE.md](AGENT-EYE.md), including the local
   Privacy policy dialog with WAN disconnected.

7. Return to the development branch, set `versionName=1.1.0`, `versionCode=2`, commit, pass
   the gate and push the reviewed main update. Tag/push `v1.1.0`; repeat signing with its new
   draft ID. Install over 1.0.0 **without uninstalling**. Record package, certificate, version
   codes, phone model/API and every device check. Every later published code must increase,
   including Play uploads; never reuse a code or derive it from a workflow counter.

   For this implementation worktree, the commands are:

   ```powershell
   git switch release-pipeline
   @('versionName=1.1.0', 'versionCode=2') | Set-Content -Encoding ascii android/version.properties
   git add android/version.properties
   git commit -m "Release Agent Eye 1.1.0"
   node --test
   .\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest lintRelease releaseTools
   node src/android/signing/signing-smoke.mjs
   .\android\gradlew.bat --stop
   git push origin HEAD:main
   git tag v1.1.0
   git push origin v1.1.0
   gh run list --repo dirtyredz/agent-eye --workflow android-release.yml
   gh release view v1.1.0 --repo dirtyredz/agent-eye --json databaseId,isDraft,targetCommitish
   git fetch origin main --tags
   git switch --detach v1.1.0
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\sign-release.ps1 -Tag v1.1.0 -Publish -DryRun
   powershell -NoProfile -ExecutionPolicy Bypass -File C:\Users\dirty\projects\agent-eye\scripts\release\sign-release.ps1 -Tag v1.0.1 -Publish
   ```

   Wait for CI success before signing, and respect
   any branch protection/review requirements when updating main.

## Gate and artifact contract

Inspect other Gradle processes before starting a build; serialize heavyweight builds. In the
worktree, with the normal Gradle cache and SDK configured:

```powershell
npm ci
node --test
.\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest
.\android\gradlew.bat -p android lintRelease releaseTools
node src/android/signing/signing-smoke.mjs
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
pinned in `src/android/signing/artifacts/bundle-signing.mjs`. `releaseTools` stages its transitive dependencies. APK signing
uses zipalign before apksigner; AAB uses jarsigner and verifies all entries. Only expected
self-signed trust/timestamp warnings are accepted. See [apksigner](https://developer.android.com/tools/apksigner)
and [command-line signing](https://developer.android.com/build/building-cmdline).

## Recovery and safety boundaries

**Failed provisioning.** The error names the stage (`store-password`, `store-document`, `recover`, `verify`,
`pin-write`) and carries the underlying cause (redacted in the log). Never regenerate over an existing key. Then:

```
powershell -NoProfile -ExecutionPolicy Bypass -File C:Usersdirtyprojectsagent-eyescriptseleaseault-repair.ps1 -Inspect
```

lists the Agent Eye items (titles, categories, created times; never values). If only an orphan from a failed run
exists, `-CleanupOrphans` (dry run) then `-CleanupOrphans -Execute` deletes exactly the two Agent Eye item titles.
It refuses unless the public pin sha256 is `null` and `gh` confirms no GitHub release exists (it fails closed if
`gh` fails). After a cleanup, rerun `provision.ps1`. If the pin is set or a release exists, recover the key instead.

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

Structure-review verification on 2026-10-25 (`review-fixes`, implementation `437e4f0`):

| Command/check | Observed result |
|---|---|
| `npm ci --offline --no-audit --cache .tmp/npm-cache` | Passed; 2 packages installed from existing machine-cache records copied into the worktree. No downloads. The initial empty-cache attempt reported `ENOTCACHED`. |
| `node --test` | 90 passed, 0 failed, 0 skipped; includes CI draft creation/resume/failures and shared asset/JAR rules. |
| `.\android\gradlew.bat -p android assembleRelease bundleRelease releaseTools --offline --no-daemon` | `BUILD SUCCESSFUL in 5m 51s`; 57 tasks executed. Built missing smoke inputs using the existing SDK/Gradle caches. |
| `node src/android/signing/signing-smoke.mjs` | Passed real unsigned inspection, APK/AAB signing/verification, bundled privacy equality and side-effect-free dry-run. Disposable key removed. |
| Owner dry-runs listed above | Setup, generate-key, verify-key, and release-sign with publication for both example versions passed, using numeric draft IDs 1 and 2. |
| Workflow YAML parse | Both workflows passed parsing with unique-key checks, read-only default permissions and local module-path checks. Actionlint was unavailable; this is a parse check, not actionlint or remote execution. |
| `.\android\gradlew.bat --stop` | `No Gradle daemons are running.` |

The module split preserves the signing/retry contract. CI now checks duplicate names through
the shared asset-set validator before uploading; the upload adapter still independently
rejects duplicates. No real owner keys or GitHub mutations were exercised.

Local verification on 2026-10-25 (implementation through `95f60fa`):

| Command/check | Observed result |
|---|---|
| `npm ci` | Passed; 2 packages added, 0 vulnerabilities. |
| `node --test` | 71 passed, 0 failed, 0 skipped. Includes Windows ACL and termination cleanup fixtures. |
| `.\android\gradlew.bat -p android ktlintCheck assembleDebug assembleRelease bundleRelease testDebugUnitTest lintRelease releaseTools` | `BUILD SUCCESSFUL in 4m 27s`; 115 tasks, 18 executed. 12 JVM tests, 0 failures/errors. Lint: 0 errors, 16 warnings. |
| `node src/android/signing/signing-smoke.mjs` | Passed real unsigned inspection, alignment, APK/AAB signing and certificate verification with a disposable RSA-4096 key; both signed archives contain the exact source privacy policy. Dry-run never invoked execution. Temporary key removed. |
| `npm run release -- setup --dry-run`; `npm run secrets -- generate-key --dry-run`; `npm run secrets -- verify-key --dry-run`; `npm run secrets -- release-sign --tag v1.0.0 --release-id 1 --dry-run --publish` | All passed without owner operations. |
| `.\.tmp\actionlint\bin\actionlint.exe` (official v1.7.7, checksum verified) | Passed both workflows. Remote workflow execution is unexercised. |
| Offline `stage --execute` fixture with child-only run ID/attempt 1, followed by `verifyFiles` | Passed schema and all hashes; fixture metadata is not evidence of a real CI run. |
| `.\android\gradlew.bat --stop` | `No Gradle daemons are running.` |
| `git diff --check`, `dk check` | Passed. |

Outputs: unsigned APK 984,867 bytes; unsigned AAB 1,491,465 bytes; R8 mapping
8,260,174 bytes. Inspected package/version/SDK identity is `local.agenteye`, `1.0.0` / `1`,
min/target/compile SDK 24/36/36. R8 mapping identifies R8 8.13.19. No real key was created.

Initial checks exposed issues that were fixed before the successful gate: ktlint reported
`Wildcard import (cannot be auto-corrected)` and `Exceeded max line length (100) (cannot be auto-corrected)`;
combining formatting/checking reported `Missing newline after "("` and `Missing newline before ")"`.
The first release lint reported `[PropertyEscape]` for an unescaped Windows SDK drive colon;
the local setup instructions now escape it. Real signing initially reported
`Error: Artifact identity mismatch: minSdk` (aapt2 field spelling) and the bundle fixture reported
`AAB signature verification failed` (JDK strict self-signed wording). Both have regression tests.
The privacy smoke then exposed a missing `assets/PRIVACY.md`; an explicit pre-build dependency
now packages the generated policy, verified in both signed artifacts. Initial failing regression
tests also caught alias substitution, assets added during signing and termination during cleanup.
No lint baseline or ignored failures were introduced.

CI draft creation, real owner signing/publication, unauthenticated browser install, minified
CameraX device procedure, offline policy display and 1.0.0-to-1.0.1 in-place update are **not yet
exercised**. No remote is created by implementation. Local verification is recorded separately;
a successful build or disposable-key smoke does not establish phone or GitHub acceptance.
