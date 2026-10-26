package local.agenteye.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdatePhase
import local.agenteye.selfupdate.UpdateState
import local.agenteye.service.CaptureService
import local.agenteye.ui.update.UpdateAction
import local.agenteye.ui.update.UpdatePresenter

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
    private val updateListener: (UpdateState) -> Unit = { state ->
        screen.updatePanel.render(UpdatePresenter.present(state, System.currentTimeMillis()))
    }
    private var service: CaptureService? = null
    private var bound = false
    private var visible = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as CaptureService.LocalBinder).service
            service?.observer = { refresh() }
            if (visible) {
                service?.attach(
                    screen.preview.surfaceProvider,
                    screen.preview.display?.rotation ?: Surface.ROTATION_0
                )
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            refresh()
        }
    }

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
            onPrivacy = { PrivacyPolicyDialog.show(this) },
            code = { service?.pairingCode() },
            renew = { service?.newPairingCode() }
        )
        screen.updatePanel.onAction = ::onUpdateAction
        setContentView(screen.root)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        ticker.post(tick)
        bound =
            bindService(
                Intent(this, CaptureService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        // Local reconciliation only: launching or foregrounding never contacts the network.
        updates.attach(this, ::stopForInstall)
        updates.coordinator.observe(updateListener)
    }

    override fun onStop() {
        visible = false
        ticker.removeCallbacks(tick)
        updates.coordinator.forget(updateListener)
        updates.detach(isChangingConfigurations)
        service?.observer = null
        service?.attach(null)
        if (bound) unbindService(connection)
        bound = false
        service = null
        super.onStop()
    }

    private fun onUpdateAction(action: UpdateAction) {
        val coordinator = updates.coordinator
        when (action) {
            UpdateAction.Check -> coordinator.check()

            UpdateAction.Update -> coordinator.update()

            UpdateAction.Cancel -> coordinator.cancel()

            UpdateAction.Install -> coordinator.install()

            UpdateAction.GrantPermission -> runCatching {
                startActivity(updates.permissionIntent())
            }

            UpdateAction.Continue -> updates.continueInstall()

            UpdateAction.AbandonRetry -> coordinator.abandonAndRetry()
        }
    }

    /** Verified install handoff: stop the camera session first. False when the service is not bound yet. */
    private fun stopForInstall(): Boolean {
        val capture = service ?: return false
        capture.stopSession()
        refresh()
        return true
    }

    private fun refresh() {
        screen.showStatus(service?.displayStatus() ?: "Stopped · Press Start to begin")
        screen.pairingPanel.tick()
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
