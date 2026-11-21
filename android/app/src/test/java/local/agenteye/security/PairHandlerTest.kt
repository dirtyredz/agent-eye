package local.agenteye.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairHandlerTest {
    private val token = SessionToken()
    private val codes = PairingCodes(digits = { 123456 }, clientLimit = 3, maxAttempts = 100)
    private val handler = PairHandler(codes, token)

    @Test fun correctCodeReturnsTheSessionToken() {
        val r = handler.handle("c", """{"code":"123456"}""")
        assertTrue(r is PairOutcome.Token)
        assertEquals(token.display(), (r as PairOutcome.Token).secret)
    }

    @Test fun whitespaceAroundFieldsIsAccepted() {
        assertTrue(handler.handle("c", """{ "code" : "123456" }""") is PairOutcome.Token)
    }

    @Test fun wrongCodeIsRejected() {
        assertEquals(PairOutcome.Rejected, handler.handle("c", """{"code":"000000"}"""))
    }

    @Test fun malformedBodiesAreBadRequests() {
        for (body in listOf(
            "",
            "{}",
            """{"code":"12345"}""",
            """{"code":"1234567"}""",
            """{"code":123456}""",
            """{"code":"123456","x":1}""",
            "not json",
            "{\"code\":\"123456\"}\n",
            """{"code":"12345é"}"""
        )) {
            assertEquals(body, PairOutcome.BadRequest, handler.handle("c", body))
        }
    }

    @Test fun malformedBodiesDoNotSpendAttempts() {
        repeat(10) { handler.handle("c", "{}") }
        assertTrue(handler.handle("c", """{"code":"123456"}""") is PairOutcome.Token)
    }

    @Test fun clientIsRateLimited() {
        repeat(3) { handler.handle("c", """{"code":"000000"}""") }
        assertEquals(PairOutcome.RateLimited, handler.handle("c", """{"code":"123456"}"""))
        assertTrue(handler.handle("other", """{"code":"123456"}""") is PairOutcome.Token)
    }

    @Test fun clearedTokenIsUnavailable() {
        token.clear()
        assertEquals(PairOutcome.Unavailable, handler.handle("c", """{"code":"123456"}"""))
        assertEquals(PairOutcome.BadRequest, handler.handle("c", "{}"))
    }
}
