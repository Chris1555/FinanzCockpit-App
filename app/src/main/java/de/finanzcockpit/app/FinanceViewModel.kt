package de.finanzcockpit.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.finanzcockpit.app.backup.FinanceJsonCodec
import de.finanzcockpit.app.data.AppDatabase
import de.finanzcockpit.app.data.FinanceRepository
import de.finanzcockpit.app.domain.Booking
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.security.BackupCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

enum class PendingImportKind { LEGACY_JSON, ENCRYPTED_BACKUP }

data class ImportSummary(
    val plans: Int,
    val bookings: Int,
    val categories: Int,
    val budgets: Int,
)

data class FinanceUiState(
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = false,
    val snapshot: FinanceSnapshot = FinanceSnapshot(),
    val message: String? = null,
    val error: String? = null,
    val pendingImportKind: PendingImportKind? = null,
    val pendingImportSummary: ImportSummary? = null,
    val onboardingRequired: Boolean = false,
    val pendingUndo: DeletedItem? = null,
    val lastBackupDate: String? = null,
    val settings: AppSettings = AppSettings(),
)

sealed interface DeletedItem {
    data class Plan(val value: FinancePlan) : DeletedItem
    data class BookingItem(val value: Booking) : DeletedItem
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        FinanceUiState(
            lastBackupDate = preferences.getString(LAST_BACKUP_DATE, null),
            settings = loadSettings(preferences),
        ),
    )
    val state: StateFlow<FinanceUiState> = _state.asStateFlow()

    private var repository: FinanceRepository? = null
    private var observation: Job? = null
    private var generation = 0L
    private var pendingImportBytes: ByteArray? = null
    private var pendingExportBytes: ByteArray? = null
    private var pendingRestoreSnapshot: FinanceSnapshot? = null

    fun unlock(passphrase: ByteArray) {
        val requestGeneration = ++generation
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            var newRepository: FinanceRepository? = null
            try {
                var initialSnapshot = FinanceSnapshot()
                newRepository = withContext(Dispatchers.IO) {
                    FinanceRepository(AppDatabase.open(getApplication(), passphrase)).also {
                        initialSnapshot = it.currentSnapshot() // Öffnet und prüft SQLCipher vor der UI-Freigabe.
                        if (
                            initialSnapshot.plans.isEmpty() && initialSnapshot.bookings.isEmpty() &&
                            initialSnapshot.categories.isEmpty() && initialSnapshot.budgets.isEmpty() &&
                            preferences.getInt(BUNDLED_SEED_VERSION, 0) < CURRENT_BUNDLED_SEED_VERSION
                        ) {
                            readBundledInitialSnapshot()?.let { bundled ->
                                it.replaceAll(bundled)
                                initialSnapshot = it.currentSnapshot()
                                preferences.edit {
                                    putInt(BUNDLED_SEED_VERSION, CURRENT_BUNDLED_SEED_VERSION)
                                    putBoolean(ONBOARDING_COMPLETE, true)
                                }
                            }
                        }
                    }
                }
                if (generation != requestGeneration) {
                    withContext(Dispatchers.IO) { newRepository.close() }
                    return@launch
                }
                repository = newRepository
                val containsData = initialSnapshot.plans.isNotEmpty() || initialSnapshot.bookings.isNotEmpty()
                if (containsData) preferences.edit { putBoolean(ONBOARDING_COMPLETE, true) }
                _state.update {
                    it.copy(
                        isUnlocked = true,
                        isLoading = false,
                        error = null,
                        onboardingRequired = !containsData &&
                            !preferences.getBoolean(ONBOARDING_COMPLETE, false),
                    )
                }
                observation?.cancel()
                observation = viewModelScope.launch {
                    newRepository.snapshot
                        .catch { error ->
                            _state.update { it.copy(error = error.safeMessage()) }
                        }
                        .collect { snapshot ->
                            if (generation == requestGeneration) {
                                _state.update { it.copy(snapshot = snapshot, isLoading = false) }
                            }
                        }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.IO) { newRepository?.close() }
                if (generation == requestGeneration) {
                    _state.update {
                        it.copy(
                            isUnlocked = false,
                            isLoading = false,
                            error = "Die verschlüsselte Datenbank konnte nicht geöffnet werden.",
                        )
                    }
                }
            } finally {
                passphrase.fill(0)
            }
        }
    }

    fun lock() {
        generation += 1
        observation?.cancel()
        observation = null
        pendingImportBytes?.fill(0)
        pendingImportBytes = null
        pendingRestoreSnapshot = null
        val oldRepository = repository
        repository = null
        _state.update {
            it.copy(
                isUnlocked = false,
                isLoading = false,
                snapshot = FinanceSnapshot(),
                message = null,
                pendingImportKind = null,
                pendingImportSummary = null,
                pendingUndo = null,
            )
        }
        if (oldRepository != null) {
            viewModelScope.launch(Dispatchers.IO) { oldRepository.close() }
        }
    }

    fun savePlan(plan: FinancePlan) = perform("Position gespeichert") { savePlan(plan) }

    fun deletePlan(plan: FinancePlan) = deleteWithUndo(
        successMessage = "Position gelöscht",
        deletedItem = DeletedItem.Plan(plan),
    ) { deletePlan(plan.id) }

    fun saveBooking(booking: Booking) = perform("Buchung gespeichert") { saveBooking(booking) }

    fun deleteBooking(booking: Booking) = deleteWithUndo(
        successMessage = "Buchung gelöscht",
        deletedItem = DeletedItem.BookingItem(booking),
    ) { deleteBooking(booking.id) }

    fun saveBudget(budget: CategoryBudget) = perform("Budget gespeichert") { saveBudget(budget) }

    fun deleteBudget(category: String) = perform("Budget entfernt") { deleteBudget(category) }

    fun addCategory(name: String) = perform("Kategorie hinzugefügt") { addCategory(name) }

    fun renameCategory(oldName: String, newName: String) =
        perform("Kategorie umbenannt") { renameCategory(oldName, newName) }

    fun deleteCategory(name: String) = perform("Kategorie gelöscht") { deleteCategory(name) }

    fun resetData() = perform("Alle Finanzdaten wurden gelöscht") {
        replaceAll(FinanceSnapshot())
    }

    suspend fun createEncryptedBackup(password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = requireRepository()
            val json = FinanceJsonCodec.encodeBackup(repo.currentSnapshot())
            try {
                pendingExportBytes?.fill(0)
                pendingExportBytes = BackupCrypto.encrypt(json, password)
            } finally {
                json.fill(0)
                password.fill('\u0000')
            }
        }
    }

    fun consumePendingExport(): ByteArray? = pendingExportBytes.also { pendingExportBytes = null }

    fun queueImport(kind: PendingImportKind, bytes: ByteArray) {
        pendingImportBytes?.fill(0)
        pendingImportBytes = null
        if (kind == PendingImportKind.LEGACY_JSON) {
            _state.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val preview = FinanceJsonCodec.parseLegacy(bytes)
                    pendingImportBytes = bytes
                    _state.update {
                        it.copy(
                            isLoading = false,
                            pendingImportKind = kind,
                            pendingImportSummary = ImportSummary(
                                preview.plans.size,
                                preview.bookings.size,
                                preview.categories.size,
                                preview.budgets.size,
                            ),
                        )
                    }
                } catch (error: Exception) {
                    bytes.fill(0)
                    _state.update { it.copy(isLoading = false, error = error.safeMessage()) }
                }
            }
        } else {
            pendingImportBytes = bytes
            _state.update {
                it.copy(pendingImportKind = kind, pendingImportSummary = null, error = null)
            }
        }
    }

    fun cancelPendingImport() {
        pendingImportBytes?.fill(0)
        pendingImportBytes = null
        pendingRestoreSnapshot = null
        _state.update { it.copy(pendingImportKind = null, pendingImportSummary = null) }
    }

    fun importLegacy() {
        val bytes = pendingImportBytes ?: return
        pendingImportBytes = null
        _state.update { it.copy(pendingImportKind = null, pendingImportSummary = null, isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = FinanceJsonCodec.parseLegacy(bytes)
                requireRepository().replaceAll(snapshot)
                markOnboardingComplete()
                _state.update {
                    it.copy(
                        isLoading = false,
                        onboardingRequired = false,
                        message = "Alte Daten wurden sicher übernommen",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = error.safeMessage()) }
            } finally {
                bytes.fill(0)
            }
        }
    }

    fun prepareEncryptedRestore(password: CharArray) {
        val container = pendingImportBytes ?: run {
            password.fill('\u0000')
            return
        }
        pendingImportBytes = null
        pendingRestoreSnapshot = null
        _state.update { it.copy(pendingImportSummary = null, isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            var json: ByteArray? = null
            try {
                json = BackupCrypto.decrypt(container, password)
                val snapshot = FinanceJsonCodec.parseBackup(json)
                pendingRestoreSnapshot = snapshot
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingImportKind = PendingImportKind.ENCRYPTED_BACKUP,
                        pendingImportSummary = ImportSummary(
                            snapshot.plans.size,
                            snapshot.bookings.size,
                            snapshot.categories.size,
                            snapshot.budgets.size,
                        ),
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        pendingImportKind = null,
                        pendingImportSummary = null,
                        error = error.safeMessage(),
                    )
                }
            } finally {
                json?.fill(0)
                container.fill(0)
                password.fill('\u0000')
            }
        }
    }

    fun confirmEncryptedRestore() {
        val snapshot = pendingRestoreSnapshot ?: return
        pendingRestoreSnapshot = null
        _state.update { it.copy(pendingImportKind = null, pendingImportSummary = null, isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                requireRepository().replaceAll(snapshot)
                markOnboardingComplete()
                _state.update {
                    it.copy(
                        isLoading = false,
                        onboardingRequired = false,
                        message = "Sicherung wiederhergestellt",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = error.safeMessage()) }
            }
        }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun consumeNotice() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun undoLastDeletion() {
        val deleted = _state.value.pendingUndo ?: return
        _state.update { it.copy(pendingUndo = null) }
        when (deleted) {
            is DeletedItem.Plan -> perform("Position wiederhergestellt") { savePlan(deleted.value) }
            is DeletedItem.BookingItem -> perform("Buchung wiederhergestellt") {
                saveBooking(deleted.value)
            }
        }
    }

    fun discardUndo() {
        _state.update { it.copy(pendingUndo = null) }
    }

    fun markBackupSaved() {
        val date = LocalDate.now().toString()
        preferences.edit { putString(LAST_BACKUP_DATE, date) }
        _state.update { it.copy(lastBackupDate = date) }
    }

    fun completeOnboarding() {
        markOnboardingComplete()
        _state.update { it.copy(onboardingRequired = false) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_state.value.settings)
        saveSettings(preferences, updated)
        _state.update { it.copy(settings = updated) }
    }

    fun setLastOpenedMonth(value: String) {
        if (_state.value.settings.lastOpenedMonth == value) return
        updateSettings { it.copy(lastOpenedMonth = value) }
    }

    private fun markOnboardingComplete() {
        preferences.edit { putBoolean(ONBOARDING_COMPLETE, true) }
    }

    private fun readBundledInitialSnapshot(): FinanceSnapshot? {
        val bytes = try {
            getApplication<Application>().assets.open(BUNDLED_INITIAL_DATA_FILE).use { input ->
                input.readBytes().also {
                    require(it.size <= FinanceJsonCodec.MAX_FILE_BYTES) { "Eingebaute Ausgangsdaten sind zu groß." }
                }
            }
        } catch (_: FileNotFoundException) {
            return null
        }
        return try {
            FinanceJsonCodec.parseLegacy(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun perform(successMessage: String, action: suspend FinanceRepository.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                requireRepository().action()
                _state.update { it.copy(message = successMessage) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.safeMessage()) }
            }
        }
    }

    private fun deleteWithUndo(
        successMessage: String,
        deletedItem: DeletedItem,
        action: suspend FinanceRepository.() -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                requireRepository().action()
                _state.update { it.copy(message = successMessage, pendingUndo = deletedItem) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.safeMessage(), pendingUndo = null) }
            }
        }
    }

    private fun requireRepository(): FinanceRepository = repository
        ?: throw IllegalStateException("Die App ist gesperrt.")

    override fun onCleared() {
        pendingImportBytes?.fill(0)
        pendingExportBytes?.fill(0)
        pendingRestoreSnapshot = null
        repository?.close()
        repository = null
    }

    private companion object {
        const val PREFERENCES_NAME = "finanzcockpit_preferences"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val LAST_BACKUP_DATE = "last_backup_date"
        const val BUNDLED_SEED_VERSION = "bundled_seed_version"
        const val CURRENT_BUNDLED_SEED_VERSION = 1
        const val BUNDLED_INITIAL_DATA_FILE = "initial_finance_data.json"
    }
}

