package local.agenteye.selfupdate.release

import java.security.MessageDigest
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem

/**
 * Pure release-contract checks, mirroring `src/android/releases/artifacts/contract.mjs`. The
 * shared fixtures under `src/android/releases/artifacts/fixtures/update-schema1/` drive both
 * the Node and these JVM tests. The signer pin is always the packaged one, never the network's.
 */
object ReleasePolicy {
    const val REPOSITORY = "dirtyredz/agent-eye"
    const val APPLICATION_ID = "local.agenteye"
    const val WORKFLOW = ".github/workflows/android-release.yml"
    const val APK = "agent-eye.apk"
    const val MANIFEST = "release.json"
    const val SUMS = "SHA256SUMS"
    val FINAL = setOf(APK, "agent-eye.aab", "mapping.txt")
    val UNSIGNED = setOf("agent-eye-unsigned.apk", "agent-eye-unsigned.aab", "mapping.txt")
    val RELEASE_ASSETS = FINAL + MANIFEST + SUMS
    const val MAX_RELEASE_JSON_BYTES = 1L shl 20
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_SUMS_BYTES = 8 * 1024
    const val MAX_APK_BYTES = 64L shl 20
    const val MAX_VERSION_CODE = 2_100_000_000L
    private const val MAX_SDK = 1000L

    private val stable = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
    private val hex40 = Regex("[a-f0-9]{40}")
    private val hex64 = Regex("[a-f0-9]{64}")

    fun isStableTag(tag: String) = tag.startsWith("v") && stable.matches(tag.substring(1))

    fun isPin(value: String) = hex64.matches(value)

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** Identity, version, provenance shape, artifact set and signer. Throws [UpdateFailure]. */
    fun validate(m: ReleaseManifest, releaseTag: String, pin: String) {
        if (!isPin(pin)) throw UpdateFailure(UpdateProblem.Channel)
        val identity =
            m.schema == 1L && m.repository == REPOSITORY && m.applicationId == APPLICATION_ID
        val version = stable.matches(m.versionName) && m.tag == "v${m.versionName}" &&
            m.tag == releaseTag && m.versionCode in 1..MAX_VERSION_CODE
        val provenance =
            hex40.matches(m.commit) && m.runId > 0 && m.runAttempt > 0 && m.workflow == WORKFLOW
        val sdk = m.minSdk in 1..MAX_SDK && m.targetSdk in m.minSdk..MAX_SDK &&
            m.compileSdk in m.targetSdk..MAX_SDK
        val artifacts = m.artifacts.keys == FINAL &&
            m.artifacts.values.all { it.size > 0 && hex64.matches(it.sha256) } &&
            m.artifacts.getValue(APK).size <= MAX_APK_BYTES
        val unsigned =
            m.unsignedHashes.keys == UNSIGNED && m.unsignedHashes.values.all { hex64.matches(it) }
        if (!(
                identity && version && provenance && sdk && artifacts && unsigned &&
                    hex64.matches(m.signerSha256)
                )
        ) {
            throw UpdateFailure(UpdateProblem.Malformed)
        }
        if (m.signerSha256 != pin) throw UpdateFailure(UpdateProblem.Channel)
    }

    /** `SHA256SUMS` must cover exactly the final files plus `release.json`, and agree with both. */
    fun validateChecksums(sums: Map<String, String>, manifestBytes: ByteArray, m: ReleaseManifest) {
        val consistent = sums.keys == FINAL + MANIFEST && sums[MANIFEST] == sha256(manifestBytes) &&
            FINAL.all { sums[it] == m.artifacts[it]?.sha256 }
        if (!consistent) throw UpdateFailure(UpdateProblem.Malformed)
    }

    enum class Availability { Upgrade, Current, Older, Incompatible }

    /** Numeric version-code comparison; a higher release needing a newer SDK is explained. */
    fun availability(m: ReleaseManifest, installedCode: Long, sdk: Int): Availability = when {
        m.versionCode == installedCode -> Availability.Current
        m.versionCode < installedCode -> Availability.Older
        m.minSdk > sdk -> Availability.Incompatible
        else -> Availability.Upgrade
    }
}
