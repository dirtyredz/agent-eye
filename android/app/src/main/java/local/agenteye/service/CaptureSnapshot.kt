package local.agenteye.service

import local.agenteye.camera.CaptureError
import local.agenteye.camera.Lens

/** Capture facts only. Credentials never enter presentation snapshots or saved state. */
data class CaptureSnapshot(
    val running: Boolean,
    val cameraReady: Boolean,
    val address: String?,
    val error: CaptureError?,
    val availableLenses: List<Lens>,
    val selectedLens: Lens?
)
