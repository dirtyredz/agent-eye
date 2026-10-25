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
import androidx.lifecycle.LifecycleService
import local.agenteye.R
import local.agenteye.camera.CameraSession
import local.agenteye.http.StillServer
import local.agenteye.network.LanAddress
import local.agenteye.network.LanBinding
import local.agenteye.network.SubnetPolicy
import local.agenteye.security.SessionToken
import local.agenteye.ui.MainActivity
import org.json.JSONObject

class CaptureService : LifecycleService() {
    inner class LocalBinder : Binder() { val service: CaptureService get() = this@CaptureService }
    private val binder = LocalBinder()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var camera: CameraSession? = null
    private var lan: LanBinding? = null
    private var power: SessionPower? = null
    private var server: StillServer? = null
    private var token: SessionToken? = null
    private var startedAt = 0L
    @Volatile private var running = false
    @Volatile private var address: String? = null
    @Volatile private var lastCapture: Long? = null
    @Volatile private var error: String? = null
    private var preview: Preview.SurfaceProvider? = null
    private var rotation = 0
    var observer: (() -> Unit)? = null

    override fun onBind(intent: Intent): IBinder { super.onBind(intent); return binder }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "STOP") { stopSession(); return START_NOT_STICKY }
        if (intent?.action != "START" || running) return START_NOT_STICKY
        try {
            showNotification()
            running = true
            startedAt = android.os.SystemClock.elapsedRealtime()
            lastCapture = null
            error = "wifi_unavailable"
            token = SessionToken()
            power = SessionPower(this).also { it.acquire() }
            camera = CameraSession(this, this) { _, code ->
                if (running) { error = code ?: if (address == null) "wifi_unavailable" else null; observer?.invoke() }
            }.also { it.attach(preview, rotation) }
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
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("capture", "Camera session", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "capture") else Notification.Builder(this)
        startForeground(1, builder.setSmallIcon(R.drawable.ic_camera_notification)
            .setContentTitle("Agent Eye is active").setContentText("Local camera session · Tap to open")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build())
    }

    private fun rebind(binding: LanAddress?) {
        server?.close(); server = null; address = null
        if (!running) return
        if (binding == null) error = "wifi_unavailable"
        else try {
            val currentToken = token ?: return
            val currentCamera = camera ?: return
            val url = "http://${binding.address.hostAddress}:8765"
            server = StillServer(binding.address,
                admit = { SubnetPolicy.allows(binding.address, binding.prefix, it) },
                token = currentToken, status = ::statusJson,
                capture = {
                    val future = currentCamera.shot()
                    future.whenComplete { shot, failure ->
                        // Worker callbacks from a stopped session must not update a new session.
                        main.post {
                            if (running && camera === currentCamera) {
                                if (failure == null) { lastCapture = shot.capturedAt; error = null }
                                else error = "capture_failed"
                                observer?.invoke()
                            }
                        }
                    }
                    future
                })
            address = url
            error = if (currentCamera.ready) null else "camera_unavailable"
        } catch (_: Exception) { error = "bind_failed" }
        observer?.invoke()
    }

    fun attach(surface: Preview.SurfaceProvider?, displayRotation: Int = rotation) {
        preview = surface
        rotation = displayRotation
        camera?.attach(surface, rotation)
    }
    fun displayStatus(): String = if (!running) "Stopped${error?.let { " · $it" } ?: ""}" else
        "${address ?: "Waiting for one private IPv4 Wi-Fi network"}\n" +
            "${if (camera?.ready == true) "Camera ready" else "Camera starting/unavailable"}\n" +
            "${error ?: "Ready"}\n\nSession token (keep private):\n${token?.display().orEmpty()}"

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
        lan?.close(); lan = null
        server?.close(); server = null
        camera?.close(); camera = null
        token?.clear(); token = null
        power?.close(); power = null
        address = null; lastCapture = null; error = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        observer?.invoke()
    }
    override fun onDestroy() { stopSession(); observer = null; preview = null; super.onDestroy() }
}
