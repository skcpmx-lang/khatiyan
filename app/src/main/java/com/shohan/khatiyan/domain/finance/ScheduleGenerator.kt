package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.core.Money
import com.shohan.khatiyan.domain.model.Frequency
import com.shohan.khatiyan.domain.model.InstallmentPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Deterministic installment schedule generator shared by Loan and EMI
 * (Phase 11/12/17). Guarantees: sum of installments == totalPaisa exactly.
 *
 * Rules:
 *  - count <= 0 or total <= 0 → empty schedule
 *  - if [fixedInstallmentPaisa] is given: base = fixed; the LAST installment
 *    absorbs the difference (more if base*n < total; less if base*n > total;
 *    trailing zero/negative installments are dropped)
 *  - otherwise base = total / n rounded HALF_UP, last absorbs remainder
 */
object ScheduleGenerator {

    fun step(dueDate: LocalDate, frequency: Frequency): LocalDate = when (frequency) {
        Frequency.WEEKLY -> dueDate.plusWeeks(1)
        Frequency.MONTHLY -> dueDate.plusMonths(1)
    }

    /** Number of installments implied by first due + maturity dates (inclusive). */
    fun countBetween(firstDue: LocalDate, maturity: LocalDate, frequency: Frequency): Int {
        if (maturity < firstDue) return 1
        val raw = when (frequency) {
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(firstDue, maturity).toInt() + 1
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(firstDue, maturity).toInt() + 1
        }
        return raw.coerceAtLeast(1)
    }

    fun generate(
        firstDue: LocalDate,
        frequency: Frequency,
        count: Int,
        totalPaisa: Long,
        fixedInstallmentPaisa: Long? = null,
    ): List<InstallmentPlan> {
        if (totalPaisa <= 0L || count <= 0) return emptyList()

        var n = count
        val base: Long = if (fixedInstallmentPaisa != null && fixedInstallmentPaisa > 0L) {
            fixedInstallmentPaisa
        } else {
            Money.divRound(totalPaisa, n)
        }
        if (base <= 0L) {
            return listOf(InstallmentPlan(1, firstDue, totalPaisa))
        }

        // With a fixed amount, the nominal count can exceed the debt; trim it.
        if (base > 0L) {
            val maxN = ((totalPaisa + base - 1) / base).toInt() // ceil(total/base)
            if (maxN in 1 until n) n = maxN
        }

        val plan = ArrayList<InstallmentPlan>(n)
        var due = firstDue
        var sumBeforeLast = 0L
        for (i in 1..n) {
            if (i == n) break
            plan += InstallmentPlan(i, due, base)
            sumBeforeLast += base
            due = step(due, frequency)
        }
        val last = totalPaisa - sumBeforeLast
        if (last > 0L) {
            plan += InstallmentPlan(n, due, last)
        } else if (last == 0L && plan.isEmpty()) {
            plan += InstallmentPlan(1, due, totalPaisa)
        } // last <= 0 means previous installments already covered it → dropped

        // Final consistency fix — must always equal the total exactly.
        var sum = 0L
        plan.forEach { sum += it.amountPaisa }
        if (sum != totalPaisa && plan.isNotEmpty()) {
            val idx = plan.lastIndex
            val fixed = plan[idx].copy(amountPaisa = plan[idx].amountPaisa + (totalPaisa - sum))
            if (fixed.amountPaisa > 0) {
                plan[idx] = fixed
            }
        }
        return plan
    }
}
