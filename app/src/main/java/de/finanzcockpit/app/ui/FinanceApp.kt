package de.finanzcockpit.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.AnimationMode
import de.finanzcockpit.app.FinanceUiState
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.StartPage
import de.finanzcockpit.app.domain.PlanType
import java.time.LocalDate
import java.time.YearMonth

private enum class AppSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Übersicht", Icons.Outlined.Home),
    PLANNING("Planung", Icons.AutoMirrored.Outlined.EventNote),
    BOOKINGS("Buchungen", Icons.AutoMirrored.Outlined.ReceiptLong),
    MORE("Mehr", Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(
    state: FinanceUiState,
    viewModel: FinanceViewModel,
    lockNow: () -> Unit,
    requestBiometricPreferredActivation: () -> Unit,
    biometricPreferredAvailabilityError: () -> String?,
    requestBackup: () -> Unit,
    requestLegacyImport: () -> Unit,
    requestRestore: () -> Unit,
) {
    val initialSection = if (state.settings.startPage == StartPage.BOOKINGS) {
        AppSection.BOOKINGS
    } else {
        AppSection.OVERVIEW
    }
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val monthlyReviewDue = state.settings.monthlyReviewEnabled &&
        today.dayOfMonth == currentMonth.lengthOfMonth() &&
        state.settings.lastMonthlyReview != currentMonth.toString()
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var analysisOpen by rememberSaveable {
        mutableStateOf(state.settings.startPage == StartPage.ANALYSIS || monthlyReviewDue)
    }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var requestedPlanFilter by remember { mutableStateOf<PlanType?>(null) }
    var planAddRequest by remember { mutableIntStateOf(0) }
    var bookingAddRequest by remember { mutableIntStateOf(0) }
    var budgetAddRequest by remember { mutableIntStateOf(0) }
    var requestedBookingCategory by remember { mutableStateOf<String?>(null) }
    var requestedBookingMonth by remember { mutableStateOf<YearMonth?>(null) }
    var bookingFilterRequest by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberAppHaptic()
    val reducedMotion = state.settings.animations == AnimationMode.REDUCED

    LaunchedEffect(monthlyReviewDue) {
        if (monthlyReviewDue) {
            viewModel.updateSettings { it.copy(lastMonthlyReview = currentMonth.toString()) }
        }
    }

    LaunchedEffect(state.message, state.error) {
        val notice = state.error ?: state.message
        if (notice != null) {
            val undoAvailable = state.error == null && state.pendingUndo != null
            val result = snackbarHostState.showSnackbar(
                message = notice,
                actionLabel = if (undoAvailable) "Rückgängig" else null,
            )
            viewModel.consumeNotice()
            if (undoAvailable) {
                if (result == SnackbarResult.ActionPerformed) viewModel.undoLastDeletion()
                else viewModel.discardUndo()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            analysisOpen -> "Analyse"
                            else -> section.label
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.updateSettings {
                            it.copy(amountsHidden = !it.amountsHidden)
                        }
                    }) {
                        Icon(
                            if (state.settings.amountsHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (state.settings.amountsHidden) "Beträge zeigen" else "Beträge verbergen",
                        )
                    }
                    IconButton(onClick = lockNow) {
                        Icon(Icons.Outlined.Lock, contentDescription = "App sperren")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppSection.entries.forEach { item ->
                    val selected = section == item && (!analysisOpen || item == AppSection.OVERVIEW)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            plusMenuOpen = false
                            analysisOpen = false
                            section = item
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.secondary,
                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(
                    visible = plusMenuOpen,
                    enter = fadeIn(tween(if (reducedMotion) 0 else 150)) + scaleIn(),
                    exit = fadeOut(tween(if (reducedMotion) 0 else 100)) + scaleOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExtendedFloatingActionButton(
                            text = { Text("Neue Buchung") },
                            icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null) },
                            onClick = {
                                bookingAddRequest += 1
                                plusMenuOpen = false
                                analysisOpen = false
                                section = AppSection.BOOKINGS
                            },
                        )
                        ExtendedFloatingActionButton(
                            text = { Text("Neue Planung") },
                            icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                            onClick = {
                                planAddRequest += 1
                                plusMenuOpen = false
                                analysisOpen = false
                                section = AppSection.PLANNING
                            },
                        )
                        ExtendedFloatingActionButton(
                            text = { Text("Neues Budget") },
                            icon = { Icon(Icons.Outlined.Savings, contentDescription = null) },
                            onClick = {
                                budgetAddRequest += 1
                                plusMenuOpen = false
                                analysisOpen = true
                                section = AppSection.OVERVIEW
                            },
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        plusMenuOpen = !plusMenuOpen
                        haptic(false)
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = if (plusMenuOpen) "Menü schließen" else "Hinzufügen")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = section to analysisOpen,
                transitionSpec = {
                    fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                        fadeOut(tween(if (reducedMotion) 0 else 110))
                },
                label = "Bereich wechseln",
            ) { (target, showAnalysis) ->
                when {
                    showAnalysis -> AnalysisScreen(
                        snapshot = state.snapshot,
                        viewModel = viewModel,
                        amountsHidden = state.settings.amountsHidden,
                        addBudgetRequest = budgetAddRequest,
                        onBack = { analysisOpen = false },
                        onOpenBudgetBookings = { category, month ->
                            requestedBookingCategory = category
                            requestedBookingMonth = month
                            bookingFilterRequest += 1
                            analysisOpen = false
                            section = AppSection.BOOKINGS
                        },
                    )
                    target == AppSection.OVERVIEW -> CockpitScreen(
                        snapshot = state.snapshot,
                        amountsHidden = state.settings.amountsHidden,
                        budgetWarning = state.settings.budgetWarning,
                        lastBackupDate = state.lastBackupDate,
                        backupReminderEnabled = state.settings.backupReminderEnabled,
                        remindersEnabled = state.settings.remindersEnabled,
                        onOpenAnalysis = { analysisOpen = true },
                        onOpenPlanning = { filter ->
                            requestedPlanFilter = filter
                            section = AppSection.PLANNING
                        },
                        onOpenData = { section = AppSection.MORE },
                    )
                    target == AppSection.PLANNING -> PlanningScreen(
                        snapshot = state.snapshot,
                        viewModel = viewModel,
                        amountsHidden = state.settings.amountsHidden,
                        requestedFilter = requestedPlanFilter,
                        addRequest = planAddRequest,
                    )
                    target == AppSection.BOOKINGS -> BookingsScreen(
                        snapshot = state.snapshot,
                        viewModel = viewModel,
                        amountsHidden = state.settings.amountsHidden,
                        addRequest = bookingAddRequest,
                        requestedCategory = requestedBookingCategory,
                        requestedMonth = requestedBookingMonth,
                        filterRequest = bookingFilterRequest,
                        defaultCategory = state.settings.quickBookingCategory,
                        defaultType = state.settings.quickBookingType,
                    )
                    else -> DataScreen(
                        snapshot = state.snapshot,
                        lastBackupDate = state.lastBackupDate,
                        settings = state.settings,
                        viewModel = viewModel,
                        requestBiometricPreferredActivation = requestBiometricPreferredActivation,
                        biometricPreferredAvailabilityError = biometricPreferredAvailabilityError,
                        requestBackup = requestBackup,
                        requestLegacyImport = requestLegacyImport,
                        requestRestore = requestRestore,
                    )
                }
            }
        }
    }
}
