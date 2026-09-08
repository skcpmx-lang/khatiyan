package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import androidx.room.withTransaction
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.LoanEntity
import com.shohan.khatiyan.data.local.entity.LoanInstallmentEntity
import com.shohan.khatiyan.data.local.entity.LoanPaymentEntity
import com.shohan.khatiyan.data.local.query.LoanRow
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.finance.ScheduleGenerator
import com.shohan.khatiyan.domain.model.Frequency
import com.shohan.khatiyan.domain.model.InstallmentPlan
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.FinanceValidationException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Bank/NGO loans (Phase 11). The installment schedule is generated from the
 * due date, frequency and total payable; all balances come from real payment
 * rows (allocations replayed whenever the loan is edited).
 */
class LoanRepository(private val db: KhatiyanDatabase) {

    private val dao get() = db.loanDao()

    data class LoanDraft(
        val id: Long,
        val institution: String,
        val loanName: String,
        val principalPaisa: Long,
        val startIso: String,
        val annualRateBps: Int,
        val processingFeePaisa: Long,
        /** 0 → computed = principal + interest + fee */
        val totalPayablePaisa: Long,
        /** 0 → derived from schedule division */
        val installmentPaisa: Long,
        val frequency: Frequency,
        val firstDueIso: String,
        val maturityIso: String,
        /** 0 → derived from firstDue..maturity */
        val installmentCount: Int,
        val note: String,
    )

    class InstallmentView(
        val entity: LoanInstallmentEntity,
        val number: Int,
        val dueDate: LocalDate?,
        val amountPaisa: Long,
        val paidPaisa: Long,
        val status: InstallmentStatus,
    )

    enum class InstallmentStatus { PAID, DUE_TODAY, OVERDUE, PENDING }

    data class LoanDetail(
        val loan: LoanEntity,
        val installments: List<InstallmentView>,
        val payments: List<LoanPaymentEntity>,
        val totalPaidPaisa: Long,
        val advancePaisa: Long,
    ) {
        val remainingPaisa: Long get() = loan.totalPayablePaisa - totalPaidPaisa
    }

    fun observeLoans(q: String, showArchived: Boolean): Flow<List<LoanRow>> {
        val today = BnDates.toIso(BnDates.today())
        return dao.observeLoans(q, today, showArchived)
    }

    fun buildSchedule(draft: LoanDraft): List<InstallmentPlan> {
        val firstDue = BnDates.fromIso(draft.firstDueIso) ?: LocalDate.now()
        val maturity = BnDates.fromIso(draft.maturityIso)
        val count = if (draft.installmentCount > 0) draft.installmentCount
        else if (maturity != null) ScheduleGenerator.countBetween(firstDue, maturity, draft.frequency)
        else 12
        return ScheduleGenerator.generate(
            firstDue = firstDue,
            frequency = draft.frequency,
            count = count,
            totalPaisa = draft.totalPayablePaisa,
            fixedInstallmentPaisa = draft.installmentPaisa.takeIf { it > 0 },
        )
    }

    suspend fun save(draft: LoanDraft) {
        if (draft.institution.isBlank() && draft.loanName.isBlank()) {
            throw FinanceValidationException("প্রতিষ্ঠানের নাম বা লোনের নাম লিখুন।")
        }
        if (draft.principalPaisa <= 0L) throw FinanceValidationException("লোনের মূল টাকার পরিমাণ লিখুন।")
        val totalPayable = if (draft.totalPayablePaisa > 0) {
            draft.totalPayablePaisa
        } else {
            val from = BnDates.fromIso(draft.startIso) ?: LocalDate.now()
            val to = BnDates.fromIso(draft.maturityIso) ?: from.plusYears(1)
            com.shohan.khatiyan.domain.finance.LoanMath.totalPayablePaisa(
                draft.principalPaisa, draft.annualRateBps, from, to, draft.processingFeePaisa,
            )
        }
        if (totalPayable <= 0L) throw FinanceValidationException("মোট পরিশোধযোগ্য ০-এর বেশি হতে হবে।")
        val schedule = buildSchedule(draft.copy(totalPayablePaisa = totalPayable))
        val base = schedule.firstOrNull()?.amountPaisa ?: 0L
        val entity = LoanEntity(
            id = draft.id,
            institution = draft.institution.trim(),
            loanName = draft.loanName.trim().ifBlank { draft.institution.trim() },
            principalPaisa = draft.principalPaisa,
            startIso = draft.startIso,
            annualRateBps = draft.annualRateBps,
            processingFeePaisa = draft.processingFeePaisa.coerceAtLeast(0),
            totalPayablePaisa = totalPayable,
            installmentPaisa = base,
            frequency = draft.frequency.name,
            firstDueIso = draft.firstDueIso,
            maturityIso = draft.maturityIso,
            note = draft.note.trim(),
            createdAtIso = BnDates.toIso(BnDates.today()),
        )
        dao.saveLoanWithSchedule(entity, schedule)
        DataBus.poke()
    }

