package com.egrmeister.watertiles.domain.mosaic

enum class TileState { FULL, PARTIAL, EMPTY }

/**
 * One droplet tile. [capacityMl] equals the tile size except for a final reduced tile
 * when the goal is not divisible by the tile size.
 */
data class Tile(
    val number: Int,
    val capacityMl: Int,
    val filledMl: Int,
    val isReducedCapacity: Boolean,
) {
    val fraction: Float get() = if (capacityMl == 0) 0f else filledMl.toFloat() / capacityMl
    val state: TileState
        get() = when {
            filledMl <= 0 -> TileState.EMPTY
            filledMl >= capacityMl -> TileState.FULL
            else -> TileState.PARTIAL
        }
}

data class Mosaic(
    val tiles: List<Tile>,
    val tileSizeMl: Int,
    val recordedMl: Long,
    /** null when no goal is saved for the day ("Recorded volume" mode). */
    val goalMl: Int?,
) {
    /** Floor of the actual percentage; may exceed 100. null without a goal. */
    val percent: Long? get() = goalMl?.let { (recordedMl * 100) / it }
    val overflowMl: Long get() = goalMl?.let { (recordedMl - it).coerceAtLeast(0) } ?: 0
    val goalReached: Boolean get() = goalMl != null && recordedMl >= goalMl
    val fullCount: Int get() = tiles.count { it.state == TileState.FULL }
    val partialTile: Tile? get() = tiles.firstOrNull { it.state == TileState.PARTIAL }
    /** Volume represented by tiles that is actually drawn (excludes overflow). */
    val shownMl: Long get() = tiles.sumOf { it.filledMl.toLong() }
}

object MosaicCalculator {
    const val MIN_GOAL_ML = 100
    const val MAX_GOAL_ML = 10_000
    val TILE_SIZES = listOf(100, 200)

    /** Safety bound for recorded-only mosaics; goal mosaics are at most 100 tiles. */
    const val MAX_RECORDED_ONLY_TILES = 300

    fun tileCount(goalMl: Int, tileSizeMl: Int): Int {
        require(goalMl > 0 && tileSizeMl > 0)
        return (goalMl + tileSizeMl - 1) / tileSizeMl
    }

    /** Goal mosaic: ceiling(G / T) tiles; overflow keeps the mosaic full, never adds tiles. */
    fun forGoal(goalMl: Int, tileSizeMl: Int, recordedMl: Long): Mosaic {
        require(goalMl > 0) { "goal must be positive" }
        require(tileSizeMl > 0) { "tile size must be positive" }
        require(recordedMl >= 0) { "recorded volume must not be negative" }
        val count = tileCount(goalMl, tileSizeMl)
        var remainingGoal = goalMl
        var remainingVolume = recordedMl
        val tiles = ArrayList<Tile>(count)
        for (i in 0 until count) {
            val capacity = minOf(tileSizeMl, remainingGoal)
            val filled = minOf(capacity.toLong(), remainingVolume).toInt()
            tiles += Tile(
                number = i + 1,
                capacityMl = capacity,
                filledMl = filled,
                isReducedCapacity = capacity < tileSizeMl,
            )
            remainingGoal -= capacity
            remainingVolume -= filled
        }
        return Mosaic(tiles, tileSizeMl, recordedMl, goalMl)
    }

    /** Volume-only mosaic for a day without a saved goal. */
    fun forRecordedOnly(tileSizeMl: Int, recordedMl: Long): Mosaic {
        require(tileSizeMl > 0)
        require(recordedMl >= 0)
        val needed = ((recordedMl + tileSizeMl - 1) / tileSizeMl).coerceAtMost(MAX_RECORDED_ONLY_TILES.toLong()).toInt()
        var remaining = recordedMl
        val tiles = (0 until needed).map { i ->
            val filled = minOf(tileSizeMl.toLong(), remaining).toInt()
            remaining -= filled
            Tile(i + 1, tileSizeMl, filled, isReducedCapacity = false)
        }
        return Mosaic(tiles, tileSizeMl, recordedMl, goalMl = null)
    }

    fun build(goalMl: Int?, tileSizeMl: Int, recordedMl: Long): Mosaic =
        if (goalMl != null) forGoal(goalMl, tileSizeMl, recordedMl) else forRecordedOnly(tileSizeMl, recordedMl)
}
