package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shohan.khatiyan.data.local.entity.EmiAllocationEntity
import com.shohan.khatiyan.data.local.entity.EmiInstallmentEntity
import com.shohan.khatiyan.data.local.entity.EmiPaymentEntity
import com.shohan.khatiyan.data.local.entity.EmiPurchaseEntity
import com.shohan.khatiyan.data.local.query.EmiRow
import com.shohan.khatiyan.domain.finance.Allocation
import com.shohan.khatiyan.domain.model.InstallmentPlan
import kotlinx.coroutines.flow.Flow

/** EMI persistence — same FIFO allocation guarantees as LoanDao (Phase 12/17). */
@Dao
abstract class EmiDao {

    @Query(
        """
        SELECT e.*,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM emi_payments p WHERE p.emiId = e.id) AS totalPaidPaisa,
               (SELECT IFNULL(SUM(p.excessPaisa), 0) FROM emi_payments p WHERE p.emiId = e.id) AS advancePaisa,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa) AS openCount,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa = i.amountPaisa) AS paidCount,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso) AS overdueCount,
               (SELECT MIN(i.dueIso) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa) AS nextDueIso
        FROM emi_purchases e
        WHERE (:showArchived = 1 OR e.archived = 0)
          AND (:q = '' OR e.productName LIKE '%' || :q || '%' OR e.seller LIKE '%' || :q || '%')
        ORDER BY e.archived ASC, (nextDueIso IS NULL) ASC, nextDueIso ASC, e.id DESC
        """
    )
    abstract fun observeEmis(q: String, todayIso: String, showArchived: Boolean): Flow<List<EmiRow>>

    @Query("SELECT * FROM emi_purchases WHERE id = :id")
    abstract suspend fun getEmi(id: Long): EmiPurchaseEntity?

    @Insert
    abstract suspend fun insertEmi(emi: EmiPurchaseEntity): Long

    @Update
    abstract suspend fun updateEmi(emi: EmiPurchaseEntity)

    @Query("UPDATE emi_purchases SET archived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM emi_purchases WHERE id = :id")
    abstract suspend fun deleteEmiById(id: Long)

    @Query(
        """
        SELECT e.*,
               (SELECT IFNULL(SUM(p.amountPaisa), 0) FROM emi_payments p WHERE p.emiId = e.id) AS totalPaidPaisa,
               (SELECT IFNULL(SUM(p.excessPaisa), 0) FROM emi_payments p WHERE p.emiId = e.id) AS advancePaisa,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa) AS openCount,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa = i.amountPaisa) AS paidCount,
               (SELECT COUNT(*) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa AND i.dueIso < :todayIso) AS overdueCount,
               (SELECT MIN(i.dueIso) FROM emi_installments i WHERE i.emiId = e.id AND i.paidPaisa < i.amountPaisa) AS nextDueIso
        FROM emi_purchases e ORDER BY e.id DESC
        """
    )
    abstract suspend fun getEmiRows(todayIso: String): List<EmiRow>

    @Query("SELECT * FROM emi_purchases WHERE archived = 0")
    abstract suspend fun getActiveEmis(): List<EmiPurchaseEntity>

    @Query("SELECT * FROM emi_installments WHERE emiId = :emiId ORDER BY number ASC")
    abstract suspend fun getInstallments(emiId: Long): List<EmiInstallmentEntity>

    @Query("SELECT * FROM emi_installments WHERE emiId = :emiId ORDER BY dueIso ASC, number ASC")
    abstract suspend fun getInstallmentsByDue(emiId: Long): List<EmiInstallmentEntity>

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY dateIso ASC, id ASC")
    abstract suspend fun getPaymentsOrdered(emiId: Long): List<EmiPaymentEntity>

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY dateIso DESC, id DESC")
    abstract suspend fun getPaymentsRecent(emiId: Long): List<EmiPaymentEntity>

    @Query("SELECT * FROM emi_payments WHERE id = :id")
    abstract suspend fun getPayment(id: Long): EmiPaymentEntity?

    @Query(
        """
        SELECT i.* FROM emi_installments i
        WHERE i.emiId IN (SELECT id FROM emi_purchases WHERE archived = 0) AND i.paidPaisa < i.amountPaisa
        ORDER BY i.dueIso ASC
        """
    )
    abstract suspend fun getOpenInstallmentsActive(): List<EmiInstallmentEntity>

    @Insert
    abstract suspend fun insertInstallments(rows: List<EmiInstallmentEntity>): List<Long>

