package local.agenteye.ui.settings

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdateState
import local.agenteye.ui.CaptureServiceLink
import local.agenteye.ui.PrivacyPolicyDialog
import local.agenteye.ui.update.UpdateAction
import local.agenteye.ui.update.UpdatePresenter

/** Owns updater visibility; opening Settings never starts capture or network work. */
class SettingsActivity : Activity() {
    private lateinit var screen: SettingsScreen
    private val updates get() = (application as AgentEyeApplication).updates
    private val link = CaptureServiceLink(this)
    private val listener: (UpdateState) -> Unit = {
        screen.updates.render(UpdatePresenter.present(it, System.currentTimeMillis()))
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
        link.bind()
    }

    override fun onResume() {
        super.onResume()
        link.attachUpdates(updates)
        updates.coordinator.observe(listener)
    }

    override fun onPause() {
        updates.coordinator.forget(listener)
        updates.detach(isChangingConfigurations)
        super.onPause()
    }

    override fun onStop() {
        link.unbind()
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
