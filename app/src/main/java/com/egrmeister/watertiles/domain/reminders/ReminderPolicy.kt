package com.egrmeister.watertiles.domain.reminders

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderSettings(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(21, 0),
    val intervalMinutes: Int = 120,
) {
    companion object {
        val INTERVALS = listOf(60, 90, 120, 180)
    }
}

sealed interface ReminderDecision {
    data object Post : ReminderDecision
    data class Skip(val reason: SkipReason) : ReminderDecision
}

enum class SkipReason { DISABLED, INVALID_WINDOW, NOT_A_CURRENT_SLOT, STALE, GOAL_REACHED, RECENT_ENTRY }

object ReminderPolicy {

    /** Same-day windows only: end must be strictly later than start. */
    fun isWindowValid(s: ReminderSettings): Boolean =
        s.end.isAfter(s.start) && s.intervalMinutes in ReminderSettings.INTERVALS

    /** Wall-clock slots for one day: start, start+interval, ... while <= end. */
    fun slotsOf(s: ReminderSettings): List<LocalTime> {
        if (!isWindowValid(s)) return emptyList()
        val out = ArrayList<LocalTime>()
        var minutes = s.start.toSecondOfDay() / 60
        val endMinutes = s.end.toSecondOfDay() / 60
        while (minutes <= endMinutes) {
            out += LocalTime.of(minutes / 60, minutes % 60)
            minutes += s.intervalMinutes
        }
        return out
    }

    /**
     * Next slot strictly after [now] in [zone], searching today and the following days.
     * Slots falling into a daylight-saving gap are skipped.
     */
    fun nextSlot(s: ReminderSettings, now: Instant, zone: ZoneId): ZonedDateTime? {
        if (!s.enabled) return null
        val slots = slotsOf(s)
        if (slots.isEmpty()) return null
        val today = now.atZone(zone).toLocalDate()
        for (dayOffset in 0L..2L) {
            val date = today.plusDays(dayOffset)
            for (t in slots) {
                val zdt = resolveSlot(date, t, zone) ?: continue
                if (zdt.toInstant().isAfter(now)) return zdt
            }
        }
        return null
    }

    private fun resolveSlot(date: LocalDate, t: LocalTime, zone: ZoneId): ZonedDateTime? {
        val ldt = date.atTime(t)
        val offsets = zone.rules.getValidOffsets(ldt)
        if (offsets.isEmpty()) return null
        return ZonedDateTime.ofStrict(ldt, offsets.maxBy { it.totalSeconds }, zone)
    }

    /** How late a delivery may be before it is considered stale and skipped. */
    fun staleTolerance(s: ReminderSettings): Duration =
        Duration.ofMinutes(minOf(45L, s.intervalMinutes / 2L))

    /**
     * Decides at delivery time whether a scheduled reminder should be shown.
     * @param scheduledFor the slot the alarm was scheduled for.
     * @param lastEntryAt timestamp of the most recent entry, if any.
     */
    fun decide(
        s: ReminderSettings,
        scheduledFor: Instant,
        now: Instant,
        zone: ZoneId,
        todayGoalMl: Int?,
        todayTotalMl: Long,
        lastEntryAt: Instant?,
    ): ReminderDecision {
        if (!s.enabled) return ReminderDecision.Skip(SkipReason.DISABLED)
        if (!isWindowValid(s)) return ReminderDecision.Skip(SkipReason.INVALID_WINDOW)
        val slotLocal = scheduledFor.atZone(zone).toLocalTime().withSecond(0).withNano(0)
        if (slotLocal !in slotsOf(s)) return ReminderDecision.Skip(SkipReason.NOT_A_CURRENT_SLOT)
        if (Duration.between(scheduledFor, now) > staleTolerance(s)) return ReminderDecision.Skip(SkipReason.STALE)
        if (todayGoalMl != null && todayTotalMl >= todayGoalMl) return ReminderDecision.Skip(SkipReason.GOAL_REACHED)
        val previousBoundary = scheduledFor.minus(Duration.ofMinutes(s.intervalMinutes.toLong()))
        if (lastEntryAt != null && !lastEntryAt.isBefore(previousBoundary)) {
            return ReminderDecision.Skip(SkipReason.RECENT_ENTRY)
        }
        return ReminderDecision.Post
    }
}
