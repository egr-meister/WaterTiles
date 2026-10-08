package com.egrmeister.watertiles.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.egrmeister.watertiles.data.local.AppTheme

/** Tile colors. Themes change only colors and decoration, never values. */
@Immutable
data class TileColors(
    val fill: Color,
    val outline: Color,
    val emptyOutline: Color,
    val waterline: Color,
    val highlight: Color,
    val track: Color,
)

val LocalTileColors = staticCompositionLocalOf {
    TileColors(Color.Cyan, Color.DarkGray, Color.Gray, Color.Black, Color.White, Color.LightGray)
}
val LocalReducedMotion = staticCompositionLocalOf { false }

private val lagoon = lightColorScheme(
    primary = Color(0xFF0F5C66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFECEF),
    onPrimaryContainer = Color(0xFF07363C),
    secondary = Color(0xFF2E7F88),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F1F2),
    onSecondaryContainer = Color(0xFF0B3A40),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF0E3A40),
    surface = Color(0xFFFBF7F0),
    onSurface = Color(0xFF0E3A40),
    surfaceVariant = Color(0xFFEAF1EE),
    onSurfaceVariant = Color(0xFF34534F),
    surfaceContainer = Color(0xFFF2F0E8),
    surfaceContainerHigh = Color(0xFFECEAE2),
    surfaceContainerLow = Color(0xFFF7F4EC),
    outline = Color(0xFF5F7C79),
    error = Color(0xFFB3261E),
)
private val lagoonTiles = TileColors(
    fill = Color(0xFF3FB8C4),
    outline = Color(0xFF0F4C55),
    emptyOutline = Color(0xFF6F8F8C),
    waterline = Color(0xFF0B3A40),
    highlight = Color(0xCCFFFFFF),
    track = Color(0xFFE3EEEA),
)

private val lavender = lightColorScheme(
    primary = Color(0xFF5E3F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DCF5),
    onPrimaryContainer = Color(0xFF331A47),
    secondary = Color(0xFF7A5C93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E6F7),
    onSecondaryContainer = Color(0xFF3A2150),
    background = Color(0xFFFCF8EE),
    onBackground = Color(0xFF3A1F45),
    surface = Color(0xFFFCF8EE),
    onSurface = Color(0xFF3A1F45),
    surfaceVariant = Color(0xFFF1EAF0),
    onSurfaceVariant = Color(0xFF5A4560),
    surfaceContainer = Color(0xFFF4EFE6),
    surfaceContainerHigh = Color(0xFFEEE8DF),
    surfaceContainerLow = Color(0xFFF8F3EA),
    outline = Color(0xFF7D6A82),
    error = Color(0xFFB3261E),
)
private val lavenderTiles = TileColors(
    fill = Color(0xFFB79AD9),
    outline = Color(0xFF4A2A63),
    emptyOutline = Color(0xFF8A7890),
    waterline = Color(0xFF331A47),
    highlight = Color(0xCCFFFFFF),
    track = Color(0xFFF0E8F2),
)

private val midnight = darkColorScheme(
    primary = Color(0xFF9CC3F0),
    onPrimary = Color(0xFF0A1D33),
    primaryContainer = Color(0xFF243B5C),
    onPrimaryContainer = Color(0xFFDCE8F8),
    secondary = Color(0xFFA8BCD6),
    onSecondary = Color(0xFF0E1E33),
    secondaryContainer = Color(0xFF253650),
    onSecondaryContainer = Color(0xFFDCE6F4),
    background = Color(0xFF0D1626),
    onBackground = Color(0xFFE6ECF5),
    surface = Color(0xFF0D1626),
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = Color(0xFF1C2A40),
    onSurfaceVariant = Color(0xFFC0CCDD),
    surfaceContainer = Color(0xFF152034),
    surfaceContainerHigh = Color(0xFF1B273D),
    surfaceContainerLow = Color(0xFF111B2D),
    outline = Color(0xFF8796AD),
    error = Color(0xFFFFB4AB),
)
private val midnightTiles = TileColors(
    fill = Color(0xFF5E86B8),
    outline = Color(0xFFC9DAF0),
    emptyOutline = Color(0xFF7D8DA6),
    waterline = Color(0xFFE6ECF5),
    highlight = Color(0x66FFFFFF),
    track = Color(0xFF1C2A40),
)

fun colorSchemeFor(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.LAGOON -> lagoon
    AppTheme.LAVENDER -> lavender
    AppTheme.MIDNIGHT -> midnight
}

fun tileColorsFor(theme: AppTheme): TileColors = when (theme) {
    AppTheme.LAGOON -> lagoonTiles
    AppTheme.LAVENDER -> lavenderTiles
    AppTheme.MIDNIGHT -> midnightTiles
}

fun isDarkTheme(theme: AppTheme): Boolean = theme == AppTheme.MIDNIGHT

private val baseTypography = Typography()
private val typography = baseTypography.copy(
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    displaySmall = baseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
)

@Composable
fun WaterTilesTheme(theme: AppTheme, reducedMotion: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTileColors provides tileColorsFor(theme),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = colorSchemeFor(theme), typography = typography, content = content)
    }
}
