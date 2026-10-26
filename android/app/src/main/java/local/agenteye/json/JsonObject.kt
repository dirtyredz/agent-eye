package local.agenteye.json

/**
 * A typed view of a parsed JSON object. Every accessor throws [StrictJson.Invalid] on a
 * missing key or wrong type, so a caller decodes a whole record in straight-line code and maps
 * the one failure to its own error. Integers are [Long]; there is no implicit coercion.
 */
class JsonObject private constructor(private val map: Map<*, *>) {
    val keys: Set<String> get() = map.keys.map { it as? String ?: bad("key") }.toSet()

    fun has(key: String) = map.containsKey(key)

    fun raw(key: String): Any? = map[key]

    fun long(key: String): Long = map[key] as? Long ?: bad(key)

    fun string(key: String): String = map[key] as? String ?: bad(key)

    fun bool(key: String): Boolean = map[key] as? Boolean ?: bad(key)

    fun obj(key: String): JsonObject = of(map[key], key)

    fun objects(key: String): List<JsonObject> = list(key).map { of(it, key) }

    fun list(key: String): List<Any?> = map[key] as? List<*> ?: bad(key)

    /** An object whose values are all objects, keyed by name. */
    fun objectMap(key: String): Map<String, JsonObject> =
        obj(key).let { o -> o.keys.associateWith { o.obj(it) } }

    /** An object whose values are all strings, keyed by name. */
    fun stringMap(key: String): Map<String, String> =
        obj(key).let { o -> o.keys.associateWith { o.string(it) } }

    companion object {
        /** Strictly parses [bytes] whose top level must be an object. */
        fun parse(bytes: ByteArray): JsonObject = of(StrictJson.parse(bytes), "root")

        private fun of(value: Any?, name: String): JsonObject =
            JsonObject(value as? Map<*, *> ?: bad(name))

        private fun bad(name: String): Nothing = throw StrictJson.Invalid("bad field: $name")
    }
}
