package local.agenteye.selfupdate.storage

import java.io.File
import local.agenteye.selfupdate.contract.Backoff

/**
 * Persists the manual-check backoff deadline in `backoff.json` beside [UpdateStore]'s records
 * (the store treats that file as known, never an orphan).
 */
class BackoffStore(
    directory: File,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
) : Backoff {
    private val records = RecordFiles(directory, rename)
    private val file = File(directory, FILE_NAME)

    init {
        directory.mkdirs()
    }

    override var until: Long
        @Synchronized get() = records.read(file) { it.long("until") } ?: 0

        @Synchronized set(value) = records.write(file, mapOf("until" to value))

    companion object {
        const val FILE_NAME = "backoff.json"
    }
}
