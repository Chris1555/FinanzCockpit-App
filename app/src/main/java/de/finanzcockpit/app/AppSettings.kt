package de.finanzcockpit.app

import androidx.biometric.BiometricManager
import de.finanzcockpit.app.domain.BookingType

enum class ThemeMode(val label: String) {
    LIGHT("Hell"),
    DARK("Dunkel"),
    SYSTEM("Systemdesign"),
}

enum class StartPage(val label: String) {
    OVERVIEW("Übersicht"),
    BOOKINGS("Buchungen"),
    ANALYSIS("Analyse"),
}

enum class StartMonthMode(val label: String) {
    CURRENT("Immer aktueller Monat"),
    LAST_OPENED("Zuletzt geöffneter Monat"),
}

enum class NumberFormatMode(val label: String) {
    GERMAN_SUFFIX("1.234,56 €"),
    EURO_PREFIX("€ 1.234,56"),
}

enum class VibrationMode(val label: String) {
    OFF("Aus"),
    SUBTLE("Dezent"),
    STRONG("Deutlich"),
}

enum class AnimationMode(val label: String) {
    NORMAL("Normal"),
    REDUCED("Reduziert"),
}

enum class BudgetWarningMode(val label: String, val threshold: Float?) {
    OFF("Aus", null),
    AT_80("Bei 80 %", 0.8f),
    AT_90("Bei 90 %", 0.9f),
    EXCEEDED("Erst bei Überschreitung", 1f),
}

enum class LockDelay(val label: String, val milliseconds: Long) {
    IMMEDIATE("Sofort", 0L),
    ONE_MINUTE("Nach 1 Minute", 60_000L),
    FIVE_MINUTES("Nach 5 Minuten", 5 * 60_000L),
    FIFTEEN_MINUTES("Nach 15 Minuten", 15 * 60_000L),
}

enum class UnlockMode(val label: String, val authenticators: Int) {
    BIOMETRIC_OR_PIN(
        "Biometrie oder Geräte-PIN",
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    ),
    BIOMETRIC_ONLY("Nur Biometrie", BiometricManager.Authenticators.BIOMETRIC_STRONG),
    DEVICE_PIN("Nur Geräte-PIN", BiometricManager.Authenticators.DEVICE_CREDENTIAL),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amountsHidden: Boolean = false,
    val startPage: StartPage = StartPage.OVERVIEW,
    val startMonthMode: StartMonthMode = StartMonthMode.CURRENT,
    val lastOpenedMonth: String? = null,
    val currency: String = "EUR",
    val numberFormat: NumberFormatMode = NumberFormatMode.GERMAN_SUFFIX,
    val vibration: VibrationMode = VibrationMode.SUBTLE,
    val animations: AnimationMode = AnimationMode.NORMAL,
    val budgetWarning: BudgetWarningMode = BudgetWarningMode.AT_80,
    val remindersEnabled: Boolean = true,
    val monthlyReviewEnabled: Boolean = true,
    val lastMonthlyReview: String? = null,
    val quickBookingCategory: String = "",
    val quickBookingType: BookingType = BookingType.EXPENSE,
    val lockDelay: LockDelay = LockDelay.IMMEDIATE,
    val unlockMode: UnlockMode = UnlockMode.BIOMETRIC_OR_PIN,
    val backupReminderEnabled: Boolean = true,
)
