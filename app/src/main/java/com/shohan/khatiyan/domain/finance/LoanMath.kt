package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Flat-rate loan math used when the user lets খতিয়ান compute the total payable.
 * Convention (documented in DATABASE.md): simple interest on the original
 * principal over the loan term measured from disbursement to maturity.
 * All intermediate steps use BigDecimal; results are exact paisa (Long).
 */
object LoanMath {

    /** "12.5" → 1250 basis points. Returns null for invalid input. */
    fun parseRateBps(text: String): Int? {
        val t = com.shohan.khatiyan.utilities.BnText.fromBnDigits(text.trim())
        val cleaned = buildString { for (c in t) if (c.isDigit() || c == '.') append(c) }
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val value = try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            return null
        }
        if (value < BigDecimal.ZERO || value > BigDecimal("1000")) return null
        return value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    fun ratePercentLabel(bps: Int): String {
        val whole = bps / 100
        val frac = bps % 100
        return if (frac == 0) whole.toString() else "$whole." + frac.toString().padStart(2, '0')
    }

    /** years (4 dp) between disbursement and maturity, using a 365-day year. */
    fun termYears(from: LocalDate, to: LocalDate): BigDecimal {
        val days = ChronoUnit.DAYS.between(from, to).coerceAtLeast(0L)
        return BigDecimal(days).divide(BigDecimal(365), 4, RoundingMode.HALF_UP)
    }

    /** Flat interest: principal × rate% × years, HALF_UP to paisa. */
    fun flatInterestPaisa(principalPaisa: Long, rateBps: Int, from: LocalDate, to: LocalDate): Long {
        if (principalPaisa <= 0L || rateBps <= 0) return 0L
        val years = termYears(from, to)
        return BigDecimal(principalPaisa)
            .multiply(BigDecimal(rateBps))
            .divide(BigDecimal(10_000), 10, RoundingMode.HALF_UP)
            .multiply(years)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** totalPayable = principal + flat interest + processing fee. */
    fun totalPayablePaisa(principalPaisa: Long, rateBps: Int, from: LocalDate, to: LocalDate, processingFeePaisa: Long): Long {
        val interest = flatInterestPaisa(principalPaisa, rateBps, from, to)
        return Money.addClamped(Money.addClamped(principalPaisa, interest), processingFeePaisa.coerceAtLeast(0L))
    }
}
