package com.shohan.khatiyan.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.ShopEntity
import com.shohan.khatiyan.domain.finance.OverpaymentException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShopRepositoryTest {

    private lateinit var db: KhatiyanDatabase
    private lateinit var repo: ShopRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KhatiyanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ShopRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun creditTotalsComeFromItems_notManualSum() = runBlocking {
        val shopId = repo.saveShop(ShopEntity(name = "স্টোর", ownerName = "রহিম"))
        repo.saveCredit(
            ShopRepository.CreditDraft(
                id = 0,
                shopId = shopId,
                dateIso = "2024-05-01",
                dueDateIso = null,
                note = "",
                items = listOf(
                    ShopRepository.CreditItemInput("চাল", 2.0, "কেজি", 3_000, 6_000),
                    ShopRepository.CreditItemInput("তেল", 1.0, "লিটার", 15_000, 15_000),
                ),
            ),
        )
        val detail = repo.loadDetail(shopId)!!
        assertEquals(21_000L, detail.totalCreditPaisa)
        assertEquals(21_000L, detail.balancePaisa)
        assertEquals(listOf("চাল", "তেল"), detail.credits.first().items.map { it.name })
    }

    @Test
    fun overpaymentRejectedUnlessConfirmed_thenBecomesAdvance() = runBlocking {
        val shopId = repo.saveShop(ShopEntity(name = "স্টোর"))
        repo.saveCredit(
            ShopRepository.CreditDraft(
                0, shopId, "2024-05-01", null, "",
                listOf(ShopRepository.CreditItemInput("ডিম", 1.0, "ডজন", 15_000, 15_000)),
            ),
        )
        repo.addPayment(shopId, "2024-05-05", 15_000, "CASH", "", allowOverpayment = false)
        assertEquals(0L, repo.loadDetail(shopId)!!.balancePaisa)

        repo.saveCredit(
            ShopRepository.CreditDraft(
                0, shopId, "2024-05-10", null, "",
                listOf(ShopRepository.CreditItemInput("মুরগি", 2.0, "পিস", 10_000, 20_000)),
            ),
        )
        val e = assertThrows(OverpaymentException::class.java) {
            runBlocking { repo.addPayment(shopId, "2024-05-11", 25_000, "BKASH", "", allowOverpayment = false) }
        }
        assertEquals(20_000L, e.remainingPaisa)
        assertEquals(5_000L, e.excessPaisa)

        repo.addPayment(shopId, "2024-05-11", 25_000, "BKASH", "", allowOverpayment = true)
        assertEquals(-5_000L, repo.loadDetail(shopId)!!.balancePaisa) // advance credit, never dropped
    }

    @Test
    fun paymentsApplyFifoToEachCreditOutstanding() = runBlocking {
        val shopId = repo.saveShop(ShopEntity(name = "স্টোর"))
        repo.saveCredit(ShopRepository.CreditDraft(0, shopId, "2024-05-01", null, "", listOf(ShopRepository.CreditItemInput("a", 1.0, "pc", 10_000, 10_000))))
        repo.saveCredit(ShopRepository.CreditDraft(0, shopId, "2024-05-02", null, "", listOf(ShopRepository.CreditItemInput("b", 1.0, "pc", 5_000, 5_000))))
        repo.addPayment(shopId, "2024-05-03", 7_000, "CASH", "", false)
        val detail = repo.loadDetail(shopId)!!
        // oldest credit (a = 10,000) absorbs the 7,000 first; list is newest-first
        assertEquals(3_000L, detail.credits[1].outstandingPaisa)
        assertEquals(5_000L, detail.credits[0].outstandingPaisa)
        assertEquals(8_000L, detail.balancePaisa)
    }

    @Test
    fun editingPaymentRecomputesBalances() = runBlocking {
        val shopId = repo.saveShop(ShopEntity(name = "স্টোর"))
        repo.saveCredit(ShopRepository.CreditDraft(0, shopId, "2024-05-01", null, "", listOf(ShopRepository.CreditItemInput("a", 1.0, "pc", 10_000, 10_000))))
        repo.addPayment(shopId, "2024-05-02", 4_000, "CASH", "", false)
        val payment = db.shopDao().getPayments(shopId).single()
        repo.updatePayment(payment, 6_000, "2024-05-02", "NAGAD", "ঠিক করা", false)
        val detail = repo.loadDetail(shopId)!!
        assertEquals(6_000L, detail.totalPaidPaisa)
        assertEquals(4_000L, detail.balancePaisa)
        assertEquals("NAGAD", detail.payments.single().method)
    }

    @Test
    fun deletingCreditRestoresBalance() = runBlocking {
        val shopId = repo.saveShop(ShopEntity(name = "স্টোর"))
        repo.saveCredit(ShopRepository.CreditDraft(0, shopId, "2024-05-01", null, "", listOf(ShopRepository.CreditItemInput("a", 1.0, "pc", 10_000, 10_000))))
        val credit = db.shopDao().getCredits(shopId).single()
        repo.deleteCredit(credit.id)
        val detail = repo.loadDetail(shopId)!!
        assertEquals(0L, detail.totalCreditPaisa)
        assertEquals(0L, detail.balancePaisa)
    }
}
