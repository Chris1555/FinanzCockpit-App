package de.finanzcockpit.app.data

import de.finanzcockpit.app.domain.Booking
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.FinanceValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class FinanceRepository(private val database: AppDatabase) {
    private val dao = database.financeDao()

    val snapshot: Flow<FinanceSnapshot> = combine(
        dao.observePlans(),
        dao.observeBookings(),
        dao.observeCategories(),
        dao.observeBudgets(),
    ) { plans, bookings, categories, budgets ->
        FinanceSnapshot(
            plans = plans.map(PlanEntity::toDomain),
            bookings = bookings.map(BookingEntity::toDomain),
            categories = categories.map { it.name },
            budgets = budgets.map(CategoryBudgetEntity::toDomain),
        )
    }

    suspend fun currentSnapshot(): FinanceSnapshot = FinanceSnapshot(
        plans = dao.getPlans().map(PlanEntity::toDomain),
        bookings = dao.getBookings().map(BookingEntity::toDomain),
        categories = dao.getCategories().map { it.name },
        budgets = dao.getBudgets().map(CategoryBudgetEntity::toDomain),
    )

    suspend fun savePlan(plan: FinancePlan) {
        require(FinanceValidation.validate(plan).isEmpty()) { "Ungültige Position" }
        dao.upsertCategory(CategoryEntity(plan.category))
        dao.upsertPlan(plan.toEntity())
    }

    suspend fun saveBooking(booking: Booking) {
        require(FinanceValidation.validate(booking).isEmpty()) { "Ungültige Buchung" }
        dao.upsertCategory(CategoryEntity(booking.category))
        dao.upsertBooking(booking.toEntity())
    }

    suspend fun deletePlan(id: String) = dao.deletePlan(id)

    suspend fun deleteBooking(id: String) = dao.deleteBooking(id)

    suspend fun saveBudget(budget: CategoryBudget) {
        require(budget.category.isNotBlank() && budget.category.length <= 50) { "Ungültige Kategorie" }
        require(budget.amountCents in 1..999_999_999_99L) { "Ungültiges Budget" }
        val normalized = budget.copy(category = budget.category.trim())
        dao.upsertCategory(CategoryEntity(normalized.category))
        dao.upsertBudget(normalized.toEntity())
    }

    suspend fun deleteBudget(category: String) = dao.deleteBudget(category)

    suspend fun addCategory(name: String) {
        require(name.isNotBlank() && name.length <= 50) { "Ungültige Kategorie" }
        dao.upsertCategory(CategoryEntity(name.trim()))
    }

    suspend fun renameCategory(oldName: String, newName: String) {
        require(newName.isNotBlank() && newName.length <= 50) { "Ungültige Kategorie" }
        dao.renameCategory(oldName, newName.trim())
    }

    suspend fun deleteCategory(name: String) {
        check(
            dao.planCategoryUsage(name) + dao.bookingCategoryUsage(name) +
                dao.budgetCategoryUsage(name) == 0,
        ) {
            "Die Kategorie wird noch verwendet."
        }
        dao.deleteCategoryRow(name)
    }

    suspend fun replaceAll(snapshot: FinanceSnapshot) {
        val planErrors = snapshot.plans.flatMap(FinanceValidation::validate)
        val bookingErrors = snapshot.bookings.flatMap(FinanceValidation::validate)
        require(planErrors.isEmpty() && bookingErrors.isEmpty()) { "Ungültige Importdaten" }
        require(snapshot.budgets.all {
            it.category.isNotBlank() && it.category.length <= 50 &&
                it.amountCents in 1..999_999_999_99L
        }) { "Ungültige Budgets" }
        val categories = (snapshot.categories + snapshot.plans.map { it.category } +
            snapshot.bookings.map { it.category } + snapshot.budgets.map { it.category })
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        dao.replaceAll(
            snapshot.plans.map(FinancePlan::toEntity),
            snapshot.bookings.map(Booking::toEntity),
            categories.map(::CategoryEntity),
            snapshot.budgets.map(CategoryBudget::toEntity),
        )
    }

    fun close() = database.close()
}
