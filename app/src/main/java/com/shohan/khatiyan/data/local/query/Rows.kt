package com.shohan.khatiyan.data.local.query

/** Projection rows for list screens (entity columns + SQL-computed aggregates). */

data class ShopRow(
    val id: Long,
    val name: String,
    val ownerName: String,
    val phone: String,
    val address: String,
    val category: String,
    val note: String,
    val archived: Boolean,
    val totalCreditPaisa: Long,
    val totalPaidPaisa: Long,
    val lastCreditIso: String?,
) {
    val balancePaisa: Long get() = totalCreditPaisa - totalPaidPaisa
}

data class LoanRow(
    val id: Long,
    val institution: String,
    val loanName: String,
    val principalPaisa: Long,
    val startIso: String,
    val annualRateBps: Int,
    val processingFeePaisa: Long,
    val totalPayablePaisa: Long,
    val installmentPaisa: Long,
    val frequency: String,
    val firstDueIso: String,
    val maturityIso: String,
    val note: String,
    val archived: Boolean,
    val createdAtIso: String,
    val totalPaidPaisa: Long,
    val advancePaisa: Long,
    val openCount: Int,
    val overdueCount: Int,
    val nextDueIso: String?,
) {
    val remainingPaisa: Long get() = totalPayablePaisa - totalPaidPaisa
}

data class EmiRow(
    val id: Long,
    val productName: String,
    val seller: String,
    val purchaseIso: String,
    val totalPricePaisa: Long,
    val downPaymentPaisa: Long,
    val financedPaisa: Long,
    val totalPayablePaisa: Long,
    val installmentPaisa: Long,
    val installmentCount: Int,
    val frequency: String,
    val firstDueIso: String,
    val note: String,
    val archived: Boolean,
    val createdAtIso: String,
    val totalPaidPaisa: Long,
    val advancePaisa: Long,
    val openCount: Int,
    val overdueCount: Int,
    val paidCount: Int,
    val nextDueIso: String?,
) {
    val remainingPaisa: Long get() = financedPaisa - totalPaidPaisa
}

data class PersonRow(
    val id: Long,
    val name: String,
    val relationship: String,
    val phone: String,
    val note: String,
    val archived: Boolean,
    val totalBorrowedPaisa: Long,
    val totalRepaidPaisa: Long,
    val openDebtCount: Int,
    val overdueCount: Int,
) {
    val remainingPaisa: Long get() = totalBorrowedPaisa - totalRepaidPaisa
}

data class DebtRow(
    val id: Long,
    val personId: Long,
    val personName: String,
    val borrowedIso: String,
    val amountPaisa: Long,
    val expectedReturnIso: String?,
    val note: String,
    val repaidPaisa: Long,
) {
    val remainingPaisa: Long get() = amountPaisa - repaidPaisa
}

data class MonthSumRow(val ym: String, val totalPaisa: Long)

data class NameSumRow(val name: String, val totalPaisa: Long)

data class ShopRefRow(val id: Long, val name: String)

data class CountRow(val count: Int)
