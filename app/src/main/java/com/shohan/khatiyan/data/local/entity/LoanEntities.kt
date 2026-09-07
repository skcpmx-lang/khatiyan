package com.shohan.khatiyan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Bank/NGO loan tables (Phase 11). */
@Serializable
@Entity(tableName = "loans", indices = [Index("loanName"), Index("institution")])
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val institution: String,
    val loanName: String,
    val principalPaisa: Long,
    val startIso: String,
    /** Annual interest in basis points (12.5% = 1250). 0 = no interest field. */
    val annualRateBps: Int = 0,
    val processingFeePaisa: Long = 0,
    /** Principal + interest + fee (user-adjustable) — the schedule's target. */
    val totalPayablePaisa: Long,
    /** Base installment amount (0 = derived from schedule division). */
    val installmentPaisa: Long = 0,
    /** Frequency.name */
    val frequency: String = "MONTHLY",
    val firstDueIso: String,
    val maturityIso: String,
    val note: String = "",
    val archived: Boolean = false,
    val createdAtIso: String = "",
)

@Serializable
@Entity(
    tableName = "loan_installments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("loanId"), Index("dueIso")],
)
data class LoanInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val number: Int,
    val dueIso: String,
    val amountPaisa: Long,
    /** Denormalized allocation total, maintained transactionally by the DAO. */
    val paidPaisa: Long = 0,
)

@Serializable
@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("loanId"), Index("dateIso")],
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val dateIso: String,
    val amountPaisa: Long,
    /** Portion that exceeded the open schedule (advance credit, Phase 18). */
    val excessPaisa: Long = 0,
    val method: String = "CASH",
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "loan_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = LoanPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LoanInstallmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["installmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paymentId"), Index("installmentId")],
)
data class LoanAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val installmentId: Long,
    val amountPaisa: Long,
)
