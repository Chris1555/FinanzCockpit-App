package de.finanzcockpit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val category: String,
    val costType: String,
    val frequency: String,
    val amountCents: Long,
    val status: String,
    val note: String,
    val dueMonth: Int?,
)

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val id: String,
    val date: String,
    val name: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val note: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
)

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val category: String,
    val amountCents: Long,
)
