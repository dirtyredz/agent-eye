package local.agenteye.selfupdate.release

import local.agenteye.json.JsonObject
import local.agenteye.json.StrictJson
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
            return try {
                decode(JsonObject.parse(bytes))
            } catch (_: StrictJson.Invalid) {
                malformed()
            }
        }

        private fun decode(root: JsonObject): ReleaseManifest {
            if (root.keys != FIELDS) malformed()
            val artifacts = root.objectMap("artifacts").mapValues { (_, a) ->
                if (a.keys != setOf("size", "sha256")) malformed()
                Artifact(a.long("size"), a.string("sha256"))
            }
            return ReleaseManifest(
                schema = root.long("schema"),
                repository = root.string("repository"),
                applicationId = root.string("applicationId"),
                minSdk = root.long("minSdk"),
                targetSdk = root.long("targetSdk"),
                compileSdk = root.long("compileSdk"),
                versionName = root.string("versionName"),
                versionCode = root.long("versionCode"),
                tag = root.string("tag"),
                commit = root.string("commit"),
                runId = root.long("runId"),
                runAttempt = root.long("runAttempt"),
                workflow = root.string("workflow"),
                artifacts = artifacts,
                signerSha256 = root.string("signerSha256"),
                unsignedHashes = root.stringMap("unsignedHashes")
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

        private fun malformed(): Nothing = throw UpdateFailure(UpdateProblem.Malformed)
    }
}
