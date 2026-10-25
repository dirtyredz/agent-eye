package local.agenteye.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.view.PreviewView
import local.agenteye.service.CaptureService

class MainActivity : Activity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private var service: CaptureService? = null
    private var bound = false
    private var visible = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as CaptureService.LocalBinder).service
            service?.observer = { refresh() }
            if (visible) {
                service?.attach(
                    preview.surfaceProvider,
                    preview.display?.rotation ?: Surface.ROTATION_0
                )
            }
            refresh()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            refresh()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Token and camera preview should not enter screenshots or the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(
            TextView(this).apply {
                text = "Agent Eye"
                textSize = 26f
            }
        )
        preview =
            PreviewView(this).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        layout.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this)
        buttons.addView(
            Button(this).apply {
                text = "Start"
                setOnClickListener { requestStart() }
            }
        )
        buttons.addView(
            Button(this).apply {
                text = "Stop"
                setOnClickListener {
                    service?.stopSession()
                    refresh()
                }
            }
        )
        layout.addView(buttons)
        layout.addView(
            Button(this).apply {
                setText(local.agenteye.R.string.privacy_policy)
                setOnClickListener { PrivacyPolicyDialog.show(this@MainActivity) }
            }
        )
        status = TextView(this).apply {
            textSize = 16f
            setTextIsSelectable(true)
        }
        layout.addView(
            ScrollView(this).apply {
                addView(status)
            },
            LinearLayout.LayoutParams(-1, 260)
        )
        layout.addView(
            TextView(this).apply {
                text =
                    "Trusted Wi-Fi only. Keep plugged in for screen-off use. Stop when finished."
            }
        )
        setContentView(layout)
    }
    override fun onStart() {
        super.onStart()
        visible = true
        bound =
            bindService(
                Intent(this, CaptureService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
    }
    override fun onStop() {
        visible = false
        service?.observer = null
        service?.attach(null)
        if (bound) unbindService(connection)
        bound = false
        service = null
        super.onStop()
    }
    private fun refresh() {
        status.text =
            service?.displayStatus() ?: "Stopped · Press Start to begin"
    }
    private fun requestStart() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(
                permissions.toTypedArray(),
                1
            )
        } else {
            startSession()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && visible) startSession()
    }
    private fun startSession() {
        if (!visible) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text =
                "Camera permission is required. Grant it in app settings or press Start to retry."
            return
        }
        try {
            val intent = Intent(this, CaptureService::class.java).setAction("START")
            if (Build.VERSION.SDK_INT >=
                26
            ) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            status.text =
                "Cannot start camera session. Reopen the app and try again."
        }
    }
}
