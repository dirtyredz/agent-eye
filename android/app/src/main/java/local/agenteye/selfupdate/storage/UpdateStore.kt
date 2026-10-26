package local.agenteye.selfupdate.storage

import java.io.File
import java.io.FileOutputStream
import local.agenteye.json.JsonObject
import local.agenteye.json.StrictJson
import local.agenteye.selfupdate.Candidate
import local.agenteye.selfupdate.transport.Backoff

/** The candidate being transferred, and whether its complete bytes were verified. */
data class TransferRecord(val candidate: Candidate, val verifiedAt: Long? = null)

/** Committing = intent persisted, commit() not yet known to have returned. */
enum class InstallStage { Staging, Committing, Committed }

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
) : Backoff {
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

    @Synchronized fun readTransfer(): TransferRecord? = read(transfer, RecordCodec::transfer)

    @Synchronized fun saveTransfer(value: TransferRecord) = write(
        transfer,
        RecordCodec.encode(value)
    )

    @Synchronized fun readInstall(): InstallRecord? = read(install, RecordCodec::install)

    @Synchronized fun saveInstall(value: InstallRecord) = write(install, RecordCodec.encode(value))

    @Synchronized fun clearInstall() {
        install.delete()
    }

    override var until: Long
        @Synchronized get() = read(backoff) { it.long("until") } ?: 0

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

    private fun <T> read(file: File, decode: (JsonObject) -> T): T? {
        if (!file.isFile) return null
        val value = try {
            if (file.length() > MAX_RECORD) throw StrictJson.Invalid("record too large")
            val record = JsonObject.parse(file.readBytes())
            require(record.long("schema") == SCHEMA)
            decode(record)
        } catch (_: StrictJson.Invalid) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
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
    }
}
