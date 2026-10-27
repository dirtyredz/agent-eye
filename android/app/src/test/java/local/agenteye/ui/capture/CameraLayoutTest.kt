package local.agenteye.ui.capture

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLayoutTest {
    @Test
    fun portraitLeavesPanelHeightToMeasuredContent() {
        val normal = cameraLayout(393.dp, 800.dp, 1f)
        val large = cameraLayout(393.dp, 800.dp, 1.6f)

        assertFalse(normal.wide)
        assertEquals(280.dp, normal.panelWidth)
        assertEquals(normal.panelWidth, large.panelWidth)
        assertTrue(large.topHeight > normal.topHeight)

        val shortPanel = cameraBodyGeometry(393, 700, 393, 180, 12, wide = false)
        val wrappedPanel = cameraBodyGeometry(393, 700, 393, 240, 12, wide = false)
        assertEquals(508, shortPanel.previewHeight)
        assertEquals(448, wrappedPanel.previewHeight)
        assertEquals(460, wrappedPanel.panelY)
    }

    @Test
    fun landscapeUsesNarrowerPanelAtCompactWidth() {
        assertEquals(280.dp, cameraLayout(640.dp, 320.dp, 1f).panelWidth)
        assertEquals(320.dp, cameraLayout(852.dp, 393.dp, 1f).panelWidth)
        assertTrue(cameraLayout(640.dp, 320.dp, 1f).wide)
    }
}
