package com.shohan.khatiyan.data.repository

import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.data.local.entity.CategoryEntity
import com.shohan.khatiyan.data.local.entity.ExpenseEntity
import com.shohan.khatiyan.data.local.entity.IncomeEntity
import com.shohan.khatiyan.utilities.DataBus
import com.shohan.khatiyan.utilities.FinanceValidationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Income/expense book (Phases 14–15) with user-defined categories. */
class CashflowRepository(private val db: KhatiyanDatabase) {

    private val dao get() = db.cashflowDao()

    enum class Sort { NEWEST, OLDEST, HIGHEST, LOWEST }

    companion object {
        val INCOME_CATEGORIES = listOf("বেতন", "ব্যবসা", "ফ্রিল্যান্স", "বোনাস", "উপহার", "রিফান্ড", "অন্যান্য")
        val EXPENSE_CATEGORIES = listOf("খাবার", "বাজার", "যাতায়াত", "বাসা", "চিকিৎসা", "শিক্ষা", "বিল", "কেনাকাটা", "বিনোদন", "অন্যান্য")
    }

    // ---- income ---------------------------------------------------------------------------

    fun observeIncomes(q: String, category: String, fromIso: String, toIso: String, sort: Sort): Flow<List<IncomeEntity>> =
        when (sort) {
            Sort.NEWEST -> dao.observeIncomes(q, category, fromIso, toIso)
            Sort.OLDEST -> dao.observeIncomesOldest(q, category, fromIso, toIso)
            Sort.HIGHEST -> dao.observeIncomesHighest(q, category, fromIso, toIso)
            Sort.LOWEST -> dao.observeIncomesLowest(q, category, fromIso, toIso)
        }

    suspend fun saveIncome(row: IncomeEntity) {
        if (row.amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        dao.upsertIncome(row.copy(source = row.source.trim(), note = row.note.trim()))
        DataBus.poke()
    }

    suspend fun getIncome(id: Long): IncomeEntity? = dao.getIncome(id)

    suspend fun deleteIncome(id: Long) {
        dao.getIncome(id)?.let { dao.deleteIncome(it) }
        DataBus.poke()
    }

    // ---- expense --------------------------------------------------------------------------

    fun observeExpenses(q: String, category: String, fromIso: String, toIso: String, sort: Sort): Flow<List<ExpenseEntity>> =
        when (sort) {
            Sort.NEWEST -> dao.observeExpenses(q, category, fromIso, toIso)
            Sort.OLDEST -> dao.observeExpensesOldest(q, category, fromIso, toIso)
            Sort.HIGHEST -> dao.observeExpensesHighest(q, category, fromIso, toIso)
            Sort.LOWEST -> dao.observeExpensesLowest(q, category, fromIso, toIso)
        }

    suspend fun saveExpense(row: ExpenseEntity) {
        if (row.amountPaisa <= 0L) throw FinanceValidationException("টাকার পরিমাণ লিখুন।")
        dao.upsertExpense(row.copy(place = row.place.trim(), note = row.note.trim()))
        DataBus.poke()
    }

    suspend fun getExpense(id: Long): ExpenseEntity? = dao.getExpense(id)

    suspend fun deleteExpense(id: Long) {
        dao.getExpense(id)?.let { dao.deleteExpense(it) }
        DataBus.poke()
    }

    // ---- categories -----------------------------------------------------------------------

    suspend fun categoriesFor(kind: String): List<String> {
        val defaults = if (kind == "income") INCOME_CATEGORIES else EXPENSE_CATEGORIES
        val custom = dao.observeCategories(kind).first().map { it.name }
        return (defaults + custom).distinct()
    }

    fun observeCustomCategories(kind: String): Flow<List<CategoryEntity>> = dao.observeCategories(kind)

    suspend fun addCategory(kind: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) throw FinanceValidationException("ক্যাটাগরির নাম লিখুন।")
        val defaults = if (kind == "income") INCOME_CATEGORIES else EXPENSE_CATEGORIES
        if (clean in defaults) return
        dao.upsertCategory(CategoryEntity(kind = kind, name = clean))
        DataBus.poke()
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        dao.deleteCategory(category)
        DataBus.poke()
    }
}
