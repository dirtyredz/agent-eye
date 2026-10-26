package local.agenteye.ui

import local.agenteye.security.PairingCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCopyTest {
    @Test fun absentAndExpiredCodesCannotBeCopied() {
        assertNull(PairingCopy.current(null, 0, 32))
        assertNull(PairingCopy.current(PairingCodes.Code("001234", 100), 100, 33))
    }

    @Test fun codeIsLiveOnlyBeforeItsExpiry() {
        val code = PairingCodes.Code("001234", 100)
        assertTrue(code.isLive(99))
        assertFalse(code.isLive(100))
    }

    @Test fun preservesLeadingZerosAndOnlyConfirmsBeforeAndroid13() {
        val code = PairingCodes.Code("001234", 100)
        assertEquals("001234", PairingCopy.current(code, 99, 32)?.text)
        assertTrue(requireNotNull(PairingCopy.current(code, 99, 32)).showConfirmation)
        assertFalse(requireNotNull(PairingCopy.current(code, 99, 33)).showConfirmation)
    }
}
