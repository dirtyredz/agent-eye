package local.agenteye.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.service.CaptureService
import local.agenteye.ui.settings.SettingsActivity

class MainActivity : Activity() {
    private lateinit var screen: CameraScreen
    private val updates get() = (application as AgentEyeApplication).updates
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            screen.pairingPanel.tick()
            if (visible) ticker.postDelayed(this, 1000)
        }
    }
    private val link = CaptureServiceLink(
        this,
        onConnected = { capture ->
            capture.observer = { refresh() }
            if (visible) pushSurface()
            refresh()
        },
        onDisconnected = { refresh() }
    )
    private val service get() = link.service
    private var visible = false
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) = pushSurface()
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }
    private val layoutListener =
        android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pushSurface() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The pairing code and camera preview should not enter screenshots or the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        screen = CameraScreen(
            this,
            onStart = ::requestStart,
            onStop = {
                service?.stopSession()
                refresh()
            },
            onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onSwitch = { service?.switchCamera() },
            code = { service?.pairingCode() },
            renew = { service?.newPairingCode() }
        )
        setContentView(screen.root)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        ticker.post(tick)
        displays.registerDisplayListener(displayListener, ticker)
        screen.preview.addOnLayoutChangeListener(layoutListener)
        link.bind()
    }

    override fun onResume() {
        super.onResume()
        // Local reconciliation also runs on the camera screen; only Settings offers network actions.
        link.attachUpdates(updates)
    }

    override fun onPause() {
        updates.detach(isChangingConfigurations)
        super.onPause()
    }

    override fun onStop() {
        visible = false
        ticker.removeCallbacks(tick)
        displays.unregisterDisplayListener(displayListener)
        screen.preview.removeOnLayoutChangeListener(layoutListener)
        service?.observer = null
        service?.attach(null)
        link.unbind()
        super.onStop()
    }

    /** The display's current rotation; the Activity (not a possibly detached view) is the truth. */
    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = if (Build.VERSION.SDK_INT >= 30) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        windowManager.defaultDisplay.rotation
    }

    /**
     * Hands the service the live surface, the display rotation and the preview's visible window.
     * Rotation and window changes land here without recreating the Activity, so the camera is
     * never torn down by turning the phone and stills keep the preview's orientation and framing.
     */
    private fun pushSurface() {
        if (!visible) return
        val rotation = displayRotation()
        service?.attach(
            screen.preview.surfaceProvider,
            rotation,
            screen.preview.getViewPort(rotation)
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screen.applyOrientation(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        pushSurface()
    }

    private fun refresh() {
        screen.showStatus(service?.displayStatus() ?: "Stopped · Press Start to begin")
        screen.pairingPanel.tick()
        screen.showLens(service?.selectedLens, service?.availableLenses.orEmpty().size > 1)
    }

    private fun requestStart() {
        val phase = updates.coordinator.state.phase
        if (phase == UpdatePhase.Installing || phase == UpdatePhase.AwaitingConfirmation) {
            screen.showStatus(
                "An update is being installed. Start is available again when it finishes or is cancelled."
            )
            return
        }
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(
                permissions.toTypedArray(),
                1
            )
        } else {
            startSession()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && visible) startSession()
    }

    private fun startSession() {
        if (!visible) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            screen.showStatus(
                "Camera permission is required. Grant it in app settings or press Start to retry."
            )
            return
        }
        try {
            val intent = Intent(this, CaptureService::class.java).setAction("START")
            if (Build.VERSION.SDK_INT >=
                26
            ) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            screen.showStatus("Cannot start camera session. Reopen the app and try again.")
        }
    }
}
