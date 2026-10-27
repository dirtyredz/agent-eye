package local.agenteye.ui.capture

import local.agenteye.camera.Lens
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureSnapshot

data class CameraState(
    val running: Boolean = false,
    val status: String = "Connecting to camera service",
    val address: String? = null,
    val canRequestStart: Boolean = false,
    val canSwitch: Boolean = false,
    val lens: Lens? = null
)

/** Pure presentation; being offline or losing camera readiness never removes Stop. */
object CameraStateHolder {
    fun present(snapshot: CaptureSnapshot?, visible: Boolean, phase: UpdatePhase): CameraState {
        if (snapshot == null) return CameraState()
        val installing =
            phase == UpdatePhase.Installing || phase == UpdatePhase.AwaitingConfirmation
        val status = when {
            !snapshot.running && installing -> "Update installation in progress"
            snapshot.error == "session_start_failed" -> "Could not start. Press Start to retry."
            !snapshot.running -> "Stopped"
            snapshot.error == "capture_failed" -> "Capture failed. Try another snapshot."
            snapshot.error == "bind_failed" -> "Wi-Fi connection failed. Reconnect to retry."
            snapshot.error == "camera_unavailable" -> "Camera starting or unavailable"
            snapshot.cameraReady -> "Camera ready"
            else -> "Camera starting"
        }
        return CameraState(
            running = snapshot.running,
            status = status,
            address = snapshot.address,
            canRequestStart = visible && !snapshot.running && !installing,
            canSwitch = visible && snapshot.availableLenses.size > 1,
            lens = snapshot.selectedLens
        )
    }

    fun canStart(
        snapshot: CaptureSnapshot?,
        visible: Boolean,
        cameraGranted: Boolean,
        phase: UpdatePhase
    ) = cameraGranted && present(snapshot, visible, phase).canRequestStart
}
