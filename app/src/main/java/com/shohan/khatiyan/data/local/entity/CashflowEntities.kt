package com.shohan.khatiyan.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Income / expense records (Phases 14–15). */
@Serializable
@Entity(tableName = "incomes", indices = [Index("dateIso"), Index("category")])
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String,
    val amountPaisa: Long,
    val source: String = "",
    val category: String = "বেতন",
    val note: String = "",
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("dateIso"), Index("category")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String,
    val amountPaisa: Long,
    val category: String = "খাবার",
    val place: String = "",
    val note: String = "",
)

/** User-created categories only; the built-in lists are constants. */
@Serializable
@Entity(
    tableName = "custom_categories",
    indices = [Index("kind")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "income" or "expense" */
    val kind: String,
    val name: String,
)
