package local.agenteye.selfupdate.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import local.agenteye.AgentEyeApplication

/**
 * Explicit, non-exported receiver for PackageInstaller callbacks. It forwards only the
 * session ID, nonce, status and (for pending user action) the confirmation intent; the
 * coordinator accepts a callback only for the stored session and nonce.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = AndroidInstallerPort.status(
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        )
        val confirmation = if (status == InstallStatus.PendingUserAction) {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        } else {
            null
        }
        (context.applicationContext as AgentEyeApplication).updates
            .installResult(sessionId, intent.getStringExtra(EXTRA_NONCE), status, confirmation)
    }

    companion object {
        const val ACTION = "local.agenteye.selfupdate.INSTALL_RESULT"
        const val EXTRA_NONCE = "local.agenteye.selfupdate.NONCE"
    }
}
