package local.agenteye.selfupdate.install

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import local.agenteye.selfupdate.UpdateCoordinator

/**
 * The Android installer UI bridge: holds the confirmation intent and a visible-Activity launcher,
 * and turns installer results into coordinator reconciliation. Holds the application context
 * only; the launcher exists solely while an Activity is attached.
 */
class InstallPrompter(context: Context, private val coordinator: UpdateCoordinator) {
    private val app = context.applicationContext

    /** The installer confirmation intent, held only while the process lives. */
    private var confirmation: Intent? = null

    /** Set only while an Activity is visible; never retained across detach. */
    private var launcher: ((Intent) -> Unit)? = null

    fun attach(activity: Activity, stopCamera: () -> Boolean) {
        launcher = { activity.startActivity(it) }
        coordinator.handoff = stopCamera
        coordinator.foreground(confirmation != null)
    }

    /** [changingConfigurations] keeps the operation alive across recreation. */
    fun detach(changingConfigurations: Boolean) {
        launcher = null
        coordinator.handoff = null
        if (!changingConfigurations) coordinator.background()
    }

    fun installResult(sessionId: Int, nonce: String?, status: InstallStatus, intent: Intent?) {
        if (!coordinator.installResult(sessionId, nonce, status)) return
        confirmation = if (status == InstallStatus.PendingUserAction) intent else null
        // Confirmation is only ever launched from a visible Activity, never from the background.
        if (confirmation != null) continueInstall()
    }

    /** Opens Android's install confirmation; when it was lost, reconciles to an explicit recovery state. */
    fun continueInstall() {
        val intent = confirmation
        val open = launcher
        if (intent != null && open != null && runCatching { open(intent) }.isSuccess) return
        if (open != null) {
            confirmation = null
            coordinator.foreground(haveConfirmation = false)
        }
    }

    /** The per-app install-source setting (API 26+) or legacy security settings (API 24-25). */
    fun permissionIntent(): Intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${app.packageName}")
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
}
