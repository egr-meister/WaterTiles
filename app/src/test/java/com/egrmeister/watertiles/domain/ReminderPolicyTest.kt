package com.egrmeister.watertiles.domain

import com.egrmeister.watertiles.domain.reminders.ReminderDecision
import com.egrmeister.watertiles.domain.reminders.ReminderPolicy
import com.egrmeister.watertiles.domain.reminders.ReminderSettings
import com.egrmeister.watertiles.domain.reminders.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {
    private val zone = ZoneId.of("America/New_York")
    private val s = ReminderSettings(enabled = true, start = LocalTime.of(9, 0), end = LocalTime.of(21, 0), intervalMinutes = 120)
    private fun at(h: Int, m: Int = 0, day: Int = 10) = ZonedDateTime.of(2026, 6, day, h, m, 0, 0, zone).toInstant()

    @Test fun slotsWithinSameDayWindow() {
        assertEquals(
            listOf(9, 11, 13, 15, 17, 19, 21).map { LocalTime.of(it, 0) },
            ReminderPolicy.slotsOf(s),
        )
        assertEquals(listOf(LocalTime.of(9, 0), LocalTime.of(10, 30)), ReminderPolicy.slotsOf(s.copy(end = LocalTime.of(11, 0), intervalMinutes = 90)))
    }

    @Test fun endMustBeAfterStart() {
        assertFalse(ReminderPolicy.isWindowValid(s.copy(end = LocalTime.of(9, 0))))
        assertFalse(ReminderPolicy.isWindowValid(s.copy(start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))))
        assertTrue(ReminderPolicy.slotsOf(s.copy(end = LocalTime.of(8, 0))).isEmpty())
    }

    @Test fun nextSlotTodayThenTomorrow() {
        assertEquals(at(11), ReminderPolicy.nextSlot(s, at(9, 1), zone)!!.toInstant())
        assertEquals(at(9, day = 11), ReminderPolicy.nextSlot(s, at(21, 5), zone)!!.toInstant())
        assertEquals(at(9), ReminderPolicy.nextSlot(s, at(3), zone)!!.toInstant())
        assertNull(ReminderPolicy.nextSlot(s.copy(enabled = false), at(3), zone))
    }

    @Test fun nextSlotFollowsCurrentTimeZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val next = ReminderPolicy.nextSlot(s, at(9, 1), tokyo)!!
        assertEquals(tokyo, next.zone)
        assertTrue(next.toLocalTime() in ReminderPolicy.slotsOf(s))
    }

    @Test fun postsWhenEligible() {
        assertEquals(ReminderDecision.Post, decide(slot = at(11), now = at(11, 2)))
    }

    @Test fun staleDeliverySkipped() {
        assertEquals(ReminderDecision.Skip(SkipReason.STALE), decide(slot = at(11), now = at(12, 30)))
    }

    @Test fun disabledOrChangedSettingsSkipped() {
        assertEquals(ReminderDecision.Skip(SkipReason.DISABLED), decide(settings = s.copy(enabled = false)))
        assertEquals(ReminderDecision.Skip(SkipReason.NOT_A_CURRENT_SLOT), decide(settings = s.copy(intervalMinutes = 180), slot = at(11), now = at(11)))
        assertEquals(ReminderDecision.Skip(SkipReason.NOT_A_CURRENT_SLOT), decide(settings = s.copy(end = LocalTime.of(10, 0)), slot = at(11), now = at(11)))
    }

    @Test fun goalReachedSuppresses() {
        assertEquals(ReminderDecision.Skip(SkipReason.GOAL_REACHED), decide(goal = 2000, total = 2000))
        assertEquals(ReminderDecision.Post, decide(goal = 2000, total = 1999))
        assertEquals(ReminderDecision.Post, decide(goal = null, total = 5000))
    }

    @Test fun recentEntrySinceBoundarySuppresses() {
        assertEquals(ReminderDecision.Skip(SkipReason.RECENT_ENTRY), decide(lastEntry = at(9, 0)))
        assertEquals(ReminderDecision.Skip(SkipReason.RECENT_ENTRY), decide(lastEntry = at(10, 45)))
        assertEquals(ReminderDecision.Post, decide(lastEntry = at(8, 59)))
    }

    @Test fun staleToleranceBounded() {
        assertEquals(Duration.ofMinutes(30), ReminderPolicy.staleTolerance(s.copy(intervalMinutes = 60)))
        assertEquals(Duration.ofMinutes(45), ReminderPolicy.staleTolerance(s.copy(intervalMinutes = 180)))
    }

    private fun decide(
        settings: ReminderSettings = s,
        slot: java.time.Instant = at(11),
        now: java.time.Instant = at(11, 1),
        goal: Int? = 2000,
        total: Long = 0,
        lastEntry: java.time.Instant? = null,
    ) = ReminderPolicy.decide(settings, slot, now, zone, goal, total, lastEntry)
}
