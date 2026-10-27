package local.agenteye.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureErrorTest {
    /** `GET /status` consumers (src/camera/client.mjs, docs/AGENT-EYE.md) depend on these. */
    @Test fun wireCodesAreStable() {
        assertEquals(
            listOf(
                "session_start_failed",
                "capture_failed",
                "camera_unavailable",
                "bind_failed",
                "wifi_unavailable"
            ),
            CaptureError.entries.map { it.code }
        )
    }
}
