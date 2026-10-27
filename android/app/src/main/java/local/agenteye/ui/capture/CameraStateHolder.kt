package local.agenteye.ui.capture

import local.agenteye.camera.CaptureError
import local.agenteye.camera.Lens
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureSnapshot

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
        phase: UpdatePhase,
        notice: CameraStatus? = null
    ): CameraState {
        if (snapshot == null) return CameraState(status = notice ?: CameraStatus.Connecting)
        val installing =
            phase == UpdatePhase.Installing || phase == UpdatePhase.AwaitingConfirmation
        return CameraState(
            running = snapshot.running,
            status = notice ?: status(snapshot, installing),
            address = snapshot.address,
            canRequestStart = visible && !snapshot.running && !installing,
            canSwitch = visible && snapshot.availableLenses.size > 1,
            lens = snapshot.selectedLens
        )
    }

    private fun status(snapshot: CaptureSnapshot, installing: Boolean): CameraStatus = when {
        !snapshot.running && installing -> CameraStatus.Installing

        !snapshot.running ->
            if (snapshot.error == CaptureError.SessionStartFailed) {
                CameraStatus.StartFailed
            } else {
                CameraStatus.Stopped
            }

        else -> when (snapshot.error) {
            CaptureError.SessionStartFailed -> CameraStatus.StartFailed
            CaptureError.CaptureFailed -> CameraStatus.CaptureFailed
            CaptureError.BindFailed -> CameraStatus.BindFailed
            CaptureError.WifiUnavailable -> CameraStatus.WifiUnavailable
            CaptureError.CameraUnavailable -> CameraStatus.CameraUnavailable
            null -> if (snapshot.cameraReady) CameraStatus.Ready else CameraStatus.Starting
        }
    }
}
