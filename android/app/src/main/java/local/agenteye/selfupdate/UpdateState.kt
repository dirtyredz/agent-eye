package local.agenteye.selfupdate

/**
 * Actionable, sanitized failure categories. They never carry a URL, header, query string or
 * response body, so they are safe to show and to persist.
 */
enum class UpdateProblem {
    /** No connection, timeout, server error or unexpected status. */
    Network,

    /** GitHub refused with 403/429; manual checks wait for the persisted backoff. */
    RateLimited,

    /** The repository has no published stable release. */
    NoRelease,

    /** Release metadata is malformed, incomplete, duplicated or inconsistent. */
    Malformed,

    /** A higher release exists but needs a newer Android version (or another SDK level). */
    Incompatible,

    /** This installation is a debug or differently signed build: it cannot self-update. */
    Channel,

    /** Downloaded bytes did not match the frozen candidate; they were deleted. */
    Corrupt,

    /** Not enough space for the download plus installer staging. */
    NoSpace,

    /** The release changed since the check; a fresh check is required. */
    Changed,
    InstallDenied,
    InstallBlocked,
    InstallCancelled,
    InstallFailed,

    /** Android lost or finished the installer session without replacing the app. */
    InstallInterrupted,

    /** The confirmation screen was lost (e.g. process death); explicit retry is offered. */
    ConfirmationLost
}

class UpdateFailure(val problem: UpdateProblem, val retryAt: Long = 0) : Exception(problem.name)

/** Thrown when the user cancels or the app is backgrounded; not a failure. */
class UpdateCancelled : Exception("cancelled")

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

enum class UpdatePhase {
    Idle,
    Checking,
    Current,
    Available,
    Downloading,
    Ready,
    Permission,
    Installing,
    AwaitingConfirmation,
    Installed,
    Failed
}

/** Immutable presentation-independent updater state. */
data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val installedVersion: String = "",
    val installedCode: Long = 0,
    val candidate: Candidate? = null,
    val downloaded: Long = 0,
    val problem: UpdateProblem? = null,
    val retryAt: Long = 0,
    val installAllowed: Boolean = false,
    val newerIncompatible: String? = null
) {
    val busy: Boolean
        get() = phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading ||
            phase == UpdatePhase.Installing
}
