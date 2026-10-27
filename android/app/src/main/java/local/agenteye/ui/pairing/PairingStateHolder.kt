package local.agenteye.ui.pairing

import local.agenteye.security.PairingCodes

data class PairingState(val digits: String? = null, val seconds: Long = 0) {
    val live get() = digits != null
}

object PairingStateHolder {
    fun present(code: PairingCodes.Code?, now: Long, active: Boolean): PairingState {
        val live = code?.takeIf { active && it.isLive(now) } ?: return PairingState()
        return PairingState(live.value, (live.expiresAt - now + 999) / 1000)
    }
}
