package de.finanzcockpit.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.finanzcockpit.app.FinanceUiState
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.PendingImportKind
import de.finanzcockpit.app.R
import de.finanzcockpit.app.UnlockMode
import de.finanzcockpit.app.backup.FinanceJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Composable
fun FinanzCockpitRoot(
    viewModel: FinanceViewModel,
    requestUnlock: () -> Unit,
    requestBiometricPreferredActivation: () -> Unit,
    biometricPreferredAvailabilityError: () -> String?,
    requestRecoveryUnlock: () -> Unit,
    lockNow: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = viewModel.consumePendingExport()
        if (uri == null || bytes == null) {
            bytes?.fill(0)
            viewModel.showMessage("Export abgebrochen")
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) { writeBytes(context, uri, bytes) }
                bytes.fill(0)
                result.fold(
                    onSuccess = {
                        viewModel.markBackupSaved()
                        viewModel.showMessage("Verschlüsselte Sicherung gespeichert")
                    },
                    onFailure = { viewModel.showError("Sicherung konnte nicht geschrieben werden.") },
                )
            }
        }
    }

    val legacyImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readBytes(context, uri).fold(
                    onSuccess = { viewModel.queueImport(PendingImportKind.LEGACY_JSON, it) },
                    onFailure = { viewModel.showError(it.message ?: "Importdatei konnte nicht gelesen werden.") },
                )
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readBytes(context, uri).fold(
                    onSuccess = { viewModel.queueImport(PendingImportKind.ENCRYPTED_BACKUP, it) },
                    onFailure = { viewModel.showError(it.message ?: "Sicherung konnte nicht gelesen werden.") },
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isUnlocked) {
            AppSettingsProvider(state.settings) {
                FinanceApp(
                    state = state,
                    viewModel = viewModel,
                    lockNow = lockNow,
                    requestBiometricPreferredActivation = requestBiometricPreferredActivation,
                    biometricPreferredAvailabilityError = biometricPreferredAvailabilityError,
                    requestBackup = { showExportPassword = true },
                    requestLegacyImport = { legacyImportLauncher.launch(arrayOf("application/json", "text/json")) },
                    requestRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                )
            }
        } else {
            LockedScreen(
                loading = state.isLoading,
                error = state.error,
                unlockMode = state.settings.unlockMode,
                onUnlock = requestUnlock,
                onRecoveryUnlock = requestRecoveryUnlock,
            )
        }

        if (state.isLoading && state.isUnlocked) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showExportPassword) {
        ExportPasswordDialog(
            onDismiss = { showExportPassword = false },
            onConfirm = { password ->
                showExportPassword = false
                scope.launch {
                    val result = viewModel.createEncryptedBackup(password)
                    result.fold(
                        onSuccess = {
                            exportLauncher.launch("FinanzCockpit-${LocalDate.now()}.fcbak")
                        },
                        onFailure = {
                            viewModel.showError(it.message ?: "Sicherung konnte nicht erstellt werden.")
                        },
                    )
                }
            },
        )
    }

    if (state.isUnlocked && state.pendingImportKind == PendingImportKind.LEGACY_JSON) {
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingImport,
            title = { Text("Alte JSON-Daten übernehmen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Die Datei wurde vollständig geprüft.")
                    state.pendingImportSummary?.let { summary ->
                        Text(
                            "Gefunden: ${summary.plans} Positionen, " +
                                "${summary.bookings} Buchungen, ${summary.categories} Kategorien " +
                                "und ${summary.budgets} Budgets.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text("Erst nach deiner Bestätigung werden die aktuellen App-Daten ersetzt.")
                }
            },
            confirmButton = {
                Button(onClick = viewModel::importLegacy) { Text("Prüfen und übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingImport) { Text("Abbrechen") }
            },
        )
    }

    if (
        state.isUnlocked &&
        state.onboardingRequired &&
        state.pendingImportKind == null &&
        !showRestorePassword
    ) {
        WelcomeDialog(
            onLegacyImport = {
                legacyImportLauncher.launch(arrayOf("application/json", "text/json"))
            },
            onRestore = {
                restoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onStartEmpty = viewModel::completeOnboarding,
        )
    }

    LaunchedEffect(state.isUnlocked, state.pendingImportKind, state.pendingImportSummary) {
        if (
            state.isUnlocked &&
            state.pendingImportKind == PendingImportKind.ENCRYPTED_BACKUP &&
            state.pendingImportSummary == null
        ) {
            showRestorePassword = true
        }
    }

    if (showRestorePassword && state.pendingImportKind == PendingImportKind.ENCRYPTED_BACKUP) {
        RestorePasswordDialog(
            onDismiss = {
                showRestorePassword = false
                viewModel.cancelPendingImport()
            },
            onConfirm = { password ->
                showRestorePassword = false
                viewModel.prepareEncryptedRestore(password)
            },
        )
    }

    if (
        state.isUnlocked &&
        state.pendingImportKind == PendingImportKind.ENCRYPTED_BACKUP &&
        state.pendingImportSummary != null &&
        !showRestorePassword
    ) {
        val summary = checkNotNull(state.pendingImportSummary)
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingImport,
            title = { Text("Sicherung wiederherstellen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Die verschlüsselte Sicherung wurde erfolgreich geprüft.")
                    Text(
                        "Enthalten: ${summary.plans} Positionen, ${summary.bookings} Buchungen, " +
                            "${summary.categories} Kategorien und ${summary.budgets} Budgets.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("Beim Wiederherstellen werden die derzeitigen Finanzdaten vollständig ersetzt.")
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmEncryptedRestore) {
                    Text("Daten ersetzen")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingImport) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun LockedScreen(
    loading: Boolean,
    error: String?,
    unlockMode: UnlockMode,
    onUnlock: () -> Unit,
    onRecoveryUnlock: () -> Unit,
) {
    var logoVisible by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.94f,
        animationSpec = tween(320),
        label = "Logo",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(260),
        label = "Logo einblenden",
    )
    LaunchedEffect(Unit) { logoVisible = true }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.finanzcockpit_logo),
                contentDescription = "FinanzCockpit-Logo",
                modifier = Modifier.size(190.dp).graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                },
            )
            Text("FinanzCockpit", style = MaterialTheme.typography.headlineMedium)
            Text("Deine Finanzdaten sind gesperrt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onUnlock) {
                    Text(
                        when (unlockMode) {
                            UnlockMode.BIOMETRIC_ONLY -> "Mit Biometrie entsperren"
                            UnlockMode.BIOMETRIC_OR_PIN -> "Mit Biometrie oder Gerätesperre entsperren"
                            UnlockMode.DEVICE_PIN -> "Mit Gerätesperre entsperren"
                        },
                    )
                }
                if (unlockMode == UnlockMode.BIOMETRIC_ONLY) {
                    OutlinedButton(onClick = { confirmRecovery = true }) {
                        Text("Notfallzugang mit Gerätesperre")
                    }
                }
            }
        }
    }
    if (confirmRecovery) {
        AlertDialog(
            onDismissRequest = { confirmRecovery = false },
            title = { Text("Notfallzugang verwenden?") },
            text = {
                Text(
                    "Du verwendest dafür die Geräte-PIN, das Muster oder das Passwort. " +
                        "Nach erfolgreicher Entsperrung wird der Modus auf „Biometrie oder " +
                        "Gerätesperre“ zurückgestellt.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRecovery = false
                        onRecoveryUnlock()
                    },
                ) { Text("Mit Gerätesperre fortfahren") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRecovery = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun WelcomeDialog(
    onLegacyImport: () -> Unit,
    onRestore: () -> Unit,
    onStartEmpty: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Willkommen im FinanzCockpit") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.finanzcockpit_logo),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp),
                )
                Text(
                    "Persönliche Finanzwerte sind aus Sicherheitsgründen nicht in der APK gespeichert. " +
                        "Du kannst sie jetzt lokal übernehmen.",
                )
                Button(onClick = onLegacyImport, modifier = Modifier.fillMaxWidth()) {
                    Text("Alte JSON-Daten übernehmen")
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text("Verschlüsselte Sicherung öffnen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStartEmpty) { Text("Leer beginnen") }
        },
    )
}

@Composable
private fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val valid = password.length >= 12 && password == repeated
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sicherung verschlüsseln") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Das Passwort wird nicht gespeichert. Ohne dieses Passwort ist die Sicherung nicht wiederherstellbar.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort · mindestens 12 Zeichen") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = { Text("Passwort wiederholen") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onConfirm(password.toCharArray()) }) {
                Text("Sicherung erstellen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun RestorePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sicherung entsperren") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Sicherungspasswort") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(enabled = password.isNotBlank(), onClick = { onConfirm(password.toCharArray()) }) {
                Text("Sicherung prüfen")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private suspend fun readBytes(context: Context, uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val limit = FinanceJsonCodec.MAX_FILE_BYTES + 1
            val output = ByteArrayOutputStream(minOf(32 * 1024, limit))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < limit) {
                val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray().also {
                require(it.size <= FinanceJsonCodec.MAX_FILE_BYTES) { "Datei ist zu groß." }
            }
        } ?: error("Datei konnte nicht geöffnet werden.")
    }
}

private fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
        output.write(bytes)
        output.flush()
    } ?: error("Zieldatei konnte nicht geöffnet werden.")
}
