package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.domain.model.DueItem
import com.shohan.khatiyan.domain.model.DueStatus
import com.shohan.khatiyan.domain.model.ObligationKind
import java.time.LocalDate

/**
 * Unified upcoming-payment engine (Phase 19). Pure function over already
 * fetched data so it can be unit tested against exact day/month boundaries.
 *
 * Only obligations with money still open appear:
 *  - shops: per-credit outstanding after FIFO payment allocation, when the
 *    credit carries a due date within the window (or overdue)
 *  - loan/EMI: unpaid installments dated inside the window (or overdue)
 *  - personal: debts with expected return date inside the window (or overdue)
 *
 * Results sorted by due date, then amount descending.
 */
object DueEngine {

    data class CreditDue(
        val shopId: Long,
        val shopName: String,
        val creditId: Long,
        val dueDate: LocalDate?,
        val outstandingPaisa: Long,
    )

    data class SlotDue(
        val obligationId: Long,
        val title: String,
        val detail: String,
        val dueDate: LocalDate,
        val openPaisa: Long,
    )

    data class PersonalDue(
        val debtId: Long,
        val personName: String,
        val dueDate: LocalDate?,
        val outstandingPaisa: Long,
    )

    fun collect(
        today: LocalDate,
        windowDays: Long,
        shopCredits: List<CreditDue>,
        loanSlots: List<SlotDue>,
        emiSlots: List<SlotDue>,
        personalDebts: List<PersonalDue>,
    ): List<DueItem> {
        val horizon = today.plusDays(windowDays)
        val items = ArrayList<DueItem>()

        for (c in shopCredits) {
            val due = c.dueDate ?: continue
            if (c.outstandingPaisa <= 0L) continue
            if (due.isAfter(horizon)) continue
            items += DueItem(
                kind = ObligationKind.SHOP,
                entityId = c.shopId,
                entityLabel = c.shopName,
                detailLabel = "বাকি",
                dueDate = due,
                amountPaisa = c.outstandingPaisa,
                status = statusFor(due, today),
            )
        }

        fun slots(list: List<SlotDue>, kind: ObligationKind) {
            for (s in list) {
                if (s.openPaisa <= 0L) continue
                if (s.dueDate.isAfter(horizon)) continue
                items += DueItem(
                    kind = kind,
                    entityId = s.obligationId,
                    entityLabel = s.title,
                    detailLabel = s.detail,
                    dueDate = s.dueDate,
                    amountPaisa = s.openPaisa,
                    status = statusFor(s.dueDate, today),
                )
            }
        }
        slots(loanSlots, ObligationKind.LOAN)
        slots(emiSlots, ObligationKind.EMI)

        for (d in personalDebts) {
            val due = d.dueDate ?: continue
            if (d.outstandingPaisa <= 0L) continue
            if (due.isAfter(horizon)) continue
            items += DueItem(
                kind = ObligationKind.PERSONAL,
                entityId = d.debtId,
                entityLabel = d.personName,
                detailLabel = "ফেরত দিতে হবে",
                dueDate = due,
                amountPaisa = d.outstandingPaisa,
                status = statusFor(due, today),
            )
        }

        return items.sortedWith(compareBy({ it.dueDate }, { -it.amountPaisa }))
    }

    fun statusFor(dueDate: LocalDate, today: LocalDate): DueStatus = when {
        dueDate.isBefore(today) -> DueStatus.OVERDUE
        dueDate == today -> DueStatus.DUE_TODAY
        else -> DueStatus.UPCOMING
    }

    data class WindowTotals(val count: Int, val paisa: Long)

    fun totals(items: List<DueItem>, today: LocalDate): Windows {
        val todayItems = items.filter { it.dueDate == today }
        val tomorrow = items.filter { it.dueDate == today.plusDays(1) }
        val in7 = items.filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(today.plusDays(6)) }
        val month = items.filter {
            !it.dueDate.isBefore(today) &&
                java.time.YearMonth.from(it.dueDate) == java.time.YearMonth.from(today)
        }
        val overdue = items.filter { it.dueDate.isBefore(today) }
        fun w(list: List<DueItem>) = WindowTotals(list.size, list.fold(0L) { a, b -> a + b.amountPaisa })
        return Windows(w(todayItems), w(tomorrow), w(in7), w(month), w(overdue))
    }

    data class Windows(
        val today: WindowTotals,
        val tomorrow: WindowTotals,
        val next7: WindowTotals,
        val thisMonth: WindowTotals,
        val overdue: WindowTotals,
    )
}
