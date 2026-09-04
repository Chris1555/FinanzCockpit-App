package de.finanzcockpit.app.data

import de.finanzcockpit.app.domain.CostType
import de.finanzcockpit.app.domain.FinancePlan
import de.finanzcockpit.app.domain.Frequency
import de.finanzcockpit.app.domain.PlanStatus
import de.finanzcockpit.app.domain.PlanType
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test
    fun `geaenderter Rhythmus bleibt beim Speichern erhalten`() {
        val yearly = FinancePlan(
            id = "plan-rhythm-test",
            name = "Versicherung",
            type = PlanType.EXPENSE,
            category = "Versicherung",
            costType = CostType.FIXED,
            frequency = Frequency.YEARLY,
            amountCents = 120_00,
            status = PlanStatus.ACTIVE,
            note = "",
            dueMonth = 1,
        )

        val saved = yearly.copy(frequency = Frequency.QUARTERLY).toEntity().toDomain()

        assertEquals(Frequency.QUARTERLY, saved.frequency)
        assertEquals("Vierteljährlich", saved.frequency.label)
    }
}
