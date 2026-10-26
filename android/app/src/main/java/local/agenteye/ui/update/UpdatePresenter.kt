package local.agenteye.ui.update

import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.UpdateState

enum class UpdateAction { Check, Update, Cancel, GrantPermission, Install, Continue, AbandonRetry }

data class UpdateButton(val label: String, val action: UpdateAction)

/** Everything the update panel shows; [progress] is 0..100 while downloading. */
data class UpdateView(
    val version: String,
    val status: String,
    val primary: UpdateButton?,
    val secondary: UpdateButton?,
    val progress: Int?
)

/** Pure state-to-presentation mapping, unit-tested without Android. */
object UpdatePresenter {
    private val check = UpdateButton("Check for updates", UpdateAction.Check)
    private val cancel = UpdateButton("Cancel", UpdateAction.Cancel)
    private const val STOPS = "Installing stops the camera session."

    fun present(state: UpdateState, now: Long): UpdateView {
        val version = if (state.installedVersion.isEmpty()) {
            "Installed version unknown"
        } else {
            "Installed: ${state.installedVersion} (${state.installedCode})"
        }
        val c = state.candidate
        val size = c?.let { mib(it.apkSize) }.orEmpty()
        return when (state.phase) {
            UpdatePhase.Idle -> UpdateView(
                version,
                "Updates are checked only when you tap Check for updates.",
                check,
                null,
                null
            )

            UpdatePhase.Checking -> UpdateView(
                version,
                "Checking GitHub for a newer release…",
                null,
                cancel,
                null
            )

            UpdatePhase.Current -> UpdateView(
                version,
                listOfNotNull(
                    "Agent Eye is up to date.",
                    state.newerIncompatible?.let {
                        "A newer version exists but cannot be installed here: $it."
                    }
                ).joinToString(" "),
                check,
                null,
                null
            )

            UpdatePhase.Available -> UpdateView(
                version,
                "Version ${c?.versionName} is available ($size). $STOPS",
                UpdateButton("Update", UpdateAction.Update),
                null,
                null
            )

            UpdatePhase.Downloading -> UpdateView(
                version,
                "Downloading ${c?.versionName}: ${mib(state.downloaded)} of $size",
                null,
                cancel,
                c?.let {
                    (state.downloaded * 100 / it.apkSize.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                }
            )

            UpdatePhase.Ready -> UpdateView(
                version,
                "Version ${c?.versionName} is downloaded and verified. $STOPS",
                UpdateButton("Install", UpdateAction.Install),
                null,
                null
            )

            UpdatePhase.Permission -> UpdateView(
                version,
                "Android needs your permission for Agent Eye to install updates. Allow it, come back, then tap Install.",
                UpdateButton("Allow installs", UpdateAction.GrantPermission),
                null,
                null
            )

            UpdatePhase.Installing -> UpdateView(
                version,
                "Handing the update to Android… The camera is stopped.",
                null,
                null,
                null
            )

            UpdatePhase.AwaitingConfirmation -> UpdateView(
                version,
                "Confirm the update in Android's installer.",
                UpdateButton("Continue installation", UpdateAction.Continue),
                null,
                null
            )

            UpdatePhase.Installed -> UpdateView(version, "Update installed.", check, null, null)

            UpdatePhase.Failed -> failure(state, version, now)
        }
    }

    private fun failure(state: UpdateState, version: String, now: Long): UpdateView {
        val retry = when {
            state.candidate == null -> check

            state.downloaded >= state.candidate.apkSize -> UpdateButton(
                "Try again",
                UpdateAction.Install
            )

            else -> UpdateButton("Try again", UpdateAction.Update)
        }
        val (text, button) = when (state.problem) {
            UpdateProblem.Network ->
                "Could not reach GitHub. Check the internet connection and try again." to
                    check

            UpdateProblem.RateLimited -> {
                val minutes = ((state.retryAt - now + 59_999) / 60_000).coerceAtLeast(1)
                "GitHub is limiting update checks. Try again in about $minutes min." to check
            }

            UpdateProblem.NoRelease -> "No published release was found." to check

            UpdateProblem.Malformed ->
                "The latest release is incomplete or inconsistent, so it was not offered." to
                    check

            UpdateProblem.Incompatible -> "This update is not compatible with this phone." to check

            UpdateProblem.Channel ->
                "This copy of Agent Eye is a debug or differently signed build, so it cannot " +
                    "update itself from the official releases." to
                    null

            UpdateProblem.Corrupt ->
                "The download did not match the release and was deleted." to
                    retry

            UpdateProblem.NoSpace ->
                "Not enough free storage for the update. Free some space and try again." to
                    retry

            UpdateProblem.Changed -> "The release changed since the check. Check again." to check

            UpdateProblem.InstallDenied -> "Android did not allow the installation." to retry

            UpdateProblem.InstallBlocked ->
                "Android blocked the installation (device policy or Play Protect)." to
                    retry

            UpdateProblem.InstallCancelled ->
                "Installation was cancelled. The current version is unchanged; " +
                    "press Start to resume the camera." to
                    retry

            UpdateProblem.InstallFailed ->
                "Installation failed. The current version is unchanged." to
                    retry

            UpdateProblem.InstallInterrupted ->
                "Installation was interrupted. The current version is unchanged." to
                    retry

            UpdateProblem.ConfirmationLost ->
                "Android's confirmation screen was closed before you answered." to
                    UpdateButton("Retry installation", UpdateAction.AbandonRetry)

            null -> "Update failed." to check
        }
        return UpdateView(version, text, button, null, null)
    }

    private fun mib(bytes: Long) = "%.1f MB".format(java.util.Locale.US, bytes / 1_048_576.0)
}
