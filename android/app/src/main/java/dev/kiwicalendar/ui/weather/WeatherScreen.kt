package dev.kiwicalendar.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.WeatherReport
import dev.kiwicalendar.data.matches
import dev.kiwicalendar.ui.GlyphIcon
import dev.kiwicalendar.ui.Glyphs
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.PrimaryButton
import dev.kiwicalendar.ui.glass
import java.time.LocalDate
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(26.dp)

@Composable
fun WeatherContent(container: AppContainer, settings: AppSettings, onOpenSettings: () -> Unit) {
    val report by container.repository.weather.collectAsStateWithLifecycle()
    val data = report?.takeIf { r -> settings.weatherPlace?.let { r.matches(it, settings.units) } == true }
    if (settings.weatherPlace == null || data == null) {
        Column(
            Modifier.fillMaxSize().glass(CardShape, fill = Mono.Card),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeatherIcon(2, true, Modifier.size(96.dp), Mono.Faint)
            Spacer(Modifier.height(20.dp))
            Text(
                if (settings.weatherPlace == null) "Choose a location to see the forecast" else "Loading the forecast",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(20.dp))
            if (settings.weatherPlace == null) PrimaryButton("Choose location", onOpenSettings)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(310.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CurrentCard(data, settings, Modifier.weight(1f).fillMaxHeight())
            DetailTiles(data, Modifier.weight(1.55f).fillMaxHeight())
        }
        Column(Modifier.fillMaxWidth().glass(CardShape).padding(22.dp)) {
            CardTitle("Next 24 hours")
            Spacer(Modifier.height(16.dp))
            HourlyChart(data.hourly, settings.use24h, Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeekCard(data, Modifier.weight(1.55f))
            data.daily.firstOrNull()?.let { today ->
                Column(Modifier.weight(1f).glass(CardShape).padding(22.dp)) {
                    CardTitle("Sun")
                    Spacer(Modifier.height(10.dp))
                    SunArc(today, settings.use24h, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(18.dp))
                    val rain = today.precipSum ?: 0.0
                    Stat("Rain today", if (rain > 0) "$rain ${data.labels.precip}" else "None expected")
                    Stat("Max UV", today.uvMax?.let { "${it.roundToInt()}  ${uvLabel(it)}" } ?: "–")
                    Stat("Max wind", today.windMax?.let { "${it.roundToInt()} ${data.labels.speed}" } ?: "–")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Mono.Muted)
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Mono.Muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CurrentCard(report: WeatherReport, settings: AppSettings, modifier: Modifier) {
    val now = report.current
    val today = report.daily.firstOrNull()
    Column(modifier.glass(CardShape, fill = Mono.CardStrong).padding(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyphs.Pin, 18.dp, Mono.Muted)
            Spacer(Modifier.width(8.dp))
            Text(
                settings.weatherPlace?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(updatedLabel(report), style = MaterialTheme.typography.labelMedium, color = Mono.Faint)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(now.code, now.isDay, Modifier.size(104.dp))
            Spacer(Modifier.width(22.dp))
            Text(temp(now.temp), style = MaterialTheme.typography.displayLarge.copy(fontSize = 104.sp))
        }
        Spacer(Modifier.weight(1f))
        Text(now.condition, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        val range = today?.let { "   H ${temp(it.high)}   L ${temp(it.low)}" }.orEmpty()
        Text("Feels like ${temp(now.feelsLike)}$range", style = MaterialTheme.typography.bodyLarge, color = Mono.Muted)
    }
}

@Composable
private fun Tile(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glass(RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Mono.Muted)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun BigValue(value: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp))
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodyMedium,
                color = Mono.Muted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun DetailTiles(report: WeatherReport, modifier: Modifier) {
    val now = report.current
    val labels = report.labels
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("Wind", Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BigValue(now.windSpeed?.roundToInt()?.toString() ?: "–", labels.speed)
                        Text(
                            "From ${cardinal(now.windDir)}" +
                                (now.windGusts?.let { ", gusts ${it.roundToInt()}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mono.Muted
                        )
                    }
                    WindCompass(now.windDir, Modifier.size(78.dp))
                }
            }
            Tile("Humidity", Modifier.weight(1f).fillMaxHeight()) {
                BigValue(now.humidity?.roundToInt()?.let { "$it%" } ?: "–")
                Spacer(Modifier.weight(1f))
                SegmentMeter(((now.humidity ?: 0.0) / 100).toFloat(), Modifier.fillMaxWidth().height(8.dp))
                Spacer(Modifier.height(10.dp))
                Text("Dew point ${temp(now.dewPoint)}", style = MaterialTheme.typography.bodyMedium, color = Mono.Muted)
            }
            Tile("UV index", Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    ArcGauge(((now.uv ?: 0.0) / 11).toFloat(), Modifier.fillMaxSize())
                    Text(now.uv?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    uvLabel(now.uv),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val aqi = report.air?.usAqi
            Tile("Air quality", Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    ArcGauge(((aqi ?: 0.0) / 300).toFloat(), Modifier.fillMaxSize())
                    Text(aqi?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (aqi == null) "Not available here" else aqiLabel(aqi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Tile("Pressure", Modifier.weight(1f).fillMaxHeight()) {
                BigValue(
                    now.pressure?.let {
                        if (report.units ==
                            "imperial"
                        ) {
                            "%.2f".format(it)
                        } else {
                            it.roundToInt().toString()
                        }
                    }
                        ?: "–",
                    labels.pressure
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "Cloud cover ${now.cloudCover?.roundToInt() ?: "–"}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted
                )
            }
            Tile("Visibility", Modifier.weight(1f).fillMaxHeight()) {
                val visibility = now.visibility
                BigValue(
                    visibility?.let {
                        if (it >=
                            10
                        ) {
                            it.roundToInt().toString()
                        } else {
                            "%.1f".format(it)
                        }
                    } ?: "–",
                    labels.distance
                )
                Spacer(Modifier.weight(1f))
                val clarity = when {
                    visibility == null -> ""
                    visibility >= 6 -> "Clear view"
                    visibility >= 2 -> "Some haze"
                    else -> "Poor visibility"
                }
                Text(clarity, style = MaterialTheme.typography.bodyMedium, color = Mono.Muted)
            }
        }
    }
}

@Composable
private fun WeekCard(report: WeatherReport, modifier: Modifier) {
    val days = report.daily
    val weekLow = days.mapNotNull { it.low }.minOrNull() ?: 0.0
    val weekHigh = days.mapNotNull { it.high }.maxOrNull() ?: 1.0
    val todayDate = LocalDate.now().toString()
    Column(modifier.glass(CardShape).padding(22.dp)) {
        CardTitle("7 days")
        Spacer(Modifier.height(8.dp))
        days.forEach { day ->
            DayRow(day, weekLow, weekHigh, isToday = day.date == todayDate, currentTemp = report.current.temp)
        }
    }
}
