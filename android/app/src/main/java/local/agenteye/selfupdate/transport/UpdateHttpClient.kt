package local.agenteye.selfupdate.transport

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import local.agenteye.selfupdate.UpdateCancelled
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem

/** One HTTP exchange; the production implementation wraps HttpsURLConnection. */
interface HttpExchange : AutoCloseable {
    val code: Int

    fun header(name: String): String?

    fun body(): InputStream
}

/**
 * Opens a GET with the fixed updater headers only (Accept, User-Agent). Implementations must
 * not follow redirects, send cookies, cache, or add authentication of any kind.
 */
fun interface HttpConnector {
    fun open(uri: URI, timeoutMs: Int): HttpExchange
}

/** Persisted manual-check backoff after GitHub 403/429 responses. */
interface Backoff {
    var until: Long
}

/**
 * Bounded HTTPS transport for update metadata and the APK. Separate from LAN networking: it
 * uses the ordinary system network. Every URL (initial and each redirect hop) passes
 * [UpdateUrlPolicy]; bodies are bounded even without a trustworthy Content-Length; overall
 * deadlines and cancellation are enforced between reads. Failures are sanitized.
 */
class UpdateHttpClient(
    private val connector: HttpConnector,
    private val backoff: Backoff,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Int = 20_000
) {
    private val active = AtomicReference<HttpExchange?>(null)

    @Volatile private var cancelled = false

    /** Disconnects the in-flight exchange from another thread. */
    fun cancel() {
        cancelled = true
        runCatching { active.getAndSet(null)?.close() }
    }

    fun reset() {
        cancelled = false
    }

    fun fetch(uri: URI, maxBytes: Long, deadlineMs: Long = 30_000): ByteArray {
        val out = ByteArrayOutputStream()
        stream(uri, maxBytes, null, out, deadlineMs) {}
        return out.toByteArray()
    }

    /**
     * Streams the body into [out]. With [exactSize] the body must be exactly that long;
     * otherwise at most [maxBytes]. Excess or truncated bodies fail.
     */
    fun stream(
        uri: URI,
        maxBytes: Long,
        exactSize: Long?,
        out: OutputStream,
        deadlineMs: Long,
        progress: (Long) -> Unit
    ) {
        if (now() < backoff.until) throw UpdateFailure(UpdateProblem.RateLimited, backoff.until)
        if (!UpdateUrlPolicy.allowed(uri)) throw UpdateFailure(UpdateProblem.Network)
        val deadline = now() + deadlineMs
        val limit = exactSize ?: maxBytes
        var target = uri
        var hops = 0
        while (true) {
            checkActive(deadline)
            val exchange = try {
                connector.open(target, timeoutMs)
            } catch (_: IOException) {
                throw failure(UpdateProblem.Network)
            }
            active.set(exchange)
            try {
                val code = exchange.code
                if (code in 300..399) {
                    val next = UpdateUrlPolicy.redirect(target, exchange.header("Location"))
                    if (next == null || ++hops > UpdateUrlPolicy.MAX_REDIRECTS) {
                        throw UpdateFailure(UpdateProblem.Network)
                    }
                    target = next
                    continue
                }
                status(code, exchange, target)
                val declared = exchange.header("Content-Length")?.toLongOrNull()
                if (declared != null &&
                    (declared > limit || (exactSize != null && declared != exactSize))
                ) {
                    throw UpdateFailure(tooLarge(exactSize))
                }
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                exchange.body().use { input ->
                    while (true) {
                        checkActive(deadline)
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw UpdateFailure(tooLarge(exactSize))
                        out.write(buffer, 0, n)
                        progress(total)
                    }
                }
                if (exactSize != null &&
                    total != exactSize
                ) {
                    throw UpdateFailure(UpdateProblem.Network)
                }
                return
            } catch (_: IOException) {
                throw failure(UpdateProblem.Network)
            } finally {
                active.compareAndSet(exchange, null)
                runCatching { exchange.close() }
            }
        }
    }

    private fun tooLarge(exactSize: Long?) = if (exactSize !=
        null
    ) {
        UpdateProblem.Corrupt
    } else {
        UpdateProblem.Malformed
    }

    private fun checkActive(deadline: Long) {
        if (cancelled) throw UpdateCancelled()
        if (now() > deadline) throw UpdateFailure(UpdateProblem.Network)
    }

    /** Sanitized: the IOException (which may name a URL) is deliberately not chained. */
    private fun failure(problem: UpdateProblem): Exception =
        if (cancelled) UpdateCancelled() else UpdateFailure(problem)

    private fun status(code: Int, exchange: HttpExchange, uri: URI) {
        when {
            code == 200 -> return

            (code == 403 || code == 429) && UpdateUrlPolicy.isApi(uri) -> {
                val retry = exchange.header("Retry-After")?.toLongOrNull()?.let {
                    now() + it * 1000
                }
                val reset = exchange.header("X-RateLimit-Reset")?.toLongOrNull()?.let { it * 1000 }
                val until = (retry ?: reset ?: (now() + 60_000)).coerceIn(
                    now() + 1000,
                    now() + 86_400_000
                )
                backoff.until = until
                throw UpdateFailure(UpdateProblem.RateLimited, until)
            }

            code == 404 -> throw UpdateFailure(
                if (UpdateUrlPolicy.isApi(uri)) UpdateProblem.NoRelease else UpdateProblem.Malformed
            )

            else -> throw UpdateFailure(UpdateProblem.Network)
        }
    }
}
