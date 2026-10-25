package local.agenteye.http

import java.io.InputStream

class HttpFailure(val status: Int) : Exception()
data class HttpRequest(val path: String, val authorization: String?) {
    companion object {
        fun read(input: InputStream): HttpRequest {
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
                !first[1].matches(Regex("/[^\\s]*")) || first[2] != "HTTP/1.1") throw HttpFailure(400)
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon <= 0) throw HttpFailure(400)
                val name = line.substring(0, colon)
                if (!name.matches(Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))) throw HttpFailure(400)
                if (headers.put(name.lowercase(), line.substring(colon + 1).trim()) != null) throw HttpFailure(400)
            }
            if (headers["host"].isNullOrBlank() || "transfer-encoding" in headers ||
                ("content-length" in headers && headers["content-length"] != "0") ||
                "expect" in headers) throw HttpFailure(400)
            if (first[0] != "GET") throw HttpFailure(405)
            return HttpRequest(first[1], headers["authorization"])
        }
    }
}
