package com.egrmeister.watertiles.domain.entries

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Domain view of a stored entry. Grouping always uses the stored local date/time. */
data class WaterEntry(
    val id: Long,
    val amountMl: Int,
    val timestamp: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: ZoneId,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Technical input limits, not consumption advice. */
object EntryLimits {
    const val MIN_ML = 1
    const val MAX_ML = 5_000
}

sealed interface AmountResult {
    data class Valid(val ml: Int) : AmountResult
    data class Invalid(val message: String) : AmountResult
}

object AmountParser {
    private val digits = Regex("^[0-9]+$")

    fun parse(raw: String, min: Int = EntryLimits.MIN_ML, max: Int = EntryLimits.MAX_ML): AmountResult {
        val text = raw.trim().replace(",", "")
        if (text.isEmpty()) return AmountResult.Invalid("Enter an amount in mL.")
        if (!digits.matches(text)) return AmountResult.Invalid("Use whole milliliters only.")
        val value = text.toLongOrNull() ?: return AmountResult.Invalid("Enter between ${fmt(min)} and ${fmt(max)} mL.")
        if (value < min || value > max) return AmountResult.Invalid("Enter between ${fmt(min)} and ${fmt(max)} mL.")
        return AmountResult.Valid(value.toInt())
    }

    private fun fmt(v: Int) = String.format(java.util.Locale.US, "%,d", v)
}

sealed interface LocalTimeResolution {
    data class Resolved(val zoned: ZonedDateTime) : LocalTimeResolution
    /** The wall-clock time does not exist (daylight-saving gap). */
    data object Nonexistent : LocalTimeResolution
}

object TimeResolver {
    /**
     * Resolves a wall-clock time in [zone]. Ambiguous times (DST overlap) consistently use the
     * earlier offset; nonexistent times are rejected instead of silently shifted.
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): LocalTimeResolution {
        val ldt = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(ldt)
        if (offsets.isEmpty()) return LocalTimeResolution.Nonexistent
        val earlier = offsets.maxBy { it.totalSeconds } // larger offset = earlier instant
        return LocalTimeResolution.Resolved(ZonedDateTime.ofStrict(ldt, earlier, zone))
    }
}

sealed interface EntryValidation {
    data class Valid(val amountMl: Int, val zoned: ZonedDateTime) : EntryValidation
    data class Invalid(val amountError: String?, val dateTimeError: String?) : EntryValidation
}

object EntryValidator {
    fun validate(
        amountText: String,
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
        now: Instant,
    ): EntryValidation {
        val amount = AmountParser.parse(amountText)
        val amountError = (amount as? AmountResult.Invalid)?.message
        val today = now.atZone(zone).toLocalDate()
        val dateTimeError: String?
        var zoned: ZonedDateTime? = null
        if (date.isAfter(today)) {
            dateTimeError = "Choose today or a past date."
        } else {
            when (val r = TimeResolver.resolve(date, time, zone)) {
                LocalTimeResolution.Nonexistent ->
                    dateTimeError = "This time doesn't exist on that date because of a daylight-saving change. Choose another time."
                is LocalTimeResolution.Resolved -> {
                    // Compare at minute precision so "now" entered from the picker is accepted.
                    val nowMinute = now.atZone(zone).withSecond(0).withNano(0)
                    dateTimeError = if (r.zoned.isAfter(nowMinute.plusMinutes(1).minusNanos(1))) {
                        "Future times can't be recorded."
                    } else {
                        zoned = r.zoned
                        null
                    }
                }
            }
        }
        return if (amount is AmountResult.Valid && zoned != null) {
            EntryValidation.Valid(amount.ml, zoned)
        } else {
            EntryValidation.Invalid(amountError, dateTimeError)
        }
    }
}