private fun loadSettings(preferences: android.content.SharedPreferences): AppSettings = AppSettings(
    themeMode = preferences.enumValue("setting_theme", ThemeMode.SYSTEM),
    amountsHidden = preferences.getBoolean("setting_amounts_hidden", false),
    startPage = preferences.enumValue("setting_start_page", StartPage.OVERVIEW),
    startMonthMode = preferences.enumValue("setting_start_month", StartMonthMode.CURRENT),
    lastOpenedMonth = preferences.getString("setting_last_month", null),
    numberFormat = preferences.enumValue("setting_number_format", NumberFormatMode.GERMAN_SUFFIX),
    vibration = preferences.enumValue("setting_vibration", VibrationMode.SUBTLE),
    animations = preferences.enumValue("setting_animations", AnimationMode.NORMAL),
    budgetWarning = preferences.enumValue("setting_budget_warning", BudgetWarningMode.AT_80),
    remindersEnabled = preferences.getBoolean("setting_reminders", true),
    monthlyReviewEnabled = preferences.getBoolean("setting_monthly_review", true),
    lastMonthlyReview = preferences.getString("setting_last_monthly_review", null),
    quickBookingCategory = preferences.getString("setting_quick_category", "").orEmpty(),
    quickBookingType = preferences.enumValue("setting_quick_type", de.finanzcockpit.app.domain.BookingType.EXPENSE),
    lockDelay = preferences.enumValue("setting_lock_delay", LockDelay.IMMEDIATE),
    unlockMode = preferences.enumValue("setting_unlock_mode", UnlockMode.BIOMETRIC_OR_PIN),
    backupReminderEnabled = preferences.getBoolean("setting_backup_reminder", true),
)

