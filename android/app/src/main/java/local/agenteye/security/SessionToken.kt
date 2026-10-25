package local.agenteye.security

import java.security.MessageDigest
import java.security.SecureRandom

class SessionToken {
    @Volatile private var secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    fun display(): String = secret
    fun accepts(header: String?): Boolean {
        val current = secret
        return current.isNotEmpty() && header != null && MessageDigest.isEqual(
            "Bearer $current".toByteArray(Charsets.US_ASCII), header.toByteArray(Charsets.US_ASCII))
    }
    fun clear() { secret = "" }
}
