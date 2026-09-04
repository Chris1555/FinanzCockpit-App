package de.finanzcockpit.app.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

object FinanceValidation {
    private val validId = Regex("[A-Za-z0-9._:-]{1,80}")

    fun validate(plan: FinancePlan): List<String> = buildList {
        if (!validId.matches(plan.id)) add("Ungültige ID")
        if (plan.name.isBlank() || plan.name.length > 120) add("Ungültige Bezeichnung")
        if (plan.category.isBlank() || plan.category.length > 50) add("Ungültige Kategorie")
        if (plan.amountCents < 0 || plan.amountCents > 999_999_999_99L) add("Ungültiger Betrag")
        if (plan.note.length > 2_000) add("Notiz ist zu lang")
        if (plan.dueMonth != null && plan.dueMonth !in 1..12) add("Ungültiger Fälligkeitsmonat")
        if (plan.frequency == Frequency.MONTHLY && plan.dueMonth != null) {
            add("Monatliche Positionen benötigen keinen Fälligkeitsmonat")
        }
    }

    fun validate(booking: Booking): List<String> = buildList {
        if (!validId.matches(booking.id)) add("Ungültige ID")
        if (booking.name.isBlank() || booking.name.length > 120) add("Ungültige Bezeichnung")
        if (booking.category.isBlank() || booking.category.length > 50) add("Ungültige Kategorie")
        if (booking.amountCents < 0 || booking.amountCents > 999_999_999_99L) add("Ungültiger Betrag")
        if (booking.note.length > 2_000) add("Notiz ist zu lang")
    }

    fun parseIsoDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("Ungültiges Datum")
    }
}
