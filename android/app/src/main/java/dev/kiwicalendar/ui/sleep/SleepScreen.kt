package dev.kiwicalendar.ui.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Game
import dev.kiwicalendar.data.GameSide
import dev.kiwicalendar.ui.Occurrence
import dev.kiwicalendar.ui.components.Clock
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.occurrence
import dev.kiwicalendar.ui.parseHexColor
import dev.kiwicalendar.ui.rememberNow
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val DateLine = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val GameDay = DateTimeFormatter.ofPattern("EEE")
private const val SCORE_REFRESH_MS = 2 * 60 * 1000L
private val LiveRed = Color(0xFFE5484D)

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
    val photos = remember(snapshot.photos) {
        snapshot.photos.map { container.store.photoFile(it.id) }.shuffled()
    }
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(photos.size, settings.photoSeconds, quiet) {
        if (quiet || photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(settings.photoSeconds * 1000L)
            index = (index + 1) % photos.size
        }
    }
    LaunchedEffect(settings.teams, quiet) {
        if (quiet) return@LaunchedEffect
        while (true) {
            container.repository.refreshScores()
            delay(SCORE_REFRESH_MS)
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
            Clock(
                use24h = settings.use24h,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
                color = Color(0xFF59625D),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            if (photos.isNotEmpty()) {
                Crossfade(
                    targetState = photos[index % photos.size],
                    animationSpec = tween(durationMillis = 1500),
                    label = "photo"
                ) { file -> PhotoLayer(file) }
            } else {
                Text(
                    text = "No photos yet. Add some from Settings.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                    )
            )
            TimeAndAgenda(container, settings, Modifier.align(Alignment.BottomStart).padding(40.dp))
            if (scores.isNotEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    scores.take(4).forEach { ScoreCard(it, settings.use24h) }
                }
            }
        }
    }
}

@Composable
private fun PhotoLayer(file: File) {
    val context = LocalContext.current
    // A tiny decode, blurred and stretched, fills the bars around photos that don't match the screen shape.
    val backdrop = remember(file) { ImageRequest.Builder(context).data(file).size(160).build() }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(36.dp).alpha(0.55f)
        )
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
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
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 104.sp),
            color = Color.White
        )
        Text(
            now.format(DateLine),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.9f)
        )
        if (upcoming.isNotEmpty()) Spacer(Modifier.size(16.dp))
        upcoming.forEach { occurrence ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                val label = when {
                    occurrence.allDay -> "Today"
                    occurrence.start.isBefore(now) -> "Now"
                    occurrence.firstDay != now.toLocalDate() ->
                        "Tomorrow " +
                            formatTime(occurrence.start.toLocalTime(), settings.use24h)
                    else -> formatTime(occurrence.start.toLocalTime(), settings.use24h)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.width(150.dp)
                )
                Text(
                    text = occurrence.event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(game: Game, use24h: Boolean) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.width(300.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LeagueNames[game.league] ?: game.league.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                )
                if (game.state == "in") {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                    Spacer(Modifier.width(6.dp))
                }
                Text(status(game, use24h), style = MaterialTheme.typography.labelLarge)
            }
            TeamLine(game.away, game)
            TeamLine(game.home, game)
        }
    }
}

@Composable
private fun TeamLine(side: GameSide?, game: Game) {
    if (side == null) return
    val other = if (side === game.home) game.away else game.home
    val mine = side.score?.toIntOrNull()
    val theirs = other?.score?.toIntOrNull()
    val won = game.state == "post" && mine != null && theirs != null && mine > theirs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(parseHexColor(side.color) ?: Color.Gray)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = side.name.ifEmpty { side.abbr },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (won || game.state != "post") FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (game.state != "pre") {
            Text(side.score ?: "", style = MaterialTheme.typography.titleLarge)
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
