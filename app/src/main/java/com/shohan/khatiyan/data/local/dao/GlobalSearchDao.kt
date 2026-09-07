package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.shohan.khatiyan.data.local.query.DebtRow
import com.shohan.khatiyan.data.local.query.EmiRow
import com.shohan.khatiyan.data.local.query.LoanRow
import com.shohan.khatiyan.data.local.query.PersonRow
import com.shohan.khatiyan.data.local.query.ShopRow

/**
 * Global search across the four books + people (Phase 24). LIKE against
 * indexed name columns; each section is capped so a keystroke stays cheap.
 */
@Dao
abstract class GlobalSearchDao {

    @Query(
        """
        SELECT s.id, s.name, s.ownerName, s.phone, s.address, s.category, s.note, s.archived,
               (SELECT IFNULL(SUM(c.totalPaisa),0) FROM shop_credits c WHERE c.shopId = s.id) AS totalCreditPaisa,
               (SELECT IFNULL(SUM(p.amountPaisa),0) FROM shop_payments p WHERE p.shopId = s.id) AS totalPaidPaisa,
               (SELECT MAX(c.dateIso) FROM shop_credits c WHERE c.shopId = s.id) AS lastCreditIso
        FROM shops s
        WHERE s.name LIKE '%' || :q || '%' OR s.ownerName LIKE '%' || :q || '%' OR s.phone LIKE '%' || :q || '%'
        ORDER BY s.name COLLATE NOCASE LIMIT 10
        """
    )
    abstract suspend fun searchShops(q: String): List<ShopRow>

    @Query(
        """
        SELECT p.id, p.name, p.relationship, p.phone, p.note, p.archived,
               (SELECT IFNULL(SUM(d.amountPaisa),0) FROM personal_debts d WHERE d.personId = p.id) AS totalBorrowedPaisa,
               (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r
                 JOIN personal_debts d2 ON d2.id = r.debtId WHERE d2.personId = p.id) AS totalRepaidPaisa,
               (SELECT COUNT(*) FROM personal_debts d WHERE d.personId = p.id) AS openDebtCount,
               0 AS overdueCount
        FROM persons p
        WHERE p.name LIKE '%' || :q || '%' OR p.phone LIKE '%' || :q || '%'
        ORDER BY p.name COLLATE NOCASE LIMIT 10
        """
    )
    abstract suspend fun searchPeople(q: String): List<PersonRow>

    @Query(
        """
        SELECT l.*, 0 AS totalPaidPaisa, 0 AS advancePaisa, 0 AS openCount, 0 AS overdueCount,
               CAST(NULL AS TEXT) AS nextDueIso
        FROM loans l
        WHERE l.loanName LIKE '%' || :q || '%' OR l.institution LIKE '%' || :q || '%'
        ORDER BY l.loanName COLLATE NOCASE LIMIT 10
        """
    )
    abstract suspend fun searchLoans(q: String): List<LoanRow>

    @Query(
        """
        SELECT e.*, 0 AS totalPaidPaisa, 0 AS advancePaisa, 0 AS openCount, 0 AS paidCount, 0 AS overdueCount,
               CAST(NULL AS TEXT) AS nextDueIso
        FROM emi_purchases e
        WHERE e.productName LIKE '%' || :q || '%' OR e.seller LIKE '%' || :q || '%'
        ORDER BY e.productName COLLATE NOCASE LIMIT 10
        """
    )
    abstract suspend fun searchEmis(q: String): List<EmiRow>

    @Query(
        """
        SELECT d.id, d.personId, p.name AS personName, d.borrowedIso, d.amountPaisa,
               d.expectedReturnIso, d.note,
               (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id) AS repaidPaisa
        FROM personal_debts d JOIN persons p ON p.id = d.personId
        WHERE p.name LIKE '%' || :q || '%' OR d.note LIKE '%' || :q || '%'
        ORDER BY d.borrowedIso DESC LIMIT 10
        """
    )
    abstract suspend fun searchDebts(q: String): List<DebtRow>
}
