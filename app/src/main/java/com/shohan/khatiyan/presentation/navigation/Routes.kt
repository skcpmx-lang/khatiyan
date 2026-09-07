package com.shohan.khatiyan.presentation.navigation

import androidx.navigation.NavBackStackEntry

/** All navigation routes (string-based, zero serialization plugins). */
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val HISAB = "hisab"
    const val LEDGER = "ledger"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val SHOPS = "shops"
    const val SHOP_EDIT = "shops/edit?shopId={shopId}"
    const val SHOP_DETAIL = "shop/{shopId}"
    const val CREDIT_EDIT = "shop/{shopId}/credit?creditId={creditId}"

    const val LOANS = "loans"
    const val LOAN_EDIT = "loans/edit?loanId={loanId}"
    const val LOAN_DETAIL = "loan/{loanId}"

    const val EMIS = "emis"
    const val EMI_EDIT = "emis/edit?emiId={emiId}"
    const val EMI_DETAIL = "emi/{emiId}"

    const val PEOPLE = "people"
    const val PERSON_EDIT = "people/edit?personId={personId}"
    const val PERSON_DETAIL = "person/{personId}"
    const val DEBT_EDIT = "person/{personId}/debt?debtId={debtId}"

    const val INCOME_EDIT = "cash/income?entryId={entryId}"
    const val EXPENSE_EDIT = "cash/expense?entryId={entryId}"

    const val SEARCH = "search"

    fun shopEdit(shopId: Long? = null) = "shops/edit" + (shopId?.let { "?shopId=$it" } ?: "")
    fun shopDetail(shopId: Long) = "shop/$shopId"
    fun creditEdit(shopId: Long, creditId: Long? = null) =
        "shop/$shopId/credit" + (creditId?.let { "?creditId=$it" } ?: "")
    fun loanEdit(id: Long? = null) = "loans/edit" + (id?.let { "?loanId=$it" } ?: "")
    fun loanDetail(id: Long) = "loan/$id"
    fun emiEdit(id: Long? = null) = "emis/edit" + (id?.let { "?emiId=$it" } ?: "")
    fun emiDetail(id: Long) = "emi/$id"
    fun personEdit(id: Long? = null) = "people/edit" + (id?.let { "?personId=$it" } ?: "")
    fun personDetail(id: Long) = "person/$id"
    fun debtEdit(personId: Long, debtId: Long? = null) =
        "person/$personId/debt" + (debtId?.let { "?debtId=$it" } ?: "")
    fun incomeEdit(id: Long? = null) = "cash/income" + (id?.let { "?entryId=$it" } ?: "")
    fun expenseEdit(id: Long? = null) = "cash/expense" + (id?.let { "?entryId=$it" } ?: "")

    fun NavBackStackEntry.longArg(name: String): Long? =
        arguments?.getString(name)?.toLongOrNull()

    fun argLong(entry: NavBackStackEntry, name: String): Long? = entry.longArg(name)
}
