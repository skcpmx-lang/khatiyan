package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.settings.SettingsRepository
import com.shohan.khatiyan.domain.finance.DueEngine
import com.shohan.khatiyan.domain.finance.InsightEngine
import com.shohan.khatiyan.domain.model.DebtSlice
import com.shohan.khatiyan.domain.model.DueStatus
import com.shohan.khatiyan.domain.model.Insight
import com.shohan.khatiyan.domain.model.MonthPoint
import com.shohan.khatiyan.domain.model.ObligationKind
import java.time.LocalDate
import java.time.YearMonth

/**
 * Flagship dashboard aggregation (Phase 9). Everything is computed from live
 * Room queries at call time — no cached or fabricated numbers anywhere.
 */
class DashboardRepository(
    private val db: KhatiyanDatabase,
    private val settings: SettingsRepository,
    private val dueRepo: DueRepository,
) {

    suspend fun snapshot(): com.shohan.khatiyan.domain.model.DashboardSnapshot {
        val st = settings.current()
        val symbol = st.currencySymbol
        val profile = db.profileDao().getProfile()
        val stats = db.statsDao()
        val today = BnDates.today()
        val todayIso = BnDates.toIso(today)

        val todayIncome = stats.incomeOnDay(todayIso)
        val todayExpense = stats.expenseOnDay(todayIso)
        val todayRepaid = stats.repaidOnDay(todayIso)

        val shopOut = stats.shopOutstandingPaisa()
        val loanOut = stats.loanOutstandingPaisa()
        val emiOut = stats.emiOutstandingPaisa()
        val personalOut = stats.personalOutstandingPaisa()
        val totalOutstanding = Money.addClamped(Money.addClamped(shopOut, loanOut), Money.addClamped(emiOut, personalOut))

        val dueItems = dueRepo.collect(today, 45)
        val windows = DueEngine.totals(dueItems, today)
        val todayDuePaisa = dueItems
            .filter { it.status == DueStatus.DUE_TODAY }
            .fold(0L) { a, d -> Money.addClamped(a, d.amountPaisa) }

        val mStart = BnDates.toIso(today.withDayOfMonth(1))
        val mEnd = BnDates.toIso(today.withDayOfMonth(today.lengthOfMonth()))
        val prevMonthDate = today.withDayOfMonth(1).minusDays(1)
        val pmStart = BnDates.toIso(prevMonthDate.withDayOfMonth(1))
        val pmEnd = BnDates.toIso(prevMonthDate.withDayOfMonth(prevMonthDate.lengthOfMonth()))

        val monthIncome = stats.incomeBetween(mStart, mEnd)
        val monthExpense = stats.expenseBetween(mStart, mEnd)
        val monthRepaid = stats.repaidBetween(mStart, mEnd)
        val prevMonthIncome = stats.incomeBetween(pmStart, pmEnd)
        val prevMonthExpense = stats.expenseBetween(pmStart, pmEnd)

        // 6-month trend (inclusive of current month)
        val trendStart = today.withDayOfMonth(1).minusMonths(5)
        val tFrom = BnDates.toIso(trendStart)
        val tTo = mEnd
        val incBy = stats.monthlyIncome(tFrom, tTo).associate { it.ym to it.totalPaisa }
        val expBy = stats.monthlyExpense(tFrom, tTo).associate { it.ym to it.totalPaisa }
        val repBy = stats.monthlyRepaid(tFrom, tTo).associate { it.ym to it.totalPaisa }
        val trend = (0..5).map { idx ->
            val ym = YearMonth.from(trendStart).plusMonths(idx.toLong())
            val key = "%04d-%02d".format(ym.year, ym.monthValue)
            MonthPoint(ym, incBy[key] ?: 0, expBy[key] ?: 0, repBy[key] ?: 0)
        }

        // Debt distribution across the four books (real outstanding only)
        val slices = buildList {
            if (shopOut > 0) add(DebtSlice(ObligationKind.SHOP, "দোকানের বাকী", shopOut))
            if (loanOut > 0) add(DebtSlice(ObligationKind.LOAN, "লোন", loanOut))
            if (emiOut > 0) add(DebtSlice(ObligationKind.EMI, "কিস্তি (EMI)", emiOut))
            if (personalOut > 0) add(DebtSlice(ObligationKind.PERSONAL, "ব্যক্তিগত ধার", personalOut))
        }

        // Biggest single obligation across all books
        val biggest = biggestDebt(today)

        val hasAnyData = stats.dataCount().count > 0

        val insights: List<Insight> = InsightEngine.build(
            InsightEngine.Input(
                symbol = symbol,
                thisMonth = InsightEngine.MonthTotals(monthIncome, monthExpense, monthRepaid),
                lastMonth = InsightEngine.MonthTotals(prevMonthIncome, prevMonthExpense, 0),
                upcoming = InsightEngine.Upcoming(
                    dueTodayCount = windows.today.count,
                    dueTodayPaisa = windows.today.paisa,
                    next7Count = windows.next7.count,
                    next7Paisa = windows.next7.paisa,
                    overdueCount = windows.overdue.count,
                    overduePaisa = windows.overdue.paisa,
                ),
                totalDebtPaisa = totalOutstanding,
                biggestDebtLabel = biggest?.label,
                biggestDebtPaisa = biggest?.paisa ?: 0,
                hasAnyData = hasAnyData,
            ),
        )

        return com.shohan.khatiyan.domain.model.DashboardSnapshot(
            symbol = symbol,
            userName = profile?.name ?: st.userName,
            todayIncomePaisa = todayIncome,
            todayExpensePaisa = todayExpense,
            todayRepaidPaisa = todayRepaid,
            todayDuePaisa = todayDuePaisa,
            totalOutstandingPaisa = totalOutstanding,
            outstandingByKind = mapOf(
                ObligationKind.SHOP to shopOut,
                ObligationKind.LOAN to loanOut,
                ObligationKind.EMI to emiOut,
                ObligationKind.PERSONAL to personalOut,
            ),
            dueTodayCount = windows.today.count,
            dueWeekCount = windows.next7.count,
            dueWeekPaisa = windows.next7.paisa,
            overdueCount = windows.overdue.count,
            overduePaisa = windows.overdue.paisa,
            monthIncomePaisa = monthIncome,
            monthExpensePaisa = monthExpense,
            monthRepaidPaisa = monthRepaid,
            trend = trend,
            debtSlices = slices,
            upcoming = dueItems.take(7),
            insights = insights,
            hasAnyData = hasAnyData,
        )
    }

    private data class Named(val label: String, val paisa: Long)

    private suspend fun biggestDebt(today: LocalDate): Named? {
        var best: Named? = null
        fun consider(label: String, paisa: Long) {
            if (paisa > 0 && (best == null || paisa > best!!.paisa)) best = Named(label, paisa)
        }
        val iso = BnDates.toIso(today)
        db.shopDao().getShopRows().forEach { consider("দোকান: ${it.name}", it.balancePaisa) }
        db.loanDao().getLoanRows(iso).forEach { consider("লোন: ${it.loanName.ifBlank { it.institution }}", it.remainingPaisa) }
        db.emiDao().getEmiRows(iso).forEach { consider("EMI: ${it.productName}", it.remainingPaisa) }
        db.personalDao().getPeopleRows(iso).forEach { consider("ধার: ${it.name}", it.remainingPaisa) }
        return best
    }
}
