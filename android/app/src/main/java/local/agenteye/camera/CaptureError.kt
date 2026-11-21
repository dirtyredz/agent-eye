package local.agenteye.camera

/**
 * Camera-level failures only. Session-wide failures (Wi-Fi, bind, session start) live in
 * `service/SessionError`, which wraps these at the CaptureService boundary.
 */
enum class CaptureError(val code: String) {
    CaptureFailed("capture_failed"),
    CameraUnavailable("camera_unavailable")
}
