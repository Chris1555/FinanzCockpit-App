package de.finanzcockpit.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private val euroFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

fun Long.formatEuro(): String = synchronized(euroFormatter) {
    euroFormatter.format(BigDecimal.valueOf(this, 2))
}

fun parseEuroCents(value: String): Long {
    val input = value.trim()
    require(input.isNotBlank()) { "Betrag fehlt." }
    return try {
        val normalized = when {
            ',' in input -> {
                require(input.count { it == ',' } == 1)
                require(input.substringAfter(',').length in 1..2)
                input.replace(".", "").replace(',', '.')
            }
            '.' in input -> {
                val parts = input.split('.')
                when {
                    parts.size == 2 && parts[1].length in 1..2 -> input
                    parts.size >= 2 && parts.drop(1).all { it.length == 3 } -> parts.joinToString("")
                    else -> throw IllegalArgumentException()
                }
            }
            else -> input
        }
        BigDecimal(normalized)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .also { require(it in 0..999_999_999_99L) }
    } catch (_: Exception) {
        throw IllegalArgumentException("Bitte einen gültigen Betrag mit höchstens zwei Nachkommastellen eingeben.")
    }
}

fun Long.toInputAmount(): String = BigDecimal.valueOf(this, 2).toPlainString().replace('.', ',')
