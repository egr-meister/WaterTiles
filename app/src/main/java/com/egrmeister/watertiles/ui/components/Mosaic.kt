package com.egrmeister.watertiles.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.egrmeister.watertiles.domain.mosaic.Mosaic
import com.egrmeister.watertiles.domain.mosaic.MosaicText
import com.egrmeister.watertiles.domain.mosaic.Tile
import com.egrmeister.watertiles.domain.mosaic.TileState
import com.egrmeister.watertiles.ui.theme.LocalReducedMotion
import com.egrmeister.watertiles.ui.theme.LocalTileColors

/** Droplet outline in a box of [size]: pointed top, round bottom (width = 0.64 × height). */
fun dropletPath(size: Size, scale: Float = 1f): Path {
    val h = minOf(size.height, size.width / 0.64f) * 0.92f * scale
    val left = (size.width - h) / 2f
    val top = (size.height - h) / 2f
    fun x(v: Float) = left + v * h
    fun y(v: Float) = top + v * h
    val r = 0.32f * h
    return Path().apply {
        moveTo(x(0.5f), y(0f))
        cubicTo(x(0.56f), y(0.12f), x(0.82f), y(0.40f), x(0.82f), y(0.68f))
        // Bottom half circle centred at (0.5, 0.68).
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(Offset(x(0.5f), y(0.68f)), r),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        cubicTo(x(0.18f), y(0.40f), x(0.44f), y(0.12f), x(0.5f), y(0f))
        close()
    }
}

/** Vertical extent of the droplet path for a box of [size], used for bottom-to-top fill. */
private fun dropletBounds(size: Size, scale: Float): Pair<Float, Float> {
    val h = minOf(size.height, size.width / 0.64f) * 0.92f * scale
    val top = (size.height - h) / 2f
    return top to top + h
}

@Composable
fun DropletTile(
    tile: Tile,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTileColors.current
    val reduced = LocalReducedMotion.current
    val fraction by animateFloatAsState(
        targetValue = tile.fraction.coerceIn(0f, 1f),
        animationSpec = if (reduced) snap() else tween(durationMillis = 450),
        label = "tileFill",
    )
    val description = MosaicText.tileDescription(tile)
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Show tile details", onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(clickable)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val scale = if (tile.isReducedCapacity) 0.82f else 1f
            val path = dropletPath(size, scale)
            val (top, bottom) = dropletBounds(size, scale)
            val strokeW = 2.dp.toPx()
            val state = when {
                fraction <= 0f -> TileState.EMPTY
                fraction >= 1f -> TileState.FULL
                else -> TileState.PARTIAL
            }
            // Track: very light fill so the shape reads on every theme.
            drawPath(path, colors.track)
            if (state != TileState.EMPTY) {
                val level = bottom - (bottom - top) * fraction
                clipPath(path) {
                    drawRect(colors.fill, topLeft = Offset(0f, level), size = Size(size.width, bottom - level + 1f))
                    if (state == TileState.PARTIAL) {
                        // Waterline pattern marks partial fill independently of color.
                        drawLine(colors.waterline, Offset(0f, level), Offset(size.width, level), strokeWidth = strokeW, cap = StrokeCap.Butt)
                        val dash = 3.dp.toPx()
                        var xPos = 0f
                        while (xPos < size.width) {
                            drawLine(colors.waterline.copy(alpha = 0.45f), Offset(xPos, level + dash * 2), Offset(xPos + dash, level + dash * 2), strokeWidth = strokeW * 0.6f)
                            xPos += dash * 3
                        }
                    }
                }
            }
            when (state) {
                TileState.EMPTY -> drawPath(
                    path,
                    colors.emptyOutline,
                    style = Stroke(width = strokeW, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
                TileState.PARTIAL -> drawPath(path, colors.outline, style = Stroke(width = strokeW))
                TileState.FULL -> {
                    drawPath(path, colors.outline, style = Stroke(width = strokeW * 1.4f))
                    // Highlight stroke: a "full" mark that does not rely on color.
                    val w = size.width
                    drawLine(
                        colors.highlight,
                        Offset(w * 0.42f, top + (bottom - top) * 0.55f),
                        Offset(w * 0.42f, top + (bottom - top) * 0.78f),
                        strokeWidth = strokeW * 1.2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Lazy mosaic rows, filled in stable row-by-row order. */
fun LazyListScope.mosaicRows(
    mosaic: Mosaic,
    columns: Int,
    tileSize: Dp,
    onTileClick: (Tile) -> Unit,
) {
    val rows = mosaic.tiles.chunked(columns)
    items(count = rows.size, key = { "mosaic-row-$it" }) { index ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            rows[index].forEach { tile ->
                DropletTile(
                    tile = tile,
                    modifier = Modifier.size(tileSize).padding(2.dp),
                    onClick = { onTileClick(tile) },
                )
            }
        }
    }
}

/** Column count that keeps tiles at least [minTile] wide; tiles never shrink below it. */
fun mosaicColumns(availableWidth: Dp, minTile: Dp = 52.dp, maxColumns: Int = 10): Int =
    (availableWidth / minTile).toInt().coerceIn(3, maxColumns)
