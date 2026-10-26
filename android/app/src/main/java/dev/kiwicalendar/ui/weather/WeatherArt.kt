package dev.kiwicalendar.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.cos
import kotlin.math.sin

enum class Sky { Clear, Partly, Cloudy, Fog, Drizzle, Rain, Snow, Thunder }

fun skyFor(code: Int?): Sky = when (code ?: -1) {
    0 -> Sky.Clear
    1, 2 -> Sky.Partly
    3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    in 51..57 -> Sky.Drizzle
    in 61..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Thunder
    else -> Sky.Cloudy
}

/** Draws a condition icon in a square at [topLeft] with side [side] (24-unit grid). */
fun DrawScope.drawWeatherIcon(
    code: Int?,
    isDay: Boolean,
    color: Color,
    topLeft: Offset = Offset.Zero,
    side: Float = size.minDimension
) {
    val u = side / 24f
    val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(topLeft.x + x * u, topLeft.y + y * u)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)

    fun cloud(dy: Float, scale: Float = 1f, dx: Float = 0f): Path {
        fun circle(cx: Float, cy: Float, r: Float) = Path().apply {
            addOval(Rect(center = at(dx + cx * scale, dy + cy * scale), radius = r * scale * u))
        }
        val base = Path().apply {
            val a = at(dx + 8f * scale, dy + 14.5f * scale)
            val b = at(dx + 17.5f * scale, dy + 18.5f * scale)
            addRect(Rect(a.x, a.y, b.x, b.y))
        }
        var shape = circle(8f, 14.5f, 4f)
        for (part in listOf(circle(13f, 11.5f, 5f), circle(17.5f, 14.5f, 4f), base)) {
            shape = Path.combine(PathOperation.Union, shape, part)
        }
        return shape
    }

    fun sun(cx: Float, cy: Float, r: Float) {
        drawCircle(color, r * u, at(cx, cy), style = stroke)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val inner = r + 1.8f
            val outer = r + 3.4f
            seg(
                cx + inner * cos(angle).toFloat(),
                cy + inner * sin(angle).toFloat(),
                cx + outer * cos(angle).toFloat(),
                cy + outer * sin(angle).toFloat()
            )
        }
    }

    fun moon(cx: Float, cy: Float, r: Float) {
        val body = Path().apply { addOval(Rect(center = at(cx, cy), radius = r * u)) }
        val bite = Path().apply { addOval(Rect(center = at(cx + r * 0.55f, cy - r * 0.45f), radius = r * 0.82f * u)) }
        drawPath(Path.combine(PathOperation.Difference, body, bite), color, style = stroke)
    }

    when (skyFor(code)) {
        Sky.Clear -> if (isDay) sun(12f, 12f, 4.6f) else moon(12f, 12f, 7.5f)
        Sky.Partly -> {
            val shape = cloud(dy = 2f, scale = 0.95f, dx = 1.6f)
            clipPath(shape, ClipOp.Difference) {
                if (isDay) sun(9f, 9f, 3.2f) else moon(9f, 9f, 5f)
            }
            drawPath(shape, color, style = stroke)
        }
        Sky.Cloudy -> drawPath(cloud(dy = 0f), color, style = stroke)
        Sky.Fog -> {
            seg(3f, 7.5f, 21f, 7.5f)
            seg(5.5f, 11.5f, 18.5f, 11.5f)
            seg(3f, 15.5f, 21f, 15.5f)
            seg(6.5f, 19.5f, 17.5f, 19.5f)
        }
        Sky.Drizzle -> {
            drawPath(cloud(dy = -3f), color, style = stroke)
            for ((x, y) in listOf(9f to 19.5f, 13f to 21f, 17f to 19.5f)) seg(x, y, x - 0.4f, y + 1.2f)
        }
        Sky.Rain -> {
            drawPath(cloud(dy = -3f), color, style = stroke)
            for (x in listOf(9f, 13f, 17f)) seg(x, 18f, x - 1.2f, 21.8f)
        }
        Sky.Snow -> {
            drawPath(cloud(dy = -3f), color, style = stroke)
            for ((x, y) in listOf(9f to 19.5f, 13f to 21.5f, 17f to 19.5f)) drawCircle(color, 1.15f * u, at(x, y))
        }
        Sky.Thunder -> {
            drawPath(cloud(dy = -3f), color, style = stroke)
            val bolt = Path().apply {
                moveTo(at(13.5f, 16f).x, at(13.5f, 16f).y)
                lineTo(at(10.5f, 19.8f).x, at(10.5f, 19.8f).y)
                lineTo(at(13.5f, 19.8f).x, at(13.5f, 19.8f).y)
                lineTo(at(11.2f, 23.2f).x, at(11.2f, 23.2f).y)
            }
            drawPath(bolt, color, style = stroke)
        }
    }
}

@Composable
fun WeatherIcon(code: Int?, isDay: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) { drawWeatherIcon(code, isDay, color) }
}
