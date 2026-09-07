package com.shohan.khatiyan.data.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.shohan.khatiyan.core.BnDates
import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.domain.model.ObligationKind
import com.shohan.khatiyan.domain.model.ReportSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Local PDF report (Phase 28) via android.graphics.pdf.PdfDocument — no cloud,
 * no external SDK. A4 points; brand header; summary; debt distribution;
 * upcoming obligations; payment history; page footer with Khatiyan branding.
 */
class PdfExporter(private val context: Context) {

    private val pageW = 595
    private val pageH = 842
    private val margin = 44f

    @Throws(IOException::class)
    suspend fun exportReport(uri: Uri, snapshot: ReportSnapshot, userName: String) =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            var pageIndex = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas
            var y = 0f

            val text = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(0x19, 0x1D, 0x1A)
                textSize = 10f
            }
            val textBold = Paint(text).apply {
                isFakeBoldText = true
            }
            val small = Paint(text).apply {
                textSize = 8f
                color = Color.rgb(0x6B, 0x71, 0x6C)
            }
            val hairline = Paint().apply {
                color = Color.rgb(0xD8, 0xD5, 0xCA)
                strokeWidth = 0.6f
            }

            fun footer() {
                val fp = Paint(small)
                canvas.drawLine(margin, pageH - 34f, pageW - margin, pageH - 34f, hairline)
                canvas.drawText("খতিয়ান — সব হিসাব, এক জায়গায়।", margin, pageH - 22f, fp)
                val pageLabel = "পৃষ্ঠা ${BnDates.bn(pageIndex.toLong())}"
                val w = fp.measureText(pageLabel)
                canvas.drawText(pageLabel, pageW - margin - w, pageH - 22f, fp)
            }

            fun newPageIfNeeded(needed: Float) {
                if (y + needed > pageH - 48f) {
                    footer()
                    doc.finishPage(page)
                    pageIndex++
                    pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + 10f
                }
            }

            fun section(title: String) {
                newPageIfNeeded(30f)
                y += 14f
                canvas.drawText(title, margin, y, Paint(textBold).apply { textSize = 13f; color = Color.rgb(0x0B, 0x5C, 0x43) })
                y += 6f
                canvas.drawLine(margin, y, pageW - margin, y, hairline)
                y += 10f
            }

            fun keyValue(label: String, value: String) {
                newPageIfNeeded(16f)
                canvas.drawText(label, margin, y, text)
                val wp = text.measureText(value)
                canvas.drawText(value, pageW - margin - wp, y, textBold)
                y += 15f
            }

            fun wrapped(prefix: String, body: String, amount: String) {
                val avail = pageW - margin * 2 - text.measureText(amount) - 8f
                val combined = if (prefix.isEmpty()) body else "$prefix — $body"
                val words = combined.split(" ")
                val line = StringBuilder()
                var first = true
                for (word in words) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (text.measureText(candidate) > avail && line.isNotEmpty()) {
                        newPageIfNeeded(15f)
                        if (first) {
                            val wp2 = text.measureText(amount)
                            canvas.drawText(line.toString(), margin, y, text)
                            canvas.drawText(amount, pageW - margin - wp2, y, textBold)
                            y += 14f
                        } else {
                            canvas.drawText(line.toString(), margin, y, text)
                            y += 14f
                        }
                        line.clear()
                        first = false
                    }
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                }
                if (line.isNotEmpty()) {
                    newPageIfNeeded(15f)
                    val wp2 = text.measureText(amount)
                    canvas.drawText(line.toString(), margin, y, text)
                    canvas.drawText(amount, pageW - margin - wp2, y, textBold)
                    y += 14f
                }
            }

            // ---- header band --------------------------------------------------------------
            canvas.drawColor(Color.WHITE)
            val headerPaint = Paint().apply { color = Color.rgb(0x08, 0x4C, 0x38) }
            canvas.drawRect(0f, 0f, pageW.toFloat(), 92f, headerPaint)
            canvas.drawText("খতিয়ান", margin, 38f, Paint(textBold).apply {
                color = Color.WHITE; textSize = 24f
            })
            canvas.drawText("সব হিসাব, এক জায়গায়।", margin, 58f, Paint(small).apply {
                color = Color.rgb(0xCF, 0xE8, 0xD9); textSize = 10f
            })
            val who = if (userName.isBlank()) "" else "ব্যবহারকারী: $userName"
            canvas.drawText(who, margin, 78f, Paint(small).apply { color = Color.rgb(0xD9, 0xE9, 0xDF) })
            val created = "তৈরি: ${BnDates.formatLong(BnDates.today())}"
            val cw = small.measureText(created)
            canvas.drawText(created, pageW - margin - cw, 78f, Paint(small).apply { color = Color.rgb(0xD9, 0xE9, 0xDF) })
            y = 120f

            // ---- period + summary ---------------------------------------------------------
            section("রিপোর্টের সময়কাল")
            val fromD = BnDates.fromIso(snapshot.fromIso) ?: BnDates.today()
            val toD = BnDates.fromIso(snapshot.toIso) ?: BnDates.today()
            keyValue("সময়কাল", "${BnDates.formatLong(fromD)}  থেকে  ${BnDates.formatLong(toD)}")

            section("আর্থিক সারসংক্ষেপ")
            keyValue("মোট আয়", Money.format(snapshot.incomePaisa, snapshot.symbol))
            keyValue("মোট ব্যয়", Money.format(snapshot.expensePaisa, snapshot.symbol))
            val net = snapshot.netPaisa
            val netPaint = Paint(textBold)
            netPaint.color = if (net >= 0) Color.rgb(0x1B, 0x7F, 0x3B) else Color.rgb(0xB3, 0x26, 0x1E)
            run {
                newPageIfNeeded(16f)
                canvas.drawText("নেট ক্যাশ ফ্লো", margin, y, text)
                val v = Money.format(net, snapshot.symbol)
                canvas.drawText(v, pageW - margin - netPaint.measureText(v), y, netPaint)
                y += 15f
            }
            keyValue("পরিশোধ (সব বকেয়া)", Money.format(snapshot.repaidPaisa, snapshot.symbol))
            keyValue("নতুন দায় (বাকি/ধার)", Money.format(snapshot.newObligationPaisa, snapshot.symbol))
            keyValue("মোট বর্তমান বকেয়া", Money.format(snapshot.totalDebtPaisa, snapshot.symbol))

            if (snapshot.debts.isNotEmpty()) {
                section("বকেয়ার বণ্টন")
                for (d in snapshot.debts.take(10)) {
                    val kind = d.kind.labelBn
                    wrapped("$kind — ${d.name}", "", Money.format(d.outstandingPaisa, snapshot.symbol))
                }
            }

            if (snapshot.upcoming.isNotEmpty()) {
                section("আসন্ন ও বকেয়া পেমেন্ট")
                for (u in snapshot.upcoming.take(12)) {
                    val status = when (u.status) {
                        com.shohan.khatiyan.domain.model.DueStatus.OVERDUE -> "ওভারডিউ"
                        com.shohan.khatiyan.domain.model.DueStatus.DUE_TODAY -> "আজ"
                        com.shohan.khatiyan.domain.model.DueStatus.UPCOMING -> BnDates.formatShort(u.dueDate)
                    }
                    wrapped(status, "${u.entityLabel} (${u.detailLabel})", Money.format(u.amountPaisa, snapshot.symbol))
                }
            }

            if (snapshot.entries.isNotEmpty()) {
                section("লেনদেনের ইতিহাস")
                for (e in snapshot.entries.take(40)) {
                    val type = com.shohan.khatiyan.domain.model.TxnType.entries
                        .firstOrNull { it.key == e.typeKey }?.labelBn ?: ""
                    val sign = if (type.contains("পরিশোধ") || type == "আয়") "+ " else "− "
                    wrapped("${e.dateIso} · $type", e.title, sign + Money.format(e.amountPaisa, ""))
                }
            }

            section("নোট")
            canvas.drawText(
                "এই রিপোর্ট খতিয়ান অ্যাপ থেকে আপনার ডিভাইসেই তৈরি হয়েছে। কোনো তথ্য কোথাও পাঠানো হয়নি।",
                margin, y, Paint(small).apply { textSize = 9f },
            )
            footer()
            doc.finishPage(page)

            context.contentResolver.openOutputStream(uri, "w").use { out ->
                if (out == null) throw IOException("cannot open output")
                doc.writeTo(out)
            }
            doc.close()
        }
}
