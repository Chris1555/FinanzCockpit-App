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
    private val requestGate = AuthenticationRequestGate()
    private var prompt: BiometricPrompt? = null
    private var activeCleanup: (() -> Unit)? = null

    fun availabilityError(authenticators: Int): String? = BiometricPolicy.availabilityError(
        authenticators,
        BiometricManager.from(activity).canAuthenticate(authenticators),
    )

    fun authenticate(
        authenticators: Int = AUTHENTICATORS,
        title: String = "FinanzCockpit entsperren",
        onUnlocked: (ByteArray) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit,
    ) {
        invalidateActiveRequest()
        val requestId = requestGate.begin()
        var pendingEnrollment: SecureKeyStore.Enrollment? = null

        fun clearPending() {
            pendingEnrollment?.databasePassphrase?.fill(0)
            pendingEnrollment = null
        }

        activeCleanup = ::clearPending

        fun finishWithError(message: String) {
            clearPending()
            if (!requestGate.claimTerminal(requestId)) return
            prompt = null
            activeCleanup = null
            onError(message)
        }

        availabilityError(authenticators)?.let {
            finishWithError(it)
            return
        }

        val existingEnrollment = try {
            secureKeyStore.isEnrolled()
        } catch (_: Exception) {
            finishWithError(BiometricPolicy.keyMaterialError())
            return
        }
        val cipher: Cipher = try {
            if (existingEnrollment) {
                secureKeyStore.prepareUnlock()
            } else {
                secureKeyStore.prepareEnrollment().also { pendingEnrollment = it }.cipher
            }
        } catch (_: Exception) {
            finishWithError(
                if (existingEnrollment) BiometricPolicy.keyMaterialError()
                else "Die sichere Geräteauthentifizierung konnte nicht vorbereitet werden.",
            )
            return
        }

        try {
            val currentPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!requestGate.claimTerminal(requestId)) {
                            clearPending()
                            return
                        }
                        prompt = null
                        activeCleanup = null
                        val authorizedCipher = result.cryptoObject?.cipher
                        if (authorizedCipher == null) {
                            clearPending()
                            onError("Die Geräteauthentifizierung konnte nicht sicher abgeschlossen werden.")
                            return
                        }
                        val passphrase = try {
                            pendingEnrollment?.let {
                                secureKeyStore.finishEnrollment(authorizedCipher, it.databasePassphrase)
                            } ?: secureKeyStore.finishUnlock(authorizedCipher)
                        } catch (_: Exception) {
                            clearPending()
                            onError(
                                if (existingEnrollment) BiometricPolicy.keyMaterialError()
                                else "Die sichere Geräteauthentifizierung konnte nicht abgeschlossen werden.",
                            )
                            return
                        }
                        pendingEnrollment = null
                        onUnlocked(passphrase)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        clearPending()
                        if (!requestGate.claimTerminal(requestId)) return
                        prompt = null
                        activeCleanup = null
                        BiometricPolicy.authenticationError(errorCode)?.let(onError) ?: onCancelled()
                    }
                },
            )

            val copy = BiometricPolicy.promptCopy(authenticators)
            val infoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(copy.subtitle)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(true)
            copy.negativeButton?.let(infoBuilder::setNegativeButtonText)
            val info = infoBuilder.build()
            if (!requestGate.isCurrent(requestId)) {
                clearPending()
                return
            }
            prompt = currentPrompt
            currentPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            finishWithError("Die sichere Geräteauthentifizierung konnte nicht gestartet werden.")
        }
    }

    fun cancel() {
        invalidateActiveRequest()
    }

    private fun invalidateActiveRequest() {
        requestGate.invalidate()
        activeCleanup?.invoke()
        activeCleanup = null
        val previousPrompt = prompt
        prompt = null
        previousPrompt?.cancelAuthentication()
    }

    companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

fun Context.canUseSecureAuthentication(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricUnlocker.AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS
