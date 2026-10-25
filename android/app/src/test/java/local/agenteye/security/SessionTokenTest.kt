package local.agenteye.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTokenTest {
    @Test fun tokensAreFreshAndCleared() {
        val tokens = (1..16).map { SessionToken() }
        assertEquals(16, tokens.map { it.display() }.toSet().size)
        tokens.forEach {
            val value = it.display()
            assertTrue(value.matches(Regex("[0-9a-f]{64}")))
            assertTrue(it.accepts("Bearer $value"))
            assertFalse(it.accepts(null))
            assertFalse(it.accepts("bearer $value"))
            it.clear()
            assertEquals("", it.display())
            assertFalse(it.accepts("Bearer $value"))
            assertFalse(it.accepts("Bearer "))
        }
    }
}
