package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.domain.model.DueItem
import com.shohan.khatiyan.domain.model.DueStatus
import com.shohan.khatiyan.domain.model.ObligationKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DueEngineTest {

    private val today = LocalDate.of(2024, 3, 15)

    private fun item(due: LocalDate, paisa: Long) = DueItem(
        kind = ObligationKind.LOAN,
        entityId = 1,
        entityLabel = "গ্রামীণ ব্যাংক",
        detailLabel = "কিস্তি",
        dueDate = due,
        amountPaisa = paisa,
        status = DueEngine.statusFor(due, today),
    )

    @Test
    fun statusBoundaries() {
        assertEquals(DueStatus.OVERDUE, DueEngine.statusFor(today.minusDays(1), today))
        assertEquals(DueStatus.DUE_TODAY, DueEngine.statusFor(today, today))
        assertEquals(DueStatus.UPCOMING, DueEngine.statusFor(today.plusDays(1), today))
    }

    @Test
    fun windowTotals() {
        val items = listOf(
            item(today, 10_000),
            item(today, 5_000),
            item(today.plusDays(1), 7_000),
            item(today.plusDays(4), 3_000),
            item(today.plusDays(60), 9_000), // far future — outside 7-day & month windows
            item(today.minusDays(3), 4_000),
        )
        val w = DueEngine.totals(items, today)
        assertEquals(2, w.today.count)
        assertEquals(15_000L, w.today.paisa)
        assertEquals(1, w.tomorrow.count)
        assertEquals(7_000L, w.tomorrow.paisa)
        assertEquals(4, w.next7.count) // today(2) + tomorrow + 4 days out
        assertEquals(25_000L, w.next7.paisa)
        assertEquals(1, w.overdue.count)
        assertEquals(4_000L, w.overdue.paisa)
        assertEquals(4, w.thisMonth.count) // same-month upcoming only (not before today)
    }

    @Test
    fun collectSortsByDueDateThenAmountDesc() {
        val credits = listOf(
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 1, dueDate = today.plusDays(2), outstandingPaisa = 1_000),
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 2, dueDate = today, outstandingPaisa = 5_000),
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 3, dueDate = today, outstandingPaisa = 9_000),
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 4, dueDate = today.plusDays(90), outstandingPaisa = 7_000),
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 5, dueDate = null, outstandingPaisa = 8_000),
            DueEngine.CreditDue(shopId = 1, shopName = "মুদি", creditId = 6, dueDate = today, outstandingPaisa = 0),
        )
        val out = DueEngine.collect(today, 30, credits, emptyList(), emptyList(), emptyList())
        assertEquals(listOf(9_000L, 5_000L, 1_000L), out.map { it.amountPaisa })
        assertEquals(ObligationKind.SHOP, out.first().kind)
    }
}
