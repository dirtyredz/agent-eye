package local.agenteye.service

import local.agenteye.camera.CaptureError

/**
 * The closed set of session-wide failures. [code] is the nonsensitive wire value reported by
 * `GET /status` (`src/camera/client.mjs` and docs/AGENT-EYE.md depend on it); it is produced only
 * at the HTTP edge, everywhere else the enum travels. Camera failures are carried over from
 * [CaptureError] by [of], keeping their codes.
 */
enum class SessionError(val code: String) {
    SessionStartFailed("session_start_failed"),
    CaptureFailed(CaptureError.CaptureFailed.code),
    CameraUnavailable(CaptureError.CameraUnavailable.code),
    BindFailed("bind_failed"),
    WifiUnavailable("wifi_unavailable");

    companion object {
        fun of(error: CaptureError): SessionError = when (error) {
            CaptureError.CaptureFailed -> CaptureFailed
            CaptureError.CameraUnavailable -> CameraUnavailable
        }
    }
}
