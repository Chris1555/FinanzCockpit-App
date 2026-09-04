package de.finanzcockpit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `deutsche Betraege werden exakt in Cent umgewandelt`() {
        assertEquals(123_45, parseEuroCents("123,45"))
        assertEquals(123_45, parseEuroCents("123.45"))
        assertEquals(123_456_78, parseEuroCents("123.456,78"))
        assertEquals(120_00, parseEuroCents("120"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mehr als zwei Nachkommastellen werden abgelehnt`() {
        parseEuroCents("1,234")
    }
}
