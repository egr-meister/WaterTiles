package com.egrmeister.watertiles.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object CalendarMath {
    /**
     * Cells for a month grid starting on [firstDayOfWeek]. Leading blanks are null; trailing
     * blanks pad the last week to 7 cells.
     */
    fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = ArrayList<LocalDate?>(42)
        repeat(lead) { cells += null }
        cells += days
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }

    fun isFuture(date: LocalDate, today: LocalDate): Boolean = date.isAfter(today)
}
