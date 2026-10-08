package com.egrmeister.watertiles.domain

import com.egrmeister.watertiles.domain.mosaic.MosaicCalculator
import com.egrmeister.watertiles.domain.mosaic.TileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MosaicCalculatorTest {

    @Test fun tileCountIsCeilingOfGoalOverTile() {
        assertEquals(15, MosaicCalculator.tileCount(1500, 100))
        assertEquals(8, MosaicCalculator.tileCount(1500, 200))
        assertEquals(10, MosaicCalculator.tileCount(2000, 200))
        assertEquals(1, MosaicCalculator.tileCount(100, 200))
        assertEquals(100, MosaicCalculator.tileCount(10_000, 100))
    }

    @Test fun goalNotDivisibleByTileSizeGivesReducedFinalTile() {
        val m = MosaicCalculator.forGoal(1500, 200, 0)
        assertEquals(8, m.tiles.size)
        m.tiles.dropLast(1).forEach { assertEquals(200, it.capacityMl); assertFalse(it.isReducedCapacity) }
        assertEquals(100, m.tiles.last().capacityMl)
        assertTrue(m.tiles.last().isReducedCapacity)
        assertEquals(1500, m.tiles.sumOf { it.capacityMl })
    }

    @Test fun partialFillOf150WithHundredTiles() {
        val m = MosaicCalculator.forGoal(2000, 100, 150)
        assertEquals(TileState.FULL, m.tiles[0].state)
        assertEquals(TileState.PARTIAL, m.tiles[1].state)
        assertEquals(50, m.tiles[1].filledMl)
        assertEquals(0.5f, m.tiles[1].fraction, 0.0001f)
        assertEquals(TileState.EMPTY, m.tiles[2].state)
    }

    @Test fun everyMilliliterIsRepresentedBelowGoal() {
        for (v in listOf(0L, 1L, 99L, 100L, 101L, 1499L, 1500L)) {
            val m = MosaicCalculator.forGoal(1500, 200, v)
            assertEquals(v, m.shownMl)
            assertEquals(v, m.recordedMl)
        }
    }

    @Test fun reducedFinalTilePartialFill() {
        val m = MosaicCalculator.forGoal(1500, 200, 1450)
        val last = m.tiles.last()
        assertEquals(100, last.capacityMl)
        assertEquals(50, last.filledMl)
        assertEquals(TileState.PARTIAL, last.state)
    }

    @Test fun overflowKeepsMosaicFullWithoutExtraTiles() {
        val m = MosaicCalculator.forGoal(1500, 100, 1650)
        assertEquals(15, m.tiles.size)
        assertTrue(m.tiles.all { it.state == TileState.FULL })
        assertEquals(150L, m.overflowMl)
        assertEquals(110L, m.percent)
        assertEquals(1650L, m.recordedMl)
        assertTrue(m.goalReached)
    }

    @Test fun percentIsActualFloor() {
        assertEquals(99L, MosaicCalculator.forGoal(2000, 100, 1999).percent)
        assertEquals(100L, MosaicCalculator.forGoal(2000, 100, 2000).percent)
        assertEquals(0L, MosaicCalculator.forGoal(2000, 100, 0).percent)
    }

    @Test fun changingTileSizeDoesNotChangeTotals() {
        val a = MosaicCalculator.forGoal(1500, 100, 730)
        val b = MosaicCalculator.forGoal(1500, 200, 730)
        assertEquals(a.recordedMl, b.recordedMl)
        assertEquals(a.percent, b.percent)
        assertEquals(a.shownMl, b.shownMl)
        assertEquals(a.goalMl, b.goalMl)
    }

    @Test fun recordedOnlyMosaicHasNoGoal() {
        val m = MosaicCalculator.build(null, 200, 450)
        assertNull(m.goalMl)
        assertNull(m.percent)
        assertEquals(3, m.tiles.size)
        assertEquals(50, m.tiles.last().filledMl)
        assertEquals(450L, m.shownMl)
    }

    @Test fun recordedOnlyEmptyDayHasNoTiles() {
        assertTrue(MosaicCalculator.build(null, 100, 0).tiles.isEmpty())
    }

    @Test fun largeTotalsUseLong() {
        val m = MosaicCalculator.forGoal(10_000, 100, 3_000_000_000L)
        assertEquals(2_999_990_000L, m.overflowMl)
        assertEquals(30_000_000L, m.percent)
        assertEquals(100, m.tiles.size)
    }
}
