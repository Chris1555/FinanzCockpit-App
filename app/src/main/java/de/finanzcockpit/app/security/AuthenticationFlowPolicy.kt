package de.finanzcockpit.app.security

import de.finanzcockpit.app.UnlockMode

class AuthenticationRequestGate {
    private var generation = 0L
    private var activeRequestId: Long? = null

    @Synchronized
    fun begin(): Long = (++generation).also { activeRequestId = it }

    @Synchronized
    fun isCurrent(requestId: Long): Boolean = activeRequestId == requestId

    @Synchronized
    fun claimTerminal(requestId: Long): Boolean {
        if (activeRequestId != requestId) return false
        activeRequestId = null
        return true
    }

    @Synchronized
    fun invalidate() {
        generation += 1
        activeRequestId = null
    }
}

object UnlockModeTransitions {
    fun afterBiometricActivation(
        current: UnlockMode,
        authenticationSucceeded: Boolean,
    ): UnlockMode = if (authenticationSucceeded) UnlockMode.BIOMETRIC_ONLY else current

    fun afterRecovery(
        current: UnlockMode,
        authenticationSucceeded: Boolean,
        databaseOpened: Boolean,
    ): UnlockMode = if (
        current == UnlockMode.BIOMETRIC_ONLY && authenticationSucceeded && databaseOpened
    ) {
        UnlockMode.BIOMETRIC_OR_PIN
    } else {
        current
    }
}
