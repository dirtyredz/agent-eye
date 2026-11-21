package local.agenteye.service

import local.agenteye.camera.Lens
import local.agenteye.json.StrictJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStatusJsonTest {
    private val base = CaptureSnapshot(
        running = true,
        cameraReady = true,
        address = "http://192.168.1.20:8080",
        error = null,
        availableLenses = listOf(Lens.BACK, Lens.FRONT),
        selectedLens = Lens.FRONT,
        uptimeMs = 5_000L,
        lastCaptureTime = 1_700_000_000_000L
    )

    private fun decode(snapshot: CaptureSnapshot) =
        StrictJson.parse(SessionStatusJson.render(snapshot)) as Map<*, *>

    @Test fun exactlySevenTypedFieldsWithoutLensData() {
        val status = decode(base)
        assertEquals(
            setOf(
                "protocolVersion",
                "sessionReady",
                "cameraReady",
                "boundAddress",
                "uptimeMs",
                "lastCaptureTime",
                "error"
            ),
            status.keys
        )
        assertEquals(1L, status["protocolVersion"])
        assertEquals(true, status["sessionReady"])
        assertEquals(true, status["cameraReady"])
        assertEquals("http://192.168.1.20:8080", status["boundAddress"])
        assertEquals(5_000L, status["uptimeMs"])
        assertEquals(1_700_000_000_000L, status["lastCaptureTime"])
    }

    @Test fun sessionReadyNeedsRunningAndAddressWhileCameraReadyIsIndependent() {
        val cases = listOf(
            Triple(true, "http://a", true),
            Triple(true, null, false),
            Triple(false, "http://a", false),
            Triple(false, null, false)
        )
        for ((running, address, ready) in cases) {
            for (camera in listOf(true, false)) {
                val status = decode(
                    base.copy(running = running, address = address, cameraReady = camera)
                )
                assertEquals("running=$running address=$address", ready, status["sessionReady"])
                assertEquals(camera, status["cameraReady"])
            }
        }
    }

    @Test fun absentValuesAreExplicitNulls() {
        val status = decode(
            base.copy(address = null, lastCaptureTime = null, error = null, running = false)
        )
        for (key in listOf("boundAddress", "lastCaptureTime", "error")) {
            assertEquals(key, true, status.containsKey(key))
            assertNull(key, status[key])
        }
    }

    @Test fun errorIsTheWireCode() {
        assertEquals(
            "wifi_unavailable",
            decode(base.copy(error = SessionError.WifiUnavailable))["error"]
        )
    }

    @Test fun uptimeAndCaptureTimeKeepValuesBeyond32Bits() {
        val big = Int.MAX_VALUE.toLong() + 1_234L
        val status = decode(base.copy(uptimeMs = big, lastCaptureTime = big + 1))
        assertEquals(big, status["uptimeMs"])
        assertEquals(big + 1, status["lastCaptureTime"])
    }
}
