package local.agenteye.selfupdate

/**
 * A frozen release candidate: the identity every later download, verification and install step
 * is bound to. Assets fetched through a moving "latest" URL are never mixed in.
 */
data class Candidate(
    val releaseId: Long,
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val manifestAssetId: Long,
    val sumsAssetId: Long,
    val apkAssetId: Long,
    val manifestSha256: String,
    val apkSize: Long,
    val apkSha256: String,
    val signerSha256: String
)
