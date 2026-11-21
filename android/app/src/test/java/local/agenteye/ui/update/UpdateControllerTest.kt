package local.agenteye.ui.update

import local.agenteye.selfupdate.UpdatePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateControllerTest {
    private val calls = mutableListOf<String>()
    private var phase = UpdatePhase.Idle
    private var stop: (() -> Unit)? = { calls += "stop" }
    private var permissionFails = false

    private val controller = UpdateController(
        phase = { phase },
        commands = UpdateCommands(
            check = { calls += "check" },
            update = { calls += "update" },
            cancel = { calls += "cancel" },
            install = { calls += "install" },
            abandonAndRetry = { calls += "abandon" }
        ),
        openPermissionSettings = {
            calls += "permission"
            if (permissionFails) error("no settings screen")
        },
        continueInstall = { calls += "continue" },
        boundStop = { stop }
    )

    @Test fun onlyInstallingAndAwaitingConfirmationBlockStart() {
        val blocked = UpdatePhase.entries.filter {
            phase = it
            controller.startBlocked()
        }
        assertEquals(listOf(UpdatePhase.Installing, UpdatePhase.AwaitingConfirmation), blocked)
    }

    @Test fun startGuardRereadsThePhaseOnEveryCall() {
        phase = UpdatePhase.Installing
        assertTrue(controller.startBlocked())
        phase = UpdatePhase.Ready
        assertFalse(controller.startBlocked())
        phase = UpdatePhase.AwaitingConfirmation
        assertTrue(controller.startBlocked())
    }

    @Test fun everyActionRunsExactlyItsOwnEffect() {
        val expected = mapOf(
            UpdateAction.Check to "check",
            UpdateAction.Update to "update",
            UpdateAction.Cancel to "cancel",
            UpdateAction.Install to "install",
            UpdateAction.GrantPermission to "permission",
            UpdateAction.Continue to "continue",
            UpdateAction.AbandonRetry to "abandon"
        )
        assertEquals(UpdateAction.entries.toSet(), expected.keys)
        for ((action, effect) in expected) {
            calls.clear()
            controller.act(action)
            assertEquals(listOf(effect), calls)
        }
    }

    @Test fun permissionLaunchFailureIsSwallowed() {
        permissionFails = true
        controller.act(UpdateAction.GrantPermission)
        assertEquals(listOf("permission"), calls)
    }

    @Test fun stopForInstallRefusesWhenUnbound() {
        stop = null
        assertFalse(controller.stopForInstall())
        assertTrue(calls.isEmpty())
    }

    @Test fun stopForInstallStopsSynchronouslyWhenBoundAndTracksRebinding() {
        assertTrue(controller.stopForInstall())
        assertEquals(listOf("stop"), calls)
        stop = null
        assertFalse(controller.stopForInstall())
        stop = { calls += "stop again" }
        assertTrue(controller.stopForInstall())
        assertEquals(listOf("stop", "stop again"), calls)
    }
}
