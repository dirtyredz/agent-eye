package local.agenteye.ui.capture

import local.agenteye.camera.CaptureError
import local.agenteye.camera.Lens
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStateHolderTest {
    private val stopped = CaptureSnapshot(false, false, null, null, listOf(Lens.BACK), Lens.BACK)

    private fun status(snapshot: CaptureSnapshot, notice: CameraStatus? = null) =
        CameraStateHolder.present(snapshot, true, UpdatePhase.Idle, notice).status

    @Test fun bindingIsNotStopped() {
        assertFalse(CameraStateHolder.present(null, true, UpdatePhase.Idle).canRequestStart)
        assertEquals(
            CameraStatus.Connecting,
            CameraStateHolder.present(null, true, UpdatePhase.Idle).status
        )
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
        assertEquals(CameraStatus.Starting, state.status)
        assertEquals(CameraStatus.Ready, status(stopped.copy(running = true, cameraReady = true)))
    }

    @Test fun everyCaptureErrorHasItsOwnStatusWhileRunning() {
        val running = stopped.copy(running = true, cameraReady = true)
        val expected = mapOf(
            CaptureError.SessionStartFailed to CameraStatus.StartFailed,
            CaptureError.CaptureFailed to CameraStatus.CaptureFailed,
            CaptureError.CameraUnavailable to CameraStatus.CameraUnavailable,
            CaptureError.BindFailed to CameraStatus.BindFailed,
            CaptureError.WifiUnavailable to CameraStatus.WifiUnavailable
        )
        assertEquals(CaptureError.entries.toSet(), expected.keys)
        for ((error, shown) in expected) assertEquals(shown, status(running.copy(error = error)))
    }

    @Test fun wifiLossIsNeverShownAsCameraReadyOrStarting() {
        for (ready in listOf(true, false)) {
            val shown = status(
                stopped.copy(
                    running = true,
                    cameraReady = ready,
                    error = CaptureError.WifiUnavailable
                )
            )
            assertEquals(CameraStatus.WifiUnavailable, shown)
        }
    }

    @Test fun stoppedShowsStartFailureOnlyForThatError() {
        assertEquals(CameraStatus.Stopped, status(stopped))
        assertEquals(
            CameraStatus.StartFailed,
            status(stopped.copy(error = CaptureError.SessionStartFailed))
        )
        assertEquals(
            CameraStatus.Installing,
            CameraStateHolder.present(stopped, true, UpdatePhase.Installing).status
        )
    }

    @Test fun noticeOverridesDerivedStatus() {
        assertEquals(
            CameraStatus.PermissionRequired,
            status(stopped, CameraStatus.PermissionRequired)
        )
        assertEquals(
            CameraStatus.StartFailed,
            CameraStateHolder.present(null, true, UpdatePhase.Idle, CameraStatus.StartFailed).status
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

    @Test fun startIsOfferedOnlyWhenVisibleStoppedAndNotInstalling() {
        assertTrue(CameraStateHolder.present(stopped, true, UpdatePhase.Idle).canRequestStart)
        assertFalse(CameraStateHolder.present(stopped, false, UpdatePhase.Idle).canRequestStart)
        for (phase in listOf(UpdatePhase.Installing, UpdatePhase.AwaitingConfirmation)) {
            assertFalse(CameraStateHolder.present(stopped, true, phase).canRequestStart)
        }
        // Notification permission is deliberately not a Start prerequisite.
        assertTrue(CameraStateHolder.present(stopped, true, UpdatePhase.Permission).canRequestStart)
    }
}
