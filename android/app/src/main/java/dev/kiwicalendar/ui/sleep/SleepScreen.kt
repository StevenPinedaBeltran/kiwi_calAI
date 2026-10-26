package dev.kiwicalendar.ui.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Game
import dev.kiwicalendar.data.GameSide
import dev.kiwicalendar.data.matches
import dev.kiwicalendar.data.isRecentScore
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
    // Keep an independent minute clock so offline cached scores expire automatically.
    val currentTime by rememberNow()
    val recentScores = scores.filter { it.isRecentScore(currentTime.atZone(ZoneId.systemDefault()).toInstant()) }
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

    // long-press = photos only on/off
    val photosOnly = settings.sleepPhotosOnly
    var hint by remember { mutableStateOf("") }
    var hintVisible by remember { mutableStateOf(false) }
    var hintToken by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val wake by rememberUpdatedState(onWake)
    // only touches remembered state + the live setting, so it's fine inside pointerInput(Unit)
    val togglePhotosOnly: () -> Unit = {
        val next = !container.settings.value.sleepPhotosOnly
        container.settings.update { it.copy(sleepPhotosOnly = next) }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        hint = if (next) "Photos only. Long-press to get the widgets back." else "Widgets back on"
        hintVisible = true
        hintToken++
    }
    LaunchedEffect(hintToken) {
        if (!hintVisible) return@LaunchedEffect
        delay(2_500)
        hintVisible = false
    }

    val sleepWeather = weather?.takeIf { w -> settings.weatherPlace?.let { w.matches(it, settings.units) } == true }
    val showWeather = !photosOnly && settings.sleepShowWeather && sleepWeather != null
    val showScores = !photosOnly && settings.sleepShowScores && recentScores.isNotEmpty()
    val showClock = !photosOnly && settings.sleepShowClock
    val showAgenda = !photosOnly && settings.sleepShowAgenda

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
            .pointerInput(Unit) {
                detectTapGestures(onTap = { wake() }, onLongPress = { togglePhotosOnly() })
            }
    ) {
        if (quiet) {
            QuietClock(settings, sleepWeather?.current?.temp)
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
            // scrims keep text readable on bright photos, and fade out with whatever sits on them
            AnimatedVisibility(
                visible = showWeather || showScores,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                )
            }
            AnimatedVisibility(
                visible = showClock || showAgenda,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                )
            }

            AnimatedVisibility(
                visible = showWeather,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopStart).padding(36.dp)
            ) {
                sleepWeather?.let { SleepWeatherCard(it, settings.use24h) }
            }
            AnimatedVisibility(
                visible = showScores,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopEnd).padding(36.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentScores.take(4).forEach { ScoreCard(it, settings.use24h, scoresAreFresh) }
                }
            }
            AnimatedVisibility(
                visible = showClock || showAgenda,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 44.dp, bottom = 40.dp)
            ) {
                TimeAndAgenda(container, settings, showClock, showAgenda, Modifier)
            }
        }

        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .glass(RoundedCornerShape(50), fill = Frosted, border = FrostEdge)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
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
private fun TimeAndAgenda(
    container: AppContainer,
    settings: AppSettings,
    showClock: Boolean,
    showAgenda: Boolean,
    modifier: Modifier
) {
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
        if (showClock) {
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
        }
        if (showAgenda && upcoming.isNotEmpty()) {
            if (showClock) Spacer(Modifier.height(22.dp))
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

/** Compact scoreboard with CDN logos and legible fallbacks, designed for the 18-inch display. */
@Composable
private fun ScoreCard(game: Game, use24h: Boolean, fresh: Boolean) {
    Column(
        Modifier
            .width(340.dp)
            .glass(
                RoundedCornerShape(24.dp),
                fill = Color(0xFF101B2B).copy(alpha = 0.92f),
                border = Color.White.copy(alpha = 0.22f)
            )
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                LeagueNames[game.league] ?: game.league.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.weight(1f)
            )
            if (game.state == "in" && fresh) {
                LiveDot()
                Spacer(Modifier.width(6.dp))
            }
            val result = when {
                !fresh -> "SAVED"
                game.state == "in" -> "LIVE"
                else -> "FINAL"
            }
            Box(
                Modifier.clip(CircleShape)
                    .background(if (game.state == "in" && fresh) Color(0xFF194E3B) else Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(result, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScoreboardTeam(game.away, Modifier.weight(1f))
            Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${game.away?.score ?: "–"}  –  ${game.home?.score ?: "–"}",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp),
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (fresh) status(game, use24h) else "Last saved result",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD2DCE9),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ScoreboardTeam(game.home, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreboardTeam(side: GameSide?, modifier: Modifier = Modifier) {
    if (side == null) return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val logo = side.logo?.takeIf { candidate ->
            runCatching {
                val uri = java.net.URI(candidate)
                uri.scheme == "https" && (uri.host == "espncdn.com" || uri.host?.endsWith(".espncdn.com") == true)
            }.getOrDefault(false)
        }
        Box(
            Modifier.size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            if (logo == null) TeamInitials(side)
            else SubcomposeAsyncImage(
                model = logo,
                contentDescription = "${side.name} logo",
                contentScale = ContentScale.Fit,
                loading = { TeamInitials(side) },
                error = { TeamInitials(side) },
                modifier = Modifier.size(43.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            side.abbr.ifEmpty { side.name.take(3) },
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Text(
            side.name,
            color = Color(0xFFD1DCE8),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TeamInitials(side: GameSide) {
    Text(
        side.abbr.ifEmpty { side.name.take(3) },
        style = MaterialTheme.typography.labelSmall,
        color = Color.White
    )
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
