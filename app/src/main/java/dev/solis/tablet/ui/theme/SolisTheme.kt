package dev.solis.tablet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

private val SolisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 84.sp,
        letterSpacing = (-2).sp,
        color = Color.White
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        color = Color.White
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = Color.White
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = Color(0xFFE2E8F0)
    )
)

@Composable
fun SolisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SolisDarkColorScheme,
        typography = SolisTypography,
        content = content
    )
}
