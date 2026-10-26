package local.agenteye.ui.settings

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import local.agenteye.R
import local.agenteye.ui.update.UpdatePanel

/** Scrolls in both orientations without rebuilding update controls. */
class SettingsScreen(context: Context, back: () -> Unit, privacy: () -> Unit) {
    val updates = UpdatePanel(context)
    val root = ScrollView(context)

    init {
        val pad = (16 * context.resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        text = "Settings"
                        textSize = 26f
                    }
                )
                addView(
                    Button(context).apply {
                        text = "Back to camera"
                        setOnClickListener { back() }
                    }
                )
                addView(updates)
                addView(
                    Button(context).apply {
                        setText(R.string.privacy_policy)
                        setOnClickListener { privacy() }
                    }
                )
                addView(
                    TextView(context).apply {
                        text =
                            "Trusted Wi-Fi only. Keep plugged in for screen-off use. Stop when finished."
                    }
                )
            }
        )
        ViewCompat.requestApplyInsets(root)
    }
}
