package local.agenteye.selfupdate.release

import local.agenteye.selfupdate.UpdateFailure
import local.agenteye.selfupdate.UpdateProblem

data class Artifact(val size: Long, val sha256: String)

/** A decoded schema-1 `release.json`, exactly as written by `artifacts.mjs` `finalManifest`. */
data class ReleaseManifest(
    val schema: Long,
    val repository: String,
    val applicationId: String,
    val minSdk: Long,
    val targetSdk: Long,
    val compileSdk: Long,
    val versionName: String,
    val versionCode: Long,
    val tag: String,
    val commit: String,
    val runId: Long,
    val runAttempt: Long,
    val workflow: String,
    val artifacts: Map<String, Artifact>,
    val signerSha256: String,
    val unsignedHashes: Map<String, String>
) {
    companion object {
        private val FIELDS = setOf(
            "schema", "repository", "applicationId", "minSdk", "targetSdk", "compileSdk",
            "versionName", "versionCode", "tag", "commit", "runId", "runAttempt", "workflow",
            "artifacts", "signerSha256", "unsignedHashes"
        )

        /** Bounded strict decoding; any shape problem is [UpdateProblem.Malformed]. */
        fun decode(bytes: ByteArray): ReleaseManifest {
            if (bytes.size > ReleasePolicy.MAX_MANIFEST_BYTES) malformed()
            val root = try {
                StrictJson.parse(bytes) as? Map<*, *>
            } catch (_: StrictJson.Invalid) {
                null
            } ?: malformed()
            if (root.keys != FIELDS) malformed()
            val artifactMap = root["artifacts"] as? Map<*, *> ?: malformed()
            val artifacts = artifactMap.entries.associate { entry ->
                val (k, v) = entry
                val a = v as? Map<*, *> ?: malformed()
                if (a.keys != setOf("size", "sha256")) malformed()
                (k as String) to Artifact(long(a["size"]), string(a["sha256"]))
            }
            val unsigned = (root["unsignedHashes"] as? Map<*, *> ?: malformed())
                .entries.associate { (k, v) -> (k as String) to string(v) }
            return ReleaseManifest(
                schema = long(root["schema"]),
                repository = string(root["repository"]),
                applicationId = string(root["applicationId"]),
                minSdk = long(root["minSdk"]),
                targetSdk = long(root["targetSdk"]),
                compileSdk = long(root["compileSdk"]),
                versionName = string(root["versionName"]),
                versionCode = long(root["versionCode"]),
                tag = string(root["tag"]),
                commit = string(root["commit"]),
                runId = long(root["runId"]),
                runAttempt = long(root["runAttempt"]),
                workflow = string(root["workflow"]),
                artifacts = artifacts,
                signerSha256 = string(root["signerSha256"]),
                unsignedHashes = unsigned
            )
        }

        /**
         * Parses the publisher's `SHA256SUMS`: one `<64 hex>  <name>\n` line per file, unique
         * exact names, nothing else.
         */
        fun checksums(bytes: ByteArray): Map<String, String> {
            if (bytes.size > ReleasePolicy.MAX_SUMS_BYTES || bytes.isEmpty()) malformed()
            val text = bytes.toString(Charsets.US_ASCII)
            if (!text.endsWith("\n") || text.contains('\r')) malformed()
            val result = LinkedHashMap<String, String>()
            for (line in text.dropLast(1).split('\n')) {
                val match = LINE.matchEntire(line) ?: malformed()
                val (hash, name) = match.destructured
                if (result.put(name, hash) != null) malformed()
            }
            return result
        }

        private val LINE = Regex("([a-f0-9]{64})  ([A-Za-z0-9._-]{1,64})")

        private fun long(value: Any?): Long = value as? Long ?: malformed()

        private fun string(value: Any?): String = value as? String ?: malformed()

        private fun malformed(): Nothing = throw UpdateFailure(UpdateProblem.Malformed)
    }
}
