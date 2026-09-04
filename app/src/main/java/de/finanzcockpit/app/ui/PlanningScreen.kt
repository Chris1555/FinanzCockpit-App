package de.finanzcockpit.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.domain.CostType
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.Frequency
import de.finanzcockpit.app.domain.PlanStatus
import de.finanzcockpit.app.domain.PlanType
import de.finanzcockpit.app.domain.parseEuroCents
import de.finanzcockpit.app.domain.toInputAmount
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class PlanSort(val label: String) {
    NAME("Bezeichnung A–Z"),
    AMOUNT_DESC("Betrag: hoch–niedrig"),
    AMOUNT_ASC("Betrag: niedrig–hoch"),
    CATEGORY("Kategorie A–Z"),
    STATUS("Status"),
}

@Composable
fun PlanningScreen(
    snapshot: FinanceSnapshot,
    viewModel: FinanceViewModel,
    amountsHidden: Boolean,
    requestedFilter: PlanType?,
    addRequest: Int,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<PlanType?>(requestedFilter) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf<PlanStatus?>(null) }
    var frequencyFilter by rememberSaveable { mutableStateOf<Frequency?>(null) }
    var sort by rememberSaveable { mutableStateOf(PlanSort.NAME) }
    var filtersVisible by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FinancePlan?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FinancePlan?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    val haptic = rememberAppHaptic()

    LaunchedEffect(requestedFilter) {
        typeFilter = requestedFilter
    }
    LaunchedEffect(addRequest) {
        if (addRequest > 0) adding = true
    }

    val filtered = snapshot.plans
        .asSequence()
        .filter { plan ->
            (typeFilter == null || plan.type == typeFilter) &&
                (categoryFilter == null || plan.category == categoryFilter) &&
                (statusFilter == null || plan.status == statusFilter) &&
                (frequencyFilter == null || plan.frequency == frequencyFilter) &&
                (query.isBlank() || "${plan.name} ${plan.category} ${plan.note}".contains(query, true))
        }
        .let { plans ->
            when (sort) {
                PlanSort.NAME -> plans.sortedBy { it.name.lowercase() }
                PlanSort.AMOUNT_DESC -> plans.sortedByDescending { it.amountCents }
                PlanSort.AMOUNT_ASC -> plans.sortedBy { it.amountCents }
                PlanSort.CATEGORY -> plans.sortedBy { it.category.lowercase() }
                PlanSort.STATUS -> plans.sortedBy { it.status.ordinal }
            }
        }
        .toList()
    val activeFilterCount = listOf(typeFilter, categoryFilter, statusFilter, frequencyFilter)
        .count { it != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionTitle("Planung", "Regelmäßige Einnahmen und Kosten verwalten.")
            OutlinedButton(onClick = { showCategories = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Kategorien verwalten")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Position suchen") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { filtersVisible = !filtersVisible },
                    modifier = Modifier.padding(top = 7.dp),
                ) {
                    Text(if (activeFilterCount == 0) "Filter" else "Filter ($activeFilterCount)")
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = filtersVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Filtern und sortieren", style = MaterialTheme.typography.titleMedium)
                        SelectorField(
                            "Art",
                            typeFilter,
                            listOf<PlanType?>(null) + PlanType.entries,
                            { it?.label ?: "Alle Arten" },
                            { typeFilter = it },
                        )
                        SelectorField(
                            "Kategorie",
                            categoryFilter,
                            listOf<String?>(null) + snapshot.categories,
                            { it ?: "Alle Kategorien" },
                            { categoryFilter = it },
                        )
                        SelectorField(
                            "Status",
                            statusFilter,
                            listOf<PlanStatus?>(null) + PlanStatus.entries,
                            { it?.label ?: "Alle Status" },
                            { statusFilter = it },
                        )
                        SelectorField(
                            "Rhythmus",
                            frequencyFilter,
                            listOf<Frequency?>(null) + Frequency.entries,
                            { it?.label ?: "Alle Rhythmen" },
                            { frequencyFilter = it },
                        )
                        SelectorField("Sortierung", sort, PlanSort.entries, PlanSort::label, { sort = it })
                        TextButton(
                            onClick = {
                                typeFilter = null
                                categoryFilter = null
                                statusFilter = null
                                frequencyFilter = null
                                sort = PlanSort.NAME
                            },
                            enabled = activeFilterCount > 0 || sort != PlanSort.NAME,
                        ) {
                            Text("Filter zurücksetzen")
                        }
                    }
                }
            }
        }
        item {
            Text(
                "${filtered.size} von ${snapshot.plans.size} Positionen",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (filtered.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Keine Positionen gefunden.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    amountsHidden = amountsHidden,
                    onEdit = { editing = plan },
                    onDuplicate = {
                        haptic(false)
                        viewModel.savePlan(
                            plan.copy(
                                id = "plan-${UUID.randomUUID()}",
                                name = "${plan.name} (Kopie)",
                            ),
                        )
                    },
                    onDelete = { deleting = plan },
                )
            }
        }
    }

    if (adding || editing != null) {
        PlanEditorDialog(
            plan = editing,
            knownCategories = snapshot.categories,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                haptic(false)
                viewModel.savePlan(it)
                adding = false
                editing = null
            },
        )
    }

    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Position löschen?") },
            text = { Text("„${plan.name}“ wird entfernt. Direkt danach kannst du den Vorgang rückgängig machen.") },
            confirmButton = {
                Button(onClick = {
                    haptic(true)
                    viewModel.deletePlan(plan)
                    deleting = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }

    if (showCategories) {
        CategoriesDialog(snapshot, viewModel, onDismiss = { showCategories = false })
    }
}

@Composable
private fun PlanCard(
    plan: FinancePlan,
    amountsHidden: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    PressableCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${plan.type.label} · ${plan.category}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(plan.amountCents.displayEuro(amountsHidden), style = MaterialTheme.typography.titleMedium)
            }
            Text("${plan.frequency.label} · ${plan.status.label}")
            if (plan.frequency != Frequency.MONTHLY) {
                Text(
                    "Nicht in den Monatskosten enthalten",
                    color = ColorOrange,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Erste Fälligkeit im Jahreszyklus: ${dueMonthLabel(plan.dueMonth)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (plan.note.isNotBlank()) {
                Text(plan.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDuplicate) { Text("Duplizieren") }
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val ColorOrange = androidx.compose.ui.graphics.Color(0xFFD97706)

@Composable
private fun PlanEditorDialog(
    plan: FinancePlan?,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (FinancePlan) -> Unit,
) {
    var name by remember(plan) { mutableStateOf(plan?.name.orEmpty()) }
    var type by remember(plan) { mutableStateOf(plan?.type ?: PlanType.EXPENSE) }
    var category by remember(plan) { mutableStateOf(plan?.category.orEmpty()) }
    var costType by remember(plan) {
        mutableStateOf(plan?.costType?.takeUnless { it == CostType.SAVING } ?: CostType.FIXED)
    }
    var frequency by remember(plan) { mutableStateOf(plan?.frequency ?: Frequency.MONTHLY) }
    var amount by remember(plan) { mutableStateOf(plan?.amountCents?.toInputAmount().orEmpty()) }
    var status by remember(plan) { mutableStateOf(plan?.status ?: PlanStatus.ACTIVE) }
    var note by remember(plan) { mutableStateOf(plan?.note.orEmpty()) }
    var dueMonth by remember(plan) { mutableStateOf(plan?.dueMonth) }
    var detailsExpanded by remember(plan) {
        mutableStateOf(plan != null && (plan.status != PlanStatus.ACTIVE || plan.note.isNotBlank()))
    }
    var error by remember(plan) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val initialAmount = plan?.amountCents?.toInputAmount().orEmpty()
    val dirty = plan == null && listOf(name, category, amount, note).any { it.isNotBlank() } ||
        plan != null && (
            name != plan.name || type != plan.type || category != plan.category ||
                costType != plan.costType || frequency != plan.frequency ||
                amount != initialAmount || status != plan.status ||
                note != plan.note || dueMonth != plan.dueMonth
            )
    val requestDismiss = {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (plan == null) "Neue Position" else "Position bearbeiten") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Die wichtigsten Angaben zuerst",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                SelectorField("Art", type, PlanType.entries, PlanType::label, { selected ->
                    type = selected
                    if (selected == PlanType.SAVING) costType = CostType.FIXED
                })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("Bezeichnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(20) },
                    label = { Text("Betrag in Euro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryField(
                    value = category,
                    knownCategories = knownCategories,
                    onValueChange = { category = it },
                )
                FrequencyField(
                    value = frequency,
                    onSelected = { selected ->
                        frequency = selected
                        if (selected == Frequency.MONTHLY) dueMonth = null
                    },
                )
                if (frequency != Frequency.MONTHLY) {
                    SelectorField(
                        "Erster Fälligkeitsmonat",
                        dueMonth,
                        listOf<Int?>(null) + (1..12).toList(),
                        ::dueMonthLabel,
                        { dueMonth = it },
                    )
                    Text(
                        "Diese Zahlung bleibt vollständig von den Monatskosten getrennt.",
                        color = ColorOrange,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(if (detailsExpanded) "Weitere Angaben ausblenden" else "Weitere Angaben")
                }
                AnimatedVisibility(visible = detailsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (type == PlanType.EXPENSE) {
                            SelectorField(
                                "Kostenart",
                                costType,
                                listOf(CostType.FIXED, CostType.VARIABLE, CostType.OPTIONAL),
                                CostType::label,
                                { costType = it },
                            )
                        }
                        SelectorField("Status", status, PlanStatus.entries, PlanStatus::label, { status = it })
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(2_000) },
                            label = { Text("Notiz") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (error != null) Text(checkNotNull(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    require(name.isNotBlank()) { "Bezeichnung fehlt." }
                    require(category.isNotBlank()) { "Kategorie fehlt." }
                    onSave(
                        FinancePlan(
                            id = plan?.id ?: "plan-${UUID.randomUUID()}",
                            name = name.trim(),
                            type = type,
                            category = category.trim(),
                            costType = when (type) {
                                PlanType.INCOME -> CostType.FIXED
                                PlanType.SAVING -> CostType.SAVING
                                PlanType.EXPENSE -> costType
                            },
                            frequency = frequency,
                            amountCents = parseEuroCents(amount),
                            status = status,
                            note = note.trim(),
                            dueMonth = if (frequency == Frequency.MONTHLY) null else dueMonth,
                        ),
                    )
                } catch (exception: IllegalArgumentException) {
                    error = exception.message
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = requestDismiss) { Text("Abbrechen") } },
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Eingaben verwerfen?") },
            text = { Text("Deine noch nicht gespeicherten Änderungen gehen verloren.") },
            confirmButton = {
                Button(onClick = {
                    confirmDiscard = false
                    onDismiss()
                }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Weiter bearbeiten") }
            },
        )
    }
}

@Composable
private fun FrequencyField(
    value: Frequency,
    onSelected: (Frequency) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rhythmus", style = MaterialTheme.typography.labelMedium)
        Frequency.entries.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = value == option,
                        onClick = { onSelected(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun dueMonthLabel(month: Int?): String = month?.let {
    Month.of(it).getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)
        .replaceFirstChar(Char::uppercase)
} ?: "Noch nicht festgelegt"

@Composable
private fun CategoriesDialog(
    snapshot: FinanceSnapshot,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
) {
    var newCategory by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
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
                            TextButton(onClick = { renaming = category }) { Text("Umbenennen") }
                            TextButton(
                                enabled = !used,
                                onClick = { viewModel.deleteCategory(category) },
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
        confirmButton = { Button(onClick = onDismiss) { Text("Schließen") } },
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
                        viewModel.renameCategory(oldName, newName)
                        renaming = null
                    },
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } },
        )
    }
}
