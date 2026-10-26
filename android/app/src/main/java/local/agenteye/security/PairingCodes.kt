package local.agenteye.security

import java.security.MessageDigest
import java.security.SecureRandom

enum class PairResult { PAIRED, REJECTED, RATE_LIMITED }

/**
 * Short-lived, single-use six-digit pairing code that is exchanged for the session token.
 * Pure state machine: time and randomness are injected so expiry, single use, the attempt
 * limit and the per-client rate limit are unit-testable. Never log codes.
 */
class PairingCodes(
    private val clock: () -> Long = System::currentTimeMillis,
    private val digits: () -> Int = SecureRandom().let { rng -> { rng.nextInt(1_000_000) } },
    private val ttlMs: Long = 120_000,
    private val maxAttempts: Int = 5,
    private val clientLimit: Int = 10,
    private val clientWindowMs: Long = 60_000
) {
    data class Code(val value: String, val expiresAt: Long) {
        fun isLive(now: Long) = expiresAt > now
    }

    private var code = newCode()
    private var failures = 0
    private val clientHits = HashMap<String, ArrayDeque<Long>>()

    private fun newCode(): Code {
        failures = 0
        return Code("%06d".format(digits()), clock() + ttlMs)
    }

    /** The code to display; rotates first when it has expired. */
    @Synchronized fun current(): Code {
        if (!code.isLive(clock())) code = newCode()
        return code
    }

    /** The "New code" button: invalidates the old code immediately. */
    @Synchronized fun refresh(): Code {
        code = newCode()
        return code
    }

    @Synchronized fun attempt(client: String, submitted: String): PairResult {
        val now = clock()
        if (!withinRate(client, now)) return PairResult.RATE_LIMITED
        val live = current()
        // Fixed-length ASCII comparison; a malformed submission simply cannot match.
        val ok = MessageDigest.isEqual(
            live.value.toByteArray(Charsets.US_ASCII),
            submitted.padEnd(
                live.value.length
            ).take(live.value.length).toByteArray(Charsets.US_ASCII)
        ) && submitted.length == live.value.length
        if (ok) {
            code = newCode() // single use
            return PairResult.PAIRED
        }
        if (++failures >= maxAttempts) code = newCode() // burned
        return PairResult.REJECTED
    }

    private fun withinRate(client: String, now: Long): Boolean {
        if (clientHits.size >
            256
        ) {
            clientHits.entries.removeAll {
                it.value.lastOrNull()?.let { t ->
                    now - t >=
                        clientWindowMs
                }
                    ?: true
            }
        }
        val hits = clientHits.getOrPut(client) { ArrayDeque() }
        while (hits.isNotEmpty() && now - hits.first() >= clientWindowMs) hits.removeFirst()
        if (hits.size >= clientLimit) return false
        hits.addLast(now)
        return true
    }
}
