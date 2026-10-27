package local.agenteye.camera

/**
 * The closed set of capture failures. [code] is the nonsensitive wire value reported by
 * `GET /status` (`src/camera/client.mjs` and docs/AGENT-EYE.md depend on it); it is produced only
 * at the HTTP edge, everywhere else the enum travels.
 */
enum class CaptureError(val code: String) {
    SessionStartFailed("session_start_failed"),
    CaptureFailed("capture_failed"),
    CameraUnavailable("camera_unavailable"),
    BindFailed("bind_failed"),
    WifiUnavailable("wifi_unavailable")
}
