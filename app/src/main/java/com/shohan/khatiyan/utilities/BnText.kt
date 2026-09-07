package com.shohan.khatiyan.utilities

/**
 * Bengali text helpers shared by money/date formatting and the UI layer.
 * Pure JVM code (no Android imports) so it is unit-testable on any machine.
 */
object BnText {

    private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    /** "1,234" -> "১,২৩৪" */
    fun toBnDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c in '0'..'9') append(BN_DIGITS[c - '0']) else append(c)
        }
    }

    /** "১২৩" -> "123" (so Bengali-typed amounts can be parsed). */
    fun fromBnDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            if (c in '০'..'৯') append('0' + (c - '০')) else append(c)
        }
    }

    /** Group an integer string with Western thousands separators: "1234567" -> "1,234,567". */
    fun groupInteger(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val sb = StringBuilder()
        val rem = intPart.length % 3
        if (rem != 0) {
            sb.append(intPart, 0, rem)
            if (intPart.length > rem) sb.append(',')
        }
        var i = rem
        while (i < intPart.length) {
            sb.append(intPart, i, i + 3)
            i += 3
            if (i < intPart.length) sb.append(',')
        }
        return sb.toString()
    }

    /** Truncate long text for list rows without breaking Bengali words mid-emoji. */
    fun ellipsize(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
}
