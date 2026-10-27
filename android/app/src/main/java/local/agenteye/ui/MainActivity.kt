package local.agenteye.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import local.agenteye.AgentEyeApplication
import local.agenteye.R
import local.agenteye.selfupdate.UpdateState
import local.agenteye.service.CaptureService
import local.agenteye.ui.capture.CameraScreen
import local.agenteye.ui.capture.CameraState
import local.agenteye.ui.capture.CameraStateHolder
import local.agenteye.ui.pairing.PairingClipboard
import local.agenteye.ui.pairing.PairingState
import local.agenteye.ui.pairing.PairingStateHolder
import local.agenteye.ui.preview.CameraPreview
import local.agenteye.ui.preview.PreviewFeed
import local.agenteye.ui.settings.SettingsActivity
import local.agenteye.ui.theme.AgentEyeTheme

class MainActivity : ComponentActivity() {
    private val updates get() = (application as AgentEyeApplication).updates
    private var cameraState by mutableStateOf(CameraState())
    private var pairingState by mutableStateOf(PairingState())
    private var message: String? = null
    private var visible = false
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshPairing()
            if (visible) ticker.postDelayed(this, 1000)
        }
    }
    private val link: CaptureServiceLink = CaptureServiceLink(
        this,
        onConnected = { capture ->
            capture.observer = {
                message = null
                refresh()
            }
            feed.requestRefresh()
            refresh()
        },
        onDisconnected = { refresh() }
    )
    private val service: CaptureService? get() = link.service
    private val feed by lazy { PreviewFeed(this) { service } }
    private val clipboard by lazy {
        PairingClipboard(this) { if (visible) service?.pairingCode() else null }
    }
    private val updateListener: (UpdateState) -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        styleBars()
        setContent {
            AgentEyeTheme {
                CameraScreen(
                    cameraState,
                    pairingState,
                    onToggle = {
                        if (service?.snapshot()?.running ==
                            true
                        ) {
                            service?.stopSession()
                        } else {
                            requestStart()
                        }
                    },
                    onSwitch = { service?.switchCamera() },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onCopy = {
                        clipboard.copy()
                        refreshPairing()
                    },
                    onRenew = {
                        service?.newPairingCode()
                        refreshPairing()
                    }
                ) { modifier -> CameraPreview(feed, modifier) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        feed.start()
        link.bind()
        refresh()
        ticker.post(tick)
    }

    override fun onResume() {
        super.onResume()
        link.attachUpdates(updates)
        updates.coordinator.observe(updateListener)
    }

    override fun onPause() {
        updates.coordinator.forget(updateListener)
        updates.detach(isChangingConfigurations)
        super.onPause()
    }

    override fun onStop() {
        visible = false
        ticker.removeCallbacks(tick)
        pairingState = PairingState()
        service?.observer = null
        feed.stop()
        link.unbind()
        refresh()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        styleBars()
        feed.requestRefresh()
    }

    private fun styleBars() {
        // The camera canvas/bars stay dark even when the control cards use the light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
    }

    private fun refreshPairing() {
        pairingState = PairingStateHolder.present(
            if (visible) service?.pairingCode() else null,
            System.currentTimeMillis(),
            visible && service?.snapshot()?.running == true
        )
    }

    private fun refresh() {
        val state = CameraStateHolder.present(
            service?.snapshot(),
            visible,
            updates.coordinator.state.phase
        )
        cameraState = message?.let { state.copy(status = it) } ?: state
        refreshPairing()
    }

    private fun requestStart() {
        message = null
        if (!CameraStateHolder.present(
                service?.snapshot(),
                visible,
                updates.coordinator.state.phase
            ).canRequestStart
        ) {
            refresh()
            return
        }
        val permissions = mutableListOf<String>()
        if (!cameraGranted()) permissions.add(Manifest.permission.CAMERA)
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
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) startSession()
    }

    private fun cameraGranted() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startSession() {
        // Permission UI can return after backgrounding, disconnection or an intervening install.
        if (!CameraStateHolder.canStart(
                service?.snapshot(),
                visible,
                cameraGranted(),
                updates.coordinator.state.phase
            )
        ) {
            if (visible &&
                !cameraGranted()
            ) {
                message = getString(R.string.camera_permission_required)
            }
            refresh()
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
            message = getString(R.string.session_start_failed)
            refresh()
        }
    }
}
