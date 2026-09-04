package de.finanzcockpit.app.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

data class BiometricPromptCopy(
    val subtitle: String,
    val negativeButton: String?,
)

object BiometricPolicy {
    private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG
    private const val CREDENTIAL = BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private const val STRONG_OR_CREDENTIAL = STRONG or CREDENTIAL

    fun promptCopy(authenticators: Int): BiometricPromptCopy = when (authenticators) {
        STRONG -> BiometricPromptCopy(
            subtitle = "Starke Biometrie verwenden",
            negativeButton = "Abbrechen",
        )
        CREDENTIAL -> BiometricPromptCopy(
            subtitle = "Geräte-PIN, Muster oder Passwort verwenden",
            negativeButton = null,
        )
        STRONG_OR_CREDENTIAL -> BiometricPromptCopy(
            subtitle = "Biometrie oder Gerätesperre verwenden",
            negativeButton = null,
        )
        else -> throw IllegalArgumentException("Nicht unterstützter Entsperrmodus")
    }

    fun availabilityError(authenticators: Int, result: Int): String? {
        if (result == BiometricManager.BIOMETRIC_SUCCESS) return null
        val biometricOnly = authenticators == STRONG
        return when (result) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> if (biometricOnly) {
                "Für „Biometrie bevorzugt“ muss zuerst eine starke Biometrie auf dem Gerät eingerichtet werden."
            } else {
                "Bitte richte auf dem Gerät eine sichere PIN, ein Muster oder ein Passwort ein."
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Die Geräteauthentifizierung ist vorübergehend nicht verfügbar. Bitte versuche es später erneut."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> if (biometricOnly) {
                "Dieses Gerät unterstützt keine starke Biometrie."
            } else {
                "Auf diesem Gerät ist keine sichere Geräteauthentifizierung verfügbar."
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Die Geräteauthentifizierung benötigt zuerst ein Sicherheitsupdate."
            else -> "Die gewünschte Geräteauthentifizierung ist derzeit nicht verfügbar."
        }
    }

    fun authenticationError(errorCode: Int): String? = when (errorCode) {
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        -> null
        BiometricPrompt.ERROR_LOCKOUT ->
            "Zu viele Versuche. Die Geräteauthentifizierung ist vorübergehend gesperrt."
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            "Die Geräteauthentifizierung ist gesperrt. Entsperre das Gerät zunächst mit PIN, Muster oder Passwort."
        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
            "Die Geräteauthentifizierung ist vorübergehend nicht verfügbar. Bitte versuche es später erneut."
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        -> "Die gewählte Geräteauthentifizierung ist auf diesem Gerät nicht eingerichtet."
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
            "Auf diesem Gerät ist keine PIN, kein Muster oder kein Passwort eingerichtet."
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
            "Die Geräteauthentifizierung benötigt zuerst ein Sicherheitsupdate."
        BiometricPrompt.ERROR_TIMEOUT ->
            "Die Authentifizierung hat zu lange gedauert. Bitte versuche es erneut."
        else -> "Die Geräteauthentifizierung konnte nicht abgeschlossen werden."
    }

    fun keyMaterialError(): String =
        "Der geschützte Geräteschlüssel ist nicht mehr verwendbar. Weitere Entsperrversuche " +
            "helfen möglicherweise nicht. Stelle deine Daten aus einer externen Sicherung wieder her."
}