    suspend fun loadDetail(loanId: Long): LoanDetail? {
        val loan = dao.getLoan(loanId) ?: return null
        val today = BnDates.today()
        val installments = dao.getInstallments(loanId).map { row ->
            val due = BnDates.fromIso(row.dueIso)
            val status = when {
                row.paidPaisa >= row.amountPaisa -> InstallmentStatus.PAID
                due == null -> InstallmentStatus.PENDING
                due.isBefore(today) -> InstallmentStatus.OVERDUE
                due == today -> InstallmentStatus.DUE_TODAY
                else -> InstallmentStatus.PENDING
            }
            InstallmentView(row, row.number, due, row.amountPaisa, row.paidPaisa, status)
        }
        val payments = dao.getPaymentsRecent(loanId)
        return LoanDetail(
            loan = loan,
            installments = installments,
            payments = payments,
            totalPaidPaisa = payments.fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) },
            advancePaisa = payments.fold(0L) { a, p -> Money.addClamped(a, p.excessPaisa) },
        )
    }

    suspend fun recordPayment(
        loanId: Long,
        dateIso: String,
        amountPaisa: Long,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val loan = dao.getLoan(loanId) ?: throw FinanceValidationException("লোনটি খুঁজে পাওয়া যায়নি।")
        val paid = dao.getPaymentsRecent(loanId).fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val remaining = loan.totalPayablePaisa - paid
        if (amountPaisa > remaining && !allowOverpayment) {
            throw OverpaymentException(remaining.coerceAtLeast(0), amountPaisa)
        }
        val open = dao.getInstallmentsByDue(loanId).filter { it.paidPaisa < it.amountPaisa }
        dao.recordPayment(
            LoanPaymentEntity(
                loanId = loanId,
                dateIso = dateIso,
                amountPaisa = amountPaisa,
                method = method,
                note = note.trim(),
            ),
            open,
        )
        DataBus.poke()
    }

    suspend fun deletePayment(loanId: Long, paymentId: Long) {
        dao.deletePayment(paymentId)
        DataBus.poke()
    }

    /**
     * Edit a recorded payment as delete + re-record inside ONE Room
     * transaction, so a failure can never drop money silently.
     */
    suspend fun updatePayment(
        loanId: Long,
        paymentId: Long,
        dateIso: String,
        amountPaisa: Long,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        val existing = dao.getPayment(paymentId) ?: throw FinanceValidationException("পরিশোধটি খুঁজে পাওয়া যায়নি।")
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val paidOthers = dao.getPaymentsRecent(loanId).filterNot { it.id == paymentId }.fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val loanEntity = dao.getLoan(loanId) ?: throw FinanceValidationException("লোনটি খুঁজে পাওয়া যায়নি।")
        val openRemaining = loanEntity.totalPayablePaisa - paidOthers
        if (amountPaisa > openRemaining && !allowOverpayment) {
            throw OverpaymentException(openRemaining.coerceAtLeast(0), amountPaisa)
        }
        db.withTransaction {
            dao.deletePayment(paymentId)
            val open = dao.getInstallmentsByDue(loanId).filter { it.paidPaisa < it.amountPaisa }
            dao.recordPayment(
                LoanPaymentEntity(loanId = loanId, dateIso = dateIso, amountPaisa = amountPaisa, method = method, note = note.trim()),
                open,
            )
        }
        DataBus.poke()
    }

    suspend fun setArchived(loanId: Long, archived: Boolean) {
        dao.setArchived(loanId, archived)
        DataBus.poke()
    }

    suspend fun deleteLoan(loanId: Long) {
        dao.deleteLoanById(loanId)
        DataBus.poke()
    }
}
