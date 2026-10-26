package local.agenteye.ui

import local.agenteye.security.PairingCodes

/** Resolve at tap time, never copy a cached display value after Stop or expiry. */
object PairingCopy {
    data class Value(val text: String, val showConfirmation: Boolean)
    fun current(code: PairingCodes.Code?, now: Long, sdk: Int): Value? =
        code?.takeIf { it.expiresAt > now }?.let { Value(it.value, sdk < 33) }
}
