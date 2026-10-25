package local.agenteye.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import local.agenteye.R

object PrivacyPolicyDialog {
    fun show(context: Context) {
        val policy = context.assets.open("PRIVACY.md").bufferedReader().use { it.readText() }
        val text = TextView(context).apply {
            this.text = policy
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.privacy_policy)
            .setView(ScrollView(context).apply { addView(text) })
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
