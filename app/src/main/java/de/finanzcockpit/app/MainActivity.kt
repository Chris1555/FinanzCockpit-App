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
import de.finanzcockpit.app.security.BiometricUnlocker
import de.finanzcockpit.app.security.SecureKeyStore
import de.finanzcockpit.app.ui.FinanzCockpitRoot
import de.finanzcockpit.app.ui.theme.FinanzCockpitTheme

class MainActivity : FragmentActivity() {
    private val viewModel: FinanceViewModel by viewModels()
    private lateinit var unlocker: BiometricUnlocker
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
        if (!isChangingConfigurations) {
            authenticationInProgress = false
            unlocker.cancel()
            val delay = viewModel.state.value.settings.lockDelay.milliseconds
            if (delay == 0L) viewModel.lock() else lockHandler.postDelayed(delayedLock, delay)
        }
        super.onStop()
    }

    private fun requestUnlock() {
        if (authenticationInProgress || viewModel.state.value.isUnlocked) return
        authenticationInProgress = true
        unlocker.authenticate(
            authenticators = viewModel.state.value.settings.unlockMode.authenticators,
            onUnlocked = { passphrase ->
                authenticationInProgress = false
                viewModel.unlock(passphrase)
            },
            onError = { message ->
                authenticationInProgress = false
                viewModel.showError(message)
            },
            onCancelled = {
                authenticationInProgress = false
            },
        )
    }
}
