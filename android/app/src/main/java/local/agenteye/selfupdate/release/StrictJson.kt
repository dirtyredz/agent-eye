package local.agenteye.selfupdate.release

/**
 * Small strict JSON reader/writer for update metadata and updater records. Pure JVM, so the
 * same code runs on the device and in local tests (Android's org.json silently keeps the last
 * duplicate key; this rejects duplicates, trailing data, invalid UTF-8 and excessive nesting).
 * Integers decode as [Long]; fractions and exponents decode as [Double].
 */
object StrictJson {
    class Invalid(message: String) : Exception(message)

    fun parse(bytes: ByteArray): Any? {
        val text = try {
            java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            throw Invalid("invalid UTF-8")
        }
        return parse(text)
    }

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.space()
        if (reader.index != text.length) throw Invalid("trailing data")
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")

            is Boolean, is Int, is Long -> out.append(value.toString())

            is String -> quote(out, value)

            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    quote(out, k as String)
                    out.append(':')
                    append(out, v)
                }
                out.append('}')
            }

            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    append(out, v)
                }
                out.append(']')
            }

            else -> throw IllegalArgumentException("unsupported JSON value")
        }
    }

    private fun quote(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private class Reader(val text: String) {
        var index = 0

        fun space() {
            while (index < text.length && text[index] in " \t\r\n") index++
        }

        fun value(depth: Int): Any? {
            if (depth > 32) throw Invalid("nesting too deep")
            space()
            if (index >= text.length) throw Invalid("unexpected end")
            return when (text[index]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, index)) throw Invalid("invalid literal")
            index += word.length
            return value
        }

        private fun obj(depth: Int): Map<String, Any?> {
            index++
            val result = LinkedHashMap<String, Any?>()
            space()
            if (peek() == '}') return result.also { index++ }
            while (true) {
                space()
                if (peek() != '"') throw Invalid("expected key")
                val key = string()
                if (key in result) throw Invalid("duplicate key")
                space()
                expect(':')
                result[key] = value(depth + 1)
                space()
                when (next()) {
                    ',' -> continue
                    '}' -> return result
                    else -> throw Invalid("expected , or }")
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            index++
            val result = ArrayList<Any?>()
            space()
            if (peek() == ']') return result.also { index++ }
            while (true) {
                result.add(value(depth + 1))
                space()
                when (next()) {
                    ',' -> continue
                    ']' -> return result
                    else -> throw Invalid("expected , or ]")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = next()
                when {
                    c == '"' -> return out.toString()

                    c == '\\' -> when (val e = next()) {
                        '"', '\\', '/' -> out.append(e)

                        'b' -> out.append('\b')

                        'f' -> out.append('\u000c')

                        'n' -> out.append('\n')

                        'r' -> out.append('\r')

                        't' -> out.append('\t')

                        'u' -> {
                            if (index + 4 > text.length) throw Invalid("bad escape")
                            val hex = text.substring(index, index + 4)
                            out.append(hex.toIntOrNull(16)?.toChar() ?: throw Invalid("bad escape"))
                            index += 4
                        }

                        else -> throw Invalid("bad escape")
                    }

                    c < ' ' -> throw Invalid("control character in string")

                    else -> out.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = index
            if (peek() == '-') index++
            while (index < text.length && text[index] in "0123456789.eE+-") index++
            val raw = text.substring(start, index)
            if (!NUMBER.matches(raw)) throw Invalid("invalid number")
            return if (raw.any { it in ".eE" }) {
                raw.toDouble()
            } else {
                raw.toLongOrNull() ?: throw Invalid("integer overflow")
            }
        }

        private fun peek(): Char = if (index <
            text.length
        ) {
            text[index]
        } else {
            throw Invalid("unexpected end")
        }

        private fun next(): Char = peek().also { index++ }

        private fun expect(c: Char) {
            if (next() != c) throw Invalid("expected $c")
        }
    }

    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
}
