package local.agenteye.service

import local.agenteye.camera.CaptureError
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionErrorTest {
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
            SessionError.entries.map { it.code }
        )
    }

    @Test fun cameraErrorsKeepTheirCodesAcrossTheSessionBoundary() {
        CaptureError.entries.forEach {
            assertEquals(it.code, SessionError.of(it).code)
        }
    }
}
