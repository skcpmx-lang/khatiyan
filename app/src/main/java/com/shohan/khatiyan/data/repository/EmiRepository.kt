package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.EmiInstallmentEntity
import com.shohan.khatiyan.data.local.entity.EmiPaymentEntity
import com.shohan.khatiyan.data.local.entity.EmiPurchaseEntity
import com.shohan.khatiyan.data.local.query.EmiRow
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.finance.ScheduleGenerator
import com.shohan.khatiyan.domain.model.Frequency
import com.shohan.khatiyan.domain.model.InstallmentPlan
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.FinanceValidationException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * EMI / product installments (Phase 12). The installment schedule covers the
 * financed amount (total payable − down payment); the down payment is part of
 * the purchase record. "পরিশোধিত" on screens = down payment + recorded payments.
 */
class EmiRepository(private val db: KhatiyanDatabase) {

    private val dao get() = db.emiDao()

    data class EmiDraft(
        val id: Long,
        val productName: String,
        val seller: String,
        val purchaseIso: String,
        val totalPricePaisa: Long,
        val downPaymentPaisa: Long,
        /** 0 → equals totalPricePaisa */
        val totalPayablePaisa: Long,
        /** 0 → derived from schedule division */
        val installmentPaisa: Long,
        val installmentCount: Int,
        val frequency: Frequency,
        val firstDueIso: String,
        val note: String,
    )

    data class InstallmentView(
        val entity: EmiInstallmentEntity,
        val number: Int,
        val dueDate: LocalDate?,
        val amountPaisa: Long,
        val paidPaisa: Long,
        val status: LoanRepository.InstallmentStatus,
    )

    data class EmiDetail(
        val emi: EmiPurchaseEntity,
        val installments: List<InstallmentView>,
        val payments: List<EmiPaymentEntity>,
        val totalPaidPaisa: Long,
        val advancePaisa: Long,
    ) {
        val remainingPaisa: Long get() = emi.financedPaisa - totalPaidPaisa
    }

    fun observeEmis(q: String, showArchived: Boolean): Flow<List<EmiRow>> {
        val today = BnDates.toIso(BnDates.today())
        return dao.observeEmis(q, today, showArchived)
    }

    fun buildSchedule(draft: EmiDraft): List<InstallmentPlan> {
        val firstDue = BnDates.fromIso(draft.firstDueIso) ?: LocalDate.now()
        val count = draft.installmentCount.coerceAtLeast(1)
        val totalPayable = if (draft.totalPayablePaisa > 0) draft.totalPayablePaisa else draft.totalPricePaisa
        val financed = (totalPayable - draft.downPaymentPaisa).coerceAtLeast(0)
        return ScheduleGenerator.generate(
            firstDue = firstDue,
            frequency = draft.frequency,
            count = count,
            totalPaisa = financed,
            fixedInstallmentPaisa = draft.installmentPaisa.takeIf { it > 0 },
        )
    }

    suspend fun save(draft: EmiDraft) {
        if (draft.productName.isBlank()) throw FinanceValidationException("পণ্যের নাম লিখুন।")
        if (draft.totalPricePaisa <= 0L) throw FinanceValidationException("পণ্যের মোট মূল্য লিখুন।")
        val totalPayable = if (draft.totalPayablePaisa > 0) draft.totalPayablePaisa else draft.totalPricePaisa
        val financed = totalPayable - draft.downPaymentPaisa
        if (financed < 0) throw FinanceValidationException("অগ্রিম পরিশোধ মোট মূল্যের চেয়ে বেশি হতে পারবে না।")
        val schedule = buildSchedule(draft)
        val base = schedule.firstOrNull()?.amountPaisa ?: 0L
        val entity = EmiPurchaseEntity(
            id = draft.id,
            productName = draft.productName.trim(),
            seller = draft.seller.trim(),
            purchaseIso = draft.purchaseIso,
            totalPricePaisa = draft.totalPricePaisa,
            downPaymentPaisa = draft.downPaymentPaisa.coerceAtLeast(0),
            financedPaisa = financed,
            totalPayablePaisa = totalPayable,
            installmentPaisa = base,
            installmentCount = schedule.size,
            frequency = draft.frequency.name,
            firstDueIso = draft.firstDueIso,
            note = draft.note.trim(),
            createdAtIso = BnDates.toIso(BnDates.today()),
        )
        dao.saveEmiWithSchedule(entity, schedule)
        DataBus.poke()
    }

    suspend fun loadDetail(emiId: Long): EmiDetail? {
        val emi = dao.getEmi(emiId) ?: return null
        val today = BnDates.today()
        val installments = dao.getInstallments(emiId).map { row ->
            val due = BnDates.fromIso(row.dueIso)
            val status = when {
                row.paidPaisa >= row.amountPaisa -> LoanRepository.InstallmentStatus.PAID
                due == null -> LoanRepository.InstallmentStatus.PENDING
                due.isBefore(today) -> LoanRepository.InstallmentStatus.OVERDUE
                due == today -> LoanRepository.InstallmentStatus.DUE_TODAY
                else -> LoanRepository.InstallmentStatus.PENDING
            }
            InstallmentView(row, row.number, due, row.amountPaisa, row.paidPaisa, status)
        }
        val payments = dao.getPaymentsRecent(emiId)
        return EmiDetail(
            emi = emi,
            installments = installments,
            payments = payments,
            totalPaidPaisa = payments.fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) },
            advancePaisa = payments.fold(0L) { a, p -> Money.addClamped(a, p.excessPaisa) },
        )
    }

    suspend fun recordPayment(
        emiId: Long,
        dateIso: String,
        amountPaisa: Long,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val emi = dao.getEmi(emiId) ?: throw FinanceValidationException("EMI রেকর্ডটি খুঁজে পাওয়া যায়নি।")
        val paid = dao.getPaymentsRecent(emiId).fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val remaining = emi.financedPaisa - paid
        if (amountPaisa > remaining && !allowOverpayment) {
            throw OverpaymentException(remaining.coerceAtLeast(0), amountPaisa)
        }
        val open = dao.getInstallmentsByDue(emiId).filter { it.paidPaisa < it.amountPaisa }
        dao.recordPayment(
            EmiPaymentEntity(
                emiId = emiId,
                dateIso = dateIso,
                amountPaisa = amountPaisa,
                method = method,
                note = note.trim(),
            ),
            open,
        )
        DataBus.poke()
    }

    suspend fun deletePayment(emiId: Long, paymentId: Long) {
        dao.deletePayment(paymentId)
        DataBus.poke()
    }

    suspend fun setArchived(emiId: Long, archived: Boolean) {
        dao.setArchived(emiId, archived)
        DataBus.poke()
    }

    suspend fun deleteEmi(emiId: Long) {
        dao.deleteEmiById(emiId)
        DataBus.poke()
    }
}
