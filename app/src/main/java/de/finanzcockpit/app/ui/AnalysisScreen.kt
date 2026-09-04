package de.finanzcockpit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.StartMonthMode
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.CategoryBudgetStatus
import de.finanzcockpit.app.domain.FinanceCalculator
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.parseEuroCents
import de.finanzcockpit.app.domain.toInputAmount
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AnalysisScreen(
    snapshot: FinanceSnapshot,
    viewModel: FinanceViewModel,
    amountsHidden: Boolean,
    addBudgetRequest: Int = 0,
    onBack: () -> Unit = {},
    onOpenBudgetBookings: (String, YearMonth) -> Unit = { _, _ -> },
) {
    val settings = LocalAppSettings.current
    val initialMonth = if (settings.startMonthMode == StartMonthMode.LAST_OPENED) {
        settings.lastOpenedMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    } else null
    var monthText by rememberSaveable { mutableStateOf((initialMonth ?: YearMonth.now()).toString()) }
    var editedBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var addingBudget by remember { mutableStateOf(false) }
    var budgetToDelete by remember { mutableStateOf<CategoryBudget?>(null) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(YearMonth.now())
    val comparison = FinanceCalculator.planActualComparison(snapshot.plans, snapshot.bookings, month)
    val budgetStatus = FinanceCalculator.categoryBudgetStatus(snapshot.budgets, snapshot.bookings, month)
    val hasUnusedCategory = snapshot.categories.any { category ->
        snapshot.budgets.none { it.category.equals(category, ignoreCase = true) }
    }
    val haptic = rememberAppHaptic()

    LaunchedEffect(addBudgetRequest) {
        if (addBudgetRequest > 0) addingBudget = true
    }
    LaunchedEffect(monthText) { viewModel.setLastOpenedMonth(monthText) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                Text("  Zur Übersicht")
            }
            SectionTitle("Plan gegen Ist", "Klarer Vergleich für ${month.fullLabel()}.")
        }
        item {
            MonthSelector(
                month = month,
                onPrevious = { haptic(false); monthText = month.minusMonths(1).toString() },
                onNext = { haptic(false); monthText = month.plusMonths(1).toString() },
                onCurrent = { haptic(false); monthText = YearMonth.now().toString() },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisValueCard(
                    label = "Einnahmen",
                    planned = comparison.plannedIncomeCents,
                    actual = comparison.actualIncomeCents,
                    amountsHidden = amountsHidden,
                    modifier = Modifier.weight(1f),
                )
                AnalysisValueCard(
                    label = "Ausgaben",
                    planned = comparison.plannedExpenseCents,
                    actual = comparison.actualExpenseCents,
                    amountsHidden = amountsHidden,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monatsergebnis", style = MaterialTheme.typography.titleMedium)
                    AnalysisRow("Geplanter Rest", comparison.plannedBalanceCents.displayEuro(amountsHidden))
                    AnalysisRow("Tatsächlicher Saldo", comparison.actualBalanceCents.displayEuro(amountsHidden))
                    HorizontalDivider()
                    AnalysisRow(
                        "Abweichung",
                        (comparison.actualBalanceCents - comparison.plannedBalanceCents)
                            .displayEuro(amountsHidden),
                        emphasized = true,
                    )
                    Text(
                        "Planwerte enthalten nur aktive monatliche Positionen. Ist-Werte stammen aus den Buchungen des ausgewählten Monats.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Text("Letzte sechs Monate", style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val history = (5L downTo 0L).map { offset ->
                        val historyMonth = month.minusMonths(offset)
                        historyMonth to FinanceCalculator.bookingTotals(snapshot.bookings, historyMonth)
                    }
                    if (amountsHidden) {
                        Text(
                            "Der Verlauf ist ausgeblendet, solange Beträge verborgen sind.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MonthlyTrendChart(history.map { it.second.balanceCents })
                    }
                    history.forEachIndexed { index, (historyMonth, totals) ->
                        AnalysisRow(historyMonth.shortLabel(), totals.balanceCents.displayEuro(amountsHidden))
                        if (index < history.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kategorie-Budgets", style = MaterialTheme.typography.titleLarge)
                TextButton(
                    enabled = hasUnusedCategory,
                    onClick = { addingBudget = true },
                ) { Text("+ Budget") }
            }
        }
        if (budgetStatus.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        if (snapshot.categories.isEmpty()) {
                            "Lege zuerst in Planung oder Buchungen eine Kategorie an."
                        } else {
                            "Noch keine Budgets festgelegt. Mit einem Monatsbudget siehst du sofort, wie viel je Kategorie übrig ist."
                        },
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(budgetStatus, key = { it.category }) { status ->
                BudgetCard(
                    status = status,
                    amountsHidden = amountsHidden,
                    onEdit = { editedBudget = snapshot.budgets.first { it.category == status.category } },
                    onDelete = { budgetToDelete = snapshot.budgets.first { it.category == status.category } },
                    onOpen = { onOpenBudgetBookings(status.category, month) },
                )
            }
        }
    }

    if (addingBudget || editedBudget != null) {
        BudgetDialog(
            existing = editedBudget,
            categories = snapshot.categories,
            usedCategories = snapshot.budgets.map { it.category },
            onDismiss = {
                addingBudget = false
                editedBudget = null
            },
            onSave = { budget ->
                haptic(false)
                viewModel.saveBudget(budget)
                addingBudget = false
                editedBudget = null
            },
        )
    }

    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("Budget entfernen?") },
            text = { Text("Das Monatsbudget für „${budget.category}“ wird entfernt. Buchungen bleiben erhalten.") },
            confirmButton = {
                Button(onClick = {
                    haptic(true)
                    viewModel.deleteBudget(budget.category)
                    budgetToDelete = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { budgetToDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPrevious) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Vorheriger Monat") }
                Text(month.fullLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Nächster Monat") }
            }
            if (month != YearMonth.now()) {
                TextButton(onClick = onCurrent, modifier = Modifier.fillMaxWidth()) { Text("Zum aktuellen Monat") }
            }
        }
    }
}

@Composable
private fun AnalysisValueCard(
    label: String,
    planned: Long,
    actual: Long,
    amountsHidden: Boolean,
    modifier: Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text("Plan ${planned.displayEuro(amountsHidden)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Ist ${actual.displayEuro(amountsHidden)}", fontWeight = FontWeight.Bold)
            Text(
                "Differenz ${(actual - planned).displayEuro(amountsHidden)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BudgetCard(
    status: CategoryBudgetStatus,
    amountsHidden: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val exceeded = status.remainingCents < 0
    val indicatorColor = budgetColor(status.progress)
    PressableCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(status.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        exceeded -> "Überschritten"
                        status.progress >= 0.8f -> "Fast erreicht"
                        else -> "Im Budget"
                    },
                    color = indicatorColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            LinearProgressIndicator(
                progress = { status.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = indicatorColor,
                trackColor = indicatorColor.copy(alpha = 0.16f),
            )
            AnalysisRow("Ausgegeben", status.spentCents.displayEuro(amountsHidden))
            AnalysisRow("Budget", status.budgetCents.displayEuro(amountsHidden))
            AnalysisRow(
                if (exceeded) "Überschritten um" else "Noch verfügbar",
                kotlin.math.abs(status.remainingCents).displayEuro(amountsHidden),
                emphasized = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDelete) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MonthlyTrendChart(values: List<Long>) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(112.dp).padding(vertical = 8.dp)) {
        if (values.isEmpty()) return@Canvas
        val min = minOf(0L, values.minOrNull() ?: 0L)
        val max = maxOf(0L, values.maxOrNull() ?: 0L)
        val range = (max - min).coerceAtLeast(1L).toFloat()
        val zeroY = size.height - ((0L - min).toFloat() / range * size.height)
        drawLine(
            gridColor,
            Offset(0f, zeroY.coerceIn(0f, size.height)),
            Offset(size.width, zeroY.coerceIn(0f, size.height)),
            strokeWidth = 1f,
        )
        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2f else size.width * index / (values.size - 1)
            val y = size.height - ((value - min).toFloat() / range * size.height)
            Offset(x, y.coerceIn(4f, size.height - 4f))
        }
        points.zipWithNext().forEach { (from, to) -> drawLine(lineColor, from, to, strokeWidth = 5f) }
        points.forEach { drawCircle(lineColor, radius = 6f, center = it) }
    }
}

@Composable
private fun AnalysisRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BudgetDialog(
    existing: CategoryBudget?,
    categories: List<String>,
    usedCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (CategoryBudget) -> Unit,
) {
    val selectable = categories.filter { category ->
        existing?.category == category || usedCategories.none { it.equals(category, ignoreCase = true) }
    }
    var category by remember(existing, selectable) {
        mutableStateOf(existing?.category ?: selectable.firstOrNull().orEmpty())
    }
    var amount by remember(existing) { mutableStateOf(existing?.amountCents?.toInputAmount().orEmpty()) }
    var error by remember(existing) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Monatsbudget anlegen" else "Monatsbudget bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectorField(
                    label = "Kategorie",
                    value = category,
                    options = selectable,
                    optionLabel = { it },
                    onSelected = { category = it },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(20) },
                    label = { Text("Budget pro Monat in Euro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = category.isNotBlank(), onClick = {
                try {
                    val cents = parseEuroCents(amount)
                    require(cents > 0) { "Das Budget muss größer als 0 € sein." }
                    onSave(CategoryBudget(category, cents))
                } catch (exception: IllegalArgumentException) {
                    error = exception.message
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun YearMonth.fullLabel(): String =
    "${month.getDisplayName(TextStyle.FULL, Locale.GERMAN).replaceFirstChar(Char::uppercase)} $year"

private fun YearMonth.shortLabel(): String =
    "${month.getDisplayName(TextStyle.SHORT, Locale.GERMAN)} $year"
