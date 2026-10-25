package dev.kiwicalendar.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.League
import dev.kiwicalendar.data.Team
import dev.kiwicalendar.data.TeamRef
import dev.kiwicalendar.ui.DeviceControls
import dev.kiwicalendar.ui.components.ChoiceChips
import dev.kiwicalendar.ui.components.QrCode
import dev.kiwicalendar.ui.components.SettingRow
import dev.kiwicalendar.ui.components.TimePickerDialog
import dev.kiwicalendar.ui.components.webLink
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.minutesToTime
import dev.kiwicalendar.ui.parseHexColor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun normalizeServerUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    return if ("://" in trimmed) trimmed else "http://$trimmed"
}

@Composable
fun ServerSection(container: AppContainer) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf(settings.serverUrl.removePrefix("http://")) }
    var token by rememberSaveable { mutableStateOf(settings.token) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Server address") },
            placeholder = { Text("192.168.1.20:8080") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            label = { Text("Access token") },
            placeholder = { Text("abcd-efgh-jkmn-pqrs") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && address.isNotBlank() && token.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val url = normalizeServerUrl(address)
                        try {
                            container.api.check(url, token)
                            container.settings.update { it.copy(serverUrl = url, token = token) }
                            container.repository.launchSync()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Couldn't connect: ${e.message ?: e.javaClass.simpleName}"
                        }
                        busy = false
                    }
                }
            ) { Text("Connect") }
            Spacer(Modifier.width(16.dp))
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                settings.isPaired -> Text(
                    "Connected to ${settings.serverUrl}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            "The token is printed when the server starts. Run docker compose logs kiwi to see it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SportsSection(container: AppContainer) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    var leagues by remember { mutableStateOf<List<League>>(emptyList()) }
    var league by rememberSaveable { mutableStateOf<String?>(null) }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.serverUrl) {
        try {
            leagues = container.api.leagues()
            if (league == null) league = leagues.firstOrNull()?.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't load leagues: ${e.message}"
        }
    }
    LaunchedEffect(league) {
        val selected = league ?: return@LaunchedEffect
        teams = emptyList()
        error = null
        try {
            teams = container.api.teams(selected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't load teams: ${e.message}"
        }
    }

    fun toggle(team: Team) {
        val leagueId = league ?: return
        container.settings.update { current ->
            val existing = current.teams.any { it.league == leagueId && it.id == team.id }
            val next = if (existing) {
                current.teams.filterNot { it.league == leagueId && it.id == team.id }
            } else {
                current.teams + TeamRef(leagueId, team.id, team.name, team.abbr, team.color)
            }
            current.copy(teams = next)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (settings.teams.isNotEmpty()) {
            Text("Following", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.teams.forEach { ref ->
                    InputChip(
                        selected = true,
                        onClick = {
                            container.settings.update { current ->
                                current.copy(
                                    teams = current.teams.filterNot {
                                        it.league == ref.league &&
                                            it.id == ref.id
                                    }
                                )
                            }
                        },
                        label = { Text(ref.name) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leagues.forEach { item ->
                FilterChip(selected = league == item.id, onClick = { league = item.id }, label = { Text(item.name) })
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search teams") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val visible = teams.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.abbr.contains(query, ignoreCase = true)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
        ) {
            items(visible, key = { it.id }) { team ->
                val following = settings.teams.any { it.league == league && it.id == team.id }
                TeamTile(team, following, onClick = { toggle(team) })
            }
        }
    }
}

@Composable
private fun TeamTile(team: Team, following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (following) colors.primaryContainer else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(parseHexColor(team.color) ?: Color.Gray)
                .border(1.dp, colors.outlineVariant, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(team.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        if (following) Icon(Icons.Default.Check, contentDescription = "Following", tint = colors.primary)
    }
}

@Composable
fun PhotosSection(container: AppContainer) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    var serverCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(settings.serverUrl, settings.token) {
        while (true) {
            serverCount = try {
                container.api.photos().photos.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            delay(5_000)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        QrCode(webLink(settings, tab = "photos"), Modifier.size(220.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan with your phone", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste a shared Google Photos album link, and the server checks it for new photos every night. " +
                    "Or connect your Google account and pick photos one by one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val serverLine = when (val count = serverCount) {
                null -> "Can't reach the server right now."
                0 -> "No photos on the server yet."
                else -> "$count photos on the server, ${snapshot.photos.size} saved on this calendar."
            }
            Text(serverLine, style = MaterialTheme.typography.bodyLarge)
            if (serverCount != null && serverCount != snapshot.photos.size) {
                OutlinedButton(onClick = { container.repository.launchSync() }) { Text("Download photos now") }
            }
        }
    }
}

@Composable
fun DisplaySection(container: AppContainer) {
    val store = container.settings
    val settings by store.flow.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Start photos after the calendar sits untouched for", style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = listOf(0 to "Never", 2 to "2 minutes", 5 to "5 minutes", 10 to "10 minutes", 30 to "30 minutes"),
            selected = settings.idleMinutes,
            onSelect = { minutes -> store.update { it.copy(idleMinutes = minutes) } }
        )
        Text("Show each photo for", style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = listOf(10 to "10 seconds", 30 to "30 seconds", 60 to "1 minute", 300 to "5 minutes"),
            selected = settings.photoSeconds,
            onSelect = { seconds -> store.update { it.copy(photoSeconds = seconds) } }
        )
        SettingRow("Quiet hours", "Dim the screen and show only the clock overnight") {
            Switch(checked = settings.quietEnabled, onCheckedChange = { on ->
                store.update { it.copy(quietEnabled = on) }
            })
        }
        if (settings.quietEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editing = "start" }) {
                    Text("From " + formatTime(minutesToTime(settings.quietStart), settings.use24h))
                }
                OutlinedButton(onClick = { editing = "end" }) {
                    Text("Until " + formatTime(minutesToTime(settings.quietEnd), settings.use24h))
                }
            }
        }
        SettingRow("24-hour clock") {
            Switch(checked = settings.use24h, onCheckedChange = { on -> store.update { it.copy(use24h = on) } })
        }
        SettingRow("Start weeks on Monday") {
            Switch(checked = settings.weekStartsMonday, onCheckedChange = { on ->
                store.update { it.copy(weekStartsMonday = on) }
            })
        }
        SettingRow("Dark theme") {
            Switch(checked = settings.darkTheme, onCheckedChange = { on -> store.update { it.copy(darkTheme = on) } })
        }
    }

    editing?.let { which ->
        val initial = if (which == "start") settings.quietStart else settings.quietEnd
        TimePickerDialog(minutesToTime(initial), settings.use24h, onDismiss = { editing = null }) { time ->
            val minutes = time.hour * 60 + time.minute
            store.update { if (which == "start") it.copy(quietStart = minutes) else it.copy(quietEnd = minutes) }
            editing = null
        }
    }
}

@Composable
fun HomeSection(device: DeviceControls) {
    var enabled by remember { mutableStateOf(device.isHomeApp()) }
    SettingRow(
        title = "Open automatically",
        subtitle = "Makes Kiwi Calendar the home screen, so it comes back on its own after a restart or power cut. " +
            "When Android asks, choose Kiwi Calendar."
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = {
                device.setHomeApp(it)
                enabled = it
            }
        )
    }
}
