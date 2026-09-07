package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.settings.SettingsRepository
import com.shohan.khatiyan.domain.model.DebtSlice
import com.shohan.khatiyan.domain.model.MonthPoint
import com.shohan.khatiyan.domain.model.ObligationKind
import com.shohan.khatiyan.domain.model.ReportSnapshot
import java.time.LocalDate
import java.time.YearMonth

/** Report aggregation for an explicit date range (Phase 21). */
class ReportRepository(
    private val db: KhatiyanDatabase,
    private val settings: SettingsRepository,
    private val dueRepo: DueRepository,
) {

    data class Range(val labelBn: String, val from: LocalDate, val to: LocalDate)

    companion object {
        fun rangeFor(preset: String, today: LocalDate = BnDates.today()): Range = when (preset) {
            "today" -> Range("আজ", today, today)
            "week" -> {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                Range("এই সপ্তাহ", start, start.plusDays(6))
            }
            "lastMonth" -> {
                val first = today.withDayOfMonth(1).minusMonths(1)
                Range("গত মাস", first, first.withDayOfMonth(first.lengthOfMonth()))
            }
            "year" -> Range("এই বছর", BnDates.yearStart(today), today.withMonth(12).withDayOfMonth(31))
            else -> {
                val first = today.withDayOfMonth(1)
                Range("এই মাস", first, first.withDayOfMonth(first.lengthOfMonth()))
            }
        }
    }

    suspend fun report(from: LocalDate, to: LocalDate): ReportSnapshot {
        val st = settings.current()
        val stats = db.statsDao()
        val fromIso = BnDates.toIso(from)
        val toIso = BnDates.toIso(to)

        val income = stats.incomeBetween(fromIso, toIso)
        val expense = stats.expenseBetween(fromIso, toIso)

        val incBy = stats.monthlyIncome(fromIso, toIso).associate { it.ym to it.totalPaisa }
        val expBy = stats.monthlyExpense(fromIso, toIso).associate { it.ym to it.totalPaisa }
        val repBy = stats.monthlyRepaid(fromIso, toIso).associate { it.ym to it.totalPaisa }
        val months = ArrayList<MonthPoint>()
        var ym = YearMonth.from(from)
        val lastYm = YearMonth.from(to)
        var guard = 0
        while ((ym == lastYm || ym.isBefore(lastYm)) && guard < 300) {
            val key = "%04d-%02d".format(ym.year, ym.monthValue)
            months += MonthPoint(ym, incBy[key] ?: 0, expBy[key] ?: 0, repBy[key] ?: 0)
            ym = ym.plusMonths(1)
            guard++
        }

        val today = BnDates.today()
        val isoToday = BnDates.toIso(today)
        val debts = ArrayList<DebtSlice>()
        db.shopDao().getShopRows().forEach { if (it.balancePaisa > 0) debts += DebtSlice(ObligationKind.SHOP, it.name, it.balancePaisa) }
        db.loanDao().getLoanRows(isoToday).forEach { if (it.remainingPaisa > 0) debts += DebtSlice(ObligationKind.LOAN, it.loanName.ifBlank { it.institution }, it.remainingPaisa) }
        db.emiDao().getEmiRows(isoToday).forEach { if (it.remainingPaisa > 0) debts += DebtSlice(ObligationKind.EMI, it.productName, it.remainingPaisa) }
        db.personalDao().getAllDebtsWithRemaining().forEach {
            if (it.remainingPaisa > 0) debts += DebtSlice(ObligationKind.PERSONAL, it.personName, it.remainingPaisa)
        }
        debts.sortByDescending { it.outstandingPaisa }
        val totalDebt = debts.fold(0L) { a, d -> Money.addClamped(a, d.outstandingPaisa) }

        val upcoming = dueRepo.collect(today, 60)
            .filter { !it.dueDate.isAfter(to) }

        val entries = LedgerMapper.toEntries(
            db.ledgerDao().entriesBetween(fromIso, toIso, "", 300, 0),
        )

        return ReportSnapshot(
            symbol = st.currencySymbol,
            fromIso = fromIso,
            toIso = toIso,
            incomePaisa = income,
            expensePaisa = expense,
            netPaisa = income - expense,
            repaidPaisa = stats.repaidBetween(fromIso, toIso),
            newObligationPaisa = stats.newObligationBetween(fromIso, toIso),
            monthly = months,
            expenseByCategory = db.cashflowDao().expenseByCategory(fromIso, toIso).map { it.name to it.totalPaisa },
            incomeByCategory = db.cashflowDao().incomeByCategory(fromIso, toIso).map { it.name to it.totalPaisa },
            debts = debts,
            totalDebtPaisa = totalDebt,
            upcoming = upcoming,
            entries = entries,
        )
    }
}
