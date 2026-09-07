package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.ShopCreditEntity
import com.shohan.khatiyan.data.local.entity.ShopCreditItemEntity
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.data.local.entity.ShopPaymentEntity
import com.shohan.khatiyan.data.local.query.ShopRow
import com.shohan.khatiyan.domain.finance.Allocation
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.FinanceValidationException
import kotlinx.coroutines.flow.Flow

/**
 * Shop / market credit book (Phase 10). Balances are always derived:
 * balance = Σ credit totals − Σ payments. Editing an entry rewrites it inside
 * one Room transaction so items never drift from their credit total.
 */
class ShopRepository(private val db: KhatiyanDatabase) {

    private val dao get() = db.shopDao()

    data class CreditItemInput(
        val name: String,
        val quantity: Double,
        val unit: String,
        val unitPricePaisa: Long,
        val lineTotalPaisa: Long,
        val note: String = "",
    )

    data class CreditDraft(
        val id: Long,
        val shopId: Long,
        val dateIso: String,
        val dueDateIso: String?,
        val note: String,
        val items: List<CreditItemInput>,
    )

    data class CreditWithItems(
        val credit: ShopCreditEntity,
        val items: List<ShopCreditItemEntity>,
        /** Portion of this credit not yet covered by payments (FIFO). */
        val outstandingPaisa: Long,
    )

    data class ShopDetail(
        val shop: ShopEntity,
        val credits: List<CreditWithItems>,
        val payments: List<ShopPaymentEntity>,
        val totalCreditPaisa: Long,
        val totalPaidPaisa: Long,
    ) {
        val balancePaisa: Long get() = totalCreditPaisa - totalPaidPaisa
    }

    fun observeShops(q: String, hideArchived: Boolean): Flow<List<ShopRow>> =
        dao.observeShopRows(q, hideArchived)

    suspend fun loadDetail(shopId: Long): ShopDetail? {
        val shop = dao.getShop(shopId) ?: return null
        val creditsAsc = dao.getCredits(shopId).sortedWith(compareBy({ it.dateIso }, { it.id }))
        val paidTotal = dao.getPayments(shopId).fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val buckets = creditsAsc.map { Allocation.Bucket(it.id, it.totalPaisa, 0) }
        val paidByCredit = Allocation.fifo(buckets, paidTotal)
        val withItems = creditsAsc.map { c ->
            CreditWithItems(
                credit = c,
                items = dao.getItems(c.id),
                outstandingPaisa = c.totalPaisa - (paidByCredit[c.id] ?: 0L),
            )
        }.reversed()
        return ShopDetail(
            shop = shop,
            credits = withItems,
            payments = dao.getPayments(shopId),
            totalCreditPaisa = creditsAsc.fold(0L) { a, c -> Money.addClamped(a, c.totalPaisa) },
            totalPaidPaisa = paidTotal,
        )
    }

    suspend fun saveShop(shop: ShopEntity): Long {
        if (shop.name.isBlank()) {
            throw FinanceValidationException("দোকানের নাম লিখুন।")
        }
        val now = BnDates.toIso(BnDates.today())
        val id = dao.upsertShop(
            shop.copy(
                createdAtIso = if (shop.id == 0L) now else shop.createdAtIso,
                updatedAtIso = now,
            ),
        )
        DataBus.poke()
        return id
    }

    suspend fun saveCredit(draft: CreditDraft) {
        if (draft.items.isEmpty()) {
            throw FinanceValidationException("অন্তত একটি পণ্যের বিবর দিন।")
        }
        if (draft.items.any { it.name.isBlank() }) {
            throw FinanceValidationException("প্রতিটি পণ্যের নাম দরকার।")
        }
        val total = draft.items.fold(0L) { a, it -> Money.addClamped(a, it.lineTotalPaisa) }
        if (total <= 0L) throw FinanceValidationException("মোট যোগফল ০-এর বেশি হতে হবে।")
        dao.saveCredit(
            ShopCreditEntity(
                id = draft.id,
                shopId = draft.shopId,
                dateIso = draft.dateIso,
                dueDateIso = draft.dueDateIso,
                totalPaisa = total,
                note = draft.note.trim(),
            ),
            draft.items.mapIndexed { idx, i ->
                ShopCreditItemEntity(
                    creditId = draft.id,
                    name = i.name.trim(),
                    quantity = i.quantity,
                    unit = i.unit.trim(),
                    unitPricePaisa = i.unitPricePaisa,
                    totalPaisa = i.lineTotalPaisa,
                    sortOrder = idx,
                    note = i.note.trim(),
                )
            },
        )
        dao.getShop(draft.shopId)?.let {
            dao.upsertShop(it.copy(updatedAtIso = BnDates.toIso(BnDates.today())))
        }
        DataBus.poke()
    }

    suspend fun deleteCredit(creditId: Long) {
        dao.deleteCredit(creditId)
        DataBus.poke()
    }

    /**
     * Payment with overpayment policy (Phase 18): amount beyond the live balance
     * is rejected unless [allowOverpayment]; then it is stored as advance credit
     * (negative balance) — never silently dropped, never corrupting totals.
     */
    suspend fun addPayment(
        shopId: Long,
        dateIso: String,
        amountPaisa: Long,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val credits = dao.getCredits(shopId)
        val payments = dao.getPayments(shopId)
        val total = credits.fold(0L) { a, c -> Money.addClamped(a, c.totalPaisa) }
        val paid = payments.fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val remaining = total - paid
        if (amountPaisa > remaining && !allowOverpayment) {
            throw OverpaymentException(remaining.coerceAtLeast(0), amountPaisa)
        }
        dao.insertPayment(
            ShopPaymentEntity(
                shopId = shopId,
                dateIso = dateIso,
                amountPaisa = amountPaisa,
                method = method,
                note = note.trim(),
            ),
        )
        dao.getShop(shopId)?.let {
            dao.upsertShop(it.copy(updatedAtIso = dateIso))
        }
        DataBus.poke()
    }

    suspend fun updatePayment(
        payment: ShopPaymentEntity,
        amountPaisa: Long,
        dateIso: String,
        method: String,
        note: String,
        allowOverpayment: Boolean,
    ) {
        if (amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        val payments = dao.getPayments(payment.shopId).filterNot { it.id == payment.id }
        val paidOthers = payments.fold(0L) { a, p -> Money.addClamped(a, p.amountPaisa) }
        val total = dao.getCredits(payment.shopId).fold(0L) { a, c -> Money.addClamped(a, c.totalPaisa) }
        val remaining = total - paidOthers
        if (amountPaisa > remaining && !allowOverpayment) {
            throw OverpaymentException(remaining.coerceAtLeast(0), amountPaisa)
        }
        dao.updatePayment(payment.copy(amountPaisa = amountPaisa, dateIso = dateIso, method = method, note = note.trim()))
        DataBus.poke()
    }

    suspend fun deletePayment(paymentId: Long) {
        dao.deletePayment(paymentId)
        DataBus.poke()
    }

    suspend fun setArchived(shopId: Long, archived: Boolean) {
        dao.setArchived(shopId, archived, BnDates.toIso(BnDates.today()))
        DataBus.poke()
    }

    /** Hard delete removes the whole ledger (cascade) — UI requires typed confirmation. */
    suspend fun deleteShop(shopId: Long) {
        val shop = dao.getShop(shopId) ?: return
        dao.deleteShop(shop)
        DataBus.poke()
    }
}
