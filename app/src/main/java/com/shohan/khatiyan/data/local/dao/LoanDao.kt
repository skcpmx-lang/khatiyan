package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shohan.khatiyan.domain.finance.Allocation
import com.shohan.khatiyan.domain.model.InstallmentPlan
import com.shohan.khatiyan.data.local.entity.LoanAllocationEntity
import com.shohan.khatiyan.data.local.entity.LoanEntity
import com.shohan.khatiyan.data.local.entity.LoanInstallmentEntity
import com.shohan.khatiyan.data.local.entity.LoanPaymentEntity
import com.shohan.khatiyan.data.local.query.LoanRow
import kotlinx.coroutines.flow.Flow

/**
 * Loan persistence with FIFO payment allocation. Schedule generation happens
 * in the repository; the DAO guarantees (in one transaction) that installments,
 * allocations and denormalized paid totals stay consistent — including when a
 * loan is EDITED (allocations are replayed from real payment rows).
 */
@Dao
abstract class LoanDao {

    @Query(
        """
        SELECT l.*,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM loan_payments p WHERE p.loanId = l.id) AS totalPaidPaisa,
               (SELECT IFNULL(SUM(p.excessPaisa), 0) FROM loan_payments p WHERE p.loanId = l.id) AS advancePaisa,
               (SELECT COUNT(*) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa) AS openCount,
               (SELECT COUNT(*) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso) AS overdueCount,
               (SELECT MIN(i.dueIso) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa) AS nextDueIso
        FROM loans l
        WHERE (:showArchived = 1 OR l.archived = 0)
          AND (:q = '' OR l.loanName LIKE '%' || :q || '%' OR l.institution LIKE '%' || :q || '%')
        ORDER BY l.archived ASC, (nextDueIso IS NULL) ASC, nextDueIso ASC, l.id DESC
        """
    )
    abstract fun observeLoans(q: String, todayIso: String, showArchived: Boolean): Flow<List<LoanRow>>

    @Query("SELECT * FROM loans WHERE id = :id")
    abstract suspend fun getLoan(id: Long): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertLoan(loan: LoanEntity): Long

    @Query("UPDATE loans SET archived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM loans WHERE id = :id")
    abstract suspend fun deleteLoanById(id: Long)

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY number ASC")
    abstract suspend fun getInstallments(loanId: Long): List<LoanInstallmentEntity>

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY dueIso ASC, number ASC")
    abstract suspend fun getInstallmentsByDue(loanId: Long): List<LoanInstallmentEntity>

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY dateIso ASC, id ASC")
    abstract suspend fun getPaymentsOrdered(loanId: Long): List<LoanPaymentEntity>

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY dateIso DESC, id DESC")
    abstract suspend fun getPaymentsRecent(loanId: Long): List<LoanPaymentEntity>

    @Query("SELECT * FROM loan_payments WHERE id = :id")
    abstract suspend fun getPayment(id: Long): LoanPaymentEntity?

    @Query(
        """
        SELECT i.* FROM loan_installments i
        WHERE i.loanId IN (SELECT id FROM loans WHERE archived = 0) AND i.paidPaisa < i.amountPaisa
        ORDER BY i.dueIso ASC
        """
    )
    abstract suspend fun getOpenInstallmentsActive(): List<LoanInstallmentEntity>

    @Query(
        """
        SELECT l.*,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM loan_payments p WHERE p.loanId = l.id) AS totalPaidPaisa,
               (SELECT IFNULL(SUM(p.excessPaisa), 0) FROM loan_payments p WHERE p.loanId = l.id) AS advancePaisa,
               (SELECT COUNT(*) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa) AS openCount,
               (SELECT COUNT(*) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso) AS overdueCount,
               (SELECT MIN(i.dueIso) FROM loan_installments i WHERE i.loanId = l.id AND i.paidPaisa < i.amountPaisa) AS nextDueIso
        FROM loans l ORDER BY l.id DESC
        """
    )
    abstract suspend fun getLoanRows(todayIso: String): List<LoanRow>

    @Query("SELECT * FROM loans WHERE archived = 0")
    abstract suspend fun getActiveLoans(): List<LoanEntity>

    @Query("SELECT COUNT(*) FROM loans")
    abstract suspend fun countLoans(): Int

    // ---- installments -------------------------------------------------------------------

    @Insert
    abstract suspend fun insertInstallments(rows: List<LoanInstallmentEntity>): List<Long>

    @Update
    abstract suspend fun updateInstallment(row: LoanInstallmentEntity)

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId")
    abstract suspend fun deleteInstallments(loanId: Long)

    @Query("DELETE FROM loan_payment_allocations WHERE installmentId IN (SELECT id FROM loan_installments WHERE loanId = :loanId)")
    abstract suspend fun deleteAllocationsForLoan(loanId: Long)

    @Insert
    abstract suspend fun insertAllocations(rows: List<LoanAllocationEntity>)

    // ---- payments -------------------------------------------------------------------------

