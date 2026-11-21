package local.agenteye.selfupdate.storage

import java.io.File
import local.agenteye.selfupdate.Candidate

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
) {
    val partial = File(directory, "update.apk.part")
    val verified = File(directory, "update.apk")
    private val transfer = File(directory, "transfer.json")
    private val install = File(directory, "install.json")
    private val records = RecordFiles(directory, rename)
    private val known get() = setOf(
        partial.name,
        verified.name,
        transfer.name,
        install.name,
        BackoffStore.FILE_NAME
    )

    init {
        directory.mkdirs()
    }

    fun usableSpace(): Long = freeSpace(directory)

    /** Marks the completed partial as the verified APK. */
    @Synchronized fun promote(): Boolean = rename(partial, verified)

    @Synchronized fun readTransfer(): TransferRecord? = records.read(
        transfer,
        RecordCodec::transfer
    )

    @Synchronized fun saveTransfer(value: TransferRecord) = records.write(
        transfer,
        RecordCodec.encode(value)
    )

    @Synchronized fun readInstall(): InstallRecord? = records.read(install, RecordCodec::install)

    @Synchronized fun saveInstall(value: InstallRecord) = records.write(
        install,
        RecordCodec.encode(value)
    )

    @Synchronized fun clearInstall() {
        install.delete()
    }

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
}
