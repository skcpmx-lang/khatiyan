package com.shohan.khatiyan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Money borrowed from people (Phase 13). */
@Serializable
@Entity(tableName = "persons", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Relationship label, e.g. বন্ধু / আত্মীয় / পরিবার / সহকর্মী. */
    val relationship: String = "বন্ধু",
    val phone: String = "",
    val note: String = "",
    val archived: Boolean = false,
    val createdAtIso: String = "",
)

@Serializable
@Entity(
    tableName = "personal_debts",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId"), Index("borrowedIso")],
)
data class PersonalDebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val borrowedIso: String,
    val amountPaisa: Long,
    val expectedReturnIso: String? = null,
    val note: String = "",
)

@Serializable
@Entity(
    tableName = "personal_repayments",
    foreignKeys = [
        ForeignKey(
            entity = PersonalDebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("debtId"), Index("dateIso")],
)
data class PersonalRepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val dateIso: String,
    val amountPaisa: Long,
    val excessPaisa: Long = 0,
    val method: String = "CASH",
    val note: String = "",
)
