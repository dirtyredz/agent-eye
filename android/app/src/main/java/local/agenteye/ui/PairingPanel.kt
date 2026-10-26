package local.agenteye.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.PersistableBundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import local.agenteye.security.PairingCodes

/**
 * Shows the six-digit pairing code with a countdown and a "New code" button.
 * [tick] is called about once a second by the Activity; the source rotates expired codes.
 */
class PairingPanel(
    context: Context,
    private val code: () -> PairingCodes.Code?,
    private val renew: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : LinearLayout(context) {
    private val digits = TextView(context).apply {
        textSize = 32f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.02f
    }
    private val countdown = TextView(context).apply { textSize = 14f }

    private val copy = Button(context).apply {
        text = "Copy"
        isEnabled = false
        setOnClickListener {
            val value = PairingCopy.current(code(), now(), Build.VERSION.SDK_INT)
            if (value != null) {
                val clip = ClipData.newPlainText("Pairing code", value.text)
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                if (value.showConfirmation) {
                    Toast.makeText(
                        context,
                        "Code copied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            tick()
        }
    }

    init {
        orientation = VERTICAL
        addView(TextView(context).apply { text = "Pairing code" })
        addView(
            LinearLayout(context).apply {
                addView(digits, LayoutParams(0, -2, 1f))
                addView(copy)
            }
        )
        addView(countdown)
        addView(
            Button(context).apply {
                text = "New code"
                setOnClickListener {
                    renew()
                    tick()
                }
            }
        )
    }

    fun tick() {
        val current = code()?.takeIf { it.expiresAt > now() }
        copy.isEnabled = current != null
        if (current == null) {
            digits.text = "------"
            countdown.text = "Press Start to get a pairing code"
            return
        }
        digits.text = current.value
        val seconds = ((current.expiresAt - now() + 999) / 1000).coerceAtLeast(0)
        countdown.text = "Expires in ${seconds / 60}:${"%02d".format(seconds % 60)} · single use"
    }
}
