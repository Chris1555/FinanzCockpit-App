package de.finanzcockpit.app.backup

import de.finanzcockpit.app.domain.FinanceCalculator
import de.finanzcockpit.app.domain.CategoryBudget
import de.finanzcockpit.app.domain.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceJsonCodecTest {
    @Test
    fun `alte Version 4 wird verlustfrei in Cent importiert`() {
        val json = """
            {
              "schemaVersion": 4,
              "plans": [{
                "id": "plan-1", "name": "Beispiel", "type": "Ausgabe",
                "category": "Test", "costType": "Fix", "frequency": "Jährlich",
                "amount": 123.45, "status": "Aktiv", "note": ""
              }],
              "bookings": [],
              "settings": {"categories": ["Test"]}
            }
        """.trimIndent().toByteArray()

        val snapshot = FinanceJsonCodec.parseLegacy(json)

        assertEquals(12_345, snapshot.plans.single().amountCents)
        assertEquals(Frequency.YEARLY, snapshot.plans.single().frequency)
        assertEquals(0, FinanceCalculator.monthlyTotals(snapshot.plans).expenseCents)
    }

    @Test
    fun `natives Sicherungsformat durchlaeuft encode und parse`() {
        val legacy = """
            {"schemaVersion":4,"plans":[],"bookings":[],"settings":{"categories":["Wohnen"]}}
        """.trimIndent().toByteArray()
        val snapshot = FinanceJsonCodec.parseLegacy(legacy)

        val restored = FinanceJsonCodec.parseBackup(FinanceJsonCodec.encodeBackup(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun `natives Sicherungsformat erhaelt Faelligkeitsmonat`() {
        val legacy = """
            {
              "schemaVersion": 4,
              "plans": [{
                "id": "plan-1", "name": "Jahreskosten", "type": "Ausgabe",
                "category": "Test", "costType": "Fix", "frequency": "Jährlich",
                "amount": 100, "status": "Aktiv", "note": ""
              }],
              "bookings": [], "settings": {"categories": ["Test"]}
            }
        """.trimIndent().toByteArray()
        val snapshot = FinanceJsonCodec.parseLegacy(legacy).let {
            it.copy(plans = it.plans.map { plan -> plan.copy(dueMonth = 11) })
        }

        val restored = FinanceJsonCodec.parseBackup(FinanceJsonCodec.encodeBackup(snapshot))

        assertEquals(11, restored.plans.single().dueMonth)
    }

    @Test
    fun `natives Sicherungsformat erhaelt Kategorie Budgets`() {
        val legacy = """
            {"schemaVersion":4,"plans":[],"bookings":[],"settings":{"categories":["Wohnen"]}}
        """.trimIndent().toByteArray()
        val snapshot = FinanceJsonCodec.parseLegacy(legacy).copy(
            budgets = listOf(CategoryBudget("Wohnen", 95_000)),
        )

        val restored = FinanceJsonCodec.parseBackup(FinanceJsonCodec.encodeBackup(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun `alte native Sicherung ohne Budgets bleibt lesbar`() {
        val oldBackup = """
            {"format":"FinanzCockpitBackup","schemaVersion":2,"plans":[],"bookings":[],"categories":["Wohnen"]}
        """.trimIndent().toByteArray()

        val restored = FinanceJsonCodec.parseBackup(oldBackup)

        assertEquals(emptyList<CategoryBudget>(), restored.budgets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `doppelte IDs werden abgelehnt`() {
        val json = """
            {
              "schemaVersion": 4,
              "plans": [
                {"id":"x","name":"A","type":"Ausgabe","category":"T","costType":"Fix","frequency":"Monatlich","amount":1,"status":"Aktiv"},
                {"id":"x","name":"B","type":"Ausgabe","category":"T","costType":"Fix","frequency":"Monatlich","amount":2,"status":"Aktiv"}
              ],
              "bookings": [], "settings": {"categories":[]}
            }
        """.trimIndent().toByteArray()
        FinanceJsonCodec.parseLegacy(json)
    }
}
