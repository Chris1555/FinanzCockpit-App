package de.finanzcockpit.app.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import de.finanzcockpit.app.UnlockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPolicyTest {
    @Test
    fun `strong biometric prompt has a non-empty cancel button`() {
        val copy = BiometricPolicy.promptCopy(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        assertFalse(copy.negativeButton.isNullOrBlank())
    }

    @Test
    fun `device credential prompt has no negative button`() {
        val copy = BiometricPolicy.promptCopy(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

        assertNull(copy.negativeButton)
    }

    @Test
    fun `combined prompt has no negative button`() {
        val copy = BiometricPolicy.promptCopy(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )

        assertNull(copy.negativeButton)
    }

    @Test
    fun `every supported unlock mode has valid prompt copy`() {
        UnlockMode.entries.forEach { mode ->
            assertTrue(BiometricPolicy.promptCopy(mode.authenticators).subtitle.isNotBlank())
        }
    }

    @Test
    fun `biometric only mode uses preferred wording`() {
        assertEquals("Biometrie bevorzugt", UnlockMode.BIOMETRIC_ONLY.label)
        assertEquals("Biometrie oder Gerätesperre", UnlockMode.BIOMETRIC_OR_PIN.label)
    }

    @Test
    fun `user cancellation is not exposed as an error`() {
        assertNull(BiometricPolicy.authenticationError(BiometricPrompt.ERROR_USER_CANCELED))
        assertNull(BiometricPolicy.authenticationError(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertNull(BiometricPolicy.authenticationError(BiometricPrompt.ERROR_CANCELED))
    }

    @Test
    fun `temporary and permanent lockout have distinct safe messages`() {
        val temporary = BiometricPolicy.authenticationError(BiometricPrompt.ERROR_LOCKOUT)
        val permanent = BiometricPolicy.authenticationError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT)

        assertTrue(temporary.orEmpty().contains("vorübergehend"))
        assertTrue(permanent.orEmpty().contains("PIN, Muster oder Passwort"))
        assertFalse(temporary == permanent)
    }

    @Test
    fun `missing strong biometric is explained for preferred mode`() {
        val message = BiometricPolicy.availabilityError(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        )

        assertTrue(message.orEmpty().contains("starke Biometrie"))
    }

    @Test
    fun `unavailable hardware gets temporary message`() {
        val message = BiometricPolicy.availabilityError(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        )

        assertTrue(message.orEmpty().contains("vorübergehend"))
    }

    @Test
    fun `successful availability check has no error`() {
        assertNull(
            BiometricPolicy.availabilityError(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
                BiometricManager.BIOMETRIC_SUCCESS,
            ),
        )
    }

    @Test
    fun `missing device credential has a specific safe message`() {
        val message = BiometricPolicy.authenticationError(
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        )

        assertTrue(message.orEmpty().contains("PIN"))
        assertTrue(message.orEmpty().contains("Muster"))
        assertTrue(message.orEmpty().contains("Passwort"))
    }

    @Test
    fun `required security update has a specific safe message`() {
        val message = BiometricPolicy.authenticationError(
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )

        assertTrue(message.orEmpty().contains("Sicherheitsupdate"))
    }

    @Test
    fun `key material failure differs from authentication failure`() {
        val keyFailure = BiometricPolicy.keyMaterialError()
        val authenticationFailure = BiometricPolicy.authenticationError(BiometricPrompt.ERROR_LOCKOUT)

        assertNotEquals(authenticationFailure, keyFailure)
        assertTrue(keyFailure.contains("Geräteschlüssel"))
        assertTrue(keyFailure.contains("externen Sicherung"))
    }
}
