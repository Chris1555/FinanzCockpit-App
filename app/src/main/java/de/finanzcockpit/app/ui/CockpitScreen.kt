package de.finanzcockpit.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.BudgetWarningMode
import de.finanzcockpit.app.domain.CategoryBudgetStatus
import de.finanzcockpit.app.domain.FinanceCalculator
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.PlanType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ImportantItem(
    val title: String,
    val detail: String,
    val urgent: Boolean = false,
    val destination: ImportantDestination = ImportantDestination.ANALYSIS,
)

private enum class ImportantDestination { ANALYSIS, PLANNING, DATA }

@Composable
fun CockpitScreen(
    snapshot: FinanceSnapshot,
    amountsHidden: Boolean,
    budgetWarning: BudgetWarningMode,
    lastBackupDate: String?,
    backupReminderEnabled: Boolean,
    remindersEnabled: Boolean,
    onOpenAnalysis: () -> Unit,
    onOpenPlanning: (PlanType?) -> Unit,
    onOpenData: () -> Unit,
) {
    val planned = FinanceCalculator.monthlyTotals(snapshot.plans)
    val month = YearMonth.now()
    val actual = FinanceCalculator.bookingTotals(snapshot.bookings, month)
    val budgets = FinanceCalculator.categoryBudgetStatus(snapshot.budgets, snapshot.bookings, month)
    val nearestDue = FinanceCalculator.activeNonMonthlyCosts(snapshot.plans)
        .mapNotNull { plan -> FinanceCalculator.nextDueMonth(plan, month)?.let { plan to it } }
        .minByOrNull { it.second }
    val checks = snapshot.plans.count { "prüfen" in it.note.lowercase() }
    val warningBudgets = budgetWarning.threshold?.let { threshold ->
        budgets.filter { it.progress >= threshold }
    }.orEmpty()
    val exceededBudgetKey = if (budgetWarning == BudgetWarningMode.OFF) "" else {
        budgets.filter { it.progress >= 1f }.joinToString("|") { it.category }
    }
    val haptic = rememberAppHaptic()
    var lastExceededBudgetKey by remember { mutableStateOf("") }
    LaunchedEffect(exceededBudgetKey) {
        if (exceededBudgetKey.isNotBlank() && exceededBudgetKey != lastExceededBudgetKey) haptic(true)
        lastExceededBudgetKey = exceededBudgetKey
    }
    val backupOverdue = backupReminderEnabled && (
        lastBackupDate == null || runCatching {
            LocalDate.parse(lastBackupDate).isBefore(LocalDate.now().minusDays(30))
        }.getOrDefault(true)
    )
    val important = buildList {
        if (remindersEnabled) {
            nearestDue?.let { (plan, due) ->
                add(
                    ImportantItem(
                        "Bald fällig",
                        "${plan.name} · ${due.germanMonth()}",
                        destination = ImportantDestination.PLANNING,
                    ),
                )
            }
            warningBudgets.firstOrNull()?.let { budget ->
                add(
                    ImportantItem(
                        if (budget.progress >= 1f) "Budget überschritten" else "Budget fast erreicht",
                        "${budget.category} · ${(budget.progress * 100).toInt()} % genutzt",
                        urgent = budget.progress >= 1f,
                    ),
                )
            }
            if (checks > 0) {
                add(
                    ImportantItem(
                        "Offene Prüfhinweise",
                        "$checks Positionen ansehen",
                        destination = ImportantDestination.PLANNING,
                    ),
                )
            }
        }
        if (backupOverdue) {
            add(
                ImportantItem(
                    "Sicherung empfohlen",
                    "Letzte Sicherung ist länger her",
                    destination = ImportantDestination.DATA,
                ),
            )
        }
    }.take(3)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Guten Überblick.", style = MaterialTheme.typography.headlineSmall)
            Text(
                month.germanMonth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            PressableCard(
                onClick = onOpenAnalysis,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Frei verfügbar", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f))
                    Text(
                        planned.freeCents.displayEuro(amountsHidden),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "Geplant pro Monat",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            PressableCard(onClick = onOpenAnalysis, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ausgaben diesen Monat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            actual.expenseCents.displayEuro(amountsHidden),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${actual.count} Buchungen", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.Analytics, contentDescription = "Analyse öffnen", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item {
            PressableCard(
                onClick = onOpenAnalysis,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Analyse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Plan, Ist und Verlauf", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
        item { Text("Heute wichtig", style = MaterialTheme.typography.titleLarge) }
        if (important.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Alles im ruhigen Bereich.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(important, key = { it.title }) { item ->
                PressableCard(
                    onClick = {
                        when (item.destination) {
                            ImportantDestination.ANALYSIS -> onOpenAnalysis()
                            ImportantDestination.PLANNING -> onOpenPlanning(null)
                            ImportantDestination.DATA -> onOpenData()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(17.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when {
                                item.title.contains("fällig", true) -> Icons.Outlined.Event
                                item.title.contains("Sicherung", true) -> Icons.Outlined.Shield
                                else -> Icons.Outlined.NotificationsNone
                            },
                            contentDescription = null,
                            tint = if (item.urgent) MaterialTheme.colorScheme.error else Color(0xFFE0A600),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Text("Wichtigste Budgets", style = MaterialTheme.typography.titleLarge) }
        if (budgets.isEmpty()) {
            item {
                PressableCard(onClick = onOpenAnalysis, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Noch keine Budgets festgelegt. Jetzt ein Budget anlegen.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(budgets.take(3), key = { it.category }) { budget ->
                OverviewBudgetCard(budget, amountsHidden, onOpenAnalysis)
            }
        }
    }
}

@Composable
private fun OverviewBudgetCard(
    budget: CategoryBudgetStatus,
    amountsHidden: Boolean,
    onClick: () -> Unit,
) {
    val color = budgetColor(budget.progress)
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(budget.category, fontWeight = FontWeight.SemiBold)
                Text(
                    "${budget.spentCents.displayEuro(amountsHidden)} / ${budget.budgetCents.displayEuro(amountsHidden)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { budget.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = color.copy(alpha = 0.16f),
            )
        }
    }
}

@Composable
fun budgetColor(progress: Float): Color = when {
    progress >= 1f -> MaterialTheme.colorScheme.error
    progress >= 0.8f -> Color(0xFFE0A600)
    else -> MaterialTheme.colorScheme.secondary
}

private val germanMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
private fun YearMonth.germanMonth(): String = format(germanMonth).replaceFirstChar(Char::uppercase)
