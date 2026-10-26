package local.agenteye.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import local.agenteye.selfupdate.UpdateRuntime
import local.agenteye.service.CaptureService

/**
 * An Activity's binding to [CaptureService] (bind in onStart, unbind in onStop) plus the
 * updater hook-up that needs it: an install must stop capture first, and may not proceed while
 * the service is unbound.
 */
class CaptureServiceLink(
    private val activity: Activity,
    private val onConnected: (CaptureService) -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    var service: CaptureService? = null
        private set
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = (binder as CaptureService.LocalBinder).service
            service = connected
            onConnected(connected)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            onDisconnected()
        }
    }

    fun bind() {
        bound = activity.bindService(
            Intent(activity, CaptureService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun unbind() {
        if (bound) activity.unbindService(connection)
        bound = false
        service = null
    }

    /** Call from onResume (not onStart: overlapping start/stop would clear the new launcher). */
    fun attachUpdates(updates: UpdateRuntime) = updates.attach(activity) {
        val capture = service
        capture?.stopSession()
        capture != null
    }
}
