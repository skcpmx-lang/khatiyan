package com.shohan.khatiyan.domain.model

import com.shohan.khatiyan.data.local.query.DebtRow
import com.shohan.khatiyan.data.local.query.EmiRow
import com.shohan.khatiyan.data.local.query.LoanRow
import com.shohan.khatiyan.data.local.query.PersonRow
import com.shohan.khatiyan.data.local.query.ShopRow

/** Aggregated global-search hits (Phase 24). */
data class SearchResults(
    val query: String,
    val shops: List<ShopRow>,
    val people: List<PersonRow>,
    val loans: List<LoanRow>,
    val emis: List<EmiRow>,
    val debts: List<DebtRow>,
    val entries: List<LedgerEntry>,
) {
    val isEmpty: Boolean
        get() = shops.isEmpty() && people.isEmpty() && loans.isEmpty() &&
            emis.isEmpty() && debts.isEmpty() && entries.isEmpty()

    val totalCount: Int
        get() = shops.size + people.size + loans.size + emis.size + debts.size + entries.size
}
