package dev.kiwicalendar.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Mono {
    // Single source of truth for custom widgets. Color pairs are contrast checked.
    var isLight: Boolean = false
    val Ink: Color get() = if (isLight) Color(0xFF15191F) else Color.White
    val Paper: Color get() = if (isLight) Color.White else Color.Black
    val Muted: Color get() = if (isLight) Color(0xFF4C5666) else Color(0xFFB9BCC4)
    val Faint: Color get() = if (isLight) Color(0xFF626C7B) else Color(0xFF929AA5)
    val Card: Color get() = if (isLight) Color.White else Color(0xFF181B21)
    val CardStrong: Color get() = if (isLight) Color(0xFFE9EDF2) else Color(0xFF2A2F38)
    val Border: Color get() = if (isLight) Color(0xFFC7CED8) else Color(0xFF414650)
    val Hairline: Color get() = if (isLight) Color(0xFFD8DEE6) else Color(0xFF303640)
    val Sheet: Color get() = if (isLight) Color(0xFFFFFFFF) else Color(0xFF171A20)
}

val ScreenBackground: Brush
    get() = if (Mono.isLight) Brush.linearGradient(
        listOf(Color(0xFFFAFBFD), Color(0xFFF0F3F7), Color(0xFFE9EDF3))
    ) else Brush.linearGradient(
        listOf(Color(0xFF20242B), Color(0xFF0E1015), Color(0xFF090A0D))
    )

typealias Glyph = DrawScope.(Color) -> Unit

/** Frosted panel: a faint white fill and a hairline edge. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    fill: Color = Mono.Card,
    border: Color = Mono.Border
): Modifier = this
    .clip(shape)
    .background(fill, shape)
    .border(1.dp, border, shape)

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
    )
}

@Composable
fun DottedDivider(modifier: Modifier = Modifier, color: Color = Mono.Border) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        )
    }
}

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color = Mono.Ink, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { glyph(color) }
}

@Composable
fun RoundButton(glyph: Glyph, label: String, onClick: () -> Unit, size: Dp = 44.dp, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) Mono.Ink else Mono.Card)
            .border(1.dp, if (selected) Mono.Ink else Mono.Border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, size * 0.44f, if (selected) Mono.Paper else Mono.Ink)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (enabled) Mono.Ink else Mono.CardStrong)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Mono.Paper else Mono.Faint, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MonoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) Mono.Ink else Mono.CardStrong)
            .border(1.dp, Mono.Border, CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Mono.Paper else Mono.Ink)
        )
    }
}

@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = textStyle.copy(color = Mono.Ink),
        cursorBrush = SolidColor(Mono.Ink),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = Mono.Faint)
                inner()
            }
        }
    )
}
