package local.agenteye.ui.preview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundedPreviewFrameTest {
    @Test
    fun mapsImplementationMatrixAndIntersectsFrame() {
        val output = FloatArray(4)

        assertTrue(
            mapAndIntersectFitBounds(
                sourceWidth = 100,
                sourceHeight = 50,
                offsetX = -20f,
                offsetY = 10f,
                frameWidth = 160,
                frameHeight = 100,
                matrix = floatArrayOf(2f, 0f, 5f, 0f, 2f, 0f, 0f, 0f, 1f),
                output = output
            )
        )
        assertArrayEquals(floatArrayOf(0f, 10f, 160f, 100f), output, 0f)
    }

    @Test
    fun rejectsMissingAreaAndUnexpectedMatrix() {
        val output = FloatArray(4)

        assertFalse(
            mapAndIntersectFitBounds(100, 50, 200f, 0f, 100, 100, IDENTITY, output)
        )
        assertFalse(
            mapAndIntersectFitBounds(100, 50, 0f, 0f, 100, 100, FloatArray(8), output)
        )
    }

    private companion object {
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
