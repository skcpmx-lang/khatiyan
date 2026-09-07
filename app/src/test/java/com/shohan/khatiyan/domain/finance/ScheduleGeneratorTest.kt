package com.shohan.khatiyan.domain.finance

import com.shohan.khatiyan.domain.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleGeneratorTest {

    private val first = LocalDate.of(2024, 1, 10)

    @Test
    fun monthlySplitSumsExactlyToTotal() {
        val plan = ScheduleGenerator.generate(first, Frequency.MONTHLY, 6, 100_000L)
        assertEquals(6, plan.size)
        assertEquals(100_000L, plan.sumOf { it.amountPaisa })
        // 5 equal parts then the remainder lands on the last installment
        assertEquals(16_667L, plan[0].amountPaisa)
        assertEquals(16_665L, plan.last().amountPaisa)
        assertEquals(LocalDate.of(2024, 6, 10), plan.last().dueDate)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), plan.map { it.number })
    }

    @Test
    fun fixedInstallmentTrimsCountWhenItOvershoots() {
        val plan = ScheduleGenerator.generate(first, Frequency.MONTHLY, 10, 100_000L, fixedInstallmentPaisa = 30_000L)
        assertEquals(4, plan.size)
        assertEquals(100_000L, plan.sumOf { it.amountPaisa })
        assertEquals(10_000L, plan.last().amountPaisa)
    }

    @Test
    fun weeklyStepsWeekByWeek() {
        val plan = ScheduleGenerator.generate(first, Frequency.WEEKLY, 3, 30_000L)
        assertEquals(listOf(first, first.plusWeeks(1), first.plusWeeks(2)), plan.map { it.dueDate })
        assertEquals(30_000L, plan.sumOf { it.amountPaisa })
    }

    @Test
    fun zeroOrNegativeTotalProducesNoSchedule() {
        assertTrue(ScheduleGenerator.generate(first, Frequency.MONTHLY, 5, 0L).isEmpty())
        assertTrue(ScheduleGenerator.generate(first, Frequency.MONTHLY, 0, 100L).isEmpty())
    }

    @Test
    fun countBetween_inclusiveBothEnds() {
        assertEquals(6, ScheduleGenerator.countBetween(first, LocalDate.of(2024, 6, 10), Frequency.MONTHLY))
        assertEquals(1, ScheduleGenerator.countBetween(first, LocalDate.of(2023, 1, 1), Frequency.MONTHLY))
        assertEquals(3, ScheduleGenerator.countBetween(first, first.plusWeeks(2), Frequency.WEEKLY))
    }

    @Test
    fun monthEndClampingKeepsMonthlyCadenceSane() {
        // 31 Jan + 1 month = 29 Feb 2024 (leap), then 31 Mar? No: LocalDate clamps to month length.
        val plan = ScheduleGenerator.generate(LocalDate.of(2024, 1, 31), Frequency.MONTHLY, 3, 300L)
        assertEquals(LocalDate.of(2024, 2, 29), plan[1].dueDate)
        assertEquals(LocalDate.of(2024, 3, 29), plan[2].dueDate)
    }
}
