package local.agenteye.ui

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import local.agenteye.R
import local.agenteye.security.PairingCodes
import local.agenteye.ui.update.UpdatePanel

/**
 * Builds the main screen: title, preview, Start/Stop, privacy, pairing panel, status, update
 * panel and the trusted-Wi-Fi note, padded clear of system bars and cutouts (targetSdk 35+ is
 * edge-to-edge). Holds views only; the Activity owns lifecycle, permissions and the service.
 */
class CameraScreen(
    context: Context,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPrivacy: () -> Unit,
    code: () -> PairingCodes.Code?,
    renew: () -> Unit
) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(PAD, PAD, PAD, PAD)
    }
    val preview = PreviewView(context).apply {
        implementationMode =
            PreviewView.ImplementationMode.COMPATIBLE
    }
    val pairingPanel = PairingPanel(context, code = code, renew = renew)
    val updatePanel = UpdatePanel(context)
    private val status = TextView(context).apply {
        textSize = 16f
        setTextIsSelectable(true)
    }

    init {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(PAD + bars.left, PAD + bars.top, PAD + bars.right, PAD + bars.bottom)
            insets
        }
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.app_name)
                textSize = 26f
            }
        )
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(context)
        buttons.addView(button(context, "Start", onStart))
        buttons.addView(button(context, "Stop", onStop))
        buttons.addView(button(context, context.getString(R.string.privacy_policy), onPrivacy))
        root.addView(buttons)
        root.addView(pairingPanel)
        root.addView(
            ScrollView(context).apply {
                addView(status)
            },
            LinearLayout.LayoutParams(-1, 160)
        )
        root.addView(updatePanel)
        root.addView(
            TextView(context).apply {
                text =
                    "Trusted Wi-Fi only. Keep plugged in for screen-off use. Stop when finished."
            }
        )
        ViewCompat.requestApplyInsets(root)
    }

    fun showStatus(text: String) {
        status.text = text
    }

    private fun button(context: Context, label: String, action: () -> Unit) =
        Button(context).apply {
            text = label
            setOnClickListener { action() }
        }

    private companion object {
        const val PAD = 24
    }
}
