package dev.kiwicalendar.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.R
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.ui.DeviceControls

private enum class Step { Welcome, Server, Weather, Teams, Photos, Display, Finish }

@Composable
fun OnboardingScreen(container: AppContainer, device: DeviceControls) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = Step.entries[index]
    val lastIndex = Step.entries.lastIndex
    val canContinue = step != Step.Server || settings.isPaired

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            if (step != Step.Welcome) {
                Text(
                    "Step $index of $lastIndex",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { index / lastIndex.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)
                )
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (step) {
                    Step.Welcome -> Welcome()
                    Step.Server -> Page(
                        "Connect to your server",
                        "The Kiwi Calendar server runs in Docker on a computer at home. It brings in photos, scores, " +
                            "and shared calendars, and it's where your phone sends new events."
                    ) { ServerSection(container) }
                    Step.Weather -> Page(
                        "Where are you?",
                        "The forecast shows on the calendar and while photos play. You can skip this and add it later."
                    ) { WeatherSection(container) }
                    Step.Teams -> Page(
                        "Pick your teams",
                        "Scores for these teams appear while photos are showing. You can skip this and add teams later."
                    ) { SportsSection(container) }
                    Step.Photos -> Page(
                        "Add photos",
                        "Photos play when nobody is using the calendar. Use your phone to choose them."
                    ) { PhotosSection(container) }
                    Step.Display -> Page(
                        "When should photos play?",
                        "Tap the screen any time to get back to the calendar."
                    ) { DisplaySection(container) }
                    Step.Finish -> Page(
                        "Ready",
                        "To add events from your phone later, tap From phone on the calendar and scan the code."
                    ) { HomeSection(device) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) TextButton(onClick = { index-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = canContinue,
                    onClick = {
                        if (step == Step.Finish) {
                            container.settings.update { it.copy(onboarded = true) }
                            container.repository.launchSync()
                        } else {
                            index++
                        }
                    }
                ) {
                    Text(
                        when (step) {
                            Step.Welcome -> "Get started"
                            Step.Finish -> "Open the calendar"
                            else -> "Continue"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 48.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(88.dp)
        )
        Text("Welcome to Kiwi Calendar", style = MaterialTheme.typography.displaySmall)
        Text(
            "This takes about five minutes. Have your phone nearby and the Kiwi Calendar server running.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "Connect to the server",
            "Add your local weather",
            "Pick the teams you follow",
            "Choose photos from Google Photos",
            "Decide when photos play"
        ).forEachIndexed { i, line ->
            Text("${i + 1}.  $line", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Page(title: String, intro: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(intro, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
