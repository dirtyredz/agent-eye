package local.agenteye.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodesTest {
    private var now = 1_000L
    private var next = 111_111
    private fun nextDigits(): Int {
        val value = next
        next += 111_111
        return value
    }
    private fun codes(limit: Int = 10) =
        PairingCodes(clock = { now }, digits = ::nextDigits, clientLimit = limit)

    @Test fun codeIsSixDigitsAndExpiresAfterTwoMinutes() {
        val pairing = codes()
        val first = pairing.current()
        assertEquals("111111", first.value)
        assertEquals(now + 120_000, first.expiresAt)
        now += 119_999
        assertEquals(first, pairing.current())
        now += 1
        val second = pairing.current()
        assertEquals("222222", second.value)
        assertEquals(now + 120_000, second.expiresAt)
    }

    @Test fun leadingZerosArePreserved() {
        next = 42
        assertEquals("000042", codes().current().value)
    }

    @Test fun expiredCodeIsRejected() {
        val pairing = codes()
        val old = pairing.current().value
        now += 120_000
        assertEquals(PairResult.REJECTED, pairing.attempt("a", old))
    }

    @Test fun correctCodeWorksOnceThenRotates() {
        val pairing = codes()
        val first = pairing.current().value
        assertEquals(PairResult.PAIRED, pairing.attempt("a", first))
        assertNotEquals(first, pairing.current().value)
        assertEquals(PairResult.REJECTED, pairing.attempt("a", first))
    }

    @Test fun codeIsBurnedAfterFiveWrongAttempts() {
        val pairing = codes()
        val first = pairing.current().value
        repeat(4) { assertEquals(PairResult.REJECTED, pairing.attempt("a", "000000")) }
        assertEquals(first, pairing.current().value)
        assertEquals(PairResult.REJECTED, pairing.attempt("a", "000000"))
        // Burned: the original code is dead even for the right answer.
        assertNotEquals(first, pairing.current().value)
        assertEquals(PairResult.REJECTED, pairing.attempt("b", first))
        assertEquals(PairResult.PAIRED, pairing.attempt("b", pairing.current().value))
    }

    @Test fun malformedSubmissionsNeverMatch() {
        val pairing = codes()
        val code = pairing.current().value
        for (bad in listOf("", code.dropLast(1), code + "0", "$code ", "abcdef")) {
            assertEquals(PairResult.REJECTED, pairing.attempt("a", bad))
        }
    }

    @Test fun refreshInvalidatesPreviousCode() {
        val pairing = codes()
        val old = pairing.current().value
        val fresh = pairing.refresh()
        assertNotEquals(old, fresh.value)
        assertEquals(now + 120_000, fresh.expiresAt)
        assertEquals(PairResult.REJECTED, pairing.attempt("a", old))
    }

    @Test fun perClientRateLimitIsIndependentAndExpires() {
        val pairing = codes(limit = 3)
        val code = pairing.current().value
        repeat(3) { pairing.attempt("noisy", "000000") }
        assertEquals(PairResult.RATE_LIMITED, pairing.attempt("noisy", code))
        // Another client is unaffected and the limited attempt did not burn the code.
        assertEquals(PairResult.PAIRED, pairing.attempt("quiet", code))
        now += 60_000
        assertEquals(PairResult.PAIRED, pairing.attempt("noisy", pairing.current().value))
    }

    @Test fun defaultGeneratorProducesSixDigitCodes() {
        val values = (1..50).map { PairingCodes().current().value }
        assertTrue(values.all { it.matches(Regex("[0-9]{6}")) })
        assertTrue(values.toSet().size > 1)
    }
}
