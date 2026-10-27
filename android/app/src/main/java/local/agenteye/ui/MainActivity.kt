package local.agenteye.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdateState
import local.agenteye.service.CaptureService
import local.agenteye.ui.capture.CameraScreen
import local.agenteye.ui.capture.CaptureScreenModel
import local.agenteye.ui.capture.SessionStarter
import local.agenteye.ui.pairing.PairingClipboard
import local.agenteye.ui.preview.CameraPreview
import local.agenteye.ui.preview.PreviewFeed
import local.agenteye.ui.settings.SettingsActivity
import local.agenteye.ui.theme.AgentEyeTheme

/** Lifecycle, the service link and preview wiring; screen state and Start live in ui/capture. */
class MainActivity : ComponentActivity() {
    private val updates get() = (application as AgentEyeApplication).updates
    private val link: CaptureServiceLink = CaptureServiceLink(
        this,
        onConnected = { capture ->
            capture.observer = {
                model.notify(null)
            }
            feed.requestRefresh()
            model.refresh()
        },
        onDisconnected = { model.refresh() }
    )
    private val service: CaptureService? get() = link.service
    private val model = CaptureScreenModel({ service }, { updates.coordinator.state.phase })
    private val starter = SessionStarter(this, model)
    private val feed by lazy { PreviewFeed(this) { service } }
    private val clipboard by lazy {
        PairingClipboard(this) { if (model.visible) service?.pairingCode() else null }
    }
    private val updateListener: (UpdateState) -> Unit = { model.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        styleBars()
        setContent {
            AgentEyeTheme {
                CameraScreen(
                    model.camera,
                    model.pairing,
                    onToggle = {
                        if (model.camera.running) service?.stopSession() else starter.request()
                    },
                    onSwitch = { service?.switchCamera() },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onCopy = {
                        clipboard.copy()
                        model.refresh()
                    },
                    onRenew = {
                        service?.newPairingCode()
                        model.refresh()
                    }
                ) { modifier -> CameraPreview(feed, modifier) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.start()
        feed.start()
        link.bind()
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
        service?.observer = null
        feed.stop()
        link.unbind()
        model.stop()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        styleBars()
        feed.requestRefresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        starter.onPermissionResult(requestCode)
    }

    private fun styleBars() {
        // The camera canvas/bars stay dark even when the control cards use the light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
    }
}
