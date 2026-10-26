package local.agenteye.selfupdate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.security.SecureRandom
import java.util.concurrent.Executors
import local.agenteye.selfupdate.download.AndroidApkInspector
import local.agenteye.selfupdate.download.ApkDownloader
import local.agenteye.selfupdate.download.ApkVerifier
import local.agenteye.selfupdate.install.AndroidInstallerPort
import local.agenteye.selfupdate.install.InstallStatus
import local.agenteye.selfupdate.install.PackageInstallerGateway
import local.agenteye.selfupdate.release.GitHubReleaseSource
import local.agenteye.selfupdate.release.ReleasePolicy
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.transport.Backoff
import local.agenteye.selfupdate.transport.HttpsConnector
import local.agenteye.selfupdate.transport.UpdateHttpClient

/**
 * Wires the Android adapters, one worker thread and main-thread delivery. Holds the
 * application context only, never an Activity. Construction performs no networking.
 */
class UpdateRuntime(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val store = UpdateStore(java.io.File(app.noBackupFilesDir, "self-update"))
    private val pin = readPin(app)
    private val inspector = AndroidApkInspector(app)
    private val http = UpdateHttpClient(
        HttpsConnector(),
        object : Backoff {
            override var until: Long
                get() = store.backoffUntil
                set(value) {
                    store.backoffUntil = value
                }
        }
    )
    private val port = AndroidInstallerPort(app)
    private val random = SecureRandom()

    /** The installer confirmation intent, held only while the process lives. */
    private var confirmation: Intent? = null

    /** Set only while an Activity is visible; never retained across detach. */
    private var launcher: ((Intent) -> Unit)? = null

    val coordinator = UpdateCoordinator(
        source = GitHubReleaseSource(http, pin),
        http = http,
        downloader = ApkDownloader(store, http),
        verifier = ApkVerifier(inspector, pin) { Build.VERSION.SDK_INT },
        installer = PackageInstallerGateway(port, store) {
            ByteArray(16).also(random::nextBytes).let(ReleasePolicy::hex)
        },
        inspector = inspector,
        store = store,
        worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "agent-eye-update").apply {
                isDaemon =
                    true
            }
        },
        deliver = { main.post(it) },
        sdk = Build.VERSION.SDK_INT
    )

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

    private companion object {
        /** The public signer pin, packaged by Gradle from `signing-certificate.json`. Empty = no updates. */
        fun readPin(context: Context): String = try {
            context.assets.open("update-signer.txt").use {
                it.readBytes()
            }.toString(Charsets.US_ASCII).trim()
                .takeIf(ReleasePolicy::isPin).orEmpty()
        } catch (_: java.io.IOException) {
            ""
        }
    }
}
