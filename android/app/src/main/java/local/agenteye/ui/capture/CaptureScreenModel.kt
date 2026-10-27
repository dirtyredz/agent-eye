package local.agenteye.ui.capture

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureService
import local.agenteye.ui.pairing.PairingState

/**
 * The single owner of what the capture screen shows: it assembles [CameraState] and
 * [PairingState] in one place ([refresh]) and runs the one-second pairing countdown while
 * [visible]. Everything else (the Activity, [SessionStarter]) asks it, never re-derives.
 */
class CaptureScreenModel(
    private val service: () -> CaptureService?,
    private val phase: () -> UpdatePhase,
    private val now: () -> Long = System::currentTimeMillis
) {
    var camera by mutableStateOf(CameraState())
        private set
    var pairing by mutableStateOf(PairingState())
        private set
    var visible = false
        private set
    private var notice: CameraStatus? = null
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (visible) ticker.postDelayed(this, 1000)
        }
    }

    fun start() {
        visible = true
        refresh()
        ticker.post(tick)
    }

    fun stop() {
        visible = false
        ticker.removeCallbacks(tick)
        refresh()
    }

    /** Show [status] until the service reports a change (or [notify] is called with null). */
    fun notify(status: CameraStatus?) {
        notice = status
        refresh()
    }

    /** Rebuild both display states from the live service; returns the camera state. */
    fun refresh(): CameraState {
        val capture = service()
        camera = CameraStateHolder.present(capture?.snapshot(), visible, phase(), notice)
        pairing = PairingState.from(
            if (visible) capture?.pairingCode() else null,
            now(),
            visible && camera.running
        )
        return camera
    }
}
