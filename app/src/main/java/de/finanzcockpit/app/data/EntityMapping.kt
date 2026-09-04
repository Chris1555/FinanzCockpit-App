package de.finanzcockpit.app.data

import de.finanzcockpit.app.domain.Booking
import de.finanzcockpit.app.domain.BookingType
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.CostType
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.Frequency
import de.finanzcockpit.app.domain.PlanStatus
import de.finanzcockpit.app.domain.PlanType
import java.time.LocalDate

fun FinancePlan.toEntity() = PlanEntity(
    id = id,
    name = name,
    type = type.name,
    category = category,
    costType = costType.name,
    frequency = frequency.name,
    amountCents = amountCents,
    status = status.name,
    note = note,
    dueMonth = dueMonth,
)

fun PlanEntity.toDomain() = FinancePlan(
    id = id,
    name = name,
    type = PlanType.valueOf(type),
    category = category,
    costType = CostType.valueOf(costType),
    frequency = Frequency.valueOf(frequency),
    amountCents = amountCents,
    status = PlanStatus.valueOf(status),
    note = note,
    dueMonth = dueMonth,
)

fun Booking.toEntity() = BookingEntity(
    id = id,
    date = date.toString(),
    name = name,
    type = type.name,
    category = category,
    amountCents = amountCents,
    note = note,
)

fun BookingEntity.toDomain() = Booking(
    id = id,
    date = LocalDate.parse(date),
    name = name,
    type = BookingType.valueOf(type),
    category = category,
    amountCents = amountCents,
    note = note,
)

fun CategoryBudget.toEntity() = CategoryBudgetEntity(
    category = category,
    amountCents = amountCents,
)

fun CategoryBudgetEntity.toDomain() = CategoryBudget(
    category = category,
    amountCents = amountCents,
)
