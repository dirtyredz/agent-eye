package local.agenteye.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider

/** Android adapter for the persisted, platform-independent lens vocabulary. */
@androidx.annotation.OptIn(markerClass = [ExperimentalLensFacing::class])
fun Lens.cameraSelector(): CameraSelector = CameraSelector.Builder().requireLensFacing(
    when (this) {
        Lens.BACK -> CameraSelector.LENS_FACING_BACK
        Lens.FRONT -> CameraSelector.LENS_FACING_FRONT
        Lens.EXTERNAL -> CameraSelector.LENS_FACING_EXTERNAL
    }
).build()

/** The logical lens directions this device's CameraX provider can bind. */
fun ProcessCameraProvider.availableLenses(): List<Lens> =
    Lens.entries.filter { hasCamera(it.cameraSelector()) }
