package de.finanzcockpit.app.ui

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.FinanceViewModel
import de.finanzcockpit.app.StartMonthMode
import de.finanzcockpit.app.domain.Booking
import de.finanzcockpit.app.domain.BookingType
import de.finanzcockpit.app.domain.FinanceCalculator
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.parseEuroCents
import de.finanzcockpit.app.domain.toInputAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class BookingSort(val label: String) {
    DATE_DESC("Datum: neu–alt"),
    DATE_ASC("Datum: alt–neu"),
    AMOUNT_DESC("Betrag: hoch–niedrig"),
    AMOUNT_ASC("Betrag: niedrig–hoch"),
    NAME("Bezeichnung A–Z"),
}

@Composable
fun BookingsScreen(
    snapshot: FinanceSnapshot,
    viewModel: FinanceViewModel,
    amountsHidden: Boolean,
    addRequest: Int,
    requestedCategory: String? = null,
    requestedMonth: YearMonth? = null,
    filterRequest: Int = 0,
    defaultCategory: String = "",
    defaultType: BookingType = BookingType.EXPENSE,
) {
    val settings = LocalAppSettings.current
    val initialMonth = if (settings.startMonthMode == StartMonthMode.LAST_OPENED) {
        settings.lastOpenedMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    } else null
    var query by rememberSaveable { mutableStateOf("") }
    var selectedMonthText by rememberSaveable { mutableStateOf((initialMonth ?: YearMonth.now()).toString()) }
    var sort by rememberSaveable { mutableStateOf(BookingSort.DATE_DESC) }
    var sortingVisible by rememberSaveable { mutableStateOf(false) }
    var typeFilterName by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Booking?>(null) }
    val selectedMonth = runCatching { YearMonth.parse(selectedMonthText) }.getOrDefault(YearMonth.now())
    val monthTotals = FinanceCalculator.bookingTotals(snapshot.bookings, selectedMonth)
    val typeFilter = BookingType.entries.firstOrNull { it.name == typeFilterName }
    val haptic = rememberAppHaptic()

    LaunchedEffect(addRequest) {
        if (addRequest > 0) adding = true
    }
    LaunchedEffect(filterRequest) {
        if (filterRequest > 0) {
            requestedCategory?.let { categoryFilter = it }
            requestedMonth?.let { selectedMonthText = it.toString() }
            sortingVisible = true
        }
    }
    LaunchedEffect(selectedMonthText) { viewModel.setLastOpenedMonth(selectedMonthText) }

    val filtered = snapshot.bookings
        .asSequence()
        .filter { YearMonth.from(it.date) == selectedMonth }
        .filter { query.isBlank() || "${it.name} ${it.category} ${it.note}".contains(query, true) }
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { categoryFilter.isBlank() || it.category.equals(categoryFilter, ignoreCase = true) }
        .let { bookings ->
            when (sort) {
                BookingSort.DATE_DESC -> bookings.sortedByDescending { it.date }
                BookingSort.DATE_ASC -> bookings.sortedBy { it.date }
                BookingSort.AMOUNT_DESC -> bookings.sortedByDescending { it.amountCents }
                BookingSort.AMOUNT_ASC -> bookings.sortedBy { it.amountCents }
                BookingSort.NAME -> bookings.sortedBy { it.name.lowercase() }
            }
        }
        .toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionTitle("Buchungen", "Schnell erfassen, ruhig überblicken.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            haptic(false)
                            selectedMonthText = selectedMonth.minusMonths(1).toString()
                        }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Vorheriger Monat")
                        }
                        Column {
                            Text(
                                selectedMonth.formatMonth(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(onClick = {
                            haptic(false)
                            selectedMonthText = selectedMonth.plusMonths(1).toString()
                        }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Nächster Monat")
                        }
                    }
                    if (selectedMonth != YearMonth.now()) {
                        TextButton(
                            onClick = { selectedMonthText = YearMonth.now().toString() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Zum aktuellen Monat") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookingSummary(
                    "Einnahmen Ist",
                    monthTotals.incomeCents.displayEuro(amountsHidden),
                    Modifier.weight(1f),
                )
                BookingSummary(
                    "Ausgaben Ist",
                    monthTotals.expenseCents.displayEuro(amountsHidden),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookingSummary(
                    "Saldo Ist",
                    monthTotals.balanceCents.displayEuro(amountsHidden),
                    Modifier.weight(1f),
                )
                BookingSummary("Buchungen", monthTotals.count.toString(), Modifier.weight(1f))
            }
        }
        item {
            OutlinedButton(onClick = { sortingVisible = !sortingVisible }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (sortingVisible) Icons.Outlined.Search else Icons.Outlined.FilterList, contentDescription = null)
                Text(if (sortingVisible) "  Suche und Filter ausblenden" else "  Suche und Filter")
            }
        }
        item {
            AnimatedVisibility(
                visible = sortingVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Buchung suchen") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SelectorField(
                            label = "Art",
                            value = typeFilter,
                            options = listOf<BookingType?>(null) + BookingType.entries,
                            optionLabel = { it?.label ?: "Alle Arten" },
                            onSelected = { typeFilterName = it?.name.orEmpty() },
                        )
                        SelectorField(
                            label = "Kategorie",
                            value = categoryFilter,
                            options = listOf("") + snapshot.categories,
                            optionLabel = { it.ifBlank { "Alle Kategorien" } },
                            onSelected = { categoryFilter = it },
                        )
                        SelectorField(
                            label = "Sortierung",
                            value = sort,
                            options = BookingSort.entries,
                            optionLabel = BookingSort::label,
                            onSelected = { sort = it },
                        )
                        if (typeFilter != null || categoryFilter.isNotBlank()) {
                            TextButton(onClick = {
                                typeFilterName = ""
                                categoryFilter = ""
                            }, modifier = Modifier.fillMaxWidth()) { Text("Filter zurücksetzen") }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "${filtered.size} passende Buchungen in ${selectedMonth.formatMonth()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (filtered.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "In diesem Monat sind noch keine passenden Buchungen vorhanden.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { booking ->
                SwipeBookingCard(
                    booking = booking,
                    amountsHidden = amountsHidden,
                    onEdit = { haptic(false); editing = booking },
                    onDelete = { haptic(true); viewModel.deleteBooking(booking) },
                )
            }
        }
    }

    if (adding || editing != null) {
        BookingEditorDialog(
            booking = editing,
            knownCategories = snapshot.categories,
            defaultCategory = defaultCategory,
            defaultType = defaultType,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = {
                haptic(false)
                viewModel.saveBooking(it)
                selectedMonthText = YearMonth.from(it.date).toString()
                adding = false
                editing = null
            },
        )
    }

}

@Composable
private fun SwipeBookingCard(
    booking: Booking,
    amountsHidden: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onEdit()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        state.reset()
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val edit = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(
                    if (edit) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.shapes.medium,
                ).padding(horizontal = 22.dp),
                horizontalArrangement = if (edit) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (edit) Icons.Outlined.Edit else Icons.Outlined.Delete,
                    contentDescription = if (edit) "Bearbeiten" else "Löschen",
                    tint = if (edit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            }
        },
        content = {
            BookingCard(booking, amountsHidden, onEdit, onDelete)
        },
    )
}

