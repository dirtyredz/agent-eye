package local.agenteye.ui.pairing

import local.agenteye.security.PairingCodes

/** What the pairing card shows: the live digits and the rounded-up seconds left, or nothing. */
data class PairingState(val digits: String? = null, val seconds: Long = 0) {
    val live get() = digits != null

    companion object {
        /** Credentials appear only while [active]; an expired or absent [code] clears them. */
        fun from(code: PairingCodes.Code?, now: Long, active: Boolean): PairingState {
            val live = code?.takeIf { active && it.isLive(now) } ?: return PairingState()
            return PairingState(live.value, (live.expiresAt - now + 999) / 1000)
        }
    }
}
