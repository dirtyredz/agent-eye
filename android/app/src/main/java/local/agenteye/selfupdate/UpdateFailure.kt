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
