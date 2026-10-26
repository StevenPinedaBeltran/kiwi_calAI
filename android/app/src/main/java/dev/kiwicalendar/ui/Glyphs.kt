package dev.kiwicalendar.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.kiwicalendar.ui.weather.drawWeatherIcon

/**
 * Line icons drawn on a 24-unit grid, so they share one stroke weight and corner style
 * with the weather art instead of mixing in stock icon sets.
 */
object Glyphs {
    private fun DrawScope.unit() = size.minDimension / 24f
    private fun line(u: Float) = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun DrawScope.seg(color: Color, u: Float, x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)

    private fun DrawScope.poly(color: Color, u: Float, vararg points: Float) {
        val path = Path().apply {
            moveTo(points[0] * u, points[1] * u)
            var i = 2
            while (i < points.size) {
                lineTo(points[i] * u, points[i + 1] * u)
                i += 2
            }
        }
        drawPath(path, color, style = line(u))
    }

    val Calendar: Glyph = { c ->
        val u = unit()
        drawRoundRect(c, Offset(3 * u, 5 * u), Size(18 * u, 16 * u), CornerRadius(3.5f * u), style = line(u))
        seg(c, u, 3f, 10f, 21f, 10f)
        seg(c, u, 8f, 3f, 8f, 7f)
        seg(c, u, 16f, 3f, 16f, 7f)
        drawCircle(c, 1.3f * u, Offset(8.5f * u, 15f * u))
    }

    val Weather: Glyph = { c -> drawWeatherIcon(2, true, c) }

    val Photos: Glyph = { c ->
        val u = unit()
        drawRoundRect(c, Offset(3 * u, 4 * u), Size(18 * u, 16 * u), CornerRadius(3.5f * u), style = line(u))
        poly(c, u, 3.5f, 17.5f, 9f, 12f, 13f, 16f, 15.5f, 13.5f, 20.5f, 18.5f)
        drawCircle(c, 1.7f * u, Offset(15.5f * u, 8.8f * u), style = line(u))
    }

    val Phone: Glyph = { c ->
        val u = unit()
        drawRoundRect(c, Offset(6.5f * u, 2.5f * u), Size(11 * u, 19 * u), CornerRadius(3f * u), style = line(u))
        seg(c, u, 10.5f, 18f, 13.5f, 18f)
    }

    val Gear: Glyph = { c ->
        val u = unit()
        val center = Offset(12 * u, 12 * u)
        drawCircle(c, 6f * u, center, style = line(u))
        drawCircle(c, 2.4f * u, center, style = line(u))
        for (i in 0 until 8) {
            rotate(i * 45f, center) {
                drawLine(
                    c,
                    Offset(12 * u, 3 * u),
                    Offset(12 * u, 5.4f * u),
                    strokeWidth = 2.6f * u,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    val Plus: Glyph = { c ->
        val u = unit()
        seg(c, u, 12f, 5f, 12f, 19f)
        seg(c, u, 5f, 12f, 19f, 12f)
    }

    val ChevronLeft: Glyph = { c -> poly(c, unit(), 14.5f, 6f, 8.5f, 12f, 14.5f, 18f) }
    val ChevronRight: Glyph = { c -> poly(c, unit(), 9.5f, 6f, 15.5f, 12f, 9.5f, 18f) }
    val ChevronDown: Glyph = { c -> poly(c, unit(), 6f, 9.5f, 12f, 15.5f, 18f, 9.5f) }

    val Close: Glyph = { c ->
        val u = unit()
        seg(c, u, 6.5f, 6.5f, 17.5f, 17.5f)
        seg(c, u, 17.5f, 6.5f, 6.5f, 17.5f)
    }

    val Clock: Glyph = { c ->
        val u = unit()
        drawCircle(c, 9f * u, Offset(12 * u, 12 * u), style = line(u))
        poly(c, u, 12f, 7f, 12f, 12f, 15.5f, 14f)
    }

    val Pin: Glyph = { c ->
        val u = unit()
        val path = Path().apply {
            moveTo(12 * u, 21.5f * u)
            cubicTo(7 * u, 16f * u, 5 * u, 12.5f * u, 5 * u, 9.5f * u)
            cubicTo(5 * u, 5.6f * u, 8.1f * u, 2.5f * u, 12 * u, 2.5f * u)
            cubicTo(15.9f * u, 2.5f * u, 19 * u, 5.6f * u, 19 * u, 9.5f * u)
            cubicTo(19 * u, 12.5f * u, 17 * u, 16f * u, 12 * u, 21.5f * u)
            close()
        }
        drawPath(path, c, style = line(u))
        drawCircle(c, 2.4f * u, Offset(12 * u, 9.5f * u), style = line(u))
    }
}