@Composable
private fun BookingSummary(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    amountsHidden: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PressableCard(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Icon(categoryIcon(booking.category), contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                    Column {
                        Text(booking.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${booking.date.formatGermanDate()} · ${booking.category}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    booking.amountCents.displayEuro(amountsHidden),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (booking.type == BookingType.INCOME) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (booking.note.isNotBlank()) Text(booking.note)
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BookingEditorDialog(
    booking: Booking?,
    knownCategories: List<String>,
    defaultCategory: String,
    defaultType: BookingType,
    onDismiss: () -> Unit,
    onSave: (Booking) -> Unit,
) {
    var date by remember(booking) { mutableStateOf(booking?.date ?: LocalDate.now()) }
    var type by remember(booking) { mutableStateOf(booking?.type ?: defaultType) }
    var name by remember(booking) { mutableStateOf(booking?.name.orEmpty()) }
    var category by remember(booking) { mutableStateOf(booking?.category ?: defaultCategory) }
    var amount by remember(booking) { mutableStateOf(booking?.amountCents?.toInputAmount().orEmpty()) }
    var note by remember(booking) { mutableStateOf(booking?.note.orEmpty()) }
    var error by remember(booking) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val initialAmount = booking?.amountCents?.toInputAmount().orEmpty()
    val dirty = booking == null && listOf(name, category, amount, note).any { it.isNotBlank() } ||
        booking != null && (
            date != booking.date || type != booking.type || name != booking.name ||
                category != booking.category || amount != initialAmount || note != booking.note
            )
    val requestDismiss = {
        if (dirty) confirmDiscard = true else onDismiss()
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (booking == null) "Neue Buchung" else "Buchung bearbeiten") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateField(date = date, onDateSelected = { date = it })
                SelectorField("Art", type, BookingType.entries, BookingType::label, { type = it })
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(2_000) },
                    label = { Text("Notiz · optional") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) Text(checkNotNull(error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    require(name.isNotBlank()) { "Bezeichnung fehlt." }
                    require(category.isNotBlank()) { "Kategorie fehlt." }
                    onSave(
                        Booking(
                            id = booking?.id ?: "booking-${UUID.randomUUID()}",
                            date = date,
                            name = name.trim(),
                            type = type,
                            category = category.trim(),
                            amountCents = parseEuroCents(amount),
                            note = note.trim(),
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
private fun DateField(date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Datum", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDateSelected(LocalDate.of(year, month + 1, day))
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(date.formatGermanDate())
        }
    }
}

private val germanDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

private fun LocalDate.formatGermanDate(): String = format(germanDateFormatter)

private fun YearMonth.formatMonth(): String =
    "${month.getDisplayName(TextStyle.FULL, Locale.GERMAN).replaceFirstChar(Char::uppercase)} $year"
