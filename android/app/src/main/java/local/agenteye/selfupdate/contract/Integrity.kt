package local.agenteye.selfupdate.contract

import java.io.File
import java.security.MessageDigest

/** SHA-256 and lower-case hex encoding shared by release, download and install. A leaf. */
object Integrity {
    fun sha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun sha256(bytes: ByteArray): String = hex(sha256Digest().digest(bytes))

    fun sha256(file: File): String {
        val digest = sha256Digest()
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return hex(digest.digest())
    }
}
