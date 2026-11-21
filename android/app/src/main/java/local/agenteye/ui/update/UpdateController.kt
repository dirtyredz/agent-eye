package local.agenteye.ui.update

import android.app.Activity
import local.agenteye.selfupdate.UpdateCoordinator
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.selfupdate.UpdateRuntime
import local.agenteye.ui.CaptureServiceLink

/** The coordinator commands the UI can issue; narrow so the controller is testable on the JVM. */
class UpdateCommands(
    val check: () -> Unit,
    val update: () -> Unit,
    val cancel: () -> Unit,
    val install: () -> Unit,
    val abandonAndRetry: () -> Unit
) {
    constructor(coordinator: UpdateCoordinator) : this(
        coordinator::check,
        coordinator::update,
        coordinator::cancel,
        coordinator::install,
        coordinator::abandonAndRetry
    )
}

/**
 * UI orchestration of the updater, separate from presentation and the state machine: dispatches
 * [UpdateAction]s, stops capture at the verified-install handoff, and answers whether Start must
 * be withheld. Everything is read live (phase, bound service) at call time, never cached.
 */
class UpdateController(
    private val phase: () -> UpdatePhase,
    private val commands: UpdateCommands,
    private val openPermissionSettings: () -> Unit,
    private val continueInstall: () -> Unit,
    private val boundStop: () -> (() -> Unit)?
) {
    fun act(action: UpdateAction) {
        when (action) {
            UpdateAction.Check -> commands.check()
            UpdateAction.Update -> commands.update()
            UpdateAction.Cancel -> commands.cancel()
            UpdateAction.Install -> commands.install()
            UpdateAction.GrantPermission -> runCatching { openPermissionSettings() }
            UpdateAction.Continue -> continueInstall()
            UpdateAction.AbandonRetry -> commands.abandonAndRetry()
        }
    }

    /** False when no service is bound; otherwise stops capture synchronously, even if stopped. */
    fun stopForInstall(): Boolean {
        val stop = boundStop() ?: return false
        stop()
        return true
    }

    /** Start is unavailable only while an install is running or awaiting confirmation. */
    fun startBlocked(): Boolean {
        val current = phase()
        return current == UpdatePhase.Installing || current == UpdatePhase.AwaitingConfirmation
    }

    companion object {
        fun forActivity(
            activity: Activity,
            updates: UpdateRuntime,
            link: CaptureServiceLink
        ): UpdateController {
            val coordinator = updates.coordinator
            return UpdateController(
                phase = { coordinator.state.phase },
                commands = UpdateCommands(coordinator),
                openPermissionSettings = {
                    activity.startActivity(updates.prompter.permissionIntent())
                },
                continueInstall = updates.prompter::continueInstall,
                boundStop = { link.service?.let { it::stopSession } }
            )
        }
    }
}
