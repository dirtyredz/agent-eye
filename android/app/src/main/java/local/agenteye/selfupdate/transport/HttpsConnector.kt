package local.agenteye.selfupdate.transport

import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Production [HttpConnector]: system trust anchors (see `network_security_config.xml`), no
 * redirects, no caches, no cookies (no CookieHandler is installed) and only fixed headers.
 */
class HttpsConnector : HttpConnector {
    override fun open(uri: URI, timeoutMs: Int): HttpExchange {
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "Accept",
            if (uri.host ==
                UpdateUrlPolicy.API_HOST
            ) {
                API_ACCEPT
            } else {
                "*/*"
            }
        )
        connection.setRequestProperty("User-Agent", "agent-eye-updater")
        if (uri.host ==
            UpdateUrlPolicy.API_HOST
        ) {
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val code = connection.responseCode
        return object : HttpExchange {
            override val code = code

            override fun header(name: String): String? = connection.getHeaderField(name)

            override fun body(): InputStream = connection.inputStream

            override fun close() = connection.disconnect()
        }
    }

    private companion object {
        const val API_ACCEPT = "application/vnd.github+json"
    }
}
