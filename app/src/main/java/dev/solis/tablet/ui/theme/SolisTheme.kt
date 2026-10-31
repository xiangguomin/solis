package dev.solis.tablet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SolisGold = Color(0xFFFFC857)
val SolisSky = Color(0xFF4EA8DE)
val SolisSurfaceDark = Color(0xFF0B0E14)
val SolisGlassCard = Color(0x88141A24)
val SolisGlassBorder = Color(0x33FFFFFF)

@Immutable
data class SolisPalette(
    val isLight: Boolean,
    val background: Color,
    val cardBackground: Color,
    val modalBackground: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val secondaryButtonBg: Color,
    val secondaryButtonText: Color
)

val DarkSolisPalette = SolisPalette(
    isLight = false,
    background = SolisSurfaceDark,
    cardBackground = SolisGlassCard,
    modalBackground = Color(0xFF0F172A),
    cardBorder = SolisGlassBorder,
    primaryText = Color.White,
    secondaryText = Color(0xFFE2E8F0),
    mutedText = Color(0xFF94A3B8),
    accent = SolisGold,
    secondaryButtonBg = Color(0xFF1E293B),
    secondaryButtonText = Color.White
)

val LightSolisPalette = SolisPalette(
    isLight = true,
    background = Color(0xFFF3EFE6),      // Warm gallery alabaster/linen
    cardBackground = Color(0xFFFFFFFF),  // Crisp warm white card
    modalBackground = Color(0xFFFAF8F5),
    cardBorder = Color(0x1F0F172A),      // Subtle slate border
    primaryText = Color(0xFF0F172A),     // Deep slate ink
    secondaryText = Color(0xFF334155),   // Slate 700
    mutedText = Color(0xFF64748B),       // Slate 500
    accent = Color(0xFFB45309),          // Warm amber accent with high contrast on white
    secondaryButtonBg = Color(0xFFE2E8F0),
    secondaryButtonText = Color(0xFF0F172A)
)

val LocalSolisPalette = staticCompositionLocalOf { DarkSolisPalette }

private val SolisDarkColorScheme = darkColorScheme(
    primary = SolisGold,
    secondary = SolisSky,
    background = SolisSurfaceDark,
    surface = SolisGlassCard,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val SolisLightColorScheme = lightColorScheme(
    primary = Color(0xFFB45309),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF3EFE6),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

private fun buildTypography(palette: SolisPalette) = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 84.sp,
        letterSpacing = (-2).sp,
        color = palette.primaryText
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        color = palette.primaryText
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = palette.primaryText
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = palette.secondaryText
    )
)

@Composable
fun SolisTheme(
    isLightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isLightMode) LightSolisPalette else DarkSolisPalette
    val colorScheme = if (isLightMode) SolisLightColorScheme else SolisDarkColorScheme

    CompositionLocalProvider(LocalSolisPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildTypography(palette),
            content = content
        )
    }
}