private fun saveSettings(preferences: android.content.SharedPreferences, settings: AppSettings) {
    preferences.edit {
        putString("setting_theme", settings.themeMode.name)
        putBoolean("setting_amounts_hidden", settings.amountsHidden)
        putString("setting_start_page", settings.startPage.name)
        putString("setting_start_month", settings.startMonthMode.name)
        putString("setting_last_month", settings.lastOpenedMonth)
        putString("setting_number_format", settings.numberFormat.name)
        putString("setting_vibration", settings.vibration.name)
        putString("setting_animations", settings.animations.name)
        putString("setting_budget_warning", settings.budgetWarning.name)
        putBoolean("setting_reminders", settings.remindersEnabled)
        putBoolean("setting_monthly_review", settings.monthlyReviewEnabled)
        putString("setting_last_monthly_review", settings.lastMonthlyReview)
        putString("setting_quick_category", settings.quickBookingCategory)
        putString("setting_quick_type", settings.quickBookingType.name)
        putString("setting_lock_delay", settings.lockDelay.name)
        putString("setting_unlock_mode", settings.unlockMode.name)
        putBoolean("setting_backup_reminder", settings.backupReminderEnabled)
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    fallback: T,
): T = getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private fun Throwable.safeMessage(): String = when (this) {
    is IllegalArgumentException, is IllegalStateException -> message ?: "Vorgang abgebrochen."
    else -> "Der Vorgang konnte nicht abgeschlossen werden."
}
