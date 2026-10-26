package local.agenteye.selfupdate.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem
import local.agenteye.selfupdate.contract.ReleaseContract
import local.agenteye.selfupdate.release.ReleasePolicy
import local.agenteye.selfupdate.storage.TransferRecord
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.transport.UpdateHttpClient
import local.agenteye.selfupdate.transport.UpdateUrlPolicy

/**
 * Streams the candidate APK into a private partial file, enforces the declared size, hashes
 * the completed bytes and only then promotes it to the verified file. Interrupted transfers
 * restart from zero (no Range resume: the APK is small).
 */
class ApkDownloader(
    private val store: UpdateStore,
    private val http: UpdateHttpClient,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** A verified, unexpired file for exactly this candidate, if one is retained. */
    fun retained(candidate: Candidate): File? {
        val record = store.readTransfer() ?: return null
        val at = record.verifiedAt ?: return null
        if (record.candidate != candidate || at > now() || now() - at > RETAIN_MS ||
            store.verified.length() != candidate.apkSize
        ) {
            store.clearTransfer()
            return null
        }
        return store.verified
    }

    fun download(candidate: Candidate, progress: (Long) -> Unit): File {
        retained(candidate)?.let { return it }
        store.clearTransfer()
        // Room for the APK, the installer's staged copy and a safety margin.
        if (store.usableSpace() <
            candidate.apkSize * 2 + MARGIN
        ) {
            throw UpdateFailure(UpdateProblem.NoSpace)
        }
        store.saveTransfer(TransferRecord(candidate))
        try {
            FileOutputStream(store.partial).use { file ->
                val guarded = object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        try {
                            file.write(b, off, len)
                        } catch (_: IOException) {
                            throw UpdateFailure(UpdateProblem.NoSpace)
                        }
                    }
                }
                http.stream(
                    UpdateUrlPolicy.asset(candidate.tag, ReleaseContract.APK),
                    ReleasePolicy.MAX_APK_BYTES,
                    candidate.apkSize,
                    guarded,
                    DEADLINE_MS,
                    progress
                )
                try {
                    file.fd.sync()
                } catch (_: IOException) {
                    throw UpdateFailure(UpdateProblem.NoSpace)
                }
            }
            if (store.partial.length() != candidate.apkSize ||
                sha256(store.partial) != candidate.apkSha256
            ) {
                throw UpdateFailure(UpdateProblem.Corrupt)
            }
            if (!store.promote()) throw UpdateFailure(UpdateProblem.NoSpace)
            store.saveTransfer(TransferRecord(candidate, verifiedAt = now()))
            return store.verified
        } catch (e: Exception) {
            store.partial.delete()
            throw e
        }
    }

    companion object {
        const val RETAIN_MS = 7L * 24 * 60 * 60 * 1000
        const val MARGIN = 16L shl 20
        const val DEADLINE_MS = 10L * 60 * 1000

        fun sha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return ReleasePolicy.hex(digest.digest())
        }
    }
}
