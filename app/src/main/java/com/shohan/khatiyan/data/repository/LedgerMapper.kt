package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.data.local.LedgerRowView
import com.shohan.khatiyan.domain.model.LedgerEntry
import com.shohan.khatiyan.domain.model.PaymentMethod
import com.shohan.khatiyan.domain.model.TxnType

object LedgerMapper {

    fun toEntry(row: LedgerRowView): LedgerEntry {
        val type = TxnType.entries.firstOrNull { it.key == row.typeKey }
        val subtitle = when (row.typeKey) {
            "shop_credit" -> "বাকী নেওয়া"
            "shop_payment", "loan_payment", "emi_payment", "personal_repay" ->
                PaymentMethod.fromName(row.meta).labelBn
            "personal_borrow" -> "ধার নেওয়া"
            "income" -> row.meta.ifBlank { "আয়" }
            "expense" -> if (row.meta.isBlank()) "ব্যয়" else row.meta
            else -> type?.labelBn.orEmpty()
        }
        return LedgerEntry(
            key = row.entryKey,
            typeKey = row.typeKey,
            dateIso = row.dateIso,
            amountPaisa = row.amountPaisa,
            title = row.title,
            subtitle = subtitle,
            note = row.note,
        )
    }

    fun toEntries(rows: List<LedgerRowView>): List<LedgerEntry> = rows.map(::toEntry)
}
