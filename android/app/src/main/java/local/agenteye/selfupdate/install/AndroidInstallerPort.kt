package local.agenteye.selfupdate.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import local.agenteye.selfupdate.release.ReleasePolicy

/** PackageInstaller adapter: MODE_FULL_INSTALL sessions for this package only. */
class AndroidInstallerPort(private val context: Context) : InstallerPort {
    private val installer get() = context.packageManager.packageInstaller

    @Suppress("DEPRECATION")
    override fun allowed(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.INSTALL_NON_MARKET_APPS,
            0
        ) ==
            1
    }

    override fun create(size: Long): Int {
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
            setSize(size)
            if (Build.VERSION.SDK_INT >=
                31
            ) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        return installer.createSession(params)
    }

    override fun write(sessionId: Int, file: File): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        written += n
                    }
                }
                session.fsync(output)
            }
        }
        return written to ReleasePolicy.hex(digest.digest())
    }

    override fun commit(sessionId: Int, nonce: String) {
        val result = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION)
            .setPackage(context.packageName)
            .putExtra(InstallResultReceiver.EXTRA_NONCE, nonce)
        // The installer fills in status extras, so the PendingIntent must be mutable on API 31+.
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, sessionId, result, flags)
        installer.openSession(sessionId).use { it.commit(pending.intentSender) }
    }

    override fun abandon(sessionId: Int) = installer.abandonSession(sessionId)

    override fun state(sessionId: Int): SessionState {
        val info = installer.getSessionInfo(sessionId) ?: return SessionState.Missing
        if (Build.VERSION.SDK_INT >= 29 && info.isCommitted) return SessionState.Committed
        // Before API 29 an active session is indistinguishable; the stored stage decides.
        return if (info.isActive) SessionState.Committed else SessionState.Open
    }

    companion object {
        fun status(code: Int): InstallStatus = when (code) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallStatus.PendingUserAction

            PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success

            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallStatus.Aborted

            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallStatus.Blocked

            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallStatus.Storage

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallStatus.Incompatible

            PackageInstaller.STATUS_FAILURE_INVALID -> InstallStatus.Invalid

            else -> InstallStatus.Failure
        }
    }
}
