package local.agenteye.ui.capture

import local.agenteye.camera.Lens
import local.agenteye.service.CaptureSnapshot
import local.agenteye.service.SessionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStateHolderTest {
    private val stopped = CaptureSnapshot(false, false, null, null, listOf(Lens.BACK), Lens.BACK)

    private fun status(snapshot: CaptureSnapshot, notice: CameraStatus? = null) =
        CameraStateHolder.present(snapshot, true, false, notice).status

    @Test fun bindingIsNotStopped() {
        assertFalse(CameraStateHolder.present(null, true, false).canRequestStart)
        assertEquals(
            CameraStatus.Connecting,
            CameraStateHolder.present(null, true, false).status
        )
        assertTrue(CameraStateHolder.present(stopped, true, false).canRequestStart)
    }

    @Test fun runningWithoutCameraOrWifiStillHasStop() {
        val state = CameraStateHolder.present(
            stopped.copy(running = true),
            true,
            true
        )
        assertTrue(state.running)
        assertFalse(state.canRequestStart)
        assertEquals(CameraStatus.Starting, state.status)
        assertEquals(CameraStatus.Ready, status(stopped.copy(running = true, cameraReady = true)))
    }

    @Test fun everySessionErrorHasItsOwnStatusWhileRunning() {
        val running = stopped.copy(running = true, cameraReady = true)
        val expected = mapOf(
            SessionError.SessionStartFailed to CameraStatus.StartFailed,
            SessionError.CaptureFailed to CameraStatus.CaptureFailed,
            SessionError.CameraUnavailable to CameraStatus.CameraUnavailable,
            SessionError.BindFailed to CameraStatus.BindFailed,
            SessionError.WifiUnavailable to CameraStatus.WifiUnavailable
        )
        assertEquals(SessionError.entries.toSet(), expected.keys)
        for ((error, shown) in expected) assertEquals(shown, status(running.copy(error = error)))
    }

    @Test fun wifiLossIsNeverShownAsCameraReadyOrStarting() {
        for (ready in listOf(true, false)) {
            val shown = status(
                stopped.copy(
                    running = true,
                    cameraReady = ready,
                    error = SessionError.WifiUnavailable
                )
            )
            assertEquals(CameraStatus.WifiUnavailable, shown)
        }
    }

    @Test fun stoppedShowsStartFailureOnlyForThatError() {
        assertEquals(CameraStatus.Stopped, status(stopped))
        assertEquals(
            CameraStatus.StartFailed,
            status(stopped.copy(error = SessionError.SessionStartFailed))
        )
        assertEquals(
            CameraStatus.Installing,
            CameraStateHolder.present(stopped, true, true).status
        )
    }

    @Test fun noticeOverridesDerivedStatus() {
        assertEquals(
            CameraStatus.PermissionRequired,
            status(stopped, CameraStatus.PermissionRequired)
        )
        assertEquals(
            CameraStatus.StartFailed,
            CameraStateHolder.present(null, true, false, CameraStatus.StartFailed).status
        )
    }

    @Test fun switchesOnlyWhenMultipleLensesExist() {
        assertFalse(CameraStateHolder.present(stopped, true, false).canSwitch)
        assertTrue(
            CameraStateHolder.present(
                stopped.copy(availableLenses = listOf(Lens.BACK, Lens.FRONT)),
                true,
                false
            ).canSwitch
        )
    }

    @Test fun startIsOfferedOnlyWhenVisibleStoppedAndNotBlocked() {
        assertTrue(CameraStateHolder.present(stopped, true, false).canRequestStart)
        assertFalse(CameraStateHolder.present(stopped, false, false).canRequestStart)
        assertFalse(CameraStateHolder.present(stopped, true, true).canRequestStart)
    }
}
