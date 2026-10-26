package dev.kiwicalendar.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiwicalendar.data.WeatherDay
import dev.kiwicalendar.data.WeatherHour
import dev.kiwicalendar.data.WeatherReport
import dev.kiwicalendar.ui.DottedDivider
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.Outfit
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.glass
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun temp(value: Double?): String = value?.let { "${it.roundToInt()}°" } ?: "–"

fun localTime(stamp: String?): LocalDateTime? = stamp?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
}

fun cardinal(degrees: Double?): String {
    if (degrees == null) return ""
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[((degrees % 360 + 360) % 360 / 45.0).roundToInt() % 8]
}

fun uvLabel(uv: Double?): String = when {
    uv == null -> ""
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

fun aqiLabel(aqi: Double?): String = when {
    aqi == null -> ""
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for some"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

fun updatedLabel(report: WeatherReport): String {
    if (report.fetchedAt == 0L) return ""
    val minutes = (System.currentTimeMillis() - report.fetchedAt) / 60_000
    return when {
        minutes < 2 -> "Updated just now"
        minutes < 60 -> "Updated $minutes min ago"
        minutes < 24 * 60 -> "Updated ${minutes / 60} hr ago"
        else -> "Updated over a day ago"
    }
}

/** Compact current-conditions pill for the dashboard header. */
@Composable
fun WeatherPill(report: WeatherReport?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(58.dp)
            .glass(CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (report == null) {
            WeatherIcon(2, true, Modifier.size(28.dp), Mono.Faint)
            Spacer(Modifier.width(12.dp))
            Text("Add your location", style = MaterialTheme.typography.labelLarge, color = Mono.Muted)
        } else {
            val now = report.current
            WeatherIcon(now.code, now.isDay, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(temp(now.temp), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(now.condition, style = MaterialTheme.typography.labelLarge)
                report.daily.firstOrNull()?.let {
                    Text(
                        "H ${temp(it.high)}   L ${temp(it.low)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mono.Muted
                    )
                }
            }
        }
    }
}

/** Dark atmospheric smart-display card, readable over photos in either system theme. */
@Composable
fun SleepWeatherCard(report: WeatherReport, use24h: Boolean, modifier: Modifier = Modifier) {
    val now = report.current
    val today = report.daily.firstOrNull()
    val ink = Color.White
    val secondary = Color(0xFFDCE8F7)
    Column(
        modifier.width(460.dp)
            .glass(
                RoundedCornerShape(30.dp),
                fill = Color(0xFF142941).copy(alpha = 0.94f),
                border = Color.White.copy(alpha = 0.23f)
            )
            .padding(23.dp)
    ) {
        Text("LOCAL FORECAST", color = secondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(now.code, now.isDay, Modifier.size(76.dp), ink)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(temp(now.temp), style = MaterialTheme.typography.displayLarge.copy(fontSize = 70.sp), color = ink)
                Text(now.condition, style = MaterialTheme.typography.titleLarge, color = ink)
                Text("Feels like ${temp(now.feelsLike)}", color = secondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (today != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("H ${temp(today.high)}", color = ink, style = MaterialTheme.typography.titleMedium)
                    Text("L ${temp(today.low)}", color = secondary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RAIN", color = secondary, style = MaterialTheme.typography.labelSmall)
                Text("${today?.precipProb?.roundToInt()?.let { "$it%" } ?: "–"}", color = ink, style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HUMIDITY", color = secondary, style = MaterialTheme.typography.labelSmall)
                Text("${now.humidity?.roundToInt()?.let { "$it%" } ?: "–"}", color = ink, style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WIND", color = secondary, style = MaterialTheme.typography.labelSmall)
                Text(now.windSpeed?.roundToInt()?.let { "$it ${report.labels.speed}" } ?: "–", color = ink, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(15.dp))
        Text("NEXT HOURS", color = secondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            report.hourly.take(6).forEachIndexed { index, hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (index == 0) "Now" else localTime(hour.time)?.let { formatTime(it.toLocalTime(), use24h) } ?: "–",
                        style = MaterialTheme.typography.labelSmall, color = secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    WeatherIcon(hour.code, hour.isDay, Modifier.size(28.dp), ink)
                    Spacer(Modifier.height(7.dp))
                    Text(temp(hour.temp), style = MaterialTheme.typography.titleSmall, color = ink)
                }
            }
        }
    }
}

@Composable
fun WindCompass(direction: Double?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Outfit, fontSize = 10.sp, color = Mono.Faint)
    Canvas(modifier) {
        val radius = size.minDimension / 2 - 2.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Mono.Border, radius, center, style = Stroke(1.dp.toPx()))
        for (i in 0 until 36) {
            val long = i % 9 == 0
            rotate(i * 10f, center) {
                drawLine(
                    color = if (long) Mono.Ink else Mono.Faint,
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y - radius + (if (long) 7.dp else 4.dp).toPx()),
                    strokeWidth = (if (long) 1.6.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (text, angle) ->
            val layout = measurer.measure(text, labelStyle)
            val r = radius - 16.dp.toPx()
            val rad = (angle - 90f) * PI.toFloat() / 180f
            drawText(
                layout,
                topLeft = Offset(
                    center.x + r * cos(rad) - layout.size.width / 2f,
                    center.y + r * sin(rad) - layout.size.height / 2f
                )
            )
        }
        if (direction != null) {
            // Meteorological direction is where wind comes from; the arrow points where it's going.
            rotate(direction.toFloat() + 180f, center) {
                val tip = Offset(center.x, center.y - radius * 0.62f)
                val tail = Offset(center.x, center.y + radius * 0.5f)
                drawLine(Mono.Ink, tail, tip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 2.dp.toPx())
                    lineTo(tip.x - 5.dp.toPx(), tip.y + 7.dp.toPx())
                    lineTo(tip.x + 5.dp.toPx(), tip.y + 7.dp.toPx())
                    close()
                }
                drawPath(head, Mono.Ink)
                drawCircle(Mono.Ink, 3.dp.toPx(), tail)
            }
        }
    }
}

/** Half-ring gauge, used for UV index and air quality. */
@Composable
fun ArcGauge(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 7.dp.toPx()
        val diameter = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(Mono.CardStrong, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val sweep = 180f * fraction.coerceIn(0f, 1f)
        drawArc(
            brush = Brush.horizontalGradient(listOf(Mono.Ink.copy(alpha = 0.45f), Mono.Ink)),
            startAngle = 180f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val angle = (180f + sweep) * PI.toFloat() / 180f
        val r = diameter / 2
        val center = Offset(topLeft.x + r, topLeft.y + r)
        drawCircle(Mono.Ink, stroke * 0.8f, Offset(center.x + r * cos(angle), center.y + r * sin(angle)))
        drawCircle(Mono.Paper, stroke * 0.35f, Offset(center.x + r * cos(angle), center.y + r * sin(angle)))
    }
}

/** Ten-segment meter for humidity. */
@Composable
fun SegmentMeter(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val segment = (size.width - gap * 9) / 10
        val lit = (fraction.coerceIn(0f, 1f) * 10).roundToInt()
        for (i in 0 until 10) {
            drawRoundRect(
                color = if (i < lit) Mono.Ink else Mono.CardStrong,
                topLeft = Offset(i * (segment + gap), 0f),
                size = Size(segment, size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
    }
}

/** Twenty-four hours as a temperature line with icons, times, and chance of rain. */
@Composable
fun HourlyChart(hours: List<WeatherHour>, use24h: Boolean, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val small = TextStyle(fontFamily = Outfit, fontSize = 12.sp, color = Mono.Muted)
    val value = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = Mono.Ink)
    val column = 68.dp
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Canvas(Modifier.width(column * hours.size).height(210.dp)) {
            val w = column.toPx()
            val temps = hours.map { it.temp ?: 0.0 }
            val low = temps.min()
            val high = temps.max()
            val span = (high - low).takeIf { it > 0.5 } ?: 1.0
            val lineTop = 92.dp.toPx()
            val lineBottom = 150.dp.toPx()
            fun y(t: Double) = (lineBottom - (t - low) / span * (lineBottom - lineTop)).toFloat()

            val points = temps.mapIndexed { i, t -> Offset(w * i + w / 2, y(t)) }
            val curve = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val midX = (prev.x + cur.x) / 2
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }
            val fill = Path().apply {
                addPath(curve)
                lineTo(points.last().x, size.height - 30.dp.toPx())
                lineTo(points.first().x, size.height - 30.dp.toPx())
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(Mono.Ink.copy(alpha = 0.14f), Color.Transparent),
                    startY = lineTop,
                    endY =
                    size.height - 30.dp.toPx()
                )
            )
            drawPath(curve, Mono.Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            hours.forEachIndexed { i, hour ->
                val x = w * i + w / 2
                val label = if (i ==
                    0
                ) {
                    "Now"
                } else {
                    localTime(hour.time)?.let { formatTime(it.toLocalTime(), use24h) } ?: ""
                }
                val time = measurer.measure(label, small)
                drawText(time, topLeft = Offset(x - time.size.width / 2f, 0f))
                val iconSide = 26.dp.toPx()
                drawWeatherIcon(hour.code, hour.isDay, Mono.Ink, Offset(x - iconSide / 2, 22.dp.toPx()), iconSide)
                val t = measurer.measure(temp(hour.temp), value)
                drawText(t, topLeft = Offset(x - t.size.width / 2f, points[i].y - t.size.height - 8.dp.toPx()))
                drawCircle(Mono.Paper, 4.dp.toPx(), points[i])
                drawCircle(Mono.Ink, 4.dp.toPx(), points[i], style = Stroke(2.dp.toPx()))

                val chance = hour.precipProb?.roundToInt() ?: 0
                val barMax = 22.dp.toPx()
                val barTop = size.height - 8.dp.toPx() - barMax * chance / 100f
                drawRoundRect(
                    color = Mono.CardStrong,
                    topLeft = Offset(x - 10.dp.toPx(), size.height - 8.dp.toPx() - barMax),
                    size = Size(20.dp.toPx(), barMax),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                if (chance > 0) {
                    drawRoundRect(
                        color = Mono.Ink.copy(alpha = 0.75f),
                        topLeft = Offset(x - 10.dp.toPx(), barTop),
                        size = Size(20.dp.toPx(), size.height - 8.dp.toPx() - barTop),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
                if (chance >= 20) {
                    val p = measurer.measure("$chance%", small)
                    drawText(
                        p,
                        topLeft = Offset(
                            x - p.size.width / 2f,
                            size.height - 8.dp.toPx() - barMax - p.size.height - 2.dp.toPx()
                        )
                    )
                }
            }
        }
    }
}

/** One row of the 7-day forecast, with the day's range drawn against the whole week's. */
@Composable
fun DayRow(day: WeatherDay, weekLow: Double, weekHigh: Double, isToday: Boolean, currentTemp: Double?) {
    val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
    val name = when {
        isToday -> "Today"
        date != null -> date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
        else -> ""
    }
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(78.dp))
        WeatherIcon(day.code, true, Modifier.size(28.dp))
        val chance = day.precipProb?.roundToInt() ?: 0
        Text(
            text = if (chance >= 10) "$chance%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = Mono.Muted,
            modifier = Modifier.width(52.dp).padding(start = 8.dp)
        )
        Text(temp(day.low), color = Mono.Muted, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val span = (weekHigh - weekLow).takeIf { it > 0 } ?: 1.0
            val start = (((day.low ?: weekLow) - weekLow) / span).toFloat() * size.width
            val end = (((day.high ?: weekHigh) - weekLow) / span).toFloat() * size.width
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(Mono.CardStrong, cornerRadius = radius)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Mono.Ink.copy(alpha = 0.5f), Mono.Ink),
                    startX = start,
                    endX = end
                ),
                topLeft = Offset(start, 0f),
                size = Size((end - start).coerceAtLeast(size.height), size.height),
                cornerRadius = radius
            )
            if (isToday && currentTemp != null) {
                val x = ((currentTemp - weekLow) / span).toFloat().coerceIn(0f, 1f) * size.width
                drawCircle(Mono.Paper, size.height * 0.9f, Offset(x, size.height / 2))
                drawCircle(Mono.Ink, size.height * 0.55f, Offset(x, size.height / 2))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(temp(day.high), textAlign = TextAlign.Start, modifier = Modifier.width(44.dp))
    }
}

/** The sun's path from sunrise to sunset, with its position right now. */
@Composable
fun SunArc(day: WeatherDay, use24h: Boolean, modifier: Modifier = Modifier) {
    val rise = localTime(day.sunrise)
    val set = localTime(day.sunset)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val stroke = 2.dp.toPx()
            val diameter = minOf(size.width - 24.dp.toPx(), (size.height - 12.dp.toPx()) * 2)
            val topLeft = Offset((size.width - diameter) / 2, size.height - 6.dp.toPx() - diameter / 2)
            val arcSize = Size(diameter, diameter)
            drawLine(
                Mono.Border,
                Offset(0f, topLeft.y + diameter / 2),
                Offset(size.width, topLeft.y + diameter / 2),
                strokeWidth = 1.dp.toPx()
            )
            drawArc(
                Mono.Faint,
                180f,
                180f,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
            )
            if (rise != null && set != null) {
                val now = LocalDateTime.now()
                val total = Duration.between(rise, set).seconds.toFloat().coerceAtLeast(1f)
                val fraction = (Duration.between(rise, now).seconds / total).coerceIn(0f, 1f)
                drawArc(
                    Mono.Ink,
                    180f,
                    180f * fraction,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val angle = (180f + 180f * fraction) * PI.toFloat() / 180f
                val r = diameter / 2
                val center = Offset(topLeft.x + r, topLeft.y + r)
                val sun = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
                drawCircle(Mono.Ink.copy(alpha = 0.15f), 14.dp.toPx(), sun)
                drawCircle(Mono.Ink, 7.dp.toPx(), sun)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Sunrise", style = MaterialTheme.typography.labelMedium, color = Mono.Muted)
                Text(
                    rise?.let {
                        formatTime(it.toLocalTime(), use24h)
                    } ?: "–",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Sunset", style = MaterialTheme.typography.labelMedium, color = Mono.Muted)
                Text(
                    set?.let {
                        formatTime(it.toLocalTime(), use24h)
                    } ?: "–",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        if (rise != null && set != null) {
            val minutes = Duration.between(rise, set).toMinutes()
            Spacer(Modifier.height(8.dp))
            Text(
                "${minutes / 60} hr ${minutes % 60} min of daylight",
                style = MaterialTheme.typography.bodyMedium,
                color = Mono.Muted
            )
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(CircleShape).background(Mono.CardStrong).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
