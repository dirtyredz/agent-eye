package local.agenteye.selfupdate

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.security.SecureRandom
import java.util.concurrent.Executors
import local.agenteye.selfupdate.contract.Integrity
import local.agenteye.selfupdate.contract.ReleaseContract
import local.agenteye.selfupdate.download.AndroidApkInspector
import local.agenteye.selfupdate.download.ApkDownloader
import local.agenteye.selfupdate.download.ApkVerifier
import local.agenteye.selfupdate.install.AndroidInstallerPort
import local.agenteye.selfupdate.install.InstallPrompter
import local.agenteye.selfupdate.install.PackageInstallerGateway
import local.agenteye.selfupdate.release.GitHubReleaseSource
import local.agenteye.selfupdate.storage.BackoffStore
import local.agenteye.selfupdate.storage.UpdateStore
import local.agenteye.selfupdate.transport.HttpsConnector
import local.agenteye.selfupdate.transport.UpdateHttpClient

/**
 * Wires the Android adapters, one worker thread and main-thread delivery. Holds the
 * application context only, never an Activity. Construction performs no networking.
 */
class UpdateRuntime(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val directory = java.io.File(app.noBackupFilesDir, "self-update")
    private val store = UpdateStore(directory)
    private val backoff = BackoffStore(directory)
    private val pin = readPin(app)
    private val inspector = AndroidApkInspector(app)
    private val http = UpdateHttpClient(HttpsConnector(), backoff)
    private val port = AndroidInstallerPort(app)
    private val random = SecureRandom()

    val coordinator = UpdateCoordinator(
        source = GitHubReleaseSource(http, pin),
        http = http,
        downloader = ApkDownloader(store, http),
        verifier = ApkVerifier(inspector, pin) { Build.VERSION.SDK_INT },
        installer = PackageInstallerGateway(port, store) {
            ByteArray(16).also(random::nextBytes).let(Integrity::hex)
        },
        inspector = inspector,
        store = store,
        backoff = backoff,
        worker = Executors.newSingleThreadExecutor { r ->
            Thread(r, "agent-eye-update").apply {
                isDaemon =
                    true
            }
        },
        deliver = { main.post(it) },
        sdk = Build.VERSION.SDK_INT
    )

    val prompter = InstallPrompter(app, coordinator)

    private companion object {
        /** The public signer pin, packaged by Gradle from `signing-certificate.json`. Empty = no updates. */
        fun readPin(context: Context): String = try {
            context.assets.open("update-signer.txt").use {
                it.readBytes()
            }.toString(Charsets.US_ASCII).trim()
                .takeIf(ReleaseContract::isSha256).orEmpty()
        } catch (_: java.io.IOException) {
            ""
        }
    }
}
