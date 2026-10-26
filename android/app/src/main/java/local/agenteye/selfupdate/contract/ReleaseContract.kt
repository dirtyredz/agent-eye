package local.agenteye.selfupdate.contract

/**
 * The shared vocabulary of a published release: repository, asset names and the shapes of
 * tags, versions and SHA-256 digests. A leaf with no dependencies, so release/, transport/ and
 * storage/ can all use it without importing each other. Mirrors `contract.mjs`.
 */
object ReleaseContract {
    const val REPOSITORY = "dirtyredz/agent-eye"
    const val APK = "agent-eye.apk"
    const val MANIFEST = "release.json"
    const val SUMS = "SHA256SUMS"
    val FINAL = setOf(APK, "agent-eye.aab", "mapping.txt")
    val RELEASE_ASSETS = FINAL + MANIFEST + SUMS

    private val stable = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
    private val sha256 = Regex("[a-f0-9]{64}")

    /** `1.2.3`, no leading zeros. */
    fun isStableVersion(version: String) = stable.matches(version)

    /** `v1.2.3`. */
    fun isStableTag(tag: String) = tag.startsWith("v") && isStableVersion(tag.substring(1))

    /** 64 lowercase hex digits: a digest, or the signer pin. */
    fun isSha256(value: String) = sha256.matches(value)
}
