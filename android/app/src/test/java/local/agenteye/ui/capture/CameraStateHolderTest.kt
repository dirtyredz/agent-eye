package local.agenteye.ui.capture

import local.agenteye.camera.Lens
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStateHolderTest {
    private val stopped = CaptureSnapshot(false, false, null, null, listOf(Lens.BACK), Lens.BACK)

    @Test fun bindingIsNotStopped() {
        assertFalse(CameraStateHolder.present(null, true, UpdatePhase.Idle).canRequestStart)
        assertTrue(CameraStateHolder.present(stopped, true, UpdatePhase.Idle).canRequestStart)
    }

    @Test fun runningWithoutCameraOrWifiStillHasStop() {
        val state = CameraStateHolder.present(
            stopped.copy(running = true),
            true,
            UpdatePhase.Installing
        )
        assertTrue(state.running)
        assertFalse(state.canRequestStart)
        assertEquals("Camera starting", state.status)
        assertEquals(
            "Camera ready",
            CameraStateHolder.present(
                stopped.copy(running = true, cameraReady = true),
                true,
                UpdatePhase.Idle
            ).status
        )
        assertEquals(
            "Capture failed. Try another snapshot.",
            CameraStateHolder.present(
                stopped.copy(running = true, error = "capture_failed"),
                true,
                UpdatePhase.Idle
            ).status
        )
    }

    @Test fun switchesOnlyWhenMultipleLensesExist() {
        assertFalse(CameraStateHolder.present(stopped, true, UpdatePhase.Idle).canSwitch)
        assertTrue(
            CameraStateHolder.present(
                stopped.copy(availableLenses = listOf(Lens.BACK, Lens.FRONT)),
                true,
                UpdatePhase.Idle
            ).canSwitch
        )
    }

    @Test fun permissionReturnRechecksVisibilityCameraAndInstall() {
        assertTrue(CameraStateHolder.canStart(stopped, true, true, UpdatePhase.Idle))
        assertFalse(CameraStateHolder.canStart(stopped, false, true, UpdatePhase.Idle))
        assertFalse(CameraStateHolder.canStart(stopped, true, false, UpdatePhase.Idle))
        for (phase in listOf(UpdatePhase.Installing, UpdatePhase.AwaitingConfirmation)) {
            assertFalse(CameraStateHolder.canStart(stopped, true, true, phase))
        }
        // Notification permission is deliberately not a Start prerequisite.
        assertTrue(CameraStateHolder.canStart(stopped, true, true, UpdatePhase.Permission))
    }
}
