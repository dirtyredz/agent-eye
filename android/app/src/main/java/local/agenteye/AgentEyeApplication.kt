package local.agenteye

import android.app.Application
import android.content.Context
import local.agenteye.selfupdate.UpdateRuntime

/** Application composition only. The updater runtime is created lazily and does no networking. */
class AgentEyeApplication : Application() {
    val updates: UpdateRuntime by lazy { UpdateRuntime(this) }
}

/** The one accessor for the process-wide updater runtime. */
val Context.updates: UpdateRuntime get() = (applicationContext as AgentEyeApplication).updates
