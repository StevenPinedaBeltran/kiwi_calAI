package dev.kiwicalendar.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.ui.DeviceControls
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.PrimaryButton
import dev.kiwicalendar.ui.glass
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsContent(container: AppContainer, device: DeviceControls) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card("Sync") { SyncSection(container) }
        Card("Weather") { WeatherSection(container) }
        Card("Display") { DisplaySection(container) }
        Card("Teams") { SportsSection(container) }
        Card("Photos") { PhotosSection(container) }
        Card("Server") { ServerSection(container) }
        Card("This device") {
            HomeSection(device)
            PrimaryButton("Run setup again", onClick = { container.settings.update { it.copy(onboarded = false) } })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SyncSection(container: AppContainer) {
    val status by container.repository.status.collectAsStateWithLifecycle()
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val last = if (status.lastSuccess > 0) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastSuccess))
    } else {
        "never"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Last synced $last. ${snapshot.events.size} events and ${snapshot.photos.size} photos saved on this calendar."
        )
        status.error?.let { Text(it, color = Mono.Muted) }
        PrimaryButton(
            text = if (status.running) "Syncing…" else "Sync now",
            onClick = { container.repository.launchSync() },
            enabled = !status.running
        )
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}
