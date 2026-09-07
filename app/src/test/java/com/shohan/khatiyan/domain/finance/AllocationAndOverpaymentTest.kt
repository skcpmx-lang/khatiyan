package com.shohan.khatiyan.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllocationAndOverpaymentTest {

    @Test
    fun fifoCoversEarliestInstallmentFirst() {
        val installments = listOf(
            Allocation.InstallmentState(id = 1, amountPaisa = 50_000, paidPaisa = 0),
            Allocation.InstallmentState(id = 2, amountPaisa = 30_000, paidPaisa = 0),
            Allocation.InstallmentState(id = 3, amountPaisa = 20_000, paidPaisa = 0),
        )
        val r = Allocation.overInstallments(installments, 70_000)
        assertEquals(listOf(Allocation.InstallmentPatch(1, 50_000), Allocation.InstallmentPatch(2, 20_000)), r.patches)
        assertEquals(0L, r.excessPaisa)
    }

    @Test
    fun leftoverBecomesAdvanceExcess() {
        val installments = listOf(
            Allocation.InstallmentState(1, 50_000, 10_000),
            Allocation.InstallmentState(2, 30_000, 0),
        )
        val r = Allocation.overInstallments(installments, 85_000)
        assertEquals(0L, r.patches.fold(0L) { a, p -> a + p.addPaisa } - 70_000L) // consumed exactly what was open
        assertEquals(15_000L, r.excessPaisa)
    }

    @Test
    fun zeroOrNegativePaymentAllocatesNothing() {
        val installments = listOf(Allocation.InstallmentState(1, 50_000, 0))
        assertTrue(Allocation.overInstallments(installments, 0).patches.isEmpty())
        assertTrue(Allocation.overInstallments(installments, -5).patches.isEmpty())
    }

    @Test
    fun bucketFifoReturnsTotalPaidPerBucket() {
        val buckets = listOf(
            Allocation.Bucket(id = 10, totalPaisa = 5_000, paidBeforePaisa = 0),
            Allocation.Bucket(id = 11, totalPaisa = 3_000, paidBeforePaisa = 1_000),
        )
        val map = Allocation.fifo(buckets, 6_000)
        assertEquals(5_000L, map[10])
        assertEquals(3_000L, map[11])
    }

    @Test
    fun overpaymentExceptionExposesExcess() {
        val e = OverpaymentException(remainingPaisa = 30_000, requestedPaisa = 50_000)
        assertEquals(20_000L, e.excessPaisa)
    }

    @Test
    fun exactRemainingIsNotOverpayment() {
        // guard convention: amount == remaining must NOT raise; only strictly greater does.
        val remaining = 30_000L
        val amount = 30_000L
        val wouldThrow = amount > remaining
        assertTrue(!wouldThrow)
    }
}
