package local.agenteye.ui.capture

import androidx.annotation.StringRes
import local.agenteye.R

/** The one-line status shown above the preview; text comes from resources at composition time. */
enum class CameraStatus(@StringRes val text: Int) {
    Connecting(R.string.status_connecting),
    Stopped(R.string.status_stopped),
    Installing(R.string.status_installing),
    StartFailed(R.string.session_start_failed),
    PermissionRequired(R.string.camera_permission_required),
    Ready(R.string.status_ready),
    Starting(R.string.status_starting),
    CameraUnavailable(R.string.status_camera_unavailable),
    CaptureFailed(R.string.status_capture_failed),
    BindFailed(R.string.status_bind_failed),
    WifiUnavailable(R.string.status_wifi_unavailable)
}
