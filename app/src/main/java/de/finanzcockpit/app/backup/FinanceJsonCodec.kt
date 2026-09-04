package de.finanzcockpit.app.backup

import de.finanzcockpit.app.domain.Booking
import de.finanzcockpit.app.domain.BookingType
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.CostType
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.FinanceSnapshot
import de.finanzcockpit.app.domain.FinanceValidation
import de.finanzcockpit.app.domain.Frequency
import de.finanzcockpit.app.domain.PlanStatus
import de.finanzcockpit.app.domain.PlanType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

object FinanceJsonCodec {
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val MAX_ITEMS = 20_000

    fun parseLegacy(bytes: ByteArray): FinanceSnapshot {
        require(bytes.size <= MAX_FILE_BYTES) { "Importdatei ist zu groß." }
        val root = parseObject(bytes)
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..4) { "Nicht unterstützte alte Datenversion." }
        return parseLegacyRoot(root)
    }

    fun encodeBackup(snapshot: FinanceSnapshot): ByteArray {
        val root = JSONObject()
            .put("format", "FinanzCockpitBackup")
            .put("schemaVersion", 3)
            .put("plans", JSONArray().apply {
                snapshot.plans.forEach { plan ->
                    put(
                        JSONObject()
                            .put("id", plan.id)
                            .put("name", plan.name)
                            .put("type", plan.type.name)
                            .put("category", plan.category)
                            .put("costType", plan.costType.name)
                            .put("frequency", plan.frequency.name)
                            .put("amountCents", plan.amountCents)
                            .put("status", plan.status.name)
                            .put("note", plan.note)
                            .put("dueMonth", plan.dueMonth ?: JSONObject.NULL),
                    )
                }
            })
            .put("bookings", JSONArray().apply {
                snapshot.bookings.forEach { booking ->
                    put(
                        JSONObject()
                            .put("id", booking.id)
                            .put("date", booking.date.toString())
                            .put("name", booking.name)
                            .put("type", booking.type.name)
                            .put("category", booking.category)
                            .put("amountCents", booking.amountCents)
                            .put("note", booking.note),
                    )
                }
            })
            .put("categories", JSONArray(snapshot.categories))
            .put("budgets", JSONArray().apply {
                snapshot.budgets.forEach { budget ->
                    put(
                        JSONObject()
                            .put("category", budget.category)
                            .put("amountCents", budget.amountCents),
                    )
                }
            })
        return root.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_FILE_BYTES) { "Sicherung ist zu groß." }
        }
    }

    fun parseBackup(bytes: ByteArray): FinanceSnapshot {
        require(bytes.size <= MAX_FILE_BYTES) { "Sicherungsinhalt ist zu groß." }
        val root = parseObject(bytes)
        require(root.optString("format") == "FinanzCockpitBackup") { "Ungültiges Sicherungsformat." }
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..3) { "Nicht unterstützte Sicherungsversion." }
        val plans = root.requiredArray("plans").mapObjects(::parseNativePlan)
        val bookings = root.requiredArray("bookings").mapObjects(::parseNativeBooking)
        val categories = root.requiredArray("categories").mapStrings()
        val budgets = if (schemaVersion >= 3) {
            root.requiredArray("budgets").mapObjects(::parseNativeBudget)
        } else {
            emptyList()
        }
        return validatedSnapshot(plans, bookings, categories, budgets)
    }

    private fun parseLegacyRoot(root: JSONObject): FinanceSnapshot {
        val plans = root.requiredArray("plans").mapObjects { item ->
            FinancePlan(
                id = item.optionalId("plan"),
                name = item.requiredString("name", 120),
                type = PlanType.fromLabel(item.requiredString("type", 20))
                    ?: throw IllegalArgumentException("Ungültige Positionsart."),
                category = item.requiredString("category", 50),
                costType = CostType.fromLabel(item.optString("costType", "Fix"))
                    ?: throw IllegalArgumentException("Ungültige Kostenart."),
                frequency = Frequency.fromLabel(item.requiredString("frequency", 30))
                    ?: throw IllegalArgumentException("Ungültiger Rhythmus."),
                amountCents = legacyAmountToCents(item.get("amount")),
                status = PlanStatus.fromLabel(item.requiredString("status", 20))
                    ?: throw IllegalArgumentException("Ungültiger Status."),
                note = item.optLimitedString("note", 2_000),
                dueMonth = null,
            )
        }
        val bookings = root.requiredArray("bookings").mapObjects { item ->
            Booking(
                id = item.optionalId("booking"),
                date = FinanceValidation.parseIsoDate(item.requiredString("date", 10)),
                name = item.requiredString("name", 120),
                type = BookingType.fromLabel(item.requiredString("type", 20))
                    ?: throw IllegalArgumentException("Ungültige Buchungsart."),
                category = item.requiredString("category", 50),
                amountCents = legacyAmountToCents(item.get("amount")),
                note = item.optLimitedString("note", 2_000),
            )
        }
        val settingsCategories = root.optJSONObject("settings")
            ?.optJSONArray("categories")
            ?.mapStrings()
            .orEmpty()
        return validatedSnapshot(plans, bookings, settingsCategories)
    }

    private fun parseNativePlan(item: JSONObject) = FinancePlan(
        id = item.requiredString("id", 80),
        name = item.requiredString("name", 120),
        type = enumValue<PlanType>(item, "type"),
        category = item.requiredString("category", 50),
        costType = enumValue<CostType>(item, "costType"),
        frequency = enumValue<Frequency>(item, "frequency"),
        amountCents = item.requiredCents(),
        status = enumValue<PlanStatus>(item, "status"),
        note = item.optLimitedString("note", 2_000),
        dueMonth = item.optionalMonth(),
    )

    private fun parseNativeBooking(item: JSONObject) = Booking(
        id = item.requiredString("id", 80),
        date = LocalDate.parse(item.requiredString("date", 10)),
        name = item.requiredString("name", 120),
        type = enumValue<BookingType>(item, "type"),
        category = item.requiredString("category", 50),
        amountCents = item.requiredCents(),
        note = item.optLimitedString("note", 2_000),
    )

    private fun parseNativeBudget(item: JSONObject) = CategoryBudget(
        category = item.requiredString("category", 50),
        amountCents = item.requiredCents(),
    )

    private fun validatedSnapshot(
        plans: List<FinancePlan>,
        bookings: List<Booking>,
        suppliedCategories: List<String>,
        budgets: List<CategoryBudget> = emptyList(),
    ): FinanceSnapshot {
        require(plans.size <= MAX_ITEMS && bookings.size <= MAX_ITEMS) { "Zu viele Datensätze." }
        require(plans.map { it.id }.distinct().size == plans.size) { "Doppelte Positions-ID." }
        require(bookings.map { it.id }.distinct().size == bookings.size) { "Doppelte Buchungs-ID." }
        require(budgets.size <= MAX_ITEMS) { "Zu viele Budgets." }
        require(budgets.map { it.category.lowercase() }.distinct().size == budgets.size) {
            "Doppelte Budget-Kategorie."
        }
        require(budgets.all { it.amountCents > 0 }) { "Ungültiges Budget." }
        val errors = plans.flatMap(FinanceValidation::validate) +
            bookings.flatMap(FinanceValidation::validate)
        require(errors.isEmpty()) { errors.take(5).joinToString("\n") }
        val categories = (suppliedCategories + plans.map { it.category } + bookings.map { it.category } +
            budgets.map { it.category })
            .map(String::trim)
            .filter(String::isNotEmpty)
            .onEach { require(it.length <= 50) { "Kategorie ist zu lang." } }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        return FinanceSnapshot(plans, bookings, categories, budgets)
    }

    private fun parseObject(bytes: ByteArray): JSONObject = try {
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains('\u0000')) { "Ungültige Textkodierung." }
        JSONObject(text)
    } catch (error: JSONException) {
        throw IllegalArgumentException("Ungültige JSON-Datei.", error)
    }

    private fun legacyAmountToCents(value: Any): Long = try {
        BigDecimal(value.toString())
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .also { require(it in 0..999_999_999_99L) }
    } catch (_: Exception) {
        throw IllegalArgumentException("Ungültiger Betrag.")
    }

    private fun JSONObject.requiredCents(): Long {
        val value = try {
            getLong("amountCents")
        } catch (_: Exception) {
            throw IllegalArgumentException("Ungültiger Betrag.")
        }
        require(value in 0..999_999_999_99L) { "Ungültiger Betrag." }
        return value
    }

    private fun JSONObject.optionalId(prefix: String): String {
        val value = optString("id").ifBlank { "$prefix-${UUID.randomUUID()}" }
        require(value.length <= 80) { "Ungültige ID." }
        return value
    }

    private fun JSONObject.requiredString(key: String, maxLength: Int): String {
        val value = optString(key).trim()
        require(value.isNotEmpty() && value.length <= maxLength) { "Ungültiges Feld: $key" }
        return value
    }

    private fun JSONObject.optLimitedString(key: String, maxLength: Int): String {
        val value = optString(key, "").trim()
        require(value.length <= maxLength) { "Feld ist zu lang: $key" }
        return value
    }

    private fun JSONObject.optionalMonth(): Int? {
        val raw = opt("dueMonth")
        if (raw == null || raw == JSONObject.NULL) return null
        val month = when (raw) {
            is Number -> raw.toInt()
            else -> throw IllegalArgumentException("Ungültiger Fälligkeitsmonat.")
        }
        require(month in 1..12) { "Ungültiger Fälligkeitsmonat." }
        return month
    }

    private fun JSONObject.requiredArray(key: String): JSONArray = optJSONArray(key)
        ?: throw IllegalArgumentException("Liste fehlt: $key")

    private inline fun <reified T : Enum<T>> enumValue(item: JSONObject, key: String): T = try {
        enumValueOf<T>(item.requiredString(key, 30))
    } catch (_: Exception) {
        throw IllegalArgumentException("Ungültiges Feld: $key")
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        require(length() <= MAX_ITEMS) { "Zu viele Datensätze." }
        return List(length()) { index ->
            val value = optJSONObject(index) ?: throw IllegalArgumentException("Ungültiger Datensatz.")
            transform(value)
        }
    }

    private fun JSONArray.mapStrings(): List<String> {
        require(length() <= MAX_ITEMS) { "Zu viele Kategorien." }
        return List(length()) { index ->
            val value = optString(index).trim()
            require(value.isNotEmpty() && value.length <= 50) { "Ungültige Kategorie." }
            value
        }
    }
}
