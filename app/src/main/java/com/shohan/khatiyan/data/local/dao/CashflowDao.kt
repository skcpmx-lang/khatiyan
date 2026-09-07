package com.shohan.khatiyan.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shohan.khatiyan.data.local.entity.CategoryEntity
import com.shohan.khatiyan.data.local.entity.ExpenseEntity
import com.shohan.khatiyan.data.local.entity.IncomeEntity
import com.shohan.khatiyan.data.local.query.NameSumRow
import kotlinx.coroutines.flow.Flow

/**
 * Income & expense persistence (Phases 14–15). Sort orders are explicit query
 * variants (newest/oldest/highest/lowest) so list screens never re-sort off-thread.
 */
@Dao
abstract class CashflowDao {

    // ---- income --------------------------------------------------------------------------

    @Query("SELECT * FROM incomes WHERE (:q = '' OR source LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso DESC, id DESC")
    abstract fun observeIncomes(q: String, cat: String, fromIso: String, toIso: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE (:q = '' OR source LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso ASC, id ASC")
    abstract fun observeIncomesOldest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE (:q = '' OR source LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY amountPaisa DESC, id DESC")
    abstract fun observeIncomesHighest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE (:q = '' OR source LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY amountPaisa ASC, id ASC")
    abstract fun observeIncomesLowest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE id = :id")
    abstract suspend fun getIncome(id: Long): IncomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertIncome(row: IncomeEntity): Long

    @Delete
    abstract suspend fun deleteIncome(row: IncomeEntity)

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM incomes")
    abstract suspend fun totalIncomePaisa(): Long

    @Query("SELECT category AS name, IFNULL(SUM(amountPaisa),0) AS totalPaisa FROM incomes WHERE dateIso BETWEEN :fromIso AND :toIso GROUP BY category ORDER BY totalPaisa DESC")
    abstract suspend fun incomeByCategory(fromIso: String, toIso: String): List<NameSumRow>

    // ---- expense -------------------------------------------------------------------------

    @Query("SELECT * FROM expenses WHERE (:q = '' OR category LIKE '%' || :q || '%' OR place LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso DESC, id DESC")
    abstract fun observeExpenses(q: String, cat: String, fromIso: String, toIso: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE (:q = '' OR category LIKE '%' || :q || '%' OR place LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY dateIso ASC, id ASC")
    abstract fun observeExpensesOldest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE (:q = '' OR category LIKE '%' || :q || '%' OR place LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY amountPaisa DESC, id DESC")
    abstract fun observeExpensesHighest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE (:q = '' OR category LIKE '%' || :q || '%' OR place LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') AND (:cat = '' OR category = :cat) AND dateIso BETWEEN :fromIso AND :toIso ORDER BY amountPaisa ASC, id ASC")
    abstract fun observeExpensesLowest(q: String, cat: String, fromIso: String, toIso: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    abstract suspend fun getExpense(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertExpense(row: ExpenseEntity): Long

    @Delete
    abstract suspend fun deleteExpense(row: ExpenseEntity)

    @Query("SELECT IFNULL(SUM(amountPaisa), 0) FROM expenses")
    abstract suspend fun totalExpensePaisa(): Long

    @Query("SELECT category AS name, IFNULL(SUM(amountPaisa),0) AS totalPaisa FROM expenses WHERE dateIso BETWEEN :fromIso AND :toIso GROUP BY category ORDER BY totalPaisa DESC")
    abstract suspend fun expenseByCategory(fromIso: String, toIso: String): List<NameSumRow>

    // ---- custom categories ----------------------------------------------------------------

    @Query("SELECT * FROM custom_categories WHERE kind = :kind ORDER BY name COLLATE NOCASE")
    abstract fun observeCategories(kind: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCategory(row: CategoryEntity): Long

    @Delete
    abstract suspend fun deleteCategory(row: CategoryEntity)

    @Query("SELECT DISTINCT category FROM incomes ORDER BY category COLLATE NOCASE")
    abstract suspend fun usedIncomeCategories(): List<String>

    @Query("SELECT DISTINCT category FROM expenses ORDER BY category COLLATE NOCASE")
    abstract suspend fun usedExpenseCategories(): List<String>
}
