package local.agenteye.ui.capture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import local.agenteye.service.CaptureService

/**
 * The Start flow: gate on the presented state, ask for camera (and, on 33+, notification)
 * permission, then start the foreground service. The gate is re-checked when the permission UI
 * returns, since the Activity may have been backgrounded or an install begun meanwhile.
 */
class SessionStarter(private val activity: Activity, private val model: CaptureScreenModel) {
    fun request() {
        model.notify(null)
        if (!model.camera.canRequestStart) return
        val missing = mutableListOf<String>()
        if (!cameraGranted()) missing.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            start()
        } else {
            activity.requestPermissions(
                missing.toTypedArray(),
                CODE
            )
        }
    }

    /** Returns true when [requestCode] was ours. */
    fun onPermissionResult(requestCode: Int): Boolean {
        if (requestCode != CODE) return false
        start()
        return true
    }

    private fun start() {
        val state = model.refresh()
        if (!canStart(state, cameraGranted())) {
            if (model.visible && !cameraGranted()) model.notify(CameraStatus.PermissionRequired)
            return
        }
        try {
            val intent = Intent(activity, CaptureService::class.java).setAction("START")
            if (Build.VERSION.SDK_INT >= 26) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
        } catch (_: Exception) {
            model.notify(CameraStatus.StartFailed)
        }
    }

    private fun cameraGranted() = activity.checkSelfPermission(Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CODE = 1

        /** Notification permission is deliberately not a Start prerequisite. */
        fun canStart(state: CameraState, cameraGranted: Boolean) =
            cameraGranted && state.canRequestStart
    }
}
