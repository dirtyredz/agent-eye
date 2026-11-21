package local.agenteye.ui.update

import androidx.activity.ComponentActivity
import local.agenteye.selfupdate.UpdateRuntime
import local.agenteye.selfupdate.UpdateState
import local.agenteye.ui.CaptureServiceLink
import local.agenteye.updates

/**
 * An Activity's updater wiring: attach the install prompter and observe state on resume, forget and
 * detach on pause (resume/pause, not start/stop, so navigation never leaves two owners attached).
 */
class UpdateLifecycle(
    private val activity: ComponentActivity,
    link: CaptureServiceLink,
    private val listener: (UpdateState) -> Unit
) {
    val updates: UpdateRuntime by lazy { activity.updates }
    val controller: UpdateController by lazy {
        UpdateController.forActivity(activity, updates, link)
    }

    fun onResume() {
        updates.prompter.attach(activity, controller::stopForInstall)
        updates.coordinator.observe(listener)
    }

    fun onPause() {
        updates.coordinator.forget(listener)
        updates.prompter.detach(activity.isChangingConfigurations)
    }
}
