package local.agenteye.ui.pairing

import local.agenteye.security.PairingCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PairingStateTest {
    private val code = PairingCodes.Code("001234", 2000)

    @Test fun countdownRoundsUpAndExpiresExactly() {
        assertEquals("001234", PairingState.from(code, 999, true).digits)
        assertEquals(2L, PairingState.from(code, 999, true).seconds)
        assertEquals(1L, PairingState.from(code, 1000, true).seconds)
        assertEquals(1L, PairingState.from(code, 1999, true).seconds)
        assertFalse(PairingState.from(code, 2000, true).live)
        assertFalse(PairingState.from(code, 2001, true).live)
    }

    @Test fun stopDisconnectAndMissingCodeClearCredentials() {
        assertEquals(PairingState(), PairingState.from(code, 0, false))
        assertEquals(PairingState(), PairingState.from(null, 0, true))
    }

    @Test fun renewalAndTapUseFreshSource() {
        val replacement = PairingCodes.Code("009876", 4000)
        assertEquals("001234", PairingState.from(code, 0, true).digits)
        assertEquals("009876", PairingCopy.current(replacement, 2000, 33)?.text)
        assertEquals("009876", PairingState.from(replacement, 2000, true).digits)
        assertNull(PairingCopy.current(null, 2000, 33))
    }
}
