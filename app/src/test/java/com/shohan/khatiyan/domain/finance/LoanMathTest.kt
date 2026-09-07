package com.shohan.khatiyan.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoanMathTest {

    @Test
    fun rateParsing() {
        assertEquals(1250, LoanMath.parseRateBps("12.5"))
        assertEquals(1250, LoanMath.parseRateBps("১২.৫"))
        assertEquals(900, LoanMath.parseRateBps("9"))
        assertEquals(0, LoanMath.parseRateBps("0"))
        assertNull(LoanMath.parseRateBps("abc"))
        assertNull(LoanMath.parseRateBps("1.2.3"))
        assertNull(LoanMath.parseRateBps("1001")) // absurd rate
    }

    @Test
    fun rateLabelRoundTrip() {
        assertEquals("12.50", LoanMath.ratePercentLabel(1250))
        assertEquals("12", LoanMath.ratePercentLabel(1200))
        assertEquals(1250, LoanMath.parseRateBps(LoanMath.ratePercentLabel(1250)))
    }

    @Test
    fun flatInterest_oneYearExactly() {
        val from = LocalDate.of(2023, 1, 1)
        val to = LocalDate.of(2024, 1, 1)
        // ৳10,000 principal (1,000,000 paisa) @ 12% flat for exactly 365 days = ৳1,200
        assertEquals(120_000L, LoanMath.flatInterestPaisa(1_000_000L, 1200, from, to))
    }

    @Test
    fun noInterestMeansTotalEqualsPrincipalPlusFee() {
        val from = LocalDate.of(2023, 1, 1)
        val to = LocalDate.of(2024, 1, 1)
        assertEquals(1_050_000L, LoanMath.totalPayablePaisa(1_000_000L, 0, from, to, 50_000L))
    }

    @Test
    fun totalPayableComposedOfPrincipalInterestFee() {
        val from = LocalDate.of(2023, 1, 1)
        val to = LocalDate.of(2024, 1, 1)
        assertEquals(1_170_000L, LoanMath.totalPayablePaisa(1_000_000L, 1200, from, to, 50_000L))
    }

    @Test
    fun zeroRateGivesZeroInterest() {
        assertEquals(0L, LoanMath.flatInterestPaisa(1_000_000L, 0, LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1)))
        assertEquals(0L, LoanMath.flatInterestPaisa(0L, 1200, LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1)))
    }
}
