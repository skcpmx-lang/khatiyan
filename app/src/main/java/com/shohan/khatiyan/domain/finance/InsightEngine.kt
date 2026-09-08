package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.domain.model.Insight
import com.shohan.khatiyan.domain.model.InsightTone

/**
 * Rule-based local insights (Phase 23) — no AI, no network. Pure function over
 * numeric snapshots; every sentence is derived from real data and only appears
 * when the underlying condition holds.
 */
object InsightEngine {

    data class MonthTotals(
        val incomePaisa: Long = 0,
        val expensePaisa: Long = 0,
        val repaidPaisa: Long = 0,
    )

    data class Upcoming(
        val dueTodayCount: Int = 0,
        val dueTodayPaisa: Long = 0,
        val next7Count: Int = 0,
        val next7Paisa: Long = 0,
        val overdueCount: Int = 0,
        val overduePaisa: Long = 0,
    )

    data class Input(
        val symbol: String = "৳",
        val thisMonth: MonthTotals = MonthTotals(),
        val lastMonth: MonthTotals = MonthTotals(),
        val upcoming: Upcoming = Upcoming(),
        val totalDebtPaisa: Long = 0,
        val biggestDebtLabel: String? = null,
        val biggestDebtPaisa: Long = 0,
        val hasAnyData: Boolean = true,
    )

    private fun m(input: Input, paisa: Long): String = Money.format(paisa, input.symbol)

    fun build(input: Input): List<Insight> {
        val out = ArrayList<Insight>()
        val sym = input.symbol

        if (!input.hasAnyData) {
            return listOf(
                Insight(
                    id = "first-run",
                    text = "এখনও কোনো হিসাব যোগ করা হয়নি।\nনিচের + থেকে আপনার প্রথম হিসাব যোগ করুন।",
                    tone = InsightTone.INFO,
                ),
            )
        }

        if (input.upcoming.overdueCount > 0) {
            out += Insight(
                id = "overdue",
                text = "${Money.bnNumber(input.upcoming.overdueCount.toLong())}টি পেমেন্টের সময় পার হয়ে গেছে (মোট ${m(input, input.upcoming.overduePaisa)})। আজই দেখে নিন।",
                tone = InsightTone.DANGER,
            )
        }
        if (input.upcoming.dueTodayCount > 0) {
            out += Insight(
                id = "due-today",
                text = "আজ ${Money.bnNumber(input.upcoming.dueTodayCount.toLong())}টি পেমেন্ট দিতে হবে — মোট ${m(input, input.upcoming.dueTodayPaisa)}।",
                tone = InsightTone.WARN,
            )
        }
        if (input.upcoming.next7Count > 0) {
            out += Insight(
                id = "next-7",
                text = "আগামী ৭ দিনে ${Money.bnNumber(input.upcoming.next7Count.toLong())}টি পেমেন্ট রয়েছে (মোট ${m(input, input.upcoming.next7Paisa)})।",
                tone = InsightTone.INFO,
            )
        }

        val net = input.thisMonth.incomePaisa - input.thisMonth.expensePaisa
        if (input.thisMonth.incomePaisa > 0 || input.thisMonth.expensePaisa > 0) {
            if (net > 0) {
                out += Insight(
                    id = "net-positive",
                    text = "এই মাসে আপনার আয় ব্যয়ের চেয়ে ${m(input, net)} বেশি। দারুণ হচ্ছে!",
                    tone = InsightTone.GOOD,
                )
            } else if (net < 0) {
                out += Insight(
                    id = "net-negative",
                    text = "এই মাসে ব্যয় আয়ের চেয়ে ${m(input, -net)} বেশি হয়েছে। একটু সাশ্রয়ী হওয়া যেতে পারে।",
                    tone = InsightTone.WARN,
                )
            } else {
                out += Insight(
                    id = "net-zero",
                    text = "এই মাসে আয় ও ব্যয় সমান — নেট ক্যাশ ফ্লো ${sym}০।",
                    tone = InsightTone.INFO,
                )
            }
        }

        val prevExpense = input.lastMonth.expensePaisa
        val curExpense = input.thisMonth.expensePaisa
        if (prevExpense > 0 && curExpense > prevExpense) {
            out += Insight(
                id = "expense-up",
                text = "এই মাসে আপনার ব্যয় গত মাসের তুলনায় ${m(input, curExpense - prevExpense)} বেশি।",
                tone = InsightTone.WARN,
            )
        } else if (prevExpense > 0 && curExpense in 1 until prevExpense) {
            out += Insight(
                id = "expense-down",
                text = "এই মাসে ব্যয় গত মাসের চেয়ে ${m(input, prevExpense - curExpense)} কমেছে।",
                tone = InsightTone.GOOD,
            )
        }

        if (input.biggestDebtLabel != null && input.biggestDebtPaisa > 0) {
            out += Insight(
                id = "biggest-debt",
                text = "আপনার সবচেয়ে বড় বকেয়া — ${input.biggestDebtLabel} (${m(input, input.biggestDebtPaisa)})।",
                tone = InsightTone.INFO,
            )
        }

        return out.sortedBy { it.tone.severity }.take(5)
    }
}
