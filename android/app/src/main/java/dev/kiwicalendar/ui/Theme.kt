package dev.kiwicalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.kiwicalendar.R
import kotlin.math.abs

val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold)
)

/** Strictly black and white. Material components (pickers, menus) pick these up too. */
private val MonoColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2B2B2B),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2B2B2B),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2B2B2B),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceBright = Color(0xFF2B2B2B),
    surfaceDim = Color.Black,
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF2B2B2B),
    error = Color.White,
    onError = Color.Black,
    scrim = Color.Black
)

private fun TextStyle.outfit(weight: FontWeight) = copy(fontFamily = Outfit, fontWeight = weight)

private val Base = Typography()

private val MonoTypography = Typography(
    displayLarge = Base.displayLarge.outfit(FontWeight.Light),
    displayMedium = Base.displayMedium.outfit(FontWeight.Light),
    displaySmall = Base.displaySmall.outfit(FontWeight.Normal),
    headlineLarge = Base.headlineLarge.outfit(FontWeight.Medium),
    headlineMedium = Base.headlineMedium.outfit(FontWeight.Normal),
    headlineSmall = Base.headlineSmall.outfit(FontWeight.Normal),
    titleLarge = Base.titleLarge.outfit(FontWeight.Medium),
    titleMedium = Base.titleMedium.outfit(FontWeight.Medium),
    titleSmall = Base.titleSmall.outfit(FontWeight.Medium),
    bodyLarge = Base.bodyLarge.outfit(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.outfit(FontWeight.Normal),
    bodySmall = Base.bodySmall.outfit(FontWeight.Normal),
    labelLarge = Base.labelLarge.outfit(FontWeight.Medium),
    labelMedium = Base.labelMedium.outfit(FontWeight.Normal),
    labelSmall = Base.labelSmall.outfit(FontWeight.Normal)
)

@Composable
fun KiwiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoColors, typography = MonoTypography, content = content)
}

/** Event "types" are shades of gray, which keeps the calendar black and white but still scannable. */
data class EventType(val name: String, val hex: String)

val EventTypes = listOf(
    EventType("Important", "#FFFFFF"),
    EventType("Family", "#C8C8C8"),
    EventType("Work", "#9A9A9A"),
    EventType("Personal", "#6E6E6E")
)

fun parseHexColor(hex: String?): Color? {
    val digits = hex?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/** Any event color, including ones picked on the phone page, shown as a readable gray. */
fun eventTone(hex: String?): Color {
    val color = parseHexColor(hex) ?: return Color.White
    if (color.red == color.green && color.green == color.blue) return color
    val level = 0.42f + 0.58f * color.luminance().coerceIn(0f, 1f)
    return Color(level, level, level)
}

fun eventTypeFor(hex: String?): EventType {
    val tone = eventTone(hex).red
    return EventTypes.minBy { abs(eventTone(it.hex).red - tone) }
}
