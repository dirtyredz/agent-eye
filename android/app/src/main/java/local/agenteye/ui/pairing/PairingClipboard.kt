package local.agenteye.ui.pairing

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import local.agenteye.R
import local.agenteye.security.PairingCodes

/** The source is invoked on the tap, never captured from rendered/saved digits. */
class PairingClipboard(private val context: Context, private val source: () -> PairingCodes.Code?) {
    fun copy() {
        val value =
            PairingCopy.current(source(), System.currentTimeMillis(), Build.VERSION.SDK_INT)
                ?: return
        val clip = ClipData.newPlainText(context.getString(R.string.pairing_code), value.text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        if (value.showConfirmation) {
            Toast.makeText(
                context,
                R.string.code_copied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
