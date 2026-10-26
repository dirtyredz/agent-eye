package local.agenteye.http

import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import local.agenteye.security.PairingCodes
import local.agenteye.security.SessionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillServerTest {
    private val local = InetAddress.getByName("127.0.0.1")
    private val jpeg = byteArrayOf(-1, -40, 1, 2, -1, -39)
    private fun request(server: StillServer, token: String, path: String = "/shot.jpg"): String =
        Socket(local, server.localPort).use {
            it.soTimeout = 2000
            it.getOutputStream().write(
                (
                    "GET $path HTTP/1.1\r\nHost: phone\r\n" +
                        "Authorization: Bearer $token\r\n\r\n"
                    ).toByteArray()
            )
            it.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }

    @Test fun authenticationRoutingFreshnessAndResponseContract() {
        val token = SessionToken()
        val count = AtomicInteger()
        StillServer(local, 0, { true }, token, { "{}" }, {
            CompletableFuture.completedFuture(Shot(jpeg, 123, count.incrementAndGet().toLong()))
        }, intervalMs = 0).use { server ->
            assertTrue(request(server, "wrong").startsWith("HTTP/1.1 401"))
            assertEquals(0, count.get())
            assertTrue(request(server, token.display(), "/unknown").startsWith("HTTP/1.1 404"))
            assertTrue(
                request(
                    server,
                    token.display(),
                    "/status"
                ).contains("Content-Type: application/json")
            )
            val first = request(server, token.display())
            assertTrue(first.contains("Content-Type: image/jpeg\r\n"))
            assertTrue(first.contains("Content-Length: 6\r\n"))
            assertTrue(first.contains("Cache-Control: no-store\r\n"))
            assertTrue(first.contains("X-Capture-Sequence: 1\r\n"))
            assertTrue(request(server, token.display()).contains("X-Capture-Sequence: 2\r\n"))
            assertEquals(2, count.get())
            token.clear()
            assertTrue(request(server, "").startsWith("HTTP/1.1 401"))
        }
    }

    @Test fun subnetDeniedBeforeCapture() {
        val count = AtomicInteger()
        StillServer(local, 0, { false }, SessionToken(), { "{}" }, {
            count.incrementAndGet()
            CompletableFuture()
        }).use { server ->
            Socket(local, server.localPort).use { socket ->
                socket.soTimeout = 1000
                assertEquals(-1, socket.getInputStream().read())
            }
            assertEquals(0, count.get())
        }
    }

    @Test fun captureTimeoutAndRateLimit() {
        val token = SessionToken()
        val future = CompletableFuture<Shot>()
        StillServer(local, 0, {
            true
        }, token, { "{}" }, { future }, captureTimeoutMs = 40).use { server ->
            assertTrue(request(server, token.display()).startsWith("HTTP/1.1 503"))
            assertTrue(future.isCancelled)
            assertTrue(request(server, token.display()).startsWith("HTTP/1.1 429"))
        }
    }

    @Test fun concurrentCaptureRejectedAndShutdownCancelsWork() {
        val token = SessionToken()
        val entered = CountDownLatch(1)
        val future = CompletableFuture<Shot>()
        val server =
            StillServer(local, 0, { true }, token, { "{}" }, {
                entered.countDown()
                future
            }, intervalMs = 0)
        val socket = Socket(local, server.localPort)
        try {
            socket.getOutputStream().write(
                (
                    "GET /shot.jpg HTTP/1.1\r\nHost: p\r\n" +
                        "Authorization: Bearer ${token.display()}\r\n\r\n"
                    ).toByteArray()
            )
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue(request(server, token.display()).startsWith("HTTP/1.1 429"))
            server.close()
            assertTrue(future.isCancelled)
            socket.soTimeout = 1000
            assertEquals(-1, socket.getInputStream().read())
        } finally {
            socket.close()
            server.close()
        }
    }

    @Test fun fourConnectionLimitAndAbsoluteReadDeadline() {
        val accepted = CountDownLatch(5)
        val server =
            StillServer(local, 0, {
                accepted.countDown()
                true
            }, SessionToken(), { "{}" }, { CompletableFuture() }, connectionTimeoutMs = 500)
        val sockets = (1..5).map { Socket(local, server.localPort).apply { soTimeout = 1500 } }
        try {
            assertTrue(accepted.await(1, TimeUnit.SECONDS))
            // Fifth is closed immediately; the four partial requests are closed at the deadline.
            assertEquals(-1, sockets.last().getInputStream().read())
            sockets.take(4).forEach { assertEquals(-1, it.getInputStream().read()) }
        } finally {
            sockets.forEach { it.close() }
            server.close()
        }
    }

    @Test fun captureFailureAndOversizeNeverReturnAnImage() {
        val token = SessionToken()
        var call = 0
        StillServer(local, 0, { true }, token, { "{}" }, {
            if (call++ ==
                0
            ) {
                CompletableFuture<Shot>().apply {
                    completeExceptionally(IllegalStateException())
                }
            } else {
                CompletableFuture.completedFuture(Shot(ByteArray(8 * 1024 * 1024 + 1), 123, 1))
            }
        }, intervalMs = 0).use { server ->
            repeat(2) {
                val response = request(server, token.display())
                assertTrue(response.startsWith("HTTP/1.1 503"))
                assertFalse(response.contains("image/jpeg"))
            }
        }
    }

    @Test fun deadlineClosesBlockedWrites() {
        val token = SessionToken()
        val entered = CountDownLatch(1)
        StillServer(local, 0, { true }, token, { "{}" }, {
            entered.countDown()
            CompletableFuture.completedFuture(Shot(ByteArray(8 * 1024 * 1024), 123, 1))
        }, connectionTimeoutMs = 150).use { server ->
            Socket().use { socket ->
                socket.receiveBufferSize = 1024
                socket.connect(java.net.InetSocketAddress(local, server.localPort))
                socket.soTimeout = 2000
                socket.getOutputStream().write(
                    (
                        "GET /shot.jpg HTTP/1.1\r\nHost: p\r\n" +
                            "Authorization: Bearer ${token.display()}\r\n\r\n"
                        ).toByteArray()
                )
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                Thread.sleep(250)
                val received = socket.getInputStream().readBytes()
                assertTrue("blocked write exceeded deadline", received.size < 8 * 1024 * 1024)
            }
        }
    }

    private fun post(server: StillServer, body: String, path: String = "/pair"): String =
        Socket(local, server.localPort).use {
            it.soTimeout = 2000
            it.getOutputStream().write(
                (
                    "POST $path HTTP/1.1\r\nHost: phone\r\n" +
                        "Content-Length: ${body.length}\r\n\r\n$body"
                    ).toByteArray()
            )
            it.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }

    private fun json(code: String) = """{"code":"$code"}"""

    @Test fun pairingExchangesCodeForTokenOnceAndBurnsAfterWrongGuesses() {
        val token = SessionToken()
        val pairing = PairingCodes()
        StillServer(local, 0, { true }, token, { "{}" }, {
            CompletableFuture.completedFuture(Shot(jpeg, 1, 1))
        }, pairing = pairing).use { server ->
            val code = pairing.current().value
            val ok = post(server, json(code))
            assertTrue(ok.startsWith("HTTP/1.1 200"))
            assertTrue(ok.contains("""{"token":"${token.display()}"}"""))
            // Single use: a replay is refused and never carries the token.
            val again = post(server, json(code))
            assertTrue(again.startsWith("HTTP/1.1 403"))
            assertFalse(again.contains(token.display()))
            assertTrue(post(server, "not json").startsWith("HTTP/1.1 400"))
            // Five wrong guesses burn the live code; the right one then fails too.
            val live = pairing.current().value
            val wrong = if (live == "000000") "000001" else "000000"
            repeat(5) { post(server, json(wrong)) }
            assertTrue(post(server, json(live)).startsWith("HTTP/1.1 403"))
        }
    }

    @Test fun pairingDisabledWithoutGateAndClientRateLimited() {
        StillServer(local, 0, { true }, SessionToken(), { "{}" }, {
            CompletableFuture.completedFuture(Shot(jpeg, 1, 1))
        }).use { server ->
            assertTrue(post(server, json("123456")).startsWith("HTTP/1.1 404"))
        }
        val pairing = PairingCodes(clientLimit = 2, maxAttempts = 100)
        StillServer(local, 0, { true }, SessionToken(), { "{}" }, {
            CompletableFuture.completedFuture(Shot(jpeg, 1, 1))
        }, pairing = pairing).use { server ->
            val wrong = json(if (pairing.current().value == "000000") "000001" else "000000")
            assertTrue(post(server, wrong).startsWith("HTTP/1.1 403"))
            assertTrue(post(server, wrong).startsWith("HTTP/1.1 403"))
            assertTrue(post(server, wrong).startsWith("HTTP/1.1 429"))
        }
    }
}
