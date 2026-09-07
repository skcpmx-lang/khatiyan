package com.shohan.khatiyan.domain.finance

/**
 * Payment allocation math (pure, deterministic, tested in Phase 37/38).
 *
 * - SCHEDULED obligations (loan/EMI) allocate a payment FIFO across
 *   installments; any surplus stays as "excess" (advance) on the obligation.
 * - UNSCHEDULED balances (shop credits, personal debts) use the same FIFO
 *   allocator so "per-entry outstanding" (for due dates) is consistent.
 */
object Allocation {

    data class InstallmentState(
        val id: Long,
        val amountPaisa: Long,
        val paidPaisa: Long,
    ) {
        val openPaisa: Long get() = (amountPaisa - paidPaisa).coerceAtLeast(0L)
    }

    data class InstallmentPatch(val installmentId: Long, val addPaisa: Long)

    data class AllocationResult(
        val patches: List<InstallmentPatch>,
        /** Payment amount left over after all installments are covered (advance). */
        val excessPaisa: Long,
    )

    /** FIFO allocation over installment rows ordered by (dueDate, number). */
    fun overInstallments(
        installments: List<InstallmentState>,
        paymentPaisa: Long,
    ): AllocationResult {
        var left = paymentPaisa
        val patches = ArrayList<InstallmentPatch>()
        if (paymentPaisa <= 0L) return AllocationResult(patches, 0L)
        for (inst in installments) {
            if (left <= 0L) break
            val need = inst.openPaisa
            if (need <= 0L) continue
            val give = if (need <= left) need else left
            patches += InstallmentPatch(inst.id, give)
            left -= give
        }
        return AllocationResult(patches, left.coerceAtLeast(0L))
    }

    data class Bucket(val id: Long, val totalPaisa: Long, val paidBeforePaisa: Long) {
        val openPaisa: Long get() = (totalPaisa - paidBeforePaisa).coerceAtLeast(0L)
    }

    /**
     * Spread [paymentsPaisa] over [buckets] in list order (earliest first) and
     * return the TOTAL paid per bucket (before + allocated). Used for
     * shop-credit and personal-debt outstanding-per-entry calculation.
     */
    fun fifo(buckets: List<Bucket>, paymentsPaisa: Long): Map<Long, Long> {
        var left = paymentsPaisa.coerceAtLeast(0L)
        val out = LinkedHashMap<Long, Long>()
        for (b in buckets) {
            val need = b.openPaisa
            val give = if (need <= left) need else left
            out[b.id] = b.paidBeforePaisa + give
            left -= give
        }
        return out
    }
}
