package local.agenteye.ui.update

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import local.agenteye.R

/** Update controls and the always-visible network disclosure. Renders an [UpdateView]. */
class UpdatePanel(context: Context) : LinearLayout(context) {
    var onAction: (UpdateAction) -> Unit = {}
    private val version = TextView(context).apply { textSize = 14f }
    private val status = TextView(context).apply { textSize = 14f }
    private val progress = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal
    ).apply {
        max =
            100
    }
    private val primary = Button(context)
    private val secondary = Button(context)

    init {
        orientation = VERTICAL
        addView(version)
        addView(status)
        addView(progress, LayoutParams(-1, -2))
        addView(
            LinearLayout(context).apply {
                addView(primary)
                addView(secondary)
            }
        )
        addView(
            TextView(context).apply {
                setText(R.string.update_disclosure)
                textSize = 12f
            }
        )
    }

    fun render(view: UpdateView) {
        version.text = view.version
        status.text = view.status
        progress.visibility = if (view.progress == null) View.GONE else View.VISIBLE
        progress.progress = view.progress ?: 0
        bind(primary, view.primary)
        bind(secondary, view.secondary)
    }

    private fun bind(button: Button, model: UpdateButton?) {
        button.visibility = if (model == null) View.GONE else View.VISIBLE
        if (model == null) return
        button.text = model.label
        button.setOnClickListener { onAction(model.action) }
    }
}
