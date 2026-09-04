package de.finanzcockpit.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM plans ORDER BY name COLLATE NOCASE")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM bookings ORDER BY date DESC, name COLLATE NOCASE")
    fun observeBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category_budgets ORDER BY category COLLATE NOCASE")
    fun observeBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM plans ORDER BY name COLLATE NOCASE")
    suspend fun getPlans(): List<PlanEntity>

    @Query("SELECT * FROM bookings ORDER BY date DESC, name COLLATE NOCASE")
    suspend fun getBookings(): List<BookingEntity>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM category_budgets ORDER BY category COLLATE NOCASE")
    suspend fun getBudgets(): List<CategoryBudgetEntity>

    @Upsert
    suspend fun upsertPlan(plan: PlanEntity)

    @Upsert
    suspend fun upsertBooking(booking: BookingEntity)

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Upsert
    suspend fun upsertPlans(plans: List<PlanEntity>)

    @Upsert
    suspend fun upsertBookings(bookings: List<BookingEntity>)

    @Upsert
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertBudget(budget: CategoryBudgetEntity)

    @Upsert
    suspend fun upsertBudgets(budgets: List<CategoryBudgetEntity>)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: String)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deleteBooking(id: String)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategoryRow(name: String)

    @Query("DELETE FROM category_budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("SELECT COUNT(*) FROM plans WHERE category = :name")
    suspend fun planCategoryUsage(name: String): Int

    @Query("SELECT COUNT(*) FROM bookings WHERE category = :name")
    suspend fun bookingCategoryUsage(name: String): Int

    @Query("SELECT COUNT(*) FROM category_budgets WHERE category = :name")
    suspend fun budgetCategoryUsage(name: String): Int

    @Query("UPDATE plans SET category = :newName WHERE category = :oldName")
    suspend fun renamePlanCategory(oldName: String, newName: String)

    @Query("UPDATE bookings SET category = :newName WHERE category = :oldName")
    suspend fun renameBookingCategory(oldName: String, newName: String)

    @Query("UPDATE category_budgets SET category = :newName WHERE category = :oldName")
    suspend fun renameBudgetCategory(oldName: String, newName: String)

    @Query("DELETE FROM plans")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM bookings")
    suspend fun deleteAllBookings()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllBudgets()

    @Transaction
    suspend fun replaceAll(
        plans: List<PlanEntity>,
        bookings: List<BookingEntity>,
        categories: List<CategoryEntity>,
        budgets: List<CategoryBudgetEntity>,
    ) {
        deleteAllBudgets()
        deleteAllBookings()
        deleteAllPlans()
        deleteAllCategories()
        if (categories.isNotEmpty()) upsertCategories(categories)
        if (plans.isNotEmpty()) upsertPlans(plans)
        if (bookings.isNotEmpty()) upsertBookings(bookings)
        if (budgets.isNotEmpty()) upsertBudgets(budgets)
    }

    @Transaction
    suspend fun renameCategory(oldName: String, newName: String) {
        upsertCategory(CategoryEntity(newName))
        renamePlanCategory(oldName, newName)
        renameBookingCategory(oldName, newName)
        renameBudgetCategory(oldName, newName)
        deleteCategoryRow(oldName)
    }
}
