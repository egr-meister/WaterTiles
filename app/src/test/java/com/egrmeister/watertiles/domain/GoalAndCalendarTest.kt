package com.egrmeister.watertiles.domain

import com.egrmeister.watertiles.domain.calendar.CalendarMath
import com.egrmeister.watertiles.domain.goals.GoalApply
import com.egrmeister.watertiles.domain.goals.GoalChange
import com.egrmeister.watertiles.domain.goals.GoalResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class GoalAndCalendarTest {
    private val d = { s: String -> LocalDate.parse(s) }

    @Test fun historicalGoalLookupUsesEffectiveDate() {
        val changes = listOf(GoalChange(d("2026-01-01"), 1500), GoalChange(d("2026-02-10"), 2000))
        assertNull(GoalResolver.goalFor(d("2025-12-31"), changes))
        assertEquals(1500, GoalResolver.goalFor(d("2026-01-01"), changes))
        assertEquals(1500, GoalResolver.goalFor(d("2026-02-09"), changes))
        assertEquals(2000, GoalResolver.goalFor(d("2026-02-10"), changes))
        assertEquals(2000, GoalResolver.goalFor(d("2027-01-01"), changes))
    }

    @Test fun applyingGoalNeverRewritesEarlierDates() {
        val changes = listOf(GoalChange(d("2026-01-01"), 1500))
        val today = d("2026-03-01")
        val updated = GoalResolver.apply(changes, GoalChange(GoalResolver.effectiveDateFor(GoalApply.TOMORROW, today), 2500))
        assertEquals(1500, GoalResolver.goalFor(today, updated))
        assertEquals(2500, GoalResolver.goalFor(today.plusDays(1), updated))
        assertEquals(1500, GoalResolver.goalFor(d("2026-02-01"), updated))
        val sameDay = GoalResolver.apply(updated, GoalChange(today, 1800))
        assertEquals(1800, GoalResolver.goalFor(today, sameDay))
        assertEquals(1500, GoalResolver.goalFor(today.minusDays(1), sameDay))
        assertEquals(1800, GoalResolver.goalFor(today.plusDays(1), sameDay)) // pending change superseded
    }

    @Test fun leapDayMonthGrid() {
        val cells = CalendarMath.monthCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        val days = cells.filterNotNull()
        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2028, 2, 29), days.last())
        assertEquals(0, cells.size % 7)
        // 2028-02-01 is a Tuesday -> one leading blank on a Monday-first grid.
        assertNull(cells[0])
        assertEquals(LocalDate.of(2028, 2, 1), cells[1])
    }

    @Test fun nonLeapFebruaryAndSundayStart() {
        val cells = CalendarMath.monthCells(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(28, cells.filterNotNull().size)
        // 2026-02-01 is a Sunday -> no leading blanks; exactly four weeks.
        assertEquals(LocalDate.of(2026, 2, 1), cells[0])
        assertEquals(28, cells.size)
    }

    @Test fun monthBoundariesAndFuture() {
        val cells = CalendarMath.monthCells(YearMonth.of(2026, 12), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 12, 31), cells.filterNotNull().last())
        val today = LocalDate.of(2026, 12, 31)
        assertFalse(CalendarMath.isFuture(today, today))
        assertTrue(CalendarMath.isFuture(LocalDate.of(2027, 1, 1), today))
    }
}
