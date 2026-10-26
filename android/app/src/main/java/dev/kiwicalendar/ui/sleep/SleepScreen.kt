package dev.kiwicalendar.ui.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Game
import dev.kiwicalendar.data.GameSide
import dev.kiwicalendar.data.matches
import dev.kiwicalendar.ui.DottedDivider
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.Occurrence
import dev.kiwicalendar.ui.components.Clock
import dev.kiwicalendar.ui.eventTone
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.glass
import dev.kiwicalendar.ui.occurrence
import dev.kiwicalendar.ui.rememberNow
import dev.kiwicalendar.ui.weather.SleepWeatherCard
import dev.kiwicalendar.ui.weather.temp
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val DateLine = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val GameDay = DateTimeFormatter.ofPattern("EEE")
private val Frosted = Color.Black.copy(alpha = 0.38f)
private val FrostEdge = Color.White.copy(alpha = 0.16f)

private val LeagueNames = mapOf(
    "nfl" to "NFL", "ncaaf" to "College football", "nba" to "NBA", "wnba" to "WNBA",
    "ncaam" to "Men's college basketball", "ncaaw" to "Women's college basketball",
    "mlb" to "MLB", "nhl" to "NHL", "mls" to "MLS", "epl" to "Premier League",
    "laliga" to "La Liga", "ucl" to "Champions League"
)

@Composable
fun SleepScreen(container: AppContainer, settings: AppSettings, quiet: Boolean, onWake: () -> Unit) {
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val scores by container.repository.scores.collectAsStateWithLifecycle()
    val scoresFetchedAt by container.repository.scoresFetchedAt.collectAsStateWithLifecycle()
    val scoresAreFresh = scoresFetchedAt > 0L &&
        System.currentTimeMillis() - scoresFetchedAt < 10 * 60 * 1000L
    val weather by container.repository.weather.collectAsStateWithLifecycle()
    // syncedAt is part of the key because a retry can repair existing photo IDs
    // without changing the server's photo list.
    val photos = remember(snapshot.photos, snapshot.syncedAt) {
        snapshot.photos.map { container.store.photoFile(it.id) }
            .filter { it.isFile && it.length() >= 100L }.shuffled()
    }
    var index by remember { mutableIntStateOf(0) }

    // Repair missing photos when the screensaver starts; no manual app restart needed.
    LaunchedEffect(photos.size, settings.isPaired, quiet) {
        if (!quiet && photos.isEmpty() && settings.isPaired) {
            container.repository.sync()
        }
    }
    LaunchedEffect(photos.size, settings.photoSeconds, quiet) {
        if (quiet || photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(settings.photoSeconds.coerceAtLeast(10) * 1000L)
            index = (index + 1) % photos.size
        }
    }
    BackHandler(onBack = onWake)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onWake
            )
    ) {
        if (quiet) {
            QuietClock(settings, weather?.takeIf { w -> settings.weatherPlace?.let { w.matches(it, settings.units) } == true }?.current?.temp)
        } else {
            if (photos.isNotEmpty()) {
                Crossfade(
                    targetState = photos[index % photos.size],
                    animationSpec = tween(durationMillis = 1600),
                    label = "photo"
                ) { file -> FullBleedPhoto(file, settings.photoSeconds) }
            } else {
                Text(
                    text = if (settings.isPaired) "No downloaded photos. Open Settings → Photos to check sync." else "Connect your calendar in Settings to add photos.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Scrims keep text readable over bright photos without boxing the whole picture.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            )

            weather?.takeIf { w -> settings.weatherPlace?.let { w.matches(it, settings.units) } == true }
                ?.let { SleepWeatherCard(it, settings.use24h, Modifier.align(Alignment.TopStart).padding(36.dp)) }
            if (scores.isNotEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    scores.take(4).forEach { ScoreCard(it, settings.use24h, scoresAreFresh) }
                }
            }
            TimeAndAgenda(
                container,
                settings,
                Modifier.align(Alignment.BottomStart).padding(start = 44.dp, bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun FullBleedPhoto(file: File, seconds: Int) {
    // A slow push-in keeps a still photo from feeling static.
    val zoom = remember(file) { Animatable(1f) }
    LaunchedEffect(file) {
        zoom.animateTo(1.07f, tween(durationMillis = seconds.coerceAtLeast(10) * 1000 + 1600, easing = LinearEasing))
    }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.value
                scaleY = zoom.value
            }
    )
}

@Composable
private fun QuietClock(settings: AppSettings, temperature: Double?) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Clock(
            use24h = settings.use24h,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 136.sp),
            color = Color(0xFFA8ADB7)
        )
        if (temperature != null) {
            Text(temp(temperature), style = MaterialTheme.typography.headlineMedium, color = Color(0xFF969BA5))
        }
    }
}

