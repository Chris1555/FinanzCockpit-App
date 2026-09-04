package de.finanzcockpit.app.domain

import java.time.YearMonth

object FinanceCalculator {
    /**
     * Verbindliche Fachregel: Nur aktive, ausdrücklich monatliche Positionen
     * fließen in Monatswerte ein. Es findet keine anteilige Umrechnung statt.
     */
    fun monthlyTotals(plans: List<FinancePlan>): MonthlyTotals {
        var income = 0L
        var expenses = 0L
        var savings = 0L

        plans.asSequence()
            .filter { it.status == PlanStatus.ACTIVE && it.frequency == Frequency.MONTHLY }
            .forEach { plan ->
                when (plan.type) {
                    PlanType.INCOME -> income = Math.addExact(income, plan.amountCents)
                    PlanType.EXPENSE -> expenses = Math.addExact(expenses, plan.amountCents)
                    PlanType.SAVING -> savings = Math.addExact(savings, plan.amountCents)
                }
            }

        return MonthlyTotals(income, expenses, savings)
    }

    fun annualCents(plan: FinancePlan): Long {
        if (plan.status != PlanStatus.ACTIVE) return 0
        return Math.multiplyExact(plan.amountCents, plan.frequency.paymentsPerYear.toLong())
    }

    fun activeNonMonthlyCosts(plans: List<FinancePlan>): List<FinancePlan> = plans
        .filter {
            it.status == PlanStatus.ACTIVE &&
                it.frequency != Frequency.MONTHLY &&
                it.type != PlanType.INCOME
        }
        .sortedWith(compareBy<FinancePlan> { it.dueMonth ?: 13 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun nonMonthlyAnnualCents(plans: List<FinancePlan>): Long = activeNonMonthlyCosts(plans).asSequence()
        .fold(0L) { total, plan -> Math.addExact(total, annualCents(plan)) }

    /**
     * Ermittelt den nächsten Fälligkeitsmonat für nichtmonatliche Positionen.
     * dueMonth ist der erste Monat des wiederkehrenden Jahreszyklus.
     */
    fun nextDueMonth(plan: FinancePlan, from: YearMonth): YearMonth? {
        val firstMonth = plan.dueMonth ?: return null
        if (plan.frequency == Frequency.MONTHLY || plan.status != PlanStatus.ACTIVE) return null
        val interval = 12 / plan.frequency.paymentsPerYear
        val months = (0 until plan.frequency.paymentsPerYear)
            .map { ((firstMonth - 1 + it * interval) % 12) + 1 }
            .sorted()
        val monthThisYear = months.firstOrNull { it >= from.monthValue }
        return if (monthThisYear != null) {
            YearMonth.of(from.year, monthThisYear)
        } else {
            YearMonth.of(from.year + 1, months.first())
        }
    }

    fun categoryTotals(plans: List<FinancePlan>): List<Pair<String, Long>> = plans.asSequence()
        .filter {
            it.status == PlanStatus.ACTIVE &&
                it.frequency == Frequency.MONTHLY &&
                (it.type == PlanType.EXPENSE || it.type == PlanType.SAVING)
        }
        .groupingBy { it.category }
        .fold(0L) { total, plan -> Math.addExact(total, plan.amountCents) }
        .toList()
        .sortedByDescending { it.second }

    fun bookingTotals(bookings: List<Booking>, month: YearMonth): BookingTotals {
        var income = 0L
        var expenses = 0L
        var count = 0
        bookings.asSequence()
            .filter { YearMonth.from(it.date) == month }
            .forEach { booking ->
                count += 1
                when (booking.type) {
                    BookingType.INCOME -> income = Math.addExact(income, booking.amountCents)
                    BookingType.EXPENSE -> expenses = Math.addExact(expenses, booking.amountCents)
                }
            }
        return BookingTotals(income, expenses, count)
    }

    fun planActualComparison(
        plans: List<FinancePlan>,
        bookings: List<Booking>,
        month: YearMonth,
    ): PlanActualComparison {
        val planned = monthlyTotals(plans)
        val actual = bookingTotals(bookings, month)
        return PlanActualComparison(
            plannedIncomeCents = planned.incomeCents,
            actualIncomeCents = actual.incomeCents,
            plannedExpenseCents = planned.expenseCents,
            actualExpenseCents = actual.expenseCents,
        )
    }

    fun categoryBudgetStatus(
        budgets: List<CategoryBudget>,
        bookings: List<Booking>,
        month: YearMonth,
    ): List<CategoryBudgetStatus> {
        val spending = bookings.asSequence()
            .filter { it.type == BookingType.EXPENSE && YearMonth.from(it.date) == month }
            .groupingBy { it.category.lowercase() }
            .fold(0L) { total, booking -> Math.addExact(total, booking.amountCents) }
        return budgets.map { budget ->
            CategoryBudgetStatus(
                category = budget.category,
                budgetCents = budget.amountCents,
                spentCents = spending[budget.category.lowercase()] ?: 0L,
            )
        }.sortedWith(
            compareByDescending<CategoryBudgetStatus> { it.progress }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.category },
        )
    }

    fun smartHints(plans: List<FinancePlan>): List<SmartHint> {
        val totals = monthlyTotals(plans)
        return buildList {
            if (totals.freeCents < 0) {
                add(SmartHint(HintLevel.ERROR, "Der geplante Monatsrest ist negativ."))
            }
            val optional = plans.asSequence()
                .filter {
                    it.status == PlanStatus.ACTIVE &&
                        it.frequency == Frequency.MONTHLY &&
                        it.type == PlanType.EXPENSE &&
                        it.costType == CostType.OPTIONAL
                }
                .sumOf { it.amountCents }
            if (optional > 0) {
                add(SmartHint(HintLevel.INFO, "Optionale monatliche Kosten sind vorhanden."))
            }
            val checks = plans.count { "prüfen" in it.note.lowercase() }
            if (checks > 0) {
                add(SmartHint(HintLevel.WARNING, "$checks Positionen enthalten einen Prüfhinweis."))
            }
            val paused = plans.count { it.status == PlanStatus.PAUSED }
            if (paused > 0) {
                add(SmartHint(HintLevel.WARNING, "$paused Positionen sind pausiert."))
            }
            if (nonMonthlyAnnualCents(plans) > 0) {
                add(
                    SmartHint(
                        HintLevel.INFO,
                        "Nicht monatliche Ausgaben werden getrennt angezeigt und nicht umgelegt.",
                    ),
                )
            }
            val missingDueMonths = activeNonMonthlyCosts(plans).count { it.dueMonth == null }
            if (missingDueMonths > 0) {
                add(
                    SmartHint(
                        HintLevel.WARNING,
                        "$missingDueMonths nicht monatliche Positionen haben noch keinen Fälligkeitsmonat.",
                    ),
                )
            }
        }
    }
}
