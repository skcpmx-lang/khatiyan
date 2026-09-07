package com.shohan.khatiyan.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** One sentence of guidance for the dashboard (Phase 23). */
data class Insight(
    val id: String,
    val text: String,
    val tone: InsightTone,
)

enum class InsightTone(val severity: Int) { DANGER(0), WARN(1), GOOD(2), INFO(3) }

/** Aggregated upcoming obligation row (Phase 19). */
data class DueItem(
    val kind: ObligationKind,
    val entityId: Long,
    val entityLabel: String,
    val detailLabel: String,
    val dueDate: LocalDate,
    val amountPaisa: Long,
    val status: DueStatus,
)

/** A point on the 6-month trend chart. */
data class MonthPoint(
    val yearMonth: YearMonth,
    val incomePaisa: Long,
    val expensePaisa: Long,
    val repaidPaisa: Long,
)

/** Outstanding balance of one obligation, for distribution charts/lists. */
data class DebtSlice(
    val kind: ObligationKind,
    val name: String,
    val outstandingPaisa: Long,
)

/** Row of the central ledger view (Phase 16). */
data class LedgerEntry(
    val key: String,
    val typeKey: String,
    val dateIso: String,
    val amountPaisa: Long,
    val title: String,
    val subtitle: String,
    val note: String,
) {
    val type: TxnType get() = TxnType.entries.firstOrNull { it.key == typeKey } ?: TxnType.OTHER2
}

// Fallback for unknown type keys (should never happen with our fixed vocabulary).
private val TxnType.OTHER2: TxnType
    get() = TxnType.EXPENSE

/** Everything the flagship dashboard renders, computed from DB + engines. */
data class DashboardSnapshot(
    val symbol: String,
    val userName: String,
    val todayIncomePaisa: Long,
    val todayExpensePaisa: Long,
    val todayRepaidPaisa: Long,
    val todayDuePaisa: Long,
    val totalOutstandingPaisa: Long,
    val outstandingByKind: Map<ObligationKind, Long>,
    val dueTodayCount: Int,
    val dueWeekCount: Int,
    val dueWeekPaisa: Long,
    val overdueCount: Int,
    val overduePaisa: Long,
    val monthIncomePaisa: Long,
    val monthExpensePaisa: Long,
    val monthRepaidPaisa: Long,
    val trend: List<MonthPoint>,
    val debtSlices: List<DebtSlice>,
    val upcoming: List<DueItem>,
    val insights: List<Insight>,
    val hasAnyData: Boolean,
)

/** Report data over an explicit date range (Phase 21). */
data class ReportSnapshot(
    val symbol: String,
    val fromIso: String,
    val toIso: String,
    val incomePaisa: Long,
    val expensePaisa: Long,
    val netPaisa: Long,
    val repaidPaisa: Long,
    val newObligationPaisa: Long,
    val monthly: List<MonthPoint>,
    val expenseByCategory: List<Pair<String, Long>>,
    val incomeByCategory: List<Pair<String, Long>>,
    val debts: List<DebtSlice>,
    val totalDebtPaisa: Long,
    val upcoming: List<DueItem>,
    val entries: List<LedgerEntry>,
)
