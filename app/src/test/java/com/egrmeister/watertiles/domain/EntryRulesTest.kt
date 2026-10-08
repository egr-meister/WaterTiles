package com.egrmeister.watertiles.domain

import com.egrmeister.watertiles.domain.entries.AmountParser
import com.egrmeister.watertiles.domain.entries.AmountResult
import com.egrmeister.watertiles.domain.entries.EntryValidation
import com.egrmeister.watertiles.domain.entries.EntryValidator
import com.egrmeister.watertiles.domain.entries.LocalTimeResolution
import com.egrmeister.watertiles.domain.entries.QuickAddGuard
import com.egrmeister.watertiles.domain.entries.TimeResolver
import com.egrmeister.watertiles.domain.entries.UndoSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EntryRulesTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 3, 10, 14, 30, 0, 0, zone).toInstant()

    @Test fun amountLimits() {
        assertEquals(AmountResult.Valid(1), AmountParser.parse("1"))
        assertEquals(AmountResult.Valid(5000), AmountParser.parse("5000"))
        assertEquals(AmountResult.Valid(1500), AmountParser.parse("1,500"))
        assertTrue(AmountParser.parse("0") is AmountResult.Invalid)
        assertTrue(AmountParser.parse("5001") is AmountResult.Invalid)
        assertTrue(AmountParser.parse("") is AmountResult.Invalid)
        assertTrue(AmountParser.parse("12.5") is AmountResult.Invalid)
        assertTrue(AmountParser.parse("-5") is AmountResult.Invalid)
        assertTrue(AmountParser.parse("99999999999999999999") is AmountResult.Invalid)
    }

    @Test fun futureDateRejected() {
        val v = EntryValidator.validate("200", LocalDate.of(2026, 3, 11), LocalTime.of(8, 0), zone, now)
        assertTrue(v is EntryValidation.Invalid)
        assertNotNull((v as EntryValidation.Invalid).dateTimeError)
    }

    @Test fun futureTimeTodayRejectedCurrentMinuteAccepted() {
        assertTrue(EntryValidator.validate("200", LocalDate.of(2026, 3, 10), LocalTime.of(14, 31), zone, now) is EntryValidation.Invalid)
        assertTrue(EntryValidator.validate("200", LocalDate.of(2026, 3, 10), LocalTime.of(14, 30), zone, now) is EntryValidation.Valid)
        assertTrue(EntryValidator.validate("200", LocalDate.of(2025, 1, 1), LocalTime.of(23, 59), zone, now) is EntryValidation.Valid)
    }

    @Test fun nonexistentDstTimeRejected() {
        // Europe/Berlin springs forward 2026-03-29 02:00 -> 03:00.
        assertEquals(
            LocalTimeResolution.Nonexistent,
            TimeResolver.resolve(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), zone),
        )
        val later = ZonedDateTime.of(2026, 4, 1, 12, 0, 0, 0, zone).toInstant()
        val v = EntryValidator.validate("200", LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), zone, later)
        assertTrue(v is EntryValidation.Invalid)
    }

    @Test fun ambiguousDstTimeUsesEarlierOffset() {
        // Europe/Berlin falls back 2026-10-25 03:00 -> 02:00; 02:30 occurs twice.
        val r = TimeResolver.resolve(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), zone)
        r as LocalTimeResolution.Resolved
        assertEquals(ZoneOffset.ofHours(2), r.zoned.offset)
    }

    @Test fun quickAddGuardBlocksDuplicateButAllowsSeparateActivations() {
        val g = QuickAddGuard()
        assertTrue(g.tryBegin(1))
        assertFalse(g.tryBegin(2)) // still processing
        g.end()
        assertFalse(g.tryBegin(1)) // same activation never reprocessed
        assertTrue(g.tryBegin(3)) // deliberate separate tap
        g.end()
    }

    @Test fun undoSlotRestoresOnce() {
        val u = UndoSlot<String>()
        u.offer("entry")
        assertEquals("entry", u.take())
        assertNull(u.take())
    }
}
