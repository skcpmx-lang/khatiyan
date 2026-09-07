package com.shohan.khatiyan.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.domain.finance.OverpaymentException
import com.shohan.khatiyan.domain.model.Frequency
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
class LoanRepositoryTest {

    private lateinit var db: KhatiyanDatabase
    private lateinit var repo: LoanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KhatiyanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LoanRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(id: Long = 0) = LoanRepository.LoanDraft(
        id = id,
        institution = "গ্রামীণ ব্যাংক",
        loanName = "বৃক্ষগোছা ঋণ",
        principalPaisa = 100_000L,
        startIso = "2024-01-01",
        annualRateBps = 0,
        processingFeePaisa = 0,
        totalPayablePaisa = 100_000L,
        installmentPaisa = 0,
        frequency = Frequency.MONTHLY,
        firstDueIso = "2024-02-01",
        maturityIso = "2024-05-01",
        installmentCount = 0,
        note = "",
    )

    @Test
    fun saveGeneratesScheduleMatchingTotal() = runBlocking {
        repo.save(draft())
        val loanId = db.loanDao().getAllLoans().single().id
        val detail = repo.loadDetail(loanId)!!
        assertEquals(4, detail.installments.size)
        assertEquals(100_000L, detail.installments.sumOf { it.amountPaisa })
        assertEquals(0L, detail.totalPaidPaisa)
        assertEquals(100_000L, detail.remainingPaisa)
    }

    @Test
    fun paymentAllocatesFifoAcrossInstallments() = runBlocking {
        repo.save(draft())
        val loanId = db.loanDao().getAllLoans().single().id
        repo.recordPayment(loanId, "2024-02-05", 30_000, "CASH", "", false)
        val detail = repo.loadDetail(loanId)!!
        assertEquals(30_000L, detail.totalPaidPaisa)
        assertEquals(25_000L, detail.installments[0].paidPaisa)
        assertEquals(LoanRepository.InstallmentStatus.PAID, detail.installments[0].status)
        assertEquals(5_000L, detail.installments[1].paidPaisa)
        // 70k left over across the remaining schedule
        assertEquals(70_000L, detail.remainingPaisa)
    }

    @Test
    fun overpaymentNeedsConfirmation_thenRecordsAdvance() = runBlocking {
        repo.save(draft())
        val loanId = db.loanDao().getAllLoans().single().id
        val e = assertThrows(OverpaymentException::class.java) {
            runBlocking { repo.recordPayment(loanId, "2024-02-05", 120_000, "CASH", "", false) }
        }
        assertEquals(20_000L, e.excessPaisa)
        repo.recordPayment(loanId, "2024-02-05", 120_000, "CASH", "advance", true)
        val detail = repo.loadDetail(loanId)!!
        assertEquals(-20_000L, detail.remainingPaisa)
        assertEquals(20_000L, detail.advancePaisa)
    }

    @Test
    fun editPaymentIsTransactionalAndRechecksOverpayment() = runBlocking {
        repo.save(draft())
        val loanId = db.loanDao().getAllLoans().single().id
        repo.recordPayment(loanId, "2024-02-05", 30_000, "CASH", "", false)
        val payment = db.loanDao().getPaymentsRecent(loanId).single()

        // growing beyond the live remainder must be rejected…
        assertThrows(OverpaymentException::class.java) {
            runBlocking { repo.updatePayment(loanId, payment.id, "2024-02-05", 130_000, "CASH", "", false) }
        }
        // …and the rejected edit must NOT have deleted the old payment
        assertEquals(30_000L, repo.loadDetail(loanId)!!.totalPaidPaisa)

        // shrinking is fine
        repo.updatePayment(loanId, payment.id, "2024-02-05", 15_000, "BKASH", "ভুল সংশোধন", false)
        val detail = repo.loadDetail(loanId)!!
        assertEquals(15_000L, detail.totalPaidPaisa)
        assertEquals("BKASH", detail.payments.single().method)
        assertEquals(85_000L, detail.remainingPaisa)
    }

    @Test
    fun deletingPaymentReturnsMoneyToTheLedger() = runBlocking {
        repo.save(draft())
        val loanId = db.loanDao().getAllLoans().single().id
        repo.recordPayment(loanId, "2024-02-05", 30_000, "CASH", "", false)
        val payment = db.loanDao().getPaymentsRecent(loanId).single()
        repo.deletePayment(loanId, payment.id)
        val detail = repo.loadDetail(loanId)!!
        assertEquals(0L, detail.totalPaidPaisa)
        assertEquals(100_000L, detail.remainingPaisa)
        assertEquals(0L, detail.installments[0].paidPaisa)
    }
}
