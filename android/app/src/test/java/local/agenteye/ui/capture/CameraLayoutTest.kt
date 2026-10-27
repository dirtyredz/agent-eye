package local.agenteye.ui.capture

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLayoutTest {
    @Test
    fun portraitUsesCompactPanelAndGivesLargeTextBoundedGrowth() {
        val normal = cameraLayout(393.dp, 800.dp, 1f)
        val large = cameraLayout(393.dp, 800.dp, 1.6f)

        assertFalse(normal.wide)
        assertEquals(216.dp, normal.panelHeight)
        assertTrue(large.panelHeight > normal.panelHeight)
        assertTrue(large.panelHeight <= 800.dp * 0.48f)
    }

    @Test
    fun landscapeUsesNarrowerPanelAtCompactWidth() {
        assertEquals(280.dp, cameraLayout(640.dp, 320.dp, 1f).panelWidth)
        assertEquals(320.dp, cameraLayout(852.dp, 393.dp, 1f).panelWidth)
        assertTrue(cameraLayout(640.dp, 320.dp, 1f).wide)
    }
}
