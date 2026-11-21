package local.agenteye.camera

import androidx.camera.core.Preview
import androidx.camera.core.ViewPort

/** Where the UI delivers preview geometry; lives in `camera` so `ui/preview` needs no service. */
interface PreviewSink {
    /** A null [surface] detaches the preview; a null [displayRotation] keeps the current one. */
    fun attach(
        surface: Preview.SurfaceProvider?,
        displayRotation: Int? = null,
        port: ViewPort? = null
    )
}
