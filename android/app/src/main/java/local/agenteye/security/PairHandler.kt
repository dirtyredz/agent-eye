package local.agenteye.security

/**
 * The pairing exchange behind `POST /pair`: validates the request body and trades a correct
 * code for the session token. Transport-agnostic: takes the raw body, returns a status and
 * response body, so it is unit-tested without sockets. Never logs codes.
 */
class PairHandler(private val codes: PairingCodes, private val token: SessionToken) {
    class Response(val status: Int, val body: String = "")

    fun handle(client: String, body: String): Response {
        if (body.any { it.code < 32 || it.code > 126 }) return Response(400)
        val code = PAIR_BODY.matchEntire(body)?.groupValues?.get(1) ?: return Response(400)
        return when (codes.attempt(client, code)) {
            PairResult.PAIRED -> {
                val secret = token.display()
                if (secret.isEmpty()) Response(503) else Response(200, "{\"token\":\"$secret\"}")
            }

            PairResult.REJECTED -> Response(403)

            PairResult.RATE_LIMITED -> Response(429)
        }
    }

    companion object {
        /** Largest accepted request body, in bytes. */
        const val MAX_BODY = 64
        private val PAIR_BODY = Regex("""\{\s*"code"\s*:\s*"([0-9]{6})"\s*\}""")
    }
}
