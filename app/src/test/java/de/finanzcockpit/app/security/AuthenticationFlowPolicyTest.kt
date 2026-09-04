package de.finanzcockpit.app.security

import de.finanzcockpit.app.UnlockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationFlowPolicyTest {
    @Test
    fun `only current request can claim callback`() {
        val gate = AuthenticationRequestGate()
        val oldRequest = gate.begin()
        val currentRequest = gate.begin()

        assertFalse(gate.claimTerminal(oldRequest))
        assertTrue(gate.claimTerminal(currentRequest))
    }

    @Test
    fun `terminal callback is processed once`() {
        val gate = AuthenticationRequestGate()
        val request = gate.begin()

        assertTrue(gate.claimTerminal(request))
        assertFalse(gate.claimTerminal(request))
    }

    @Test
    fun `cancel invalidates request before delayed callback`() {
        val gate = AuthenticationRequestGate()
        val request = gate.begin()

        gate.invalidate()

        assertFalse(gate.isCurrent(request))
        assertFalse(gate.claimTerminal(request))
    }

    @Test
    fun `old request cannot reset newer request`() {
        val gate = AuthenticationRequestGate()
        val oldRequest = gate.begin()
        val newerRequest = gate.begin()

        assertFalse(gate.claimTerminal(oldRequest))
        assertTrue(gate.isCurrent(newerRequest))
    }

    @Test
    fun `delayed success cannot activate biometric mode`() {
        val gate = AuthenticationRequestGate()
        val delayedRequest = gate.begin()
        gate.begin()
        var mode = UnlockMode.DEVICE_PIN

        if (gate.claimTerminal(delayedRequest)) {
            mode = UnlockModeTransitions.afterBiometricActivation(mode, true)
        }

        assertEquals(UnlockMode.DEVICE_PIN, mode)
    }

    @Test
    fun `recovery changes mode only after authentication and database success`() {
        val updated = UnlockModeTransitions.afterRecovery(
            current = UnlockMode.BIOMETRIC_ONLY,
            authenticationSucceeded = true,
            databaseOpened = true,
        )

        assertEquals(UnlockMode.BIOMETRIC_OR_PIN, updated)
    }

    @Test
    fun `database failure prevents recovery mode change`() {
        val updated = UnlockModeTransitions.afterRecovery(
            current = UnlockMode.BIOMETRIC_ONLY,
            authenticationSucceeded = true,
            databaseOpened = false,
        )

        assertEquals(UnlockMode.BIOMETRIC_ONLY, updated)
    }

    @Test
    fun `authentication failure prevents recovery mode change`() {
        val updated = UnlockModeTransitions.afterRecovery(
            current = UnlockMode.BIOMETRIC_ONLY,
            authenticationSucceeded = false,
            databaseOpened = true,
        )

        assertEquals(UnlockMode.BIOMETRIC_ONLY, updated)
    }

    @Test
    fun `recovery leaves other unlock modes unchanged`() {
        listOf(UnlockMode.BIOMETRIC_OR_PIN, UnlockMode.DEVICE_PIN).forEach { mode ->
            assertEquals(
                mode,
                UnlockModeTransitions.afterRecovery(
                    current = mode,
                    authenticationSucceeded = true,
                    databaseOpened = true,
                ),
            )
        }
    }
}
