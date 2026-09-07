package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shohan.khatiyan.data.local.entity.PersonEntity
import com.shohan.khatiyan.data.local.entity.PersonalDebtEntity
import com.shohan.khatiyan.data.local.entity.PersonalRepaymentEntity
import com.shohan.khatiyan.data.local.query.DebtRow
import com.shohan.khatiyan.data.local.query.PersonRow
import kotlinx.coroutines.flow.Flow

/** Personal borrowing book (Phase 13). Repayment overpayment is validated in the
 *  repository against the live remaining balance (Phase 18). */
@Dao
abstract class PersonalDao {

    @Query(
        """
        SELECT p.id, p.name, p.relationship, p.phone, p.note, p.archived,
               (SELECT IFNULL(SUM(d.amountPaisa), 0) FROM personal_debts d WHERE d.personId = p.id) AS totalBorrowedPaisa,
               (SELECT IFNULL(SUM(r.amountPaisa), 0) FROM personal_repayments r
                 JOIN personal_debts d2 ON d2.id = r.debtId WHERE d2.personId = p.id) AS totalRepaidPaisa,
               (SELECT COUNT(*) FROM personal_debts d WHERE d.personId = p.id
                 AND d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)) AS openDebtCount,
               (SELECT COUNT(*) FROM personal_debts d WHERE d.personId = p.id
                 AND d.expectedReturnIso IS NOT NULL AND d.expectedReturnIso < :todayIso
                 AND d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)) AS overdueCount
        FROM persons p
        WHERE (:hideArchived = 0 OR p.archived = 0)
          AND (:q = '' OR p.name LIKE '%' || :q || '%' OR p.relationship LIKE '%' || :q || '%')
        ORDER BY p.archived ASC, p.name COLLATE NOCASE ASC, p.id DESC
        """
    )
    abstract fun observePeople(q: String, todayIso: String, hideArchived: Boolean): Flow<List<PersonRow>>

    @Query("SELECT * FROM persons WHERE id = :id")
    abstract suspend fun getPerson(id: Long): PersonEntity?

    @Insert
    abstract suspend fun insertPerson(person: PersonEntity): Long

    @Update
    abstract suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE persons SET archived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM persons WHERE id = :id")
    abstract suspend fun deletePersonById(id: Long)

    @Query(
        """
        SELECT d.id, d.personId, p.name AS personName, d.borrowedIso, d.amountPaisa,
               d.expectedReturnIso, d.note,
               (SELECT IFNULL(SUM(r.amountPaisa), 0) FROM personal_repayments r WHERE r.debtId = d.id) AS repaidPaisa
        FROM personal_debts d JOIN persons p ON p.id = d.personId
        WHERE d.personId = :personId
        ORDER BY d.borrowedIso DESC, d.id DESC
        """
    )
    abstract suspend fun getDebtsForPerson(personId: Long): List<DebtRow>

    @Query("SELECT * FROM personal_debts WHERE id = :id")
    abstract suspend fun getDebt(id: Long): PersonalDebtEntity?

    @Insert
    abstract suspend fun insertDebt(debt: PersonalDebtEntity): Long

    @Update
    abstract suspend fun updateDebt(debt: PersonalDebtEntity)

    @Query("DELETE FROM personal_debts WHERE id = :id")
    abstract suspend fun deleteDebt(id: Long)

    @Query(
        """
        SELECT d.* FROM personal_debts d
        WHERE d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)
        """
    )
    abstract suspend fun getOpenDebts(): List<PersonalDebtEntity>

    @Query(
        """
        SELECT r.* FROM personal_repayments r
        JOIN personal_debts d ON d.id = r.debtId
        ORDER BY r.dateIso DESC, r.id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun getAllRepaymentsRecent(limit: Int): List<PersonalRepaymentEntity>

    @Query("SELECT * FROM personal_repayments WHERE debtId = :debtId ORDER BY dateIso DESC, id DESC")
    abstract suspend fun getRepayments(debtId: Long): List<PersonalRepaymentEntity>

    @Insert
    abstract suspend fun insertRepayment(repayment: PersonalRepaymentEntity): Long

    @Update
    abstract suspend fun updateRepayment(repayment: PersonalRepaymentEntity)

    @Query("DELETE FROM personal_repayments WHERE id = :id")
    abstract suspend fun deleteRepayment(id: Long)

    /** Record repayment + persist its (post-policy) excess atomically. */
    @Transaction
    open suspend fun recordRepayment(repayment: PersonalRepaymentEntity): Long {
        val id = insertRepayment(repayment)
        return id
    }

    @Query(
        """
        SELECT p.id, p.name, p.relationship, p.phone, p.note, p.archived,
               (SELECT IFNULL(SUM(d.amountPaisa), 0) FROM personal_debts d WHERE d.personId = p.id) AS totalBorrowedPaisa,
               (SELECT IFNULL(SUM(r.amountPaisa), 0) FROM personal_repayments r
                 JOIN personal_debts d2 ON d2.id = r.debtId WHERE d2.personId = p.id) AS totalRepaidPaisa,
               (SELECT COUNT(*) FROM personal_debts d WHERE d.personId = p.id
                 AND d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)) AS openDebtCount,
               (SELECT COUNT(*) FROM personal_debts d WHERE d.personId = p.id
                 AND d.expectedReturnIso IS NOT NULL AND d.expectedReturnIso < :todayIso
                 AND d.amountPaisa > (SELECT IFNULL(SUM(r.amountPaisa),0) FROM personal_repayments r WHERE r.debtId = d.id)) AS overdueCount
        FROM persons p ORDER BY p.name COLLATE NOCASE
        """
    )
    abstract suspend fun getPeopleRows(todayIso: String): List<PersonRow>

    @Query(
        """
        SELECT d.id, d.personId, p.name AS personName, d.borrowedIso, d.amountPaisa,
               d.expectedReturnIso, d.note,
               (SELECT IFNULL(SUM(r.amountPaisa), 0) FROM personal_repayments r WHERE r.debtId = d.id) AS repaidPaisa
        FROM personal_debts d JOIN persons p ON p.id = d.personId
        ORDER BY d.borrowedIso DESC, d.id DESC
        """
    )
    abstract suspend fun getAllDebtsWithRemaining(): List<DebtRow>

    @Query("SELECT id, name FROM persons")
    abstract suspend fun getAllPersonRefs(): List<com.shohan.khatiyan.data.local.query.ShopRefRow>

    // ---- backup ------------------------------------------------------------------------

    @Query("SELECT * FROM persons")
    abstract suspend fun getAllPeople(): List<PersonEntity>

    @Query("SELECT * FROM personal_debts")
    abstract suspend fun getAllDebts(): List<PersonalDebtEntity>

    @Query("SELECT * FROM personal_repayments")
    abstract suspend fun getAllRepayments(): List<PersonalRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restorePeople(rows: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreDebts(rows: List<PersonalDebtEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreRepayments(rows: List<PersonalRepaymentEntity>)
}
