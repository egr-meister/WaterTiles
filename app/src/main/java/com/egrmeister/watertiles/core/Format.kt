package com.egrmeister.watertiles.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The interface is English; numbers and dates use US English formatting throughout. */
object Format {
    private val locale: Locale = Locale.US
    private val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val time = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun ml(value: Long): String = String.format(locale, "%,d mL", value)
    fun ml(value: Int): String = ml(value.toLong())
    fun number(value: Long): String = String.format(locale, "%,d", value)
    fun longDate(date: LocalDate): String = longDate.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun time(t: LocalTime): String = time.format(t)
    fun monthYear(date: LocalDate): String = monthYear.format(date)
}
