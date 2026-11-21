package local.agenteye.selfupdate.contract

import java.io.InputStream
import java.net.URI

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
