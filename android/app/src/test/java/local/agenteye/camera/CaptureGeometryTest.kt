package local.agenteye.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureGeometryTest {
    @Test fun fullFrameNeedsNoCrop() {
        assertNull(CaptureGeometry.cropRegion(0, 0, 4000, 3000, 4000, 3000, 2000, 1500))
    }

    @Test fun degenerateOrOutOfRangeRectsAreIgnored() {
        assertNull(CaptureGeometry.cropRegion(10, 10, 10, 50, 100, 100, 100, 100))
        assertNull(CaptureGeometry.cropRegion(0, 0, 0, 0, 100, 100, 100, 100))
        assertNull(CaptureGeometry.cropRegion(0, 0, 100, 100, 0, 100, 100, 100))
    }

    @Test fun cropIsScaledByTheDecodeDownSample() {
        // 4:3 sensor frame cropped to the 16:9 band the preview shows, decoded at half size.
        val region = CaptureGeometry.cropRegion(0, 375, 4000, 2625, 4000, 3000, 2000, 1500)
        assertEquals(CaptureGeometry.Region(0, 188, 2000, 1125), region)
    }

    @Test fun cropIsClampedInsideTheBitmap() {
        val region = CaptureGeometry.cropRegion(-50, -50, 5000, 80, 100, 100, 100, 100)
        assertEquals(CaptureGeometry.Region(0, 0, 100, 80), region)
    }
}