    @Update
    abstract suspend fun updateInstallment(row: EmiInstallmentEntity)

    @Query("DELETE FROM emi_installments WHERE emiId = :emiId")
    abstract suspend fun deleteInstallments(emiId: Long)

    @Query("DELETE FROM emi_payment_allocations WHERE installmentId IN (SELECT id FROM emi_installments WHERE emiId = :emiId)")
    abstract suspend fun deleteAllocationsForEmi(emiId: Long)

    @Insert
    abstract suspend fun insertAllocations(rows: List<EmiAllocationEntity>)

    @Insert
    abstract suspend fun insertPayment(payment: EmiPaymentEntity): Long

    @Update
    abstract suspend fun updatePayment(payment: EmiPaymentEntity)

    @Query("DELETE FROM emi_payments WHERE id = :id")
    abstract suspend fun deletePaymentById(id: Long)

    @Query("SELECT * FROM emi_payment_allocations WHERE paymentId = :paymentId")
    abstract suspend fun getAllocationsForPayment(paymentId: Long): List<EmiAllocationEntity>

    @Query("SELECT * FROM emi_installments WHERE id = :id")
    abstract suspend fun getInstallmentById(id: Long): EmiInstallmentEntity?

    @Query("UPDATE emi_installments SET paidPaisa = :paid WHERE id = :id")
    abstract suspend fun updateInstallmentPaid(id: Long, paid: Long)

    @Transaction
    open suspend fun saveEmiWithSchedule(emi: EmiPurchaseEntity, schedule: List<InstallmentPlan>) {
        val emiId = if (emi.id == 0L) insertEmi(emi) else { updateEmi(emi); emi.id }
        deleteAllocationsForEmi(emiId)
        deleteInstallments(emiId)
        if (schedule.isEmpty()) return
        val newIds = insertInstallments(
            schedule.map {
                EmiInstallmentEntity(
                    emiId = emiId,
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
        for (p in getPaymentsOrdered(emiId)) {
            val res = Allocation.overInstallments(state, p.amountPaisa)
            insertAllocations(
                res.patches.map {
                    EmiAllocationEntity(paymentId = p.id, installmentId = it.installmentId, amountPaisa = it.addPaisa)
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

    @Transaction
    open suspend fun recordPayment(
        payment: EmiPaymentEntity,
        openInstallments: List<EmiInstallmentEntity>,
    ): Pair<Long, Long> {
        val paymentId = insertPayment(payment)
        val state = openInstallments.map { Allocation.InstallmentState(it.id, it.amountPaisa, it.paidPaisa) }
        val res = Allocation.overInstallments(state, payment.amountPaisa)
        if (res.patches.isNotEmpty()) {
            insertAllocations(
                res.patches.map {
                    EmiAllocationEntity(paymentId = paymentId, installmentId = it.installmentId, amountPaisa = it.addPaisa)
                },
            )
            for (patch in res.patches) {
                val inst = openInstallments.first { it.id == patch.installmentId }
                updateInstallmentPaid(inst.id, inst.paidPaisa + patch.addPaisa)
            }
        }
        if (res.excessPaisa > 0) {
            getPayment(paymentId)?.let { updatePayment(it.copy(excessPaisa = res.excessPaisa)) }
        }
        return paymentId to res.excessPaisa
    }

    @Transaction
    open suspend fun deletePayment(id: Long) {
        for (a in getAllocationsForPayment(id)) {
            val inst = getInstallmentById(a.installmentId) ?: continue
            updateInstallmentPaid(inst.id, (inst.paidPaisa - a.amountPaisa).coerceAtLeast(0))
        }
        deletePaymentById(id)
    }

    // ---- backup ------------------------------------------------------------------------

    @Query("SELECT * FROM emi_purchases")
    abstract suspend fun getAllEmis(): List<EmiPurchaseEntity>

    @Query("SELECT * FROM emi_installments")
    abstract suspend fun getAllInstallments(): List<EmiInstallmentEntity>

    @Query("SELECT * FROM emi_payments")
    abstract suspend fun getAllPayments(): List<EmiPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreEmis(rows: List<EmiPurchaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreInstallments(rows: List<EmiInstallmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restorePayments(rows: List<EmiPaymentEntity>)

    @Query("SELECT * FROM emi_payment_allocations")
    abstract suspend fun getAllAllocations(): List<EmiAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreAllocations(rows: List<EmiAllocationEntity>)
}
