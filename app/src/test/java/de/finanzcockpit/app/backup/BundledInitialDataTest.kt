package de.finanzcockpit.app.backup

import de.finanzcockpit.app.domain.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledInitialDataTest {
    @Test
    fun `eingebaute HTML Ausgangsdaten sind vollstaendig und gueltig`() {
        val file = File("src/parallelTest/assets/initial_finance_data.json")
        assertTrue("Ausgangsdaten fehlen", file.isFile)

        val snapshot = FinanceJsonCodec.parseLegacy(file.readBytes())
        val totals = FinanceCalculator.monthlyTotals(snapshot.plans)

        assertEquals(24, snapshot.plans.size)
        assertEquals(0, snapshot.bookings.size)
        assertEquals(9, snapshot.categories.size)
        assertEquals(354_682, totals.incomeCents)
        assertEquals(113_514, totals.regularExpenseCents)
        assertEquals(125_000, totals.savingCents)
        assertEquals(116_168, totals.freeCents)
    }
}
