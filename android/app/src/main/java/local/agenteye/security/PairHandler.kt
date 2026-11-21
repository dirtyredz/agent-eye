package local.agenteye.security

/**
 * The pairing exchange behind `POST /pair`: validates the request body and trades a correct
 * code for the session token. Transport-agnostic: takes the raw body, returns a [PairOutcome],
 * so it is unit-tested without sockets. Never logs codes.
 */
class PairHandler(private val codes: PairingCodes, private val token: SessionToken) {
    fun handle(client: String, body: String): PairOutcome {
        if (body.any { it.code < 32 || it.code > 126 }) return PairOutcome.BadRequest
        val code = PAIR_BODY.matchEntire(body)?.groupValues?.get(1) ?: return PairOutcome.BadRequest
        return when (codes.attempt(client, code)) {
            PairResult.PAIRED -> {
                val secret = token.display()
                if (secret.isEmpty()) PairOutcome.Unavailable else PairOutcome.Token(secret)
            }

            PairResult.REJECTED -> PairOutcome.Rejected

            PairResult.RATE_LIMITED -> PairOutcome.RateLimited
        }
    }

    companion object {
        /** Largest accepted request body, in bytes. */
        const val MAX_BODY = 64
        private val PAIR_BODY = Regex("""\{\s*"code"\s*:\s*"([0-9]{6})"\s*\}""")
    }
}
