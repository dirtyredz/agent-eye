package local.agenteye.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import local.agenteye.AgentEyeApplication
import local.agenteye.selfupdate.UpdateState
import local.agenteye.ui.CaptureServiceLink
import local.agenteye.ui.theme.AgentEyeTheme
import local.agenteye.ui.update.UpdateController
import local.agenteye.ui.update.UpdatePresenter
import local.agenteye.ui.update.UpdateView

/** Owns updater visibility; opening Settings never starts capture or network work. */
class SettingsActivity : ComponentActivity() {
    private var view by mutableStateOf(UpdateView("", "", null, null, null))
    private val updates get() = (application as AgentEyeApplication).updates
    private val link = CaptureServiceLink(this)
    private val controller by lazy { UpdateController.forActivity(this, updates, link) }
    private val listener: (UpdateState) -> Unit = {
        view = UpdatePresenter.present(it, System.currentTimeMillis())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        view = UpdatePresenter.present(updates.coordinator.state, System.currentTimeMillis())
        setContent { AgentEyeTheme { SettingsScreen(view, { finish() }, controller::act) } }
    }

    override fun onStart() {
        super.onStart()
        link.bind()
    }

    override fun onResume() {
        super.onResume()
        updates.prompter.attach(this, controller::stopForInstall)
        updates.coordinator.observe(listener)
    }

    override fun onPause() {
        updates.coordinator.forget(listener)
        updates.prompter.detach(isChangingConfigurations)
        super.onPause()
    }

    override fun onStop() {
        link.unbind()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}
