package dev.kiwicalendar.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.BuildConfig
import dev.kiwicalendar.R
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.ui.DeviceControls
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(container: AppContainer, device: DeviceControls, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to calendar")
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            Card("Sync") { SyncSection(container) }
            Card("Server") { ServerSection(container) }
            Card("Teams") { SportsSection(container) }
            Card("Photos") { PhotosSection(container) }
            Card("Display") { DisplaySection(container) }
            Card("This device") {
                HomeSection(device)
                TextButton(onClick = { container.settings.update { it.copy(onboarded = false) } }) {
                    Text("Run setup again")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Kiwi Calendar ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Last synced $last. ${snapshot.events.size} events and ${snapshot.photos.size} photos saved on this calendar."
        )
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { container.repository.launchSync() }, enabled = !status.running) {
            Text(if (status.running) "Syncing…" else "Sync now")
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
