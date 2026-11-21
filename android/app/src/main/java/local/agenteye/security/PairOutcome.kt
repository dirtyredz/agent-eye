package local.agenteye.security

/** The result of a pairing exchange, free of HTTP; the transport maps it to a status and body. */
sealed interface PairOutcome {
    class Token(val secret: String) : PairOutcome

    data object BadRequest : PairOutcome

    data object Rejected : PairOutcome

    data object RateLimited : PairOutcome

    data object Unavailable : PairOutcome
}
