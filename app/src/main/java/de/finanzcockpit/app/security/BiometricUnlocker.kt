package de.finanzcockpit.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

class BiometricUnlocker(
    private val activity: FragmentActivity,
    private val secureKeyStore: SecureKeyStore,
) {
    private var prompt: BiometricPrompt? = null
    private var pendingEnrollment: SecureKeyStore.Enrollment? = null

    fun authenticate(
        authenticators: Int = AUTHENTICATORS,
        onUnlocked: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit,
    ) {
        val biometricManager = BiometricManager.from(activity)
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Bitte richte auf dem Gerät eine sichere PIN und optional Biometrie ein.")
            return
        }

        try {
            val cipher: Cipher
            if (secureKeyStore.isEnrolled()) {
                cipher = secureKeyStore.prepareUnlock()
            } else {
                pendingEnrollment = secureKeyStore.prepareEnrollment()
                cipher = checkNotNull(pendingEnrollment).cipher
            }

            prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authorizedCipher = result.cryptoObject?.cipher
                        if (authorizedCipher == null) {
                            clearPending()
                            onError("Die sichere Entsperrung lieferte keinen Kryptoschlüssel.")
                            return
                        }
                        try {
                            val passphrase = pendingEnrollment?.let {
                                secureKeyStore.finishEnrollment(authorizedCipher, it.databasePassphrase)
                            } ?: secureKeyStore.finishUnlock(authorizedCipher)
                            pendingEnrollment = null
                            onUnlocked(passphrase)
                        } catch (_: Exception) {
                            clearPending()
                            onError("Der Geräteschlüssel konnte nicht verwendet werden. Nutze eine Sicherung zur Wiederherstellung.")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        clearPending()
                        if (
                            errorCode == BiometricPrompt.ERROR_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            onCancelled()
                        } else {
                            onError(errString.toString())
                        }
                    }
                },
            )

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("FinanzCockpit entsperren")
                .setSubtitle(
                    if (authenticators == BiometricManager.Authenticators.BIOMETRIC_STRONG) {
                        "Biometrie verwenden"
                    } else if (authenticators == BiometricManager.Authenticators.DEVICE_CREDENTIAL) {
                        "Geräte-PIN verwenden"
                    } else {
                        "Biometrie oder Geräte-PIN verwenden"
                    },
                )
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
                .build()
            prompt?.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            clearPending()
            onError("Die sichere Geräteauthentifizierung konnte nicht gestartet werden.")
        }
    }

    fun cancel() {
        prompt?.cancelAuthentication()
        prompt = null
        clearPending()
    }

    private fun clearPending() {
        pendingEnrollment?.databasePassphrase?.fill(0)
        pendingEnrollment = null
    }

    companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

fun Context.canUseSecureAuthentication(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricUnlocker.AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS
