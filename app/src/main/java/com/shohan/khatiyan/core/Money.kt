package com.shohan.khatiyan.core

import com.shohan.khatiyan.utilities.BnText
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money is stored everywhere as integer PAISA (Phase 17 — money precision).
 * ৳100.50 == 10050 paisa. Financial balance code paths never touch Double
 * arithmetic; only the explicit [parse]/[format] boundary converts user text.
 *
 * Upper bound keeps every sum of our aggregates inside Long comfortably
 * (Long.MAX ≈ 9.22e18 paisa ≈ 9.22e16 ৳; our cap is 99,999,999,999.99 ৳).
 */
object Money {

    /** Maximum accepted amount: ৳9,999,999,999.99 (≈ ten billion taka) */
    const val MAX_PAISA: Long = 999_999_999_999L

    const val DEFAULT_SYMBOL = "৳"

    /**
     * Parse user input (Bengali or ASCII digits, optional ৳/₹/$ symbols and
     * thousands separators, at most 2 decimals — extra decimals are rounded
     * HALF_UP, e.g. "10.999" -> ৳11.00). Returns null for anything that is
     * not a positive money value (empty, negative, letters, absurd size).
     */
    fun parse(text: String): Long? {
        val raw = BnText.fromBnDigits(text).trim()
        if (raw.isEmpty()) return null
        if (raw.contains('-')) return null
        val cleaned = buildString {
            for (c in raw) {
                if (c.isDigit() || c == '.') append(c)
            }
        }
        if (cleaned.isEmpty()) return null
        val dotParts = cleaned.split('.')
        if (dotParts.size > 2) return null
        val intPart = dotParts[0].ifEmpty { "0" }.trimStart('0').ifEmpty { "0" }
        if (intPart.length > 11) return null
        val fracRaw = dotParts.getOrNull(1) ?: ""
        if (fracRaw.length > 2 && fracRaw.trimEnd('0').length > 2) return null
        val frac = fracRaw.padEnd(2, '0').take(2)
        val paisa = try {
            intPart.toLong() * 100 + frac.toLong()
        } catch (e: NumberFormatException) {
            return null
        }
        if (paisa <= 0) return null
        if (paisa > MAX_PAISA) return null
        return paisa
    }

    /** Like [parse] but also accepts an explicit zero (useful for optional fees). */
    fun parseAllowZero(text: String): Long? {
        val t = BnText.fromBnDigits(text).trim()
        if (t.isEmpty()) return null
        val cleaned = buildString { for (c in t) if (c.isDigit() || c == '.') append(c) }
        val isZero = cleaned.isNotEmpty() && BigDecimal(cleaned).compareTo(BigDecimal.ZERO) == 0
        return if (isZero) 0L else parse(text)
    }

    /** Plain grouped string with decimals only when needed: "1,234.50", "700". */
    fun formatPlain(paisa: Long): String {
        val neg = paisa < 0
        val abs = if (paisa == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paisa)
        val taka = abs / 100
        val p = (abs % 100).toInt()
        val body = if (p == 0) {
            BnText.groupInteger(taka.toString())
        } else {
            BnText.groupInteger(taka.toString()) + "." + p.toString().padStart(2, '0')
        }
        return (if (neg) "-" else "") + body
    }

    /**
     * Display string for the UI.
     * @param symbol currency symbol prefix (from user settings, default ৳)
     * @param banglaDigits render numerals as ০১২৩৪৫৬৭৮৯ (default, matches the Bangla UI)
     */
    fun format(paisa: Long, symbol: String = DEFAULT_SYMBOL, banglaDigits: Boolean = true): String {
        val s = formatPlain(paisa)
        val withSymbol = if (symbol.isEmpty()) s else "$symbol$s"
        return if (banglaDigits) BnText.toBnDigits(withSymbol) else withSymbol
    }

    /** Count-only formatting, e.g. "৩টি". */
    fun bnNumber(n: Long): String = BnText.toBnDigits(n.toString())

    /** Exact integer division with HALF_UP rounding via BigDecimal (no float error). */
    fun divRound(totalPaisa: Long, divisor: Int): Long =
        BigDecimal(totalPaisa).divide(BigDecimal(divisor), 0, RoundingMode.HALF_UP).toLong()

    fun addClamped(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (e: ArithmeticException) {
            if ((a < 0) == (b < 0)) if (a < 0) Long.MIN_VALUE else Long.MAX_VALUE
            else if (a > b) a else b
        }
    }

    val ZERO: Long = 0L
}