    @Insert
    abstract suspend fun insertPayment(payment: LoanPaymentEntity): Long

    @Update
    abstract suspend fun updatePayment(payment: LoanPaymentEntity)

    @Query("DELETE FROM loan_payments WHERE id = :id")
    abstract suspend fun deletePaymentById(id: Long)

    @Query("SELECT * FROM loan_payment_allocations WHERE paymentId = :paymentId")
    abstract suspend fun getAllocationsForPayment(paymentId: Long): List<LoanAllocationEntity>

    /**
     * Save loan + freshly generated schedule, then replay every existing payment
     * (chronological) onto the new installments so paid totals/allocation rows
     * are derived only from real payment records.
     */
    @Transaction
    open suspend fun saveLoanWithSchedule(loan: LoanEntity, schedule: List<InstallmentPlan>) {
        val loanId = if (loan.id == 0L) upsertLoan(loan) else { upsertLoan(loan); loan.id }
        deleteAllocationsForLoan(loanId)
        deleteInstallments(loanId)
        if (schedule.isEmpty()) return
        val newIds = insertInstallments(
            schedule.map {
                LoanInstallmentEntity(
                    loanId = loanId,
                    number = it.number,
                    dueIso = it.dueDate.toString(),
                    amountPaisa = it.amountPaisa,
                    paidPaisa = 0,
                )
            },
        )
        var state = newIds.mapIndexed { idx, id ->
            Allocation.InstallmentState(id, schedule[idx].amountPaisa, 0)
        }.toMutableList()
        val payments = getPaymentsOrdered(loanId)
        for (p in payments) {
            val res = Allocation.overInstallments(state, p.amountPaisa)
            insertAllocations(
                res.patches.map {
                    LoanAllocationEntity(paymentId = p.id, installmentId = it.installmentId, amountPaisa = it.addPaisa)
                },
            )
            for (patch in res.patches) {
                state = state.map {
                    if (it.id == patch.installmentId) it.copy(paidPaisa = it.paidPaisa + patch.addPaisa) else it
                }.toMutableList()
            }
            for (patch in res.patches) {
                updateInstallmentPaid(patch.installmentId, state.first { it.id == patch.installmentId }.paidPaisa)
            }
            if (res.excessPaisa != p.excessPaisa) {
                updatePayment(p.copy(excessPaisa = res.excessPaisa))
            }
        }
    }

    @Query("UPDATE loan_installments SET paidPaisa = :paid WHERE id = :id")
    abstract suspend fun updateInstallmentPaid(id: Long, paid: Long)

    @Transaction
    open suspend fun recordPayment(
        payment: LoanPaymentEntity,
        openInstallments: List<LoanInstallmentEntity>,
    ): Pair<Long, Long> {
        val paymentId = insertPayment(payment)
        val state = openInstallments.map { Allocation.InstallmentState(it.id, it.amountPaisa, it.paidPaisa) }
        val res = Allocation.overInstallments(state, payment.amountPaisa)
        if (res.patches.isNotEmpty()) {
            insertAllocations(
                res.patches.map {
                    LoanAllocationEntity(paymentId = paymentId, installmentId = it.installmentId, amountPaisa = it.addPaisa)
                },
            )
            for (patch in res.patches) {
                val inst = openInstallments.first { it.id == patch.installmentId }
                updateInstallmentPaid(inst.id, inst.paidPaisa + patch.addPaisa)
            }
        }
        if (res.excessPaisa > 0) {
            val stored = getPayment(paymentId)
            if (stored != null) updatePayment(stored.copy(excessPaisa = res.excessPaisa))
        }
        return paymentId to res.excessPaisa
    }

    /** Removing a payment un-does its allocations exactly (never corrupts balances). */
    @Transaction
    open suspend fun deletePayment(id: Long) {
        val allocs = getAllocationsForPayment(id)
        for (a in allocs) {
            val inst = getInstallmentById(a.installmentId) ?: continue
            updateInstallmentPaid(inst.id, (inst.paidPaisa - a.amountPaisa).coerceAtLeast(0))
        }
        deletePaymentById(id)
    }

    @Query("SELECT * FROM loan_installments WHERE id = :id")
    abstract suspend fun getInstallmentById(id: Long): LoanInstallmentEntity?

    // ---- backup ---------------------------------------------------------------------------

    @Query("SELECT * FROM loans")
    abstract suspend fun getAllLoans(): List<LoanEntity>

    @Query("SELECT * FROM loan_installments")
    abstract suspend fun getAllInstallments(): List<LoanInstallmentEntity>

    @Query("SELECT * FROM loan_payments")
    abstract suspend fun getAllPayments(): List<LoanPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreLoans(rows: List<LoanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreInstallments(rows: List<LoanInstallmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restorePayments(rows: List<LoanPaymentEntity>)

    @Query("SELECT * FROM loan_payment_allocations")
    abstract suspend fun getAllAllocations(): List<LoanAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreAllocations(rows: List<LoanAllocationEntity>)
}
