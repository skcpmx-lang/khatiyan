package com.shohan.khatiyan.domain.finance

/**
 * Thrown when a payment would exceed the remaining balance.
 * Policy (Phase 18): payments are REJECTED unless the user explicitly confirms
 * an overpayment, which is then recorded as an advance credit — never a
 * negative debt. Same behavior for shop/loan/EMI/personal.
 */
class OverpaymentException(
    val remainingPaisa: Long,
    val requestedPaisa: Long,
) : Exception("payment exceeds remaining balance") {

    val excessPaisa: Long get() = requestedPaisa - remainingPaisa
}
