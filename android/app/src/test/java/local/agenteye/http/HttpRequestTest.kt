package local.agenteye.http

import org.junit.Assert.*
import org.junit.Test

class HttpRequestTest {
    private fun read(text: String) = HttpRequest.read(text.byteInputStream())
    private fun rejects(code: Int, text: String) {
        try { read(text); fail("accepted invalid request") } catch (e: HttpFailure) { assertEquals(code, e.status) }
    }
    @Test fun parsesValidRequest() {
        val request = read("GET /shot.jpg HTTP/1.1\r\nHost: phone\r\nAuthorization: Bearer abc\r\n\r\n")
        assertEquals("/shot.jpg", request.path)
        assertEquals("Bearer abc", request.authorization)
    }
    @Test fun rejectsMalformedAndUnboundedRequests() {
        for (text in listOf("", "GET / HTTP/1.1\r\n", "GET  / HTTP/1.1\r\nHost: p\r\n\r\n", "GET / HTTP/1.0\r\nHost: p\r\n\r\n",
            "GET / HTTP/1.1\r\n\r\n", "GET / HTTP/1.1\r\nHost : p\r\n\r\n", "GET / HTTP/1.1\nHost: p\n\n")) rejects(400, text)
        rejects(400, "GET / HTTP/1.1\r\nHost: p\r\nX-Large: ${"a".repeat(8192)}\r\n\r\n")
        for (header in listOf("Authorization: a\r\nauthorization: b", "Content-Length: 1", "Transfer-Encoding: chunked", "Expect: 100-continue", " Folded: x", "X-Test: a\nb", "X-Test: a\rb")) {
            rejects(400, "GET / HTTP/1.1\r\nHost: p\r\n$header\r\n\r\n")
        }
        rejects(405, "POST / HTTP/1.1\r\nHost: p\r\n\r\n")
    }
}
