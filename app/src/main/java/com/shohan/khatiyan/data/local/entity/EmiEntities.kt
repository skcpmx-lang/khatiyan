package com.shohan.khatiyan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** EMI / product installment tables (Phase 12). Schedule covers financedPaisa
 *  (= totalPayable − downPayment); the down payment is recorded on the purchase. */
@Serializable
@Entity(tableName = "emi_purchases", indices = [Index("productName"), Index("seller")])
data class EmiPurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val seller: String,
    val purchaseIso: String,
    val totalPricePaisa: Long,
    val downPaymentPaisa: Long = 0,
    val financedPaisa: Long,
    /** Full cost to the user including any interest/fees (≥ price or as entered). */
    val totalPayablePaisa: Long,
    val installmentPaisa: Long = 0,
    val installmentCount: Int = 0,
    val frequency: String = "MONTHLY",
    val firstDueIso: String,
    val note: String = "",
    val archived: Boolean = false,
    val createdAtIso: String = "",
)

@Serializable
@Entity(
    tableName = "emi_installments",
    foreignKeys = [
        ForeignKey(
            entity = EmiPurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["emiId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("emiId"), Index("dueIso")],
)
data class EmiInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emiId: Long,
    val number: Int,
    val dueIso: String,
    val amountPaisa: Long,
    val paidPaisa: Long = 0,
)

@Serializable
@Entity(
    tableName = "emi_payments",
    foreignKeys = [
        ForeignKey(
            entity = EmiPurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["emiId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("emiId"), Index("dateIso")],
)
data class EmiPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emiId: Long,
    val dateIso: String,
    val amountPaisa: Long,
    val excessPaisa: Long = 0,
    val method: String = "CASH",
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "emi_payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = EmiPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EmiInstallmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["installmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("paymentId"), Index("installmentId")],
)
data class EmiAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val installmentId: Long,
    val amountPaisa: Long,
)
