package com.shohan.khatiyan.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Shop / market-credit tables (Phase 10). Money columns are integer PAISA.
 * All fields are plain types so Room and kotlinx.serialization share the same
 * classes for the local backup file (Phase 26).
 */
@Serializable
@Entity(
    tableName = "shops",
    indices = [Index("name"), Index("updatedAtIso")],
)
data class ShopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val category: String = "",
    val note: String = "",
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "") val createdAtIso: String = "",
    @ColumnInfo(defaultValue = "") val updatedAtIso: String = "",
)

@Serializable
@Entity(
    tableName = "shop_credits",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shopId"), Index("dateIso")],
)
data class ShopCreditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val dateIso: String,
    /** Optional informal "settle by" date used by the upcoming-payment engine. */
    val dueDateIso: String? = null,
    /** Sum of item totals, denormalized for fast aggregation (written in the same transaction). */
    val totalPaisa: Long,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "shop_credit_items",
    foreignKeys = [
        ForeignKey(
            entity = ShopCreditEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("creditId")],
)
data class ShopCreditItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditId: Long,
    val name: String,
    /** Display-only quantity (may be fractional like 2.5 kg); never used for balance math. */
    val quantity: Double = 1.0,
    val unit: String = "",
    val unitPricePaisa: Long = 0,
    /** Authoritative line total in paisa (unit*qty rounded at entry time). */
    val totalPaisa: Long,
    val sortOrder: Int = 0,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "shop_payments",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["id"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shopId"), Index("dateIso")],
)
data class ShopPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val dateIso: String,
    val amountPaisa: Long,
    /** PaymentMethod.name */
    val method: String = "CASH",
    val note: String = "",
)
