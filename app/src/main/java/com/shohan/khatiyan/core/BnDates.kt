package com.shohan.khatiyan.core

import com.shohan.khatiyan.utilities.BnText
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Date utilities. All stored dates are ISO-8601 "yyyy-MM-dd" LocalDate strings
 * (lexicographically sortable, timezone-free — critical for due-date math and
 * month/year boundaries tested in Phase 37). Display happens only via this
 * object so the whole app shares one natural-Bangla voice.
 */
object BnDates {

    val MONTHS = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
    )

    val MONTHS_SHORT = listOf(
        "জানু", "ফেব্রু", "মার্চ", "এপ্রি", "মে", "জুন",
        "জুলা", "আগ", "সেপ্টে", "অক্টো", "নভে", "ডিসে",
    )

    /** Index 0 = Monday … 6 = Sunday (java.time DayOfWeek value - 1). */
    val WEEKDAYS_SHORT = listOf("সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি", "রবি")

    fun today(): LocalDate = LocalDate.now()

    fun toIso(d: LocalDate): String = d.toString()

    fun fromIso(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return try {
            LocalDate.parse(s)
        } catch (e: Exception) {
            null
        }
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    fun bn(n: Long): String = BnText.toBnDigits(n.toString())

    /** "৮ সেপ্টেম্বর ২০২৬" */
    fun formatLong(d: LocalDate): String =
        "${bn(d.dayOfMonth)} ${MONTHS[d.monthValue - 1]} ${bn(d.year)}"

    /** "৮ সেপ্টে ২০২৬" */
    fun formatShort(d: LocalDate): String =
        "${bn(d.dayOfMonth)} ${MONTHS_SHORT[d.monthValue - 1]} ${bn(d.year)}"

    /** "সেপ্টেম্বর ২০২৬" */
    fun formatMonthYear(y: Int, m: Int): String = "${MONTHS[m - 1]} ${bn(y.toLong())}"

    fun formatMonthYear(ym: YearMonth): String = formatMonthYear(ym.year, ym.monthValue)

    /** Relative label used everywhere for due dates (natural Bangla). */
    fun relative(d: LocalDate, from: LocalDate = today()): String {
        val diff = daysBetween(from, d)
        return when {
            diff == 0L -> "আজ"
            diff == 1L -> "আগামীকাল"
            diff == -1L -> "গতকাল"
            diff > 1L -> "${bn(diff)} দিন বাকি"
            else -> "${bn(-diff)} দিন আগে"
        }
    }

    /** Compact "আজ / কাল / ৩ দিন আগে" style used in dense list rows. */
    fun relativeCompact(d: LocalDate, from: LocalDate = today()): String = relative(d, from)

    fun weekdayShort(d: LocalDate): String = WEEKDAYS_SHORT[d.dayOfWeek.value - 1]

    fun yearStart(d: LocalDate): LocalDate = LocalDate.of(d.year, 1, 1)

    fun monthStart(d: LocalDate): LocalDate = LocalDate.of(d.year, d.month, 1)

    fun monthEnd(d: LocalDate): LocalDate = d.withDayOfMonth(d.lengthOfMonth())

    fun previousMonthSameDay(d: LocalDate, offsetDays: Long): LocalDate = d.plusDays(offsetDays)
}
