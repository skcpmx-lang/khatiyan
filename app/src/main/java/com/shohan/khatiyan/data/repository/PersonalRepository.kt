package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.PersonEntity
import com.shohan.khatiyan.data.local.entity.PersonalDebtEntity
import com.shohan.khatiyan.data.local.entity.PersonalRepaymentEntity
import com.shohan.khatiyan.data.local.query.DebtRow
import com.shohan.khatiyan.data.local.query.PersonRow
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.FinanceValidationException
import kotlinx.coroutines.flow.Flow

/** Money borrowed from people (Phase 13). Same overpayment policy as the rest. */
class PersonalRepository(private val db: KhatiyanDatabase) {

    private val dao get() = db.personalDao()

    companion object {
        val RELATIONSHIPS = listOf("বন্ধু", "আত্মীয়", "পরিবার", "সহকর্মী", "পরিচিত", "অন্যান্য")
    }

    data class PersonDetail(
        val person: PersonEntity,
        val debts: List<DebtRow>,
    ) {
        val borrowedPaisa: Long get() = debts.fold(0L) { a, d -> Money.addClamped(a, d.amountPaisa) }
        val repaidPaisa: Long get() = debts.fold(0L) { a, d -> Money.addClamped(a, d.repaidPaisa) }
        val remainingPaisa: Long get() = borrowedPaisa - repaidPaisa
    }

    fun observePeople(q: String, hideArchived: Boolean): Flow<List<PersonRow>> {
        val today = BnDates.toIso(BnDates.today())
        return dao.observePeople(q, today, hideArchived)
    }

    suspend fun savePerson(person: PersonEntity): Long {
        if (person.name.isBlank()) throw FinanceValidationException("নাম লিখুন।")
        val entity = person.copy(
            relationship = person.relationship.ifBlank { RELATIONSHIPS.first() },
            createdAtIso = if (person.id == 0L) BnDates.toIso(BnDates.today()) else person.createdAtIso,
        )
        val id = if (person.id == 0L) dao.insertPerson(entity) else { dao.updatePerson(entity); person.id }
        DataBus.poke()
        return id
    }

    suspend fun loadDetail(personId: Long): PersonDetail? {
        val person = dao.getPerson(personId) ?: return null
        return PersonDetail(person, dao.getDebtsForPerson(personId))
    }

    suspend fun saveDebt(
        debt: PersonalDebtEntity,
    ) {
        if (debt.amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        if (dao.getPerson(debt.personId) == null) throw FinanceValidationException("আগে ব্যক্তির নাম যোগ করুন।")
        if (debt.id == 0L) dao.insertDebt(debt) else dao.updateDebt(debt)
        DataBus.poke()
    }

    suspend fun deleteDebt(debtId: Long) {
        dao.deleteDebt(debtId)
        DataBus.poke()
    }

    suspend fun recordRepayment(
        debtId: Long,
        dateIso: String,
        amountPaisa: Long,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val debt = dao.getDebt(debtId) ?: throw FinanceValidationException("ধারের রেকর্ডটি পাওয়া যায়নি।")
        val repaid = dao.getRepayments(debtId).fold(0L) { a, r -> Money.addClamped(a, r.amountPaisa) }
        val remaining = debt.amountPaisa - repaid
        if (amountPaisa > remaining && !allowOverpayment) {
            throw OverpaymentException(remaining.coerceAtLeast(0), amountPaisa)
        }
        val excess = (amountPaisa - remaining).coerceAtLeast(0)
        dao.insertRepayment(
            PersonalRepaymentEntity(
                debtId = debtId,
                dateIso = dateIso,
                amountPaisa = amountPaisa,
                excessPaisa = excess,
                method = method,
                note = note.trim(),
            ),
        )
        DataBus.poke()
    }

    suspend fun deleteRepayment(repaymentId: Long) {
        dao.deleteRepayment(repaymentId)
        DataBus.poke()
    }

    suspend fun setArchived(personId: Long, archived: Boolean) {
        dao.setArchived(personId, archived)
        DataBus.poke()
    }

    suspend fun deletePerson(personId: Long) {
        dao.deletePersonById(personId)
        DataBus.poke()
    }
}
