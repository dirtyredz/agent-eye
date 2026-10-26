package local.agenteye.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensSelectionTest {
    @Test fun cyclesAvailableLensesAndRestoresAfterRestart() {
        var saved: String? = null
        val selection = LensSelection(saved) { saved = it }
        selection.discover(listOf(Lens.FRONT, Lens.BACK, Lens.EXTERNAL, Lens.FRONT))
        assertEquals(Lens.BACK, selection.selected)
        assertEquals(Lens.FRONT, selection.cycle())
        val restarted = LensSelection(saved) { saved = it }
        restarted.discover(listOf(Lens.BACK, Lens.FRONT))
        assertEquals(Lens.FRONT, restarted.selected)
        assertEquals(Lens.EXTERNAL, selection.cycle())
        assertEquals(Lens.BACK, selection.cycle())
    }

    @Test fun unavailableOrInvalidSavedLensFallsBackToBack() {
        for (saved in listOf("EXTERNAL", "invalid", null)) {
            var persisted: String? = null
            val selection = LensSelection(saved) { persisted = it }
            selection.discover(listOf(Lens.FRONT, Lens.BACK))
            assertEquals(Lens.BACK, selection.selected)
            assertEquals("BACK", persisted)
        }
    }

    @Test fun singleFrontAndNoCameraAreSafe() {
        val selection = LensSelection("BACK") {}
        selection.discover(emptyList())
        assertNull(selection.cycle())
        selection.discover(listOf(Lens.FRONT))
        assertEquals(Lens.FRONT, selection.cycle())
    }
}
