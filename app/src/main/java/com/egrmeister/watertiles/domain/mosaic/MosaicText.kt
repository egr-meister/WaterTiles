package com.egrmeister.watertiles.domain.mosaic

import com.egrmeister.watertiles.core.Format

/** Text equivalents of the visual mosaic for summaries and screen readers. */
object MosaicText {

    fun tileStateLabel(tile: Tile): String = when (tile.state) {
        TileState.FULL -> "Full"
        TileState.PARTIAL -> "Partially filled"
        TileState.EMPTY -> "Empty"
    }

    fun tileDescription(tile: Tile): String {
        val reduced = if (tile.isReducedCapacity) ", final tile with reduced capacity" else ""
        return "Tile ${tile.number}: ${Format.ml(tile.filledMl)} of ${Format.ml(tile.capacityMl)}$reduced, " +
            tileStateLabel(tile).lowercase()
    }

    fun summary(m: Mosaic): String {
        if (m.tiles.isEmpty()) {
            return if (m.goalMl == null) "No records for this day." else "No water recorded yet. Goal ${Format.ml(m.goalMl)}."
        }
        val partial = m.partialTile
        val parts = buildList {
            add("${m.fullCount} of ${m.tiles.size} tiles full")
            if (partial != null) {
                add("tile ${partial.number} holds ${Format.ml(partial.filledMl)} of ${Format.ml(partial.capacityMl)}")
            }
        }.joinToString(", ")
        val total = if (m.goalMl != null) {
            "${Format.ml(m.recordedMl)} of ${Format.ml(m.goalMl)} recorded, ${m.percent}% of your selected goal"
        } else {
            "Recorded volume ${Format.ml(m.recordedMl)}"
        }
        val over = if (m.overflowMl > 0) ". ${overflowText(m.overflowMl)}" else ""
        val hidden = m.recordedMl - m.shownMl
        val notShown = if (m.goalMl == null && hidden > 0) ". ${Format.ml(hidden)} not drawn as tiles" else ""
        return "$total. $parts$over$notShown."
    }

    fun overflowText(overflowMl: Long): String = "${Format.ml(overflowMl)} above your selected goal"

    fun tileSizeCaption(tileSizeMl: Int): String = "Each full tile represents ${Format.ml(tileSizeMl)}"
}
