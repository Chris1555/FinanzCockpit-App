package de.finanzcockpit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceCalculatorTest {
    @Test
    fun `nur aktive monatliche Positionen zaehlen zum Monatswert`() {
        val plans = listOf(
            plan("einkommen", PlanType.INCOME, Frequency.MONTHLY, 300_000),
            plan("bonus", PlanType.INCOME, Frequency.YEARLY, 120_000),
            plan("wohnen", PlanType.EXPENSE, Frequency.MONTHLY, 100_000),
            plan("jahresrechnung", PlanType.EXPENSE, Frequency.YEARLY, 60_000),
            plan("vorsorge", PlanType.SAVING, Frequency.MONTHLY, 50_000),
            plan("pausiert", PlanType.EXPENSE, Frequency.MONTHLY, 90_000, PlanStatus.PAUSED),
        )

        val totals = FinanceCalculator.monthlyTotals(plans)

        assertEquals(300_000, totals.incomeCents)
        assertEquals(100_000, totals.regularExpenseCents)
        assertEquals(50_000, totals.savingCents)
        assertEquals(150_000, totals.expenseCents)
        assertEquals(150_000, totals.freeCents)
    }

    @Test
    fun `nicht monatliche Rhythmen werden niemals anteilig umgelegt`() {
        val plans = listOf(
            plan("quartal", PlanType.EXPENSE, Frequency.QUARTERLY, 30_000),
            plan("halbjahr", PlanType.EXPENSE, Frequency.HALF_YEARLY, 50_000),
            plan("jahr", PlanType.EXPENSE, Frequency.YEARLY, 80_000),
        )

        assertEquals(0, FinanceCalculator.monthlyTotals(plans).expenseCents)
        assertEquals(300_000, FinanceCalculator.nonMonthlyAnnualCents(plans))
    }

    @Test
    fun `Jahresuebersicht enthaelt nur aktive nicht monatliche Kosten`() {
        val plans = listOf(
            plan("aktiv", PlanType.EXPENSE, Frequency.YEARLY, 80_000),
            plan("einnahme", PlanType.INCOME, Frequency.YEARLY, 100_000),
            plan("pausiert", PlanType.EXPENSE, Frequency.QUARTERLY, 50_000, PlanStatus.PAUSED),
            plan("monatlich", PlanType.EXPENSE, Frequency.MONTHLY, 10_000),
        )

        assertEquals(listOf("aktiv"), FinanceCalculator.activeNonMonthlyCosts(plans).map { it.id })
        assertEquals(80_000, FinanceCalculator.nonMonthlyAnnualCents(plans))
    }

    @Test
    fun `naechste vierteljaehrliche Faelligkeit wird ohne Monatsumlage bestimmt`() {
        val plan = plan("quartal", PlanType.EXPENSE, Frequency.QUARTERLY, 30_000)
            .copy(dueMonth = 2)

        assertEquals(
            YearMonth.of(2026, 8),
            FinanceCalculator.nextDueMonth(plan, YearMonth.of(2026, 7)),
        )
        assertEquals(0, FinanceCalculator.monthlyTotals(listOf(plan)).expenseCents)
    }

    @Test
    fun `Buchungssummen verwenden nur den ausgewaehlten Monat`() {
        val bookings = listOf(
            booking("1", "2026-09-01", BookingType.INCOME, 200_000),
            booking("2", "2026-09-12", BookingType.EXPENSE, 75_000),
            booking("3", "2026-08-31", BookingType.EXPENSE, 99_000),
        )

        val totals = FinanceCalculator.bookingTotals(bookings, YearMonth.of(2026, 9))

        assertEquals(200_000, totals.incomeCents)
        assertEquals(75_000, totals.expenseCents)
        assertEquals(125_000, totals.balanceCents)
        assertEquals(2, totals.count)
    }

    @Test
    fun `Plan Ist Vergleich verbindet Monatsplanung und Buchungen`() {
        val plans = listOf(
            plan("gehalt", PlanType.INCOME, Frequency.MONTHLY, 300_000),
            plan("kosten", PlanType.EXPENSE, Frequency.MONTHLY, 120_000),
        )
        val bookings = listOf(
            booking("gehalt-ist", "2026-09-01", BookingType.INCOME, 305_000),
            booking("kosten-ist", "2026-09-12", BookingType.EXPENSE, 110_000),
        )

        val result = FinanceCalculator.planActualComparison(plans, bookings, YearMonth.of(2026, 9))

        assertEquals(300_000, result.plannedIncomeCents)
        assertEquals(305_000, result.actualIncomeCents)
        assertEquals(120_000, result.plannedExpenseCents)
        assertEquals(110_000, result.actualExpenseCents)
        assertEquals(180_000, result.plannedBalanceCents)
        assertEquals(195_000, result.actualBalanceCents)
    }

    @Test
    fun `Kategorie Budget zaehlt nur Ausgaben des Monats`() {
        val budgets = listOf(CategoryBudget("Lebensmittel", 50_000))
        val bookings = listOf(
            Booking("1", LocalDate.parse("2026-09-02"), "Einkauf", BookingType.EXPENSE, "Lebensmittel", 12_000, ""),
            Booking("2", LocalDate.parse("2026-09-05"), "Erstattung", BookingType.INCOME, "Lebensmittel", 3_000, ""),
            Booking("3", LocalDate.parse("2026-08-31"), "Alt", BookingType.EXPENSE, "Lebensmittel", 9_000, ""),
        )

        val result = FinanceCalculator.categoryBudgetStatus(budgets, bookings, YearMonth.of(2026, 9)).single()

        assertEquals(12_000, result.spentCents)
        assertEquals(38_000, result.remainingCents)
    }

    private fun plan(
        id: String,
        type: PlanType,
        frequency: Frequency,
        cents: Long,
        status: PlanStatus = PlanStatus.ACTIVE,
    ) = FinancePlan(
        id = id,
        name = id,
        type = type,
        category = "Test",
        costType = if (type == PlanType.SAVING) CostType.SAVING else CostType.FIXED,
        frequency = frequency,
        amountCents = cents,
        status = status,
        note = "",
    )

    private fun booking(id: String, date: String, type: BookingType, cents: Long) = Booking(
        id = id,
        date = LocalDate.parse(date),
        name = id,
        type = type,
        category = "Test",
        amountCents = cents,
        note = "",
    )
}
