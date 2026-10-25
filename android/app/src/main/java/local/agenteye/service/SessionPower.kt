package local.agenteye.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

class SessionPower(context: Context) : AutoCloseable {
    private val wake = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AgentEye:session")

    @Suppress("DEPRECATION")
    private val wifi = if (Build.VERSION.SDK_INT < 34) {
        context.applicationContext
            .getSystemService(
                WifiManager::class.java
            ).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AgentEye:session")
    } else {
        null
    }
    fun acquire() {
        try {
            wake.acquire()
            wifi?.acquire()
        } catch (e: Exception) {
            close()
            throw e
        }
    }
    override fun close() {
        if (wifi?.isHeld == true) wifi.release()
        if (wake.isHeld) wake.release()
    }
}
