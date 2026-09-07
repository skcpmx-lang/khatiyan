package com.shohan.khatiyan.data.backup

import android.content.Context
import android.net.Uri
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.data.local.KhatiyanDatabase
import com.shohan.khatiyan.domain.model.PaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Real CSV exports (Phase 27) — full ledger data, not a summary stub.
 * ASCII digits + "taka" decimal amounts so Excel/Google Sheets parse them
 * as numbers; a UTF-8 BOM keeps Bangla headers readable in Excel.
 */
object CsvExporter {

    fun escape(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun row(fields: List<String>): String = fields.joinToString(",") { escape(it) } + "\r\n"

    fun takaPlain(paisa: Long): String {
        val neg = paisa < 0
        val abs = if (paisa == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paisa)
        return (if (neg) "-" else "") + (abs / 100).toString() + "." + (abs % 100).toString().padStart(2, '0')
    }

    private fun headerRow() = row(
        listOf("তারিখ", "ধরন", "বিবরণ", "পরিমাণ", "একক", "একক মূল্য (৳)", "মোট (৳)", "নোট"),
    )

    suspend fun shopLedgerCsv(db: KhatiyanDatabase, shopId: Long): String = withContext(Dispatchers.IO) {
        val dao = db.shopDao()
        val shop = dao.getShop(shopId) ?: return@withContext ""
        val credits = dao.getCredits(shopId).sortedWith(compareBy({ it.dateIso }, { it.id }))
        val payments = dao.getPayments(shopId)
        val totalCredit = credits.fold(0L) { a, c -> a + c.totalPaisa }
        val totalPaid = payments.fold(0L) { a, p -> a + p.amountPaisa }
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(row(listOf("খতিয়ান হিসাব — দোকান", shop.name, if (shop.ownerName.isBlank()) "" else "মালিক: ${shop.ownerName}")))
        sb.append(row(listOf("ঠিকানা", shop.address, "ফোন", shop.phone)))
        sb.append(row(listOf("মোট বাকি (৳)", takaPlain(totalCredit), "মোট পরিশোধ (৳)", takaPlain(totalPaid), "বর্তমান বাকি (৳)", takaPlain(totalCredit - totalPaid))))
        sb.append("\r\n")
        sb.append(headerRow())
        for (c in credits) {
            val items = dao.getItems(c.id)
            if (items.isEmpty()) {
                sb.append(row(listOf(c.dateIso, "বাকী", c.note.ifBlank { "বাকি নেওয়া" }, "", "", "", takaPlain(c.totalPaisa), "")))
            } else {
                for (i in items) {
                    val qty = if (i.quantity == i.quantity.toLong().toDouble()) i.quantity.toLong().toString() else i.quantity.toString()
                    sb.append(
                        row(
                            listOf(
                                c.dateIso, "বাকীর পণ্য", i.name, qty, i.unit,
                                if (i.unitPricePaisa > 0) takaPlain(i.unitPricePaisa) else "",
                                takaPlain(i.totalPaisa), i.note,
                            ),
                        ),
                    )
                }
                if (c.dueDateIso != null) {
                    sb.append(row(listOf(c.dueDateIso, "পরিশোধের তারিখ", "বিবরণ: ${c.dateIso} এর বাকির রসিদ", "", "", "", takaPlain(c.totalPaisa), "সময় নির্ধারিত")))
                }
            }
        }
        for (p in payments.sortedWith(compareBy({ it.dateIso }, { it.id }))) {
            sb.append(row(listOf(p.dateIso, "পরিশোধ", PaymentMethod.fromName(p.method).labelBn, "", "", "", takaPlain(p.amountPaisa), p.note)))
        }
        sb.toString()
    }

    suspend fun transactionsCsv(db: KhatiyanDatabase, fromIso: String, toIso: String): String = withContext(Dispatchers.IO) {
        val rows = db.ledgerDao().allEntriesBetween(fromIso, toIso, "")
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(row(listOf("তারিখ", "ধরন", "শিরোনাম", "উপতথ্য", "টাকা (৳)", "নোট")))
        for (r in rows.sortedBy { it.dateIso }) {
            sb.append(row(listOf(r.dateIso, com.shohan.khatiyan.domain.model.TxnType.entries.firstOrNull { t -> t.key == r.typeKey }?.labelBn ?: r.typeKey, r.title, r.meta, takaPlain(r.amountPaisa), r.note)))
        }
        sb.toString()
    }

    suspend fun loanLedgerCsv(
        db: KhatiyanDatabase,
        loanId: Long,
    ): String = withContext(Dispatchers.IO) {
        val dao = db.loanDao()
        val loan = dao.getLoan(loanId) ?: return@withContext ""
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(row(listOf("খতিয়ান লোন হিসাব", loan.loanName, loan.institution)))
        sb.append(row(listOf("মূল (৳)", takaPlain(loan.principalPaisa), "মোট পরিশোধযোগ্য (৳)", takaPlain(loan.totalPayablePaisa))))
        sb.append("\r\n")
        sb.append(row(listOf("কিস্তি", "তারিখ", "কিস্তির টাকার অঙ্ক (৳)", "পরিশোধিত (৳)", "অবস্থা")))
        val today = BnDates.today()
        for (i in dao.getInstallments(loanId)) {
            val status = when {
                i.paidPaisa >= i.amountPaisa -> "পরিশোধিত"
                (BnDates.fromIso(i.dueIso)?.isBefore(today) ?: false) -> "ওভারডিউ"
                else -> "বাকি"
            }
            sb.append(row(listOf(i.number.toString(), i.dueIso, takaPlain(i.amountPaisa), takaPlain(i.paidPaisa), status)))
        }
        sb.append("\r\n")
        sb.append(row(listOf("তারিখ", "পরিশোধ (৳)", "মাধ্যম", "নোট")))
        for (p in dao.getPaymentsRecent(loanId).sortedBy { it.dateIso }) {
            sb.append(row(listOf(p.dateIso, takaPlain(p.amountPaisa), PaymentMethod.fromName(p.method).labelBn, p.note)))
        }
        sb.toString()
    }

    suspend fun emiLedgerCsv(db: KhatiyanDatabase, emiId: Long): String = withContext(Dispatchers.IO) {
        val dao = db.emiDao()
        val emi = dao.getEmi(emiId) ?: return@withContext ""
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(row(listOf("খতিয়ান EMI হিসাব", emi.productName, emi.seller)))
        sb.append(row(listOf("মোট মূল্য (৳)", takaPlain(emi.totalPricePaisa), "অগ্রিম (৳)", takaPlain(emi.downPaymentPaisa))))
        sb.append("\r\n")
        sb.append(row(listOf("কিস্তি", "তারিখ", "টাকার অঙ্ক (৳)", "পরিশোধিত (৳)", "অবস্থা")))
        val today = BnDates.today()
        for (i in dao.getInstallments(emiId)) {
            val status = when {
                i.paidPaisa >= i.amountPaisa -> "পরিশোধিত"
                (BnDates.fromIso(i.dueIso)?.isBefore(today) ?: false) -> "ওভারডিউ"
                else -> "বাকি"
            }
            sb.append(row(listOf(i.number.toString(), i.dueIso, takaPlain(i.amountPaisa), takaPlain(i.paidPaisa), status)))
        }
        sb.append("\r\n")
        sb.append(row(listOf("তারিখ", "পরিশোধ (৳)", "মাধ্যম", "নোট")))
        for (p in dao.getPaymentsRecent(emiId).sortedBy { it.dateIso }) {
            sb.append(row(listOf(p.dateIso, takaPlain(p.amountPaisa), PaymentMethod.fromName(p.method).labelBn, p.note)))
        }
        sb.toString()
    }

    suspend fun personalLedgerCsv(db: KhatiyanDatabase, personId: Long): String = withContext(Dispatchers.IO) {
        val dao = db.personalDao()
        val person = dao.getPerson(personId) ?: return@withContext ""
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append(row(listOf("খতিয়ান ব্যক্তিগত ধারের হিসাব", person.name, person.relationship)))
        sb.append("\r\n")
        sb.append(row(listOf("তারিখ", "ধার (৳)", "ফেরত (৳)", "অবস্থা", "নোট")))
        for (d in dao.getDebtsForPerson(personId).sortedBy { it.borrowedIso }) {
            val status = if (d.remainingPaisa <= 0L) "শোধ হয়েছে" else "বাকি ${takaPlain(d.remainingPaisa)} ৳"
            sb.append(row(listOf(d.borrowedIso, takaPlain(d.amountPaisa), takaPlain(d.repaidPaisa), status, d.note)))
        }
        sb.toString()
    }

    /** Streams text through SAF; caller already holds a persistable write URI. */
    suspend fun writeTo(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                out.write("\uFEFF".toByteArray(Charsets.UTF_8).let { bom ->
                    val body = if (text.startsWith("\uFEFF")) text.substring(1) else text
                    bom + body.toByteArray(Charsets.UTF_8)
                })
            } ?: throw IOException("write failed")
        } catch (e: IOException) {
            throw e
        }
    }
}
