package local.agenteye.selfupdate.transport

import java.io.ByteArrayOutputStream
import java.net.URI
import local.agenteye.selfupdate.FakeConnector
import local.agenteye.selfupdate.FakeResponse
import local.agenteye.selfupdate.MemoryBackoff
import local.agenteye.selfupdate.UpdateCancelled
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateHttpClientTest {
    private val asset = UpdateUrlPolicy.asset("v1.1.1", "agent-eye.apk")
    private val latest = UpdateUrlPolicy.latest()
    private val connector = FakeConnector()
    private val backoff = MemoryBackoff()
    private var clock = 1_000_000L
    private val client = UpdateHttpClient(connector, backoff, now = { clock })

    private fun problem(block: () -> Unit) = assertThrows(UpdateFailure::class.java) {
        block()
    }.problem

    private fun route(uri: Any, response: () -> FakeResponse) {
        connector.routes[uri.toString()] = response
    }

    private fun redirect(to: String) = FakeResponse(302, headers = mapOf("Location" to to))

    @Test fun followsApprovedRedirectChainsUpToFiveHops() {
        route(asset) { redirect("https://objects.githubusercontent.com/1") }
        for (i in 1..4) route("https://objects.githubusercontent.com/$i") { redirect("/${i + 1}") }
        route("https://objects.githubusercontent.com/5") { FakeResponse(200, "ok".toByteArray()) }
        val out = ByteArrayOutputStream()
        client.stream(asset, 10, 2, out, 1000) {}
        assertArrayEquals("ok".toByteArray(), out.toByteArray())
        route("https://objects.githubusercontent.com/5") { redirect("/6") }
        assertEquals(UpdateProblem.Network, problem { client.fetch(asset, 10) })
    }

    @Test fun refusesForbiddenRedirectsAndInitialUrls() {
        route(asset) { redirect("http://objects.githubusercontent.com/x") }
        assertEquals(UpdateProblem.Network, problem { client.fetch(asset, 10) })
        route(asset) { redirect("https://objects.githubusercontent.com.evil/x") }
        assertEquals(UpdateProblem.Network, problem { client.fetch(asset, 10) })
        route(latest) { redirect("https://api.github.com/repositories/1/releases/latest") }
        assertEquals(UpdateProblem.Network, problem { client.fetch(latest, 10) })
        assertEquals(
            UpdateProblem.Network,
            problem {
                client.fetch(URI("https://example.com/x"), 10)
            }
        )
        assertTrue(
            connector.requests.none {
                it.host != "github.com" && it.host != "api.github.com"
            }
        )
    }

    @Test fun boundsOversizeTruncatedAndSlowBodies() {
        route(latest) { FakeResponse(200, ByteArray(11)) }
        assertEquals(UpdateProblem.Malformed, problem { client.fetch(latest, 10) })
        route(latest) { FakeResponse(200, ByteArray(5), mapOf("Content-Length" to "99")) }
        assertEquals(UpdateProblem.Malformed, problem { client.fetch(latest, 10) })
        route(asset) { FakeResponse(200, ByteArray(5)) }
        assertEquals(
            UpdateProblem.Network,
            problem {
                client.stream(asset, 10, 6, ByteArrayOutputStream(), 1000) {}
            }
        )
        route(asset) { FakeResponse(200, ByteArray(7)) }
        assertEquals(
            UpdateProblem.Corrupt,
            problem {
                client.stream(asset, 10, 6, ByteArrayOutputStream(), 1000) {}
            }
        )
        route(asset) { FakeResponse(200, ByteArray(6), failAfter = 3) }
        assertEquals(
            UpdateProblem.Network,
            problem {
                client.stream(asset, 10, 6, ByteArrayOutputStream(), 1000) {}
            }
        )
        route(asset) { FakeResponse(200, ByteArray(6)) }
        assertEquals(
            UpdateProblem.Network,
            problem {
                client.stream(asset, 10, 6, ByteArrayOutputStream(), 1000) {
                    clock +=
                        2000
                }
            }
        )
    }

    @Test fun rateLimitsPersistBackoffAndAreDistinctFromNotFound() {
        route(latest) { FakeResponse(429, headers = mapOf("Retry-After" to "120")) }
        assertEquals(UpdateProblem.RateLimited, problem { client.fetch(latest, 10) })
        assertEquals(clock + 120_000, backoff.until)
        val before = connector.requests.size
        assertEquals(UpdateProblem.RateLimited, problem { client.fetch(latest, 10) })
        assertEquals("no request while backing off", before, connector.requests.size)
        clock += 121_000
        route(latest) { FakeResponse(404) }
        assertEquals(UpdateProblem.NoRelease, problem { client.fetch(latest, 10) })
        route(latest) {
            FakeResponse(403, headers = mapOf("X-RateLimit-Reset" to "${(clock + 5000) / 1000}"))
        }
        assertEquals(UpdateProblem.RateLimited, problem { client.fetch(latest, 10) })
        clock += 10_000
        route(latest) { FakeResponse(503) }
        assertEquals(UpdateProblem.Network, problem { client.fetch(latest, 10) })
    }

    @Test fun cancellationStopsTheTransferAndClosesTheExchange() {
        val response = FakeResponse(200, ByteArray(200_000))
        route(asset) { response }
        assertThrows(UpdateCancelled::class.java) {
            client.stream(asset, 300_000, 200_000, ByteArrayOutputStream(), 1000) {
                client.cancel()
            }
        }
        assertTrue(response.closed)
    }
}
