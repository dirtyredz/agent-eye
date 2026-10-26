package local.agenteye

import android.app.Application
import local.agenteye.selfupdate.UpdateRuntime

/** Application composition only. The updater runtime is created lazily and does no networking. */
class AgentEyeApplication : Application() {
    val updates: UpdateRuntime by lazy { UpdateRuntime(this) }
}
