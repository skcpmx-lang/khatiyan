package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.domain.finance.Allocation
import com.shohan.khatiyan.domain.finance.DueEngine
import com.shohan.khatiyan.domain.model.DueItem
import java.time.LocalDate

/**
 * Pulls raw rows from every book and feeds the pure [DueEngine] (Phase 19).
 * Shop per-credit outstanding uses FIFO allocation of shop payments so a due
 * date on one bill means exactly "that bill's open amount".
 */
class DueRepository(private val db: KhatiyanDatabase) {

    suspend fun collect(
        today: LocalDate = BnDates.today(),
        windowDays: Long = 45,
    ): List<DueItem> {
        val shopDao = db.shopDao()
        val shops = shopDao.getActiveShopRefs()
        val creditDues = shops.flatMap { ref ->
            val credits = shopDao.getCredits(ref.id).sortedWith(compareBy({ it.dateIso }, { it.id }))
            if (credits.none { it.dueDateIso != null }) return@flatMap emptyList()
            val paid = shopDao.getPayments(ref.id).fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
            val buckets = credits.map { Allocation.Bucket(it.id, it.totalPaisa, 0) }
            val paidMap = Allocation.fifo(buckets, paid)
            credits.map { c ->
                DueEngine.CreditDue(
                    shopId = ref.id,
                    shopName = ref.name,
                    creditId = c.id,
                    dueDate = BnDates.fromIso(c.dueDateIso),
                    outstandingPaisa = c.totalPaisa - (paidMap[c.id] ?: 0L),
                )
            }
        }

        val loanDao = db.loanDao()
        val loans = loanDao.getActiveLoans().associateBy { it.id }
        val loanSlots = loanDao.getOpenInstallmentsActive().mapNotNull { i ->
            val loan = loans[i.loanId] ?: return@mapNotNull null
            val due = BnDates.fromIso(i.dueIso) ?: return@mapNotNull null
            DueEngine.SlotDue(
                obligationId = loan.id,
                title = loan.loanName.ifBlank { loan.institution },
                detail = "লোনের কিস্তি ${BnDates.bn(i.number.toLong())}",
                dueDate = due,
                openPaisa = i.amountPaisa - i.paidPaisa,
            )
        }

        val emiDao = db.emiDao()
        val emis = emiDao.getActiveEmis().associateBy { it.id }
        val emiSlots = emiDao.getOpenInstallmentsActive().mapNotNull { i ->
            val emi = emis[i.emiId] ?: return@mapNotNull null
            val due = BnDates.fromIso(i.dueIso) ?: return@mapNotNull null
            DueEngine.SlotDue(
                obligationId = emi.id,
                title = emi.productName,
                detail = "EMI কিস্তি ${BnDates.bn(i.number.toLong())}",
                dueDate = due,
                openPaisa = i.amountPaisa - i.paidPaisa,
            )
        }

        val personalDao = db.personalDao()
        val personNames = personalDao.getAllPersonRefs().associate { it.id to it.name }
        val personalDues = personalDao.getOpenDebts().map { d ->
            DueEngine.PersonalDue(
                debtId = d.id,
                personName = personNames[d.personId] ?: "ব্যক্তি",
                dueDate = BnDates.fromIso(d.expectedReturnIso),
                outstandingPaisa = d.amountPaisa,
            )
        }

        return DueEngine.collect(
            today = today,
            windowDays = windowDays,
            shopCredits = creditDues,
            loanSlots = loanSlots,
            emiSlots = emiSlots,
            personalDebts = personalDues,
        )
    }
}
