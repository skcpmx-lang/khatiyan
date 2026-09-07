package com.shohan.khatiyan.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BnDatesTest {

    @Test
    fun isoRoundTrip_andRejectsInvalid() {
        val d = LocalDate.of(2024, 12, 5)
        assertEquals("2024-12-05", BnDates.toIso(d))
        assertEquals(d, BnDates.fromIso("2024-12-05"))
        assertNull(BnDates.fromIso(null))
        assertNull(BnDates.fromIso(""))
        assertNull(BnDates.fromIso("2024-02-30"))
        assertNull(BnDates.fromIso("garbage"))
    }

    @Test
    fun leapDaySupported() {
        assertEquals(LocalDate.of(2024, 2, 29), BnDates.fromIso("2024-02-29"))
    }

    @Test
    fun daysBetween_andFormats() {
        assertEquals(30L, BnDates.daysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
        assertEquals(-1L, BnDates.daysBetween(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 1, 30)))
        assertEquals("৫ জানু ২০২৪", BnDates.formatShort(LocalDate.of(2024, 1, 5)))
    }

    @Test
    fun relative_labelsInBangla() {
        val today = LocalDate.of(2024, 6, 15)
        assertEquals("আজ", BnDates.relative(today, today))
        assertEquals("আগামীকাল", BnDates.relative(today.plusDays(1), today))
        assertEquals("গতকাল", BnDates.relative(today.minusDays(1), today))
        assertEquals("৫ দিন বাকি", BnDates.relative(today.plusDays(5), today))
        assertEquals("৩ দিন আগে", BnDates.relative(today.minusDays(3), today))
        assertEquals("৫ দিন বাকি", BnDates.relativeCompact(today.plusDays(5), today))
    }

    @Test
    fun monthHelpers() {
        val d = LocalDate.of(2024, 2, 15)
        assertEquals(LocalDate.of(2024, 2, 1), BnDates.monthStart(d))
        assertEquals(LocalDate.of(2024, 2, 29), BnDates.monthEnd(d))
        assertEquals(LocalDate.of(2024, 1, 1), BnDates.yearStart(d))
    }
}
