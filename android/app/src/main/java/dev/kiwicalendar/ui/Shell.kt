package dev.kiwicalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiwicalendar.R
import dev.kiwicalendar.data.WeatherReport
import dev.kiwicalendar.ui.components.Clock
import dev.kiwicalendar.ui.weather.WeatherPill
import java.time.LocalTime

enum class Destination { Calendar, Weather, Photos, Phone, Settings }

fun greeting(now: LocalTime, name: String): String {
    val part = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, ${name.trim()}"
}

/** Sidebar, header, and a content area. Every screen except sleep mode lives inside this. */
@Composable
fun Shell(
    current: Destination,
    title: String,
    subtitle: String,
    weather: WeatherReport?,
    use24h: Boolean,
    onNavigate: (Destination) -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .safeDrawingPadding()
    ) {
        Sidebar(current, onNavigate)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 26.dp, end = 28.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Mono.Muted, maxLines = 1)
                }
                WeatherPill(weather, onClick = { onNavigate(Destination.Weather) })
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .height(58.dp)
                        .glass(CircleShape)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Clock(use24h, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun Sidebar(current: Destination, onNavigate: (Destination) -> Unit) {
    Column(
        Modifier
            .width(100.dp)
            .fillMaxHeight()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Kiwi Calendar",
            tint = Mono.Ink,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .glass(RoundedCornerShape(34.dp), fill = Mono.Ink.copy(alpha = 0.035f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RoundButton(
                Glyphs.Calendar,
                "Calendar",
                { onNavigate(Destination.Calendar) },
                52.dp,
                current == Destination.Calendar
            )
            RoundButton(
                Glyphs.Weather,
                "Weather",
                { onNavigate(Destination.Weather) },
                52.dp,
                current == Destination.Weather
            )
            RoundButton(Glyphs.Photos, "Show photos", { onNavigate(Destination.Photos) }, 52.dp)
            RoundButton(Glyphs.Phone, "Add from phone", { onNavigate(Destination.Phone) }, 52.dp)
        }
        Spacer(Modifier.weight(1f))
        RoundButton(
            Glyphs.Gear,
            "Settings",
            { onNavigate(Destination.Settings) },
            52.dp,
            current == Destination.Settings
        )
    }
}
