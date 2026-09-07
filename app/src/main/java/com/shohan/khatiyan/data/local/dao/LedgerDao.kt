package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.shohan.khatiyan.data.local.LedgerRowView

/**
 * Queries the central ledger view: paged history, report windows, global
 * search (Phase 24). Fast because all queries hit indexed source tables
 * through the view; LIMIT/OFFSET keeps lists bounded (Phase 34).
 */
@Dao
abstract class LedgerDao {

    @Query(
        """
        SELECT * FROM ledger_view
        WHERE dateIso >= :fromIso AND dateIso <= :toIso
          AND (:typeKey = '' OR typeKey = :typeKey)
        ORDER BY dateIso DESC, entryKey DESC
        LIMIT :limit OFFSET :offset
        """
    )
    abstract suspend fun entriesBetween(
        fromIso: String,
        toIso: String,
        typeKey: String,
        limit: Int,
        offset: Int,
    ): List<LedgerRowView>

    @Query(
        """
        SELECT * FROM ledger_view
        WHERE dateIso >= :fromIso AND dateIso <= :toIso
          AND (:typeKey = '' OR typeKey = :typeKey)
        ORDER BY dateIso DESC, entryKey DESC
        """
    )
    abstract suspend fun allEntriesBetween(fromIso: String, toIso: String, typeKey: String): List<LedgerRowView>

    @Query(
        """
        SELECT * FROM ledger_view
        WHERE title LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%'
        ORDER BY dateIso DESC LIMIT 40
        """
    )
    abstract suspend fun searchEntries(q: String): List<LedgerRowView>

    @Query("SELECT * FROM ledger_view WHERE entryKey = :key")
    abstract suspend fun entryByKey(key: String): LedgerRowView?
}