@Composable
private fun TimeAndAgenda(container: AppContainer, settings: AppSettings, modifier: Modifier) {
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val now by rememberNow()
    val zone = remember { ZoneId.systemDefault() }
    val upcoming = remember(snapshot.events, now) {
        val today = now.toLocalDate()
        val horizon = now.plusHours(18)
        snapshot.events
            .mapNotNull { it.occurrence(zone) }
            .filter {
                if (it.allDay) {
                    !today.isBefore(it.firstDay) && !today.isAfter(it.lastDay)
                } else {
                    it.end.isAfter(now) && it.start.isBefore(horizon)
                }
            }
            .sortedWith(compareBy<Occurrence>({ !it.allDay }, { it.start }))
            .take(3)
    }
    Column(modifier) {
        Clock(
            use24h = settings.use24h,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 128.sp, fontWeight = FontWeight.Light),
            color = Color.White
        )
        Text(
            now.format(DateLine),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
            color = Color.White.copy(alpha = 0.9f)
        )
        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Column(
                Modifier
                    .width(460.dp)
                    .glass(RoundedCornerShape(24.dp), fill = Frosted, border = FrostEdge)
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Text("Up next", style = MaterialTheme.typography.labelLarge, color = Mono.Muted)
                Spacer(Modifier.height(10.dp))
                upcoming.forEach { occurrence ->
                    val label = when {
                        occurrence.allDay -> "Today"
                        occurrence.start.isBefore(now) -> "Now"
                        occurrence.firstDay != now.toLocalDate() -> "Tomorrow"
                        else -> formatTime(occurrence.start.toLocalTime(), settings.use24h)
                    }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(
                                3.dp
                            ).height(22.dp).clip(CircleShape).background(eventTone(occurrence.event.color))
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.width(96.dp)
                        )
                        Text(
                            occurrence.event.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(game: Game, use24h: Boolean, fresh: Boolean) {
    Column(
        Modifier
            .width(320.dp)
            .glass(RoundedCornerShape(24.dp), fill = Frosted, border = FrostEdge)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LeagueNames[game.league] ?: game.league.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.weight(1f)
            )
            if (game.state == "in" && fresh) {
                LiveDot()
                Spacer(Modifier.width(8.dp))
            }
            Text(if (fresh) status(game, use24h) else if (game.state == "pre") "Saved schedule" else "Saved score", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        DottedDivider(color = Color.White.copy(alpha = 0.18f))
        Spacer(Modifier.height(12.dp))
        TeamLine(game.away, game)
        Spacer(Modifier.height(10.dp))
        TeamLine(game.home, game)
    }
}

@Composable
private fun LiveDot() {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(Color.White))
}

@Composable
private fun TeamLine(side: GameSide?, game: Game) {
    if (side == null) return
    val other = if (side === game.home) game.away else game.home
    val mine = side.score?.toIntOrNull()
    val theirs = other?.score?.toIntOrNull()
    val dimmed = game.state == "post" && mine != null && theirs != null && mine < theirs
    val ink = if (dimmed) Color.White.copy(alpha = 0.78f) else Color.White
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(
                width = 46.dp,
                height = 30.dp
            ).glass(RoundedCornerShape(9.dp), fill = Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(side.abbr.ifEmpty { side.name.take(3) }, style = MaterialTheme.typography.labelLarge, color = ink)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = side.name,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (game.state != "pre") {
            Text(side.score ?: "", style = MaterialTheme.typography.headlineSmall, color = ink)
        }
    }
}

private fun status(game: Game, use24h: Boolean): String {
    if (game.state != "pre") return game.detail
    val start = runCatching { OffsetDateTime.parse(game.start).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
        ?: return game.detail
    val time = formatTime(start.toLocalTime(), use24h)
    val today = LocalDate.now()
    return when (start.toLocalDate()) {
        today -> time
        today.plusDays(1) -> "Tomorrow $time"
        else -> start.format(GameDay) + " " + time
    }
}
