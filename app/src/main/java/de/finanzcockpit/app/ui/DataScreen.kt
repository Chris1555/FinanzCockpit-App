package de.finanzcockpit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.AnimationMode
import de.finanzcockpit.app.AppSettings
import de.finanzcockpit.app.BudgetWarningMode
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.LockDelay
import de.finanzcockpit.app.NumberFormatMode
import de.finanzcockpit.app.StartMonthMode
import de.finanzcockpit.app.StartPage
import de.finanzcockpit.app.ThemeMode
import de.finanzcockpit.app.UnlockMode
import de.finanzcockpit.app.VibrationMode
import de.finanzcockpit.app.domain.BookingType
import de.finanzcockpit.app.domain.FinanceSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DataScreen(
    snapshot: FinanceSnapshot,
    lastBackupDate: String?,
    settings: AppSettings,
    viewModel: FinanceViewModel,
    requestBiometricPreferredActivation: () -> Unit,
    biometricPreferredAvailabilityError: () -> String?,
    requestBackup: () -> Unit,
    requestLegacyImport: () -> Unit,
    requestRestore: () -> Unit,
) {
    var advancedSettingsVisible by remember { mutableStateOf(false) }
    var securityDetailsVisible by remember { mutableStateOf(false) }
    var dataOptionsVisible by remember { mutableStateOf(false) }
    var categoriesVisible by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmBiometricPreferred by remember { mutableStateOf(false) }
    val haptic = rememberAppHaptic()

    fun update(transform: (AppSettings) -> AppSettings) = viewModel.updateSettings(transform)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionTitle(
                "Einstellungen",
                "Richte das FinanzCockpit so ein, wie es für dich angenehm ist.",
            )
        }
        item {
            SettingsCard("Darstellung") {
                SelectorField(
                    "Design",
                    settings.themeMode,
                    ThemeMode.entries,
                    ThemeMode::label,
                    { update { current -> current.copy(themeMode = it) } },
                )
                SettingSwitch(
                    title = "Beträge verbergen",
                    subtitle = "Geldwerte werden in der gesamten App unkenntlich gemacht.",
                    checked = settings.amountsHidden,
                    onCheckedChange = { hidden -> update { it.copy(amountsHidden = hidden) } },
                )
            }
        }
        item {
            SettingsCard("Beim Öffnen") {
                SelectorField(
                    "Startseite",
                    settings.startPage,
                    StartPage.entries,
                    StartPage::label,
                    { update { current -> current.copy(startPage = it) } },
                )
                SelectorField(
                    "Startmonat",
                    settings.startMonthMode,
                    StartMonthMode.entries,
                    StartMonthMode::label,
                    { update { current -> current.copy(startMonthMode = it) } },
                )
            }
        }
        item {
            SettingsCard("Bedienung & Hinweise") {
                SelectorField(
                    "Vibration",
                    settings.vibration,
                    VibrationMode.entries,
                    VibrationMode::label,
                    {
                        update { current -> current.copy(vibration = it) }
                        if (it != VibrationMode.OFF) haptic(it == VibrationMode.STRONG)
                    },
                )
                SelectorField(
                    "Animationen",
                    settings.animations,
                    AnimationMode.entries,
                    AnimationMode::label,
                    { update { current -> current.copy(animations = it) } },
                )
                SelectorField(
                    "Budgetwarnungen",
                    settings.budgetWarning,
                    BudgetWarningMode.entries,
                    BudgetWarningMode::label,
                    { update { current -> current.copy(budgetWarning = it) } },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { advancedSettingsVisible = !advancedSettingsVisible },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (advancedSettingsVisible) "Erweiterte Einstellungen ausblenden" else "Erweitert")
            }
        }
        item {
            AnimatedVisibility(
                visible = advancedSettingsVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsCard("Beträge & Schnellbuchung") {
                        ReadOnlySetting("Währung", "Euro (EUR)")
                        SelectorField(
                            "Zahlenformat",
                            settings.numberFormat,
                            NumberFormatMode.entries,
                            NumberFormatMode::label,
                            { update { current -> current.copy(numberFormat = it) } },
                        )
                        SelectorField(
                            "Standardart",
                            settings.quickBookingType,
                            BookingType.entries,
                            BookingType::label,
                            { update { current -> current.copy(quickBookingType = it) } },
                        )
                        SelectorField(
                            "Standardkategorie",
                            settings.quickBookingCategory,
                            listOf("") + snapshot.categories,
                            { value -> value.ifBlank { "Keine Vorgabe" } },
                            { update { current -> current.copy(quickBookingCategory = it) } },
                        )
                    }
                    SettingsCard("Erinnerungen") {
                        SettingSwitch(
                            "Zahlungs- und Budgethinweise",
                            "Hinweise werden im Bereich „Heute wichtig“ angezeigt.",
                            settings.remindersEnabled,
                            { enabled -> update { it.copy(remindersEnabled = enabled) } },
                        )
                        SettingSwitch(
                            "Monatsrückblick",
                            "Am letzten Tag des Monats automatisch die Auswertung öffnen.",
                            settings.monthlyReviewEnabled,
                            { enabled -> update { it.copy(monthlyReviewEnabled = enabled) } },
                        )
                        SettingSwitch(
                            "Sicherungserinnerung",
                            "Hinweis anzeigen, wenn die letzte Sicherung länger zurückliegt.",
                            settings.backupReminderEnabled,
                            { enabled -> update { it.copy(backupReminderEnabled = enabled) } },
                        )
                    }
                    SettingsCard("Datenschutz") {
                        SelectorField(
                            "App sperren",
                            settings.lockDelay,
                            LockDelay.entries,
                            LockDelay::label,
                            { update { current -> current.copy(lockDelay = it) } },
                        )
                        SelectorField(
                            "Entsperren",
                            settings.unlockMode,
                            UnlockMode.entries,
                            UnlockMode::label,
                            { selected ->
                                if (selected == settings.unlockMode) return@SelectorField
                                if (selected == UnlockMode.BIOMETRIC_ONLY) {
                                    biometricPreferredAvailabilityError()?.let(viewModel::showError)
                                        ?: run { confirmBiometricPreferred = true }
                                } else {
                                    update { current -> current.copy(unlockMode = selected) }
                                }
                            },
                        )
                        TextButton(onClick = { securityDetailsVisible = !securityDetailsVisible }) {
                            Text(if (securityDetailsVisible) "Sicherheitsdetails ausblenden" else "Sicherheitsdetails anzeigen")
                        }
                        AnimatedVisibility(securityDetailsVisible) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SecurityLine("Verschlüsselte lokale Datenbank")
                                SecurityLine("Schlüssel im geschützten Android-Keystore")
                                SecurityLine("Screenshots und App-Vorschau blockiert")
                                SecurityLine("Keine Internetberechtigung")
                                SecurityLine("Android-Cloud-Backup deaktiviert")
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(
                "Daten",
                "Sicherungen und deine gespeicherten Inhalte.",
            )
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Verschlüsselte Sicherung", style = MaterialTheme.typography.titleLarge)
                    Text(
                        lastBackupDate?.let { "Letzte Sicherung: ${it.formatBackupDate()}" }
                            ?: "Bisher wurde auf diesem Gerät keine Sicherung gespeichert.",
                        color = if (lastBackupDate == null) Color(0xFFC98500) else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Bewahre das Sicherungspasswort gut auf. Ohne dieses Passwort kann die Datei nicht wiederhergestellt werden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = requestBackup, modifier = Modifier.fillMaxWidth()) {
                        Text("Sicherung erstellen")
                    }
                    OutlinedButton(onClick = requestRestore, modifier = Modifier.fillMaxWidth()) {
                        Text("Sicherung wiederherstellen")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Gespeicherte Daten", style = MaterialTheme.typography.titleLarge)
                    DataCount("Planpositionen", snapshot.plans.size)
                    DataCount("Buchungen", snapshot.bookings.size)
                    DataCount("Kategorien", snapshot.categories.size)
                    DataCount("Kategorie-Budgets", snapshot.budgets.size)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = { categoriesVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Kategorien verwalten") }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { dataOptionsVisible = !dataOptionsVisible },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (dataOptionsVisible) "Weitere Datenoptionen ausblenden" else "Weitere Datenoptionen")
            }
        }
        item {
            AnimatedVisibility(
                visible = dataOptionsVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Umstieg aus der HTML-App", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Eine alte JSON-Sicherung wird vor dem Ersetzen vollständig geprüft.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = requestLegacyImport, modifier = Modifier.fillMaxWidth()) {
                                Text("Alte JSON-Sicherung importieren")
                            }
                        }
                    }
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Alle Finanzdaten löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (categoriesVisible) {
        CategoryManagementDialog(snapshot, viewModel, onDismiss = { categoriesVisible = false })
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Alle Finanzdaten löschen?") },
            text = { Text("Dieser Vorgang kann nur über eine zuvor erstellte Sicherung rückgängig gemacht werden.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    haptic(true)
                    viewModel.resetData()
                }) { Text("Endgültig löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmBiometricPreferred) {
        AlertDialog(
            onDismissRequest = { confirmBiometricPreferred = false },
            title = { Text("„Biometrie bevorzugt“ aktivieren?") },
            text = {
                Text(
                    "Beim normalen Entsperren wird ausschließlich starke Biometrie angeboten. " +
                        "Für Notfälle bleibt auf dem Sperrbildschirm ein separat bestätigter Zugang " +
                        "über Geräte-PIN, Muster oder Passwort verfügbar. Die Einstellung wird erst " +
                        "nach erfolgreicher biometrischer Bestätigung gespeichert.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBiometricPreferred = false
                        requestBiometricPreferredActivation()
                    },
                ) { Text("Biometrie bestätigen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBiometricPreferred = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReadOnlySetting(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SecurityLine(text: String) {
    Text("✓  $text", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DataCount(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(count.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryManagementDialog(
    snapshot: FinanceSnapshot,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
) {
    var newCategory by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    val haptic = rememberAppHaptic()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorien verwalten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it.take(50) },
                        label = { Text("Neue Kategorie") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newCategory.isNotBlank(),
                        onClick = {
                            viewModel.addCategory(newCategory.trim())
                            newCategory = ""
                            haptic(false)
                        },
                    ) { Text("+") }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(snapshot.categories, key = { it }) { category ->
                        val used = snapshot.plans.any { it.category == category } ||
                            snapshot.bookings.any { it.category == category } ||
                            snapshot.budgets.any { it.category == category }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, modifier = Modifier.weight(1f).padding(top = 12.dp))
                            TextButton(onClick = { renaming = category }) { Text("Ändern") }
                            TextButton(
                                enabled = !used,
                                onClick = {
                                    haptic(true)
                                    viewModel.deleteCategory(category)
                                },
                            ) { Text("Löschen") }
                        }
                    }
                }
                Text(
                    "Verwendete Kategorien können erst gelöscht werden, wenn keine Position, " +
                        "Buchung oder kein Budget mehr darauf verweist.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Fertig") } },
    )

    renaming?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Kategorie umbenennen") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(50) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank() && newName != oldName,
                    onClick = {
                        viewModel.renameCategory(oldName, newName.trim())
                        renaming = null
                        haptic(false)
                    },
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } },
        )
    }
}

private fun String.formatBackupDate(): String = runCatching {
    LocalDate.parse(this).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
}.getOrDefault(this)
