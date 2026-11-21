package local.agenteye.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureErrorTest {
    @Test fun cameraWireCodesAreStable() {
        val codes = CaptureError.entries.map { it.code }
        assertEquals(listOf("capture_failed", "camera_unavailable"), codes)
    }
}
