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
        assertEquals(200, r.status)
        assertEquals("""{"token":"${token.display()}"}""", r.body)
    }

    @Test fun whitespaceAroundFieldsIsAccepted() {
        assertEquals(200, handler.handle("c", """{ "code" : "123456" }""").status)
    }

    @Test fun wrongCodeIsForbiddenAndLeaksNothing() {
        val r = handler.handle("c", """{"code":"000000"}""")
        assertEquals(403, r.status)
        assertEquals("", r.body)
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
            assertEquals(body, 400, handler.handle("c", body).status)
        }
    }

    @Test fun malformedBodiesDoNotSpendAttempts() {
        repeat(10) { handler.handle("c", "{}") }
        assertEquals(200, handler.handle("c", """{"code":"123456"}""").status)
    }

    @Test fun clientIsRateLimited() {
        repeat(3) { handler.handle("c", """{"code":"000000"}""") }
        assertEquals(429, handler.handle("c", """{"code":"123456"}""").status)
        assertEquals(200, handler.handle("other", """{"code":"123456"}""").status)
    }

    @Test fun clearedTokenIsUnavailable() {
        token.clear()
        assertEquals(503, handler.handle("c", """{"code":"123456"}""").status)
        assertTrue(handler.handle("c", "{}").body.isEmpty())
    }
}
