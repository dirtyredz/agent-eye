package local.agenteye.selfupdate.storage

import java.io.File
import java.io.FileOutputStream
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.release.StrictJson

/** The candidate being transferred, and whether its complete bytes were verified. */
data class TransferRecord(val candidate: Candidate, val verifiedAt: Long? = null)

enum class InstallStage { Staging, Committed }

/** Installer session identity, persisted before each irreversible transition. */
data class InstallRecord(
    val candidate: Candidate,
    val sessionId: Int,
    val nonce: String,
    val stage: InstallStage,
    val pendingUserAction: Boolean = false
)

/**
 * Updater-owned private files under `noBackupFilesDir/self-update/`: one partial, one verified
 * APK and atomic, credential-free JSON records (no URLs, no query strings). Corrupt or
 * foreign-schema records are discarded; cleanup never leaves this directory.
 */
class UpdateStore(
    val directory: File,
    /** Atomic same-directory replace (POSIX rename on Android); Windows-hosted tests inject one. */
    private val rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    private val freeSpace: (File) -> Long = { it.usableSpace }
) {
    val partial = File(directory, "update.apk.part")
    val verified = File(directory, "update.apk")
    private val transfer = File(directory, "transfer.json")
    private val install = File(directory, "install.json")
    private val backoff = File(directory, "backoff.json")
    private val known get() = setOf(partial, verified, transfer, install, backoff).map {
        it.name
    }.toSet()

    init {
        directory.mkdirs()
    }

    fun usableSpace(): Long = freeSpace(directory)

    /** Marks the completed partial as the verified APK. */
    @Synchronized fun promote(): Boolean = rename(partial, verified)

    @Synchronized fun readTransfer(): TransferRecord? = read(transfer) { map ->
        TransferRecord(candidate(map["candidate"] as Map<*, *>), map["verifiedAt"] as Long?)
    }

    @Synchronized fun saveTransfer(value: TransferRecord) = write(
        transfer,
        mapOf("candidate" to encode(value.candidate), "verifiedAt" to value.verifiedAt)
    )

    @Synchronized fun readInstall(): InstallRecord? = read(install) { map ->
        InstallRecord(
            candidate = candidate(map["candidate"] as Map<*, *>),
            sessionId = (map["sessionId"] as Long).toInt(),
            nonce = (map["nonce"] as String).also { require(NONCE.matches(it)) },
            stage = InstallStage.valueOf(map["stage"] as String),
            pendingUserAction = map["pendingUserAction"] as Boolean
        )
    }

    @Synchronized fun saveInstall(value: InstallRecord) = write(
        install,
        mapOf(
            "candidate" to encode(value.candidate),
            "sessionId" to value.sessionId.toLong(),
            "nonce" to value.nonce,
            "stage" to value.stage.name,
            "pendingUserAction" to value.pendingUserAction
        )
    )

    @Synchronized fun clearInstall() {
        install.delete()
    }

    var backoffUntil: Long
        @Synchronized get() = read(backoff) { it["until"] as Long } ?: 0

        @Synchronized set(value) = write(backoff, mapOf("until" to value))

    /** Removes transfer files and record (not the install record or backoff). */
    @Synchronized fun clearTransfer() {
        partial.delete()
        verified.delete()
        transfer.delete()
    }

    /** Deletes any file in the updater directory this store does not own (temp leftovers etc.). */
    @Synchronized fun removeOrphans() {
        directory.listFiles()?.filter { it.isFile && it.name !in known }?.forEach { it.delete() }
        if (readTransfer() == null) {
            partial.delete()
            verified.delete()
        }
    }

    private fun <T> read(file: File, decode: (Map<*, *>) -> T): T? {
        if (!file.isFile) return null
        val value = try {
            if (file.length() > MAX_RECORD) throw IllegalStateException()
            val map = StrictJson.parse(file.readBytes()) as Map<*, *>
            require(map["schema"] == SCHEMA)
            decode(map)
        } catch (_: Exception) {
            null
        }
        if (value == null) file.delete()
        return value
    }

    private fun write(file: File, fields: Map<String, Any?>) {
        val temporary = File(directory, "${file.name}.tmp")
        FileOutputStream(temporary).use {
            it.write(StrictJson.write(mapOf("schema" to SCHEMA) + fields).toByteArray())
            it.fd.sync()
        }
        if (!rename(temporary, file)) {
            temporary.delete()
            throw java.io.IOException("record write failed")
        }
    }

    companion object {
        const val SCHEMA = 1L
        private const val MAX_RECORD = 16 * 1024L
        val NONCE = Regex("[a-f0-9]{32}")

        fun encode(c: Candidate): Map<String, Any?> = mapOf(
            "releaseId" to c.releaseId, "tag" to c.tag, "versionName" to c.versionName,
            "versionCode" to c.versionCode, "minSdk" to c.minSdk.toLong(),
            "manifestAssetId" to c.manifestAssetId, "sumsAssetId" to c.sumsAssetId,
            "apkAssetId" to c.apkAssetId, "manifestSha256" to c.manifestSha256,
            "apkSize" to c.apkSize, "apkSha256" to c.apkSha256, "signerSha256" to c.signerSha256
        )

        fun candidate(m: Map<*, *>) = Candidate(
            releaseId = m["releaseId"] as Long,
            tag = m["tag"] as String,
            versionName = m["versionName"] as String,
            versionCode = m["versionCode"] as Long,
            minSdk = (m["minSdk"] as Long).toInt(),
            manifestAssetId = m["manifestAssetId"] as Long,
            sumsAssetId = m["sumsAssetId"] as Long,
            apkAssetId = m["apkAssetId"] as Long,
            manifestSha256 = m["manifestSha256"] as String,
            apkSize = m["apkSize"] as Long,
            apkSha256 = m["apkSha256"] as String,
            signerSha256 = m["signerSha256"] as String
        ).also {
            require(
                Regex("[a-f0-9]{64}").matches(it.apkSha256) && it.apkSize > 0 && it.releaseId > 0
            )
        }
    }
}
