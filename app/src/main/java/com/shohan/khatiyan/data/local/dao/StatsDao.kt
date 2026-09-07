package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.shohan.khatiyan.data.local.query.CountRow
import com.shohan.khatiyan.data.local.query.MonthSumRow
import kotlinx.coroutines.flow.Flow

/**
 * Aggregate queries powering dashboard/reports (Phases 9/21/22). Outstanding
 * balances are DERIVED (obligations − payments), never stored (Phase 17).
 * MAX(0, …) clamps displayed outstanding to zero when the user holds an
 * advance credit larger than the debt — the raw value is still recoverable
 * on the detail screens via advancePaisa.
 */
@Dao
abstract class StatsDao {

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM incomes WHERE dateIso = :dayIso")
    abstract suspend fun incomeOnDay(dayIso: String): Long

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM expenses WHERE dateIso = :dayIso")
    abstract suspend fun expenseOnDay(dayIso: String): Long

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM (
            SELECT amountPaisa AS amount FROM shop_payments WHERE dateIso = :dayIso
            UNION ALL SELECT amountPaisa FROM loan_payments WHERE dateIso = :dayIso
            UNION ALL SELECT amountPaisa FROM emi_payments WHERE dateIso = :dayIso
            UNION ALL SELECT amountPaisa FROM personal_repayments WHERE dateIso = :dayIso
        )
        """
    )
    abstract suspend fun repaidOnDay(dayIso: String): Long

    @Query(
        """
        SELECT MAX(0, (SELECT IFNULL(SUM(totalPaisa),0) FROM shop_credits) -
                       (SELECT IFNULL(SUM(amountPaisa),0) FROM shop_payments))
        """
    )
    abstract suspend fun shopOutstandingPaisa(): Long

    @Query(
        """
        SELECT MAX(0, (SELECT IFNULL(SUM(totalPayablePaisa),0) FROM loans) -
                       (SELECT IFNULL(SUM(amountPaisa),0) FROM loan_payments))
        """
    )
    abstract suspend fun loanOutstandingPaisa(): Long

    @Query(
        """
        SELECT MAX(0, (SELECT IFNULL(SUM(financedPaisa),0) FROM emi_purchases) -
                       (SELECT IFNULL(SUM(amountPaisa),0) FROM emi_payments))
        """
    )
    abstract suspend fun emiOutstandingPaisa(): Long

    @Query(
        """
        SELECT MAX(0, (SELECT IFNULL(SUM(amountPaisa),0) FROM personal_debts) -
                       (SELECT IFNULL(SUM(amountPaisa),0) FROM personal_repayments))
        """
    )
    abstract suspend fun personalOutstandingPaisa(): Long

    @Query(
        """
        SELECT COUNT(*) AS count FROM (
            SELECT 1 FROM shops WHERE archived = 0
            UNION ALL SELECT 1 FROM loans WHERE archived = 0
            UNION ALL SELECT 1 FROM emi_purchases WHERE archived = 0
            UNION ALL SELECT 1 FROM persons WHERE archived = 0
            UNION ALL SELECT 1 FROM incomes
            UNION ALL SELECT 1 FROM expenses
        )
        """
    )
    abstract suspend fun dataCount(): CountRow

    // ---- monthly series (chart + reports) -------------------------------------------------

    @Query(
        """
        SELECT substr(dateIso, 1, 7) AS ym, IFNULL(SUM(amountPaisa), 0) AS totalPaisa
        FROM incomes WHERE dateIso >= :fromIso AND dateIso <= :toIso
        GROUP BY ym ORDER BY ym ASC
        """
    )
    abstract suspend fun monthlyIncome(fromIso: String, toIso: String): List<MonthSumRow>

    @Query(
        """
        SELECT substr(dateIso, 1, 7) AS ym, IFNULL(SUM(amountPaisa), 0) AS totalPaisa
        FROM expenses WHERE dateIso >= :fromIso AND dateIso <= :toIso
        GROUP BY ym ORDER BY ym ASC
        """
    )
    abstract suspend fun monthlyExpense(fromIso: String, toIso: String): List<MonthSumRow>

    @Query(
        """
        SELECT ym, IFNULL(SUM(amount), 0) AS totalPaisa FROM (
            SELECT substr(dateIso, 1, 7) AS ym, amountPaisa AS amount FROM shop_payments
            WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL
            SELECT substr(dateIso, 1, 7), amountPaisa FROM loan_payments
            WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL
            SELECT substr(dateIso, 1, 7), amountPaisa FROM emi_payments
            WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL
            SELECT substr(dateIso, 1, 7), amountPaisa FROM personal_repayments
            WHERE dateIso >= :fromIso AND dateIso <= :toIso
        ) GROUP BY ym ORDER BY ym ASC
        """
    )
    abstract suspend fun monthlyRepaid(fromIso: String, toIso: String): List<MonthSumRow>

    @Query(
        """
        SELECT ym, IFNULL(SUM(amount), 0) AS totalPaisa FROM (
            SELECT substr(dateIso, 1, 7) AS ym, amountPaisa AS amount FROM shop_credits
            WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL
            SELECT substr(borrowedIso, 1, 7), amountPaisa FROM personal_debts
            WHERE borrowedIso >= :fromIso AND borrowedIso <= :toIso
        ) GROUP BY ym ORDER BY ym ASC
        """
    )
    abstract suspend fun monthlyNewObligation(fromIso: String, toIso: String): List<MonthSumRow>

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM (
            SELECT amountPaisa AS amount FROM shop_payments WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL SELECT amountPaisa FROM loan_payments WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL SELECT amountPaisa FROM emi_payments WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL SELECT amountPaisa FROM personal_repayments WHERE dateIso >= :fromIso AND dateIso <= :toIso
        )
        """
    )
    abstract suspend fun repaidBetween(fromIso: String, toIso: String): Long

    @Query(
        """
        SELECT IFNULL(SUM(amount), 0) FROM (
            SELECT totalPaisa AS amount FROM shop_credits WHERE dateIso >= :fromIso AND dateIso <= :toIso
            UNION ALL SELECT amountPaisa FROM personal_debts WHERE borrowedIso >= :fromIso AND borrowedIso <= :toIso
        )
        """
    )
    abstract suspend fun newObligationBetween(fromIso: String, toIso: String): Long

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM incomes WHERE dateIso >= :fromIso AND dateIso <= :toIso")
    abstract suspend fun incomeBetween(fromIso: String, toIso: String): Long

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM expenses WHERE dateIso >= :fromIso AND dateIso <= :toIso")
    abstract suspend fun expenseBetween(fromIso: String, toIso: String): Long

    @Query("SELECT IFNULL(SUM(totalPaisa), 0) FROM shop_credits WHERE dateIso >= :fromIso AND dateIso <= :toIso")
    abstract suspend fun shopCreditBetween(fromIso: String, toIso: String): Long

    // ---- flow variants for reactive screens -------------------------------------------------

    @Query(
        """
        SELECT COUNT(*) AS count FROM (
            SELECT 1 AS x FROM loan_installments i JOIN loans l ON l.id = i.loanId
              WHERE l.archived = 0 AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso
            UNION ALL
            SELECT 1 FROM emi_installments i JOIN emi_purchases e ON e.id = i.emiId
              WHERE e.archived = 0 AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso
            UNION ALL
            SELECT 1 FROM personal_debts d JOIN persons p ON p.id = d.personId
              WHERE p.archived = 0 AND d.expectedReturnIso IS NOT NULL AND d.expectedReturnIso < :todayIso
              AND d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)
        )
        """
    )
    abstract fun observeOverdueCount(todayIso: String): Flow<CountRow>
}
