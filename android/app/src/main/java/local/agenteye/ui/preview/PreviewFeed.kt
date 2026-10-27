package local.agenteye.ui.preview

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import androidx.camera.view.PreviewView
import local.agenteye.service.CaptureService

/** Started-Activity-only geometry delivery, independent of composition and service arrival order. */
class PreviewFeed(private val activity: Activity, private val service: () -> CaptureService?) {
    private val handler = Handler(Looper.getMainLooper())
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private var preview: PreviewView? = null
    private var started = false
    private val refresh = Runnable { publish() }
    private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestRefresh()
    }
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) = requestRefresh()
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    fun created(view: PreviewView) {
        preview = view
        if (started) view.addOnLayoutChangeListener(layout)
        requestRefresh()
    }

    fun released(view: PreviewView) {
        view.removeOnLayoutChangeListener(layout)
        if (preview === view) {
            handler.removeCallbacks(refresh)
            service()?.attach(null)
            preview = null
        }
    }

    fun start() {
        if (started) return
        started = true
        displays.registerDisplayListener(listener, handler)
        preview?.addOnLayoutChangeListener(layout)
        requestRefresh()
    }

    fun stop() {
        started = false
        displays.unregisterDisplayListener(listener)
        preview?.removeOnLayoutChangeListener(layout)
        handler.removeCallbacks(refresh)
        service()?.attach(null)
    }

    fun requestRefresh() {
        handler.removeCallbacks(refresh)
        if (started) handler.post(refresh)
    }

    @Suppress("DEPRECATION")
    private fun rotation() = if (Build.VERSION.SDK_INT >= 30) {
        activity.display?.rotation ?: Surface.ROTATION_0
    } else {
        activity.windowManager.defaultDisplay.rotation
    }

    private fun publish() {
        val view = preview ?: return
        if (!started || view.width == 0 || view.height == 0) return
        val rotation = rotation()
        // FIT yields ViewPort.FIT: CameraX keeps the shared camera frame, not the screen ratio.
        val port = view.getViewPort(rotation) ?: return
        service()?.attach(view.surfaceProvider, rotation, port)
    }
}
