package local.agenteye.selfupdate

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
