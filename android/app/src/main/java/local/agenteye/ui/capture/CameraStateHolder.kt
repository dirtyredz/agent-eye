package local.agenteye.ui.capture

import local.agenteye.camera.Lens
import local.agenteye.service.CaptureSnapshot
import local.agenteye.service.SessionError

data class CameraState(
    val running: Boolean = false,
    val status: CameraStatus = CameraStatus.Connecting,
    val address: String? = null,
    val canRequestStart: Boolean = false,
    val canSwitch: Boolean = false,
    val lens: Lens? = null
)

/**
 * Pure snapshot -> display mapping; being offline or losing camera readiness never removes Stop.
 * A [notice] (a start-flow outcome the service never saw) overrides the derived status.
 */
object CameraStateHolder {
    fun present(
        snapshot: CaptureSnapshot?,
        visible: Boolean,
        startBlocked: Boolean,
        notice: CameraStatus? = null
    ): CameraState {
        if (snapshot == null) return CameraState(status = notice ?: CameraStatus.Connecting)
        return CameraState(
            running = snapshot.running,
            status = notice ?: status(snapshot, startBlocked),
            address = snapshot.address,
            canRequestStart = visible && !snapshot.running && !startBlocked,
            canSwitch = visible && snapshot.availableLenses.size > 1,
            lens = snapshot.selectedLens
        )
    }

    private fun status(snapshot: CaptureSnapshot, startBlocked: Boolean): CameraStatus = when {
        !snapshot.running && startBlocked -> CameraStatus.Installing

        !snapshot.running ->
            if (snapshot.error == SessionError.SessionStartFailed) {
                CameraStatus.StartFailed
            } else {
                CameraStatus.Stopped
            }

        else -> when (snapshot.error) {
            SessionError.SessionStartFailed -> CameraStatus.StartFailed
            SessionError.CaptureFailed -> CameraStatus.CaptureFailed
            SessionError.BindFailed -> CameraStatus.BindFailed
            SessionError.WifiUnavailable -> CameraStatus.WifiUnavailable
            SessionError.CameraUnavailable -> CameraStatus.CameraUnavailable
            null -> if (snapshot.cameraReady) CameraStatus.Ready else CameraStatus.Starting
        }
    }
}
