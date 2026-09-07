package com.shohan.khatiyan.domain.model

/** Payment method choices for any money movement (Bangla labels, Phase 6). */
enum class PaymentMethod(val labelBn: String) {
    CASH("নগদ"),
    BKASH("বিকাশ"),
    NAGAD("নগদ (Nagad)"),
    ROCKET("রকেট"),
    BANK("ব্যাংক"),
    OTHER("অন্যান্য"),
    ;

    companion object {
        fun fromName(name: String?): PaymentMethod =
            entries.firstOrNull { it.name == name } ?: CASH
    }
}

/** Installment cadence for loans and EMI (Phase 11/12). */
enum class Frequency(val labelBn: String, val shortBn: String) {
    WEEKLY("সাপ্তাহিক", "সাপ্তা"),
    MONTHLY("মাসিক", "মাস"),
    ;

    companion object {
        fun fromName(name: String?): Frequency =
            entries.firstOrNull { it.name == name } ?: MONTHLY
    }
}

/** The four obligation books of the app. */
enum class ObligationKind(val labelBn: String) {
    SHOP("দোকানের বাকী"),
    LOAN("লোন"),
    EMI("কিস্তি (EMI)"),
    PERSONAL("ব্যক্তিগত ধার"),
}

enum class DueStatus { OVERDUE, DUE_TODAY, UPCOMING }

/** A single generated payment slot in a loan/EMI schedule. */
data class InstallmentPlan(
    val number: Int,
    val dueDate: java.time.LocalDate,
    val amountPaisa: Long,
)

/** Transaction kinds recorded in the central ledger view (Phase 16). */
enum class TxnType(val key: String, val labelBn: String) {
    SHOP_CREDIT("shop_credit", "দোকানে বাকী"),
    SHOP_PAYMENT("shop_payment", "দোকানের পরিশোধ"),
    LOAN_PAYMENT("loan_payment", "লোনের কিস্তি"),
    EMI_PAYMENT("emi_payment", "ইএমআই কিস্তি"),
    PERSONAL_BORROW("personal_borrow", "ধার নেওয়া"),
    PERSONAL_REPAY("personal_repay", "ধার পরিশোধ"),
    INCOME("income", "আয়"),
    EXPENSE("expense", "ব্যয়"),
    ;

    /** Money the user received or owes-less from today's perspective. */
    val isRepayment: Boolean
        get() = this == SHOP_PAYMENT || this == LOAN_PAYMENT ||
            this == EMI_PAYMENT || this == PERSONAL_REPAY
}
