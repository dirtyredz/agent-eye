package local.agenteye.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        textSize = 40f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.1f
    }
    private val countdown = TextView(context).apply { textSize = 14f }

    init {
        orientation = VERTICAL
        addView(TextView(context).apply { text = "Pairing code" })
        addView(digits)
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
        val current = code()
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
