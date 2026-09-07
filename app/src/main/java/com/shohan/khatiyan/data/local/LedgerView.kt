package com.shohan.khatiyan.data.local

import androidx.room.DatabaseView

/**
 * Central transaction engine (Phase 16) implemented as a SQL view over the
 * source-of-truth tables: every financial movement appears here with a unique
 * key, date, amount, type and related context. Because it is a VIEW it can
 * never drift out of sync with the underlying ledgers.
 *
 * meta carries the payment method name for payment rows ("" otherwise) and is
 * mapped to a Bangla label in the repository layer.
 */
@DatabaseView(
    value = """
    SELECT 'sc_' || c.id AS entryKey, 'shop_credit' AS typeKey, c.dateIso AS dateIso,
           c.totalPaisa AS amountPaisa, s.name AS title, '' AS meta, c.note AS note
    FROM shop_credits c JOIN shops s ON s.id = c.shopId
    UNION ALL
    SELECT 'sp_' || p.id, 'shop_payment', p.dateIso, p.amountPaisa, s.name, p.method, p.note
    FROM shop_payments p JOIN shops s ON s.id = p.shopId
    UNION ALL
    SELECT 'lp_' || p.id, 'loan_payment', p.dateIso, p.amountPaisa,
           l.loanName || ' · ' || l.institution, p.method, p.note
    FROM loan_payments p JOIN loans l ON l.id = p.loanId
    UNION ALL
    SELECT 'ep_' || p.id, 'emi_payment', p.dateIso, p.amountPaisa,
           e.productName, p.method, p.note
    FROM emi_payments p JOIN emi_purchases e ON e.id = p.emiId
    UNION ALL
    SELECT 'pb_' || d.id, 'personal_borrow', d.borrowedIso, d.amountPaisa,
           pr.name, '', d.note
    FROM personal_debts d JOIN persons pr ON pr.id = d.personId
    UNION ALL
    SELECT 'pr_' || r.id, 'personal_repay', r.dateIso, r.amountPaisa,
           pr.name, r.method, r.note
    FROM personal_repayments r
    JOIN personal_debts d ON d.id = r.debtId
    JOIN persons pr ON pr.id = d.personId
    UNION ALL
    SELECT 'in_' || i.id, 'income', i.dateIso, i.amountPaisa,
           CASE WHEN i.source = '' THEN i.category ELSE i.source END, i.category, i.note
    FROM incomes i
    UNION ALL
    SELECT 'ex_' || x.id, 'expense', x.dateIso, x.amountPaisa, x.category, x.place, x.note
    FROM expenses x
    """,
    viewName = "ledger_view",
)
data class LedgerRowView(
    val entryKey: String,
    val typeKey: String,
    val dateIso: String,
    val amountPaisa: Long,
    val title: String,
    val meta: String,
    val note: String,
)
