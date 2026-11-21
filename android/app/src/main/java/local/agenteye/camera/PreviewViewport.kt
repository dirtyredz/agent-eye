package local.agenteye.camera

import androidx.camera.core.Preview
import androidx.camera.core.ViewPort

/**
 * The one retained preview window, shared by preview and still so both frame the same field of
 * view. It outlives individual [CameraSession]s, so geometry delivered before Start and across
 * Stop/Start is kept. It never holds the surface itself.
 */
class PreviewViewport {
    var current: ViewPort? = null
        private set

    /**
     * Retains [port] only when it arrives with a live [surface]; a null surface (screen off) or a
     * null port keeps the last window. Returns true when the retained geometry changed.
     */
    fun update(surface: Preview.SurfaceProvider?, port: ViewPort?): Boolean {
        if (surface == null || port == null) return false
        val changed = !sameWindow(port, current)
        current = port
        return changed
    }

    private fun sameWindow(a: ViewPort, b: ViewPort?) = a === b || (
        b != null && a.aspectRatio == b.aspectRatio && a.rotation == b.rotation &&
            a.scaleType == b.scaleType && a.layoutDirection == b.layoutDirection
        )
}
