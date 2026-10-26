package local.agenteye.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrictJsonTest {
    @Test fun roundTripsRecords() {
        val value = mapOf(
            "a" to 1L,
            "b" to "x\"\n",
            "c" to listOf(true, null),
            "d" to mapOf<String, Any?>()
        )
        assertEquals(value, StrictJson.parse(StrictJson.write(value)))
    }

    @Test fun rejectsInvalidDocuments() {
        for (text in listOf(
            "{\"a\":01}",
            "[".repeat(40) + "]".repeat(40),
            "{\"a\":1,\"a\":2}",
            "{} {}",
            "{\"a\":1e}"
        )) {
            assertThrows(text, StrictJson.Invalid::class.java) { StrictJson.parse(text) }
        }
        assertThrows(StrictJson.Invalid::class.java) {
            StrictJson.parse(byteArrayOf(0xC3.toByte()))
        }
    }
}
