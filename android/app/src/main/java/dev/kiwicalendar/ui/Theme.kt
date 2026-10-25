package dev.kiwicalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.kiwicalendar.R

/** Atkinson Hyperlegible was designed for low-vision readers, which suits a screen read from across a room. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold)
)

private val Pine = Color(0xFF22553A)
private val Sage = Color(0xFFDCE8DF)
private val Ink = Color(0xFF1B2420)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Sage,
    onPrimaryContainer = Color(0xFF0F2418),
    secondary = Pine,
    onSecondary = Color.White,
    secondaryContainer = Sage,
    onSecondaryContainer = Ink,
    tertiary = Pine,
    onTertiary = Color.White,
    tertiaryContainer = Sage,
    onTertiaryContainer = Ink,
    background = Color(0xFFFAFAF7),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFF2EE),
    onSurfaceVariant = Color(0xFF5D6B63),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8F5),
    surfaceContainer = Color(0xFFF2F4F0),
    surfaceContainerHigh = Color(0xFFF7F8F5),
    surfaceContainerHighest = Color(0xFFE8ECE7),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3E7E2),
    outline = Color(0xFF8A968F),
    outlineVariant = Color(0xFFE3E7E2),
    error = Color(0xFFA3341F),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBA4),
    onPrimary = Color(0xFF0F2418),
    primaryContainer = Color(0xFF23402F),
    onPrimaryContainer = Color(0xFFDCE8DF),
    secondary = Color(0xFF8FCBA4),
    onSecondary = Color(0xFF0F2418),
    secondaryContainer = Color(0xFF23402F),
    onSecondaryContainer = Color(0xFFE6EBE7),
    tertiary = Color(0xFF8FCBA4),
    onTertiary = Color(0xFF0F2418),
    tertiaryContainer = Color(0xFF23402F),
    onTertiaryContainer = Color(0xFFE6EBE7),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE6EBE7),
    surface = Color(0xFF1A201D),
    onSurface = Color(0xFFE6EBE7),
    surfaceVariant = Color(0xFF232A26),
    onSurfaceVariant = Color(0xFF9AA8A0),
    surfaceContainerLowest = Color(0xFF0E1110),
    surfaceContainerLow = Color(0xFF161B18),
    surfaceContainer = Color(0xFF1A201D),
    surfaceContainerHigh = Color(0xFF212824),
    surfaceContainerHighest = Color(0xFF2B332F),
    surfaceBright = Color(0xFF2B332F),
    surfaceDim = Color(0xFF121614),
    outline = Color(0xFF6B7871),
    outlineVariant = Color(0xFF2B332F),
    error = Color(0xFFF08B74),
    onError = Color(0xFF3A0C02)
)

private fun TextStyle.atkinson(bold: Boolean = false) =
    copy(fontFamily = Atkinson, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private val Base = Typography()

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.atkinson(bold = true),
    displayMedium = Base.displayMedium.atkinson(bold = true),
    displaySmall = Base.displaySmall.atkinson(bold = true),
    headlineLarge = Base.headlineLarge.atkinson(bold = true),
    headlineMedium = Base.headlineMedium.atkinson(bold = true),
    headlineSmall = Base.headlineSmall.atkinson(bold = true),
    titleLarge = Base.titleLarge.atkinson(bold = true),
    titleMedium = Base.titleMedium.atkinson(bold = true),
    titleSmall = Base.titleSmall.atkinson(bold = true),
    bodyLarge = Base.bodyLarge.atkinson(),
    bodyMedium = Base.bodyMedium.atkinson(),
    bodySmall = Base.bodySmall.atkinson(),
    labelLarge = Base.labelLarge.atkinson(bold = true),
    labelMedium = Base.labelMedium.atkinson(),
    labelSmall = Base.labelSmall.atkinson()
)

@Composable
fun KiwiTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

val EventPalette = listOf("#22553A", "#3B6EA8", "#7A4FB5", "#B8457A", "#C2412D", "#C97A1A", "#8A7A12", "#566069")

fun parseHexColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

fun textOn(background: Color): Color = if (background.luminance() > 0.45f) Ink else Color.White
