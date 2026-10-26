package local.agenteye.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import local.agenteye.R
import local.agenteye.camera.Lens
import local.agenteye.security.PairingCodes

/**
 * Builds the main screen: preview, capture controls, pairing and status, padded clear of system bars and cutouts (targetSdk 35+ is
 * edge-to-edge). Holds views only; the Activity owns lifecycle, permissions and the service.
 */
class CameraScreen(
    context: Context,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onSwitch: () -> Unit,
    code: () -> PairingCodes.Code?,
    renew: () -> Unit
) {
    private val title = TextView(context).apply {
        text = context.getString(R.string.app_name)
        textSize = 26f
    }
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val controlsScroll = ScrollView(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(PAD, PAD, PAD, PAD)
    }
    val preview = PreviewView(context).apply {
        implementationMode =
            PreviewView.ImplementationMode.COMPATIBLE
    }
    val pairingPanel = PairingPanel(context, code = code, renew = renew)
    private val switchCamera = button(context, "Switch camera", onSwitch).apply {
        visibility = View.GONE
    }
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
        val buttons = LinearLayout(context)
        buttons.addView(button(context, "Start", onStart))
        buttons.addView(button(context, "Stop", onStop))
        buttons.addView(button(context, "Settings", onSettings))
        controls.addView(buttons)
        controls.addView(switchCamera)
        controls.addView(pairingPanel)
        controls.addView(
            ScrollView(context).apply {
                addView(status)
            },
            LinearLayout.LayoutParams(-1, 160)
        )
        applyOrientation(
            context.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        )
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Portrait stacks everything under a tall preview; landscape puts the preview beside a
     * scrolling control column so the preview is never squeezed to nothing. Views are re-parented,
     * never rebuilt, so pairing and status survive a rotation.
     */
    fun applyOrientation(landscape: Boolean) {
        root.removeAllViews()
        controlsScroll.removeAllViews()
        (title.parent as? ViewGroup)?.removeView(title)
        (controls.parent as? ViewGroup)?.removeView(controls)
        if (landscape) {
            root.orientation = LinearLayout.HORIZONTAL
            root.addView(preview, LinearLayout.LayoutParams(0, -1, 1f))
            val column = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
            column.addView(title)
            column.addView(controls)
            controlsScroll.addView(column)
            root.addView(controlsScroll, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            root.addView(title)
            root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(controls)
        }
    }

    fun showLens(lens: Lens?, canSwitch: Boolean) {
        switchCamera.visibility = if (canSwitch) View.VISIBLE else View.GONE
        switchCamera.text = "Switch camera (" + (lens?.name?.lowercase() ?: "unavailable") + ")"
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
