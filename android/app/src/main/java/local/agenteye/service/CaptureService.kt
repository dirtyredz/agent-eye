package local.agenteye.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.camera.core.Preview
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import local.agenteye.R
import local.agenteye.camera.CameraSession
import local.agenteye.camera.Lens
import local.agenteye.camera.LensSelection
import local.agenteye.camera.availableLenses
import local.agenteye.camera.fromPreferences
import local.agenteye.http.StillServer
import local.agenteye.network.LanAddress
import local.agenteye.network.LanBinding
import local.agenteye.network.SubnetPolicy
import local.agenteye.security.PairingCodes
import local.agenteye.security.SessionToken
import local.agenteye.ui.MainActivity
import org.json.JSONObject

class CaptureService : LifecycleService() {
    inner class LocalBinder : Binder() {
        val service: CaptureService get() = this@CaptureService
    }
    private val binder = LocalBinder()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var camera: CameraSession? = null
    private var lan: LanBinding? = null
    private var power: SessionPower? = null
    private var server: StillServer? = null
    private var token: SessionToken? = null
    private var pairing: PairingCodes? = null
    private var startedAt = 0L

    @Volatile private var running = false

    @Volatile private var address: String? = null

    @Volatile private var lastCapture: Long? = null

    @Volatile private var error: String? = null
    private var preview: Preview.SurfaceProvider? = null
    private var rotation = 0
    private var viewPort: ViewPort? = null
    var observer: (() -> Unit)? = null

    private val lenses by lazy { LensSelection.fromPreferences(this) }
    val availableLenses get() = lenses.available
    val selectedLens get() = lenses.selected

    override fun onCreate() {
        super.onCreate()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                lenses.discover(provider.availableLenses())
                lenses.selected?.let { camera?.selectLens(it) }
            }
            observer?.invoke()
        }, ContextCompat.getMainExecutor(this))
    }

    fun switchCamera() {
        lenses.cycle()?.let { camera?.selectLens(it) }
        observer?.invoke()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "STOP") {
            stopSession()
            return START_NOT_STICKY
        }
        if (intent?.action != "START" || running) return START_NOT_STICKY
        try {
            showNotification()
            running = true
            startedAt = android.os.SystemClock.elapsedRealtime()
            lastCapture = null
            error = "wifi_unavailable"
            token = SessionToken()
            pairing = PairingCodes()
            power = SessionPower(this).also { it.acquire() }
            camera = CameraSession(this, this, lenses.selected ?: Lens.BACK) { _, code ->
                if (running) {
                    error = code ?: if (address ==
                        null
                    ) {
                        "wifi_unavailable"
                    } else {
                        null
                    }
                    observer?.invoke()
                }
            }.also { it.attach(preview, rotation, viewPort) }
            lan = LanBinding(this, ::rebind)
        } catch (_: Exception) {
            stopSession()
            error = "session_start_failed"
        }
        observer?.invoke()
        return START_NOT_STICKY
    }

    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("capture", "Camera session", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, CaptureService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >=
            26
        ) {
            Notification.Builder(this, "capture")
        } else {
            Notification.Builder(this)
        }
        startForeground(
            1,
            builder.setSmallIcon(R.drawable.ic_camera_notification)
                .setContentTitle(
                    "Agent Eye is active"
                ).setContentText("Local camera session · Tap to open")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        )
    }

    private fun rebind(binding: LanAddress?) {
        server?.close()
        server = null
        address = null
        if (!running) return
        if (binding == null) {
            error = "wifi_unavailable"
        } else {
            try {
                val currentToken = token ?: return
                val currentCamera = camera ?: return
                val url = "http://${binding.address.hostAddress}:8765"
                server = StillServer(
                    binding.address,
                    admit = { SubnetPolicy.allows(binding.address, binding.prefix, it) },
                    token = currentToken,
                    pairing = pairing,
                    status = ::statusJson,
                    capture = {
                        val future = currentCamera.shot()
                        future.whenComplete { shot, failure ->
                            // A stopped session's worker callbacks must not update a new session.
                            main.post {
                                if (running && camera === currentCamera) {
                                    if (failure == null) {
                                        lastCapture = shot.capturedAt
                                        error = null
                                    } else {
                                        error = "capture_failed"
                                    }
                                    observer?.invoke()
                                }
                            }
                        }
                        future
                    }
                )
                address = url
                error = if (currentCamera.ready) null else "camera_unavailable"
            } catch (_: Exception) {
                error = "bind_failed"
            }
        }
        observer?.invoke()
    }

    fun attach(
        surface: Preview.SurfaceProvider?,
        displayRotation: Int = rotation,
        port: ViewPort? = null
    ) {
        preview = surface
        rotation = displayRotation
        if (surface != null && port != null) viewPort = port
        camera?.attach(surface, rotation, port)
    }
    fun displayStatus(): String = if (!running) {
        "Stopped${error?.let { " · $it" } ?: ""}"
    } else {
        "${address ?: "Waiting for one private IPv4 Wi-Fi network"}\n" +
            "${if (camera?.ready == true) "Camera ready" else "Camera starting/unavailable"}\n" +
            (error ?: "Ready")
    }

    /** The live pairing code (rotated when expired), or null when no session runs. */
    fun pairingCode(): PairingCodes.Code? = if (running) pairing?.current() else null
    fun newPairingCode(): PairingCodes.Code? = if (running) pairing?.refresh() else null

    private fun statusJson(): String = JSONObject().apply {
        put("protocolVersion", 1)
        put("sessionReady", running && address != null)
        put("cameraReady", camera?.ready == true)
        put("boundAddress", address ?: JSONObject.NULL)
        put("uptimeMs", android.os.SystemClock.elapsedRealtime() - startedAt)
        put("lastCaptureTime", lastCapture ?: JSONObject.NULL)
        put("error", error ?: JSONObject.NULL)
    }.toString()

    fun stopSession() {
        running = false
        lan?.close()
        lan = null
        server?.close()
        server = null
        camera?.close()
        camera = null
        token?.clear()
        token = null
        pairing = null
        power?.close()
        power = null
        address = null
        lastCapture = null
        error = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        observer?.invoke()
    }
    override fun onDestroy() {
        stopSession()
        observer = null
        preview = null
        super.onDestroy()
    }
}
