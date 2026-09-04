package de.finanzcockpit.app.domain

import java.time.LocalDate

enum class PlanType(val label: String) {
    INCOME("Einnahme"),
    EXPENSE("Ausgabe"),
    SAVING("Sparen");

    companion object {
        fun fromLabel(value: String): PlanType? = entries.firstOrNull { it.label == value }
    }
}

enum class CostType(val label: String) {
    FIXED("Fix"),
    VARIABLE("Variabel"),
    OPTIONAL("Optional"),
    SAVING("Sparen");

    companion object {
        fun fromLabel(value: String): CostType? = entries.firstOrNull { it.label == value }
    }
}

enum class Frequency(val label: String, val paymentsPerYear: Int) {
    MONTHLY("Monatlich", 12),
    QUARTERLY("Vierteljährlich", 4),
    HALF_YEARLY("Halbjährlich", 2),
    YEARLY("Jährlich", 1);

    companion object {
        fun fromLabel(value: String): Frequency? = entries.firstOrNull { it.label == value }
    }
}

enum class PlanStatus(val label: String) {
    ACTIVE("Aktiv"),
    PAUSED("Pausiert"),
    ENDED("Beendet");

    companion object {
        fun fromLabel(value: String): PlanStatus? = entries.firstOrNull { it.label == value }
    }
}

enum class BookingType(val label: String) {
    INCOME("Einnahme"),
    EXPENSE("Ausgabe");

    companion object {
        fun fromLabel(value: String): BookingType? = entries.firstOrNull { it.label == value }
    }
}

data class FinancePlan(
    val id: String,
    val name: String,
    val type: PlanType,
    val category: String,
    val costType: CostType,
    val frequency: Frequency,
    val amountCents: Long,
    val status: PlanStatus,
    val note: String,
    /**
     * Optionaler erster Fälligkeitsmonat im Jahreszyklus (1 = Januar).
     * Der Wert dient ausschließlich der getrennten Terminübersicht und wird
     * niemals für die Monatskostenberechnung verwendet.
     */
    val dueMonth: Int? = null,
)

data class Booking(
    val id: String,
    val date: LocalDate,
    val name: String,
    val type: BookingType,
    val category: String,
    val amountCents: Long,
    val note: String,
)

data class CategoryBudget(
    val category: String,
    val amountCents: Long,
)

data class FinanceSnapshot(
    val plans: List<FinancePlan> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val categories: List<String> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
)

data class MonthlyTotals(
    val incomeCents: Long,
    val regularExpenseCents: Long,
    val savingCents: Long,
) {
    val expenseCents: Long = Math.addExact(regularExpenseCents, savingCents)
    val freeCents: Long = Math.subtractExact(incomeCents, expenseCents)
}

data class BookingTotals(
    val incomeCents: Long,
    val expenseCents: Long,
    val count: Int,
) {
    val balanceCents: Long = Math.subtractExact(incomeCents, expenseCents)
}

data class SmartHint(
    val level: HintLevel,
    val text: String,
)

enum class HintLevel { INFO, WARNING, ERROR }

data class PlanActualComparison(
    val plannedIncomeCents: Long,
    val actualIncomeCents: Long,
    val plannedExpenseCents: Long,
    val actualExpenseCents: Long,
) {
    val incomeDifferenceCents = Math.subtractExact(actualIncomeCents, plannedIncomeCents)
    val expenseDifferenceCents = Math.subtractExact(actualExpenseCents, plannedExpenseCents)
    val plannedBalanceCents = Math.subtractExact(plannedIncomeCents, plannedExpenseCents)
    val actualBalanceCents = Math.subtractExact(actualIncomeCents, actualExpenseCents)
}

data class CategoryBudgetStatus(
    val category: String,
    val budgetCents: Long,
    val spentCents: Long,
) {
    val remainingCents = Math.subtractExact(budgetCents, spentCents)
    val progress: Float = if (budgetCents == 0L) 0f else spentCents.toFloat() / budgetCents.toFloat()
}
