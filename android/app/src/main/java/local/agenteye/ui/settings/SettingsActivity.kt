package local.agenteye.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdateState
import local.agenteye.service.CaptureService
import local.agenteye.ui.PrivacyPolicyDialog
import local.agenteye.ui.update.UpdateAction
import local.agenteye.ui.update.UpdatePresenter

/** Owns updater visibility; opening Settings never starts capture or network work. */
class SettingsActivity : Activity() {
    private lateinit var screen: SettingsScreen
    private val updates get() = (application as AgentEyeApplication).updates
    private var capture: CaptureService? = null
    private var bound = false
    private val listener: (UpdateState) -> Unit = {
        screen.updates.render(UpdatePresenter.present(it, System.currentTimeMillis()))
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            capture = (binder as CaptureService.LocalBinder).service
        }
        override fun onServiceDisconnected(name: ComponentName) {
            capture = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        screen = SettingsScreen(this, { finish() }, { PrivacyPolicyDialog.show(this) })
        screen.updates.onAction = ::act
        setContentView(screen.root)
    }

    override fun onStart() {
        super.onStart()
        bound =
            bindService(
                Intent(this, CaptureService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
    }

    override fun onResume() {
        super.onResume()
        updates.attach(this) {
            val service = capture
            if (service == null) {
                false
            } else {
                service.stopSession()
                true
            }
        }
        updates.coordinator.observe(listener)
    }

    override fun onPause() {
        updates.coordinator.forget(listener)
        updates.detach(isChangingConfigurations)
        super.onPause()
    }

    override fun onStop() {
        if (bound) unbindService(connection)
        bound = false
        capture = null
        super.onStop()
    }

    private fun act(action: UpdateAction) {
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
}
