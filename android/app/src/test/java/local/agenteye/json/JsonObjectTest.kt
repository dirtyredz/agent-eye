package local.agenteye.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonObjectTest {
    private fun parse(text: String) = JsonObject.parse(text.toByteArray())

    @Test fun readsTypedFields() {
        val o = parse(
            """{"n":5,"s":"x","b":true,"o":{"k":"v"},"m":{"a":{"n":1}},"l":[{"n":2}]}"""
        )
        assertEquals(setOf("n", "s", "b", "o", "m", "l"), o.keys)
        assertEquals(5L, o.long("n"))
        assertEquals("x", o.string("s"))
        assertEquals(true, o.bool("b"))
        assertEquals(mapOf("k" to "v"), o.stringMap("o"))
        assertEquals(1L, o.objectMap("m").getValue("a").long("n"))
        assertEquals(2L, o.objects("l").single().long("n"))
    }

    @Test fun wrongTypeMissingKeyAndNonObjectRootAllThrowTheOneError() {
        val o = parse("""{"n":"5","o":{"k":1},"l":[1]}""")
        for (read in listOf<() -> Any?>(
            { o.long("n") },
            { o.string("missing") },
            { o.bool("n") },
            { o.obj("n") },
            { o.stringMap("o") },
            { o.objects("l") },
            { o.list("n") }
        )) {
            assertThrows(StrictJson.Invalid::class.java) { read() }
        }
        assertThrows(StrictJson.Invalid::class.java) { parse("[1]") }
        assertThrows(StrictJson.Invalid::class.java) { parse("{") }
    }
}
