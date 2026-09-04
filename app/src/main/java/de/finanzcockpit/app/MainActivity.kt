package de.finanzcockpit.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.biometric.BiometricManager
import de.finanzcockpit.app.security.BiometricUnlocker
import de.finanzcockpit.app.security.AuthenticationRequestGate
import de.finanzcockpit.app.security.SecureKeyStore
import de.finanzcockpit.app.security.UnlockModeTransitions
import de.finanzcockpit.app.ui.FinanzCockpitRoot
import de.finanzcockpit.app.ui.theme.FinanzCockpitTheme

class MainActivity : FragmentActivity() {
    private val viewModel: FinanceViewModel by viewModels()
    private lateinit var unlocker: BiometricUnlocker
    private val authenticationRequests = AuthenticationRequestGate()
    private var authenticationInProgress = false
    private val lockHandler = Handler(Looper.getMainLooper())
    private val delayedLock = Runnable { viewModel.lock() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        unlocker = BiometricUnlocker(this, SecureKeyStore(this))
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            FinanzCockpitTheme(state.settings.themeMode) {
                FinanzCockpitRoot(
                    viewModel = viewModel,
                    requestUnlock = ::requestUnlock,
                    requestBiometricPreferredActivation = ::requestBiometricPreferredActivation,
                    biometricPreferredAvailabilityError = ::biometricPreferredAvailabilityError,
                    requestRecoveryUnlock = ::requestRecoveryUnlock,
                    lockNow = viewModel::lock,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lockHandler.removeCallbacks(delayedLock)
        if (!viewModel.state.value.isUnlocked) requestUnlock()
    }

    override fun onStop() {
        authenticationRequests.invalidate()
        authenticationInProgress = false
        unlocker.cancel()
        viewModel.cancelPendingUnlock()
        if (!isChangingConfigurations) {
            val delay = viewModel.state.value.settings.lockDelay.milliseconds
            if (delay == 0L) viewModel.lock() else lockHandler.postDelayed(delayedLock, delay)
        }
        super.onStop()
    }

    private fun requestUnlock() {
        if (
            authenticationInProgress || viewModel.state.value.isUnlocked ||
            viewModel.state.value.isLoading
        ) return
        val requestId = beginAuthenticationRequest()
        unlocker.authenticate(
            authenticators = viewModel.state.value.settings.unlockMode.authenticators,
            onUnlocked = { passphrase ->
                if (finishAuthenticationRequest(requestId)) viewModel.unlock(passphrase)
                else passphrase.fill(0)
            },
            onError = { message ->
                if (finishAuthenticationRequest(requestId)) viewModel.showError(message)
            },
            onCancelled = {
                finishAuthenticationRequest(requestId)
            },
        )
    }

    private fun biometricPreferredAvailabilityError(): String? = unlocker.availabilityError(
        BiometricManager.Authenticators.BIOMETRIC_STRONG,
    )

    private fun requestBiometricPreferredActivation() {
        if (authenticationInProgress || !viewModel.state.value.isUnlocked) return
        biometricPreferredAvailabilityError()?.let {
            viewModel.showError(it)
            return
        }
        val requestId = beginAuthenticationRequest()
        unlocker.authenticate(
            authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
            title = "Biometrie bestätigen",
            onUnlocked = { passphrase ->
                passphrase.fill(0)
                if (finishAuthenticationRequest(requestId)) {
                    val current = viewModel.state.value.settings.unlockMode
                    val updated = UnlockModeTransitions.afterBiometricActivation(
                        current = current,
                        authenticationSucceeded = true,
                    )
                    if (updated != current) {
                        viewModel.updateSettings { it.copy(unlockMode = updated) }
                        viewModel.showMessage("„Biometrie bevorzugt“ wurde aktiviert.")
                    }
                }
            },
            onError = { message ->
                if (finishAuthenticationRequest(requestId)) viewModel.showError(message)
            },
            onCancelled = {
                finishAuthenticationRequest(requestId)
            },
        )
    }

    private fun requestRecoveryUnlock() {
        if (
            authenticationInProgress || viewModel.state.value.isUnlocked ||
            viewModel.state.value.isLoading ||
            viewModel.state.value.settings.unlockMode != UnlockMode.BIOMETRIC_ONLY
        ) return
        val requestId = beginAuthenticationRequest()
        unlocker.authenticate(
            authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            title = "Notfallzugang bestätigen",
            onUnlocked = { passphrase ->
                if (!authenticationRequests.isCurrent(requestId)) {
                    passphrase.fill(0)
                    return@authenticate
                }
                authenticationInProgress = false
                viewModel.unlock(passphrase) { successful ->
                    if (!finishAuthenticationRequest(requestId)) return@unlock
                    val current = viewModel.state.value.settings.unlockMode
                    val updated = UnlockModeTransitions.afterRecovery(
                        current = current,
                        authenticationSucceeded = true,
                        databaseOpened = successful,
                    )
                    if (updated != current) {
                        viewModel.updateSettings { it.copy(unlockMode = updated) }
                        viewModel.showMessage(
                            "Notfallzugang erfolgreich. Künftig ist auch die Gerätesperre verfügbar.",
                        )
                    }
                }
            },
            onError = { message ->
                if (finishAuthenticationRequest(requestId)) viewModel.showError(message)
            },
            onCancelled = {
                finishAuthenticationRequest(requestId)
            },
        )
    }

    private fun beginAuthenticationRequest(): Long {
        authenticationInProgress = true
        return authenticationRequests.begin()
    }

    private fun finishAuthenticationRequest(requestId: Long): Boolean {
        if (!authenticationRequests.claimTerminal(requestId)) return false
        authenticationInProgress = false
        return true
    }
}
