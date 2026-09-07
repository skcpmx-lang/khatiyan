package com.shohan.khatiyan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parse_bengaliDigitsAndSeparators() {
        assertEquals(123_450L, Money.parse("১,২৩৪.৫০"))
        assertEquals(123_450L, Money.parse("1234.50"))
        assertEquals(100_000L, Money.parse("1000"))
        assertEquals(1050L, Money.parse("10.5"))
        assertEquals(1000L, Money.parse("10."))
    }

    @Test
    fun parse_symbolPrefixStripped() {
        assertEquals(50_000L, Money.parse("৳500"))
        assertEquals(50_000L, Money.parse("৳ 5,00.00"))
    }

    @Test
    fun parse_rejectsNonPositiveOrAbsurd() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("0.00"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("1.999")) // more than 2 real decimals
        assertNull(Money.parse("100000000000")) // int part > 11 digits
    }

    @Test
    fun parseAllowZero_acceptsExplicitZero() {
        assertEquals(0L, Money.parseAllowZero("0"))
        assertEquals(0L, Money.parseAllowZero("০"))
        assertEquals(1200L, Money.parseAllowZero("12"))
        assertNull(Money.parseAllowZero(""))
    }

    @Test
    fun formatPlain_groupsAndKeepsDecimalsOnlyWhenNeeded() {
        assertEquals("700", Money.formatPlain(70_000L))
        assertEquals("1,234.50", Money.formatPlain(123_450L))
        assertEquals("0", Money.formatPlain(0L))
        assertEquals("-1.05", Money.formatPlain(-105L))
    }

    @Test
    fun format_banglaDigitsByDefault() {
        assertEquals("৳৭০০", Money.format(70_000L))
        assertEquals("৳১,২৩৪.৫০", Money.format(123_450L))
        assertEquals("৳৭০০", Money.format(70_000L, banglaDigits = true))
        assertEquals("700", Money.format(70_000L, symbol = "", banglaDigits = false))
    }

    @Test
    fun divRound_halfUpExact() {
        assertEquals(33_333L, Money.divRound(100_000L, 3))
        assertEquals(33_334L, Money.divRound(100_001L, 3))
        assertEquals(16_667L, Money.divRound(100_000L, 6))
    }

    @Test
    fun addClamped_neverOverflows() {
        assertEquals(12L, Money.addClamped(5L, 7L))
        assertEquals(Long.MAX_VALUE, Money.addClamped(Long.MAX_VALUE - 1, 5L))
        assertEquals(Long.MIN_VALUE, Money.addClamped(Long.MIN_VALUE + 1, -5L))
    }
}
