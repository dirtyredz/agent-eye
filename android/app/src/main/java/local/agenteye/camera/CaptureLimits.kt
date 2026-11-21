package local.agenteye.camera

import java.util.concurrent.TimeUnit

/** The still-capture contract shared by the camera, the HTTP server and the service. */
object CaptureLimits {
    const val PORT = 8765

    /** Minimum spacing between still captures; enforced by the server (429) and the camera. */
    const val INTERVAL_MS = 1000L
    const val CAPTURE_TIMEOUT_MS = 5000L
    const val CONNECTION_TIMEOUT_MS = 10000L

    val INTERVAL_NS: Long = TimeUnit.MILLISECONDS.toNanos(INTERVAL_MS)
}
