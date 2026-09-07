package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.domain.model.SearchResults

/** Global search fan-out (Phase 24) — LIKE on indexed columns, capped per section. */
class SearchRepository(private val db: KhatiyanDatabase) {

    suspend fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults(q, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val shops = db.globalSearchDao().searchShops(q)
        val people = db.globalSearchDao().searchPeople(q)
        val loans = db.globalSearchDao().searchLoans(q)
        val emis = db.globalSearchDao().searchEmis(q)
        val debts = db.globalSearchDao().searchDebts(q)
        val entries = LedgerMapper.toEntries(db.ledgerDao().searchEntries(q))
        return SearchResults(q, shops, people, loans, emis, debts, entries)
    }
}
