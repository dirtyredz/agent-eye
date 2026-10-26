package local.agenteye.http

import java.io.InputStream

class HttpFailure(val status: Int) : Exception()
data class HttpRequest(
    val path: String,
    val authorization: String?,
    val method: String = "GET",
    /** Raw body bytes as ISO-8859-1 (lossless); the route's handler validates the content. */
    val body: String = ""
) {
    companion object {
        /**
         * [bodyLimit] is the route policy: the largest body a method+path may carry, 0 for none.
         * This parser only enforces it; it knows no routes.
         */
        fun read(
            input: InputStream,
            bodyLimit: (method: String, path: String) -> Int = { _, _ -> 0 }
        ): HttpRequest {
            val bytes = ArrayList<Byte>()
            while (true) {
                val c = input.read()
                if (c < 0 || c > 126 || (c < 32 && c != 13 && c != 10)) throw HttpFailure(400)
                bytes.add(c.toByte())
                if (bytes.size > 8192) throw HttpFailure(400)
                val n = bytes.size
                if (n >= 4 && bytes.subList(n - 4, n) == listOf<Byte>(13, 10, 13, 10)) break
            }
            val lines = bytes.toByteArray().toString(Charsets.US_ASCII).dropLast(4).split("\r\n")
            if (lines.any { line -> line.any { it.code < 32 } }) throw HttpFailure(400)
            val first = lines.first().split(' ')
            if (first.size != 3 || !first[0].matches(Regex("[A-Z]+")) ||
                !first[1].matches(Regex("/[^\\s]*")) ||
                first[2] != "HTTP/1.1"
            ) {
                throw HttpFailure(400)
            }
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) throw HttpFailure(400)
                val name = line.substring(0, colon)
                if (!name.matches(Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))) throw HttpFailure(400)
                if (headers.put(name.lowercase(), line.substring(colon + 1).trim()) !=
                    null
                ) {
                    throw HttpFailure(400)
                }
            }
            if (headers["host"].isNullOrBlank() || "transfer-encoding" in headers ||
                "expect" in headers
            ) {
                throw HttpFailure(400)
            }
            val length = headers["content-length"]
            val limit = bodyLimit(first[0], first[1])
            if (limit > 0) {
                // A route that takes a body: a declared, bounded, fully-read length.
                if (length == null || !length.matches(Regex("[0-9]{1,6}")) ||
                    length.toInt() !in 1..limit
                ) {
                    throw HttpFailure(400)
                }
                val body = ByteArray(length.toInt())
                var got = 0
                while (got < body.size) {
                    val n = input.read(body, got, body.size - got)
                    if (n < 0) throw HttpFailure(400)
                    got += n
                }
                return HttpRequest(
                    first[1],
                    headers["authorization"],
                    first[0],
                    body.toString(Charsets.ISO_8859_1)
                )
            }
            if (length != null && length != "0") throw HttpFailure(400)
            if (first[0] != "GET") throw HttpFailure(405)
            return HttpRequest(first[1], headers["authorization"])
        }
    }
}
