package local.agenteye.selfupdate.storage

import java.io.File
import java.io.FileOutputStream
import local.agenteye.json.JsonObject
import local.agenteye.json.StrictJson

/**
 * Atomic, schema-tagged JSON record files inside one updater directory. Corrupt, oversized or
 * foreign-schema records read as absent and are deleted.
 */
internal class RecordFiles(
    private val directory: File,
    /** Atomic same-directory replace (POSIX rename on Android); Windows-hosted tests inject one. */
    private val rename: (File, File) -> Boolean
) {
    fun <T> read(file: File, decode: (JsonObject) -> T): T? {
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

    fun write(file: File, fields: Map<String, Any?>) {
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
