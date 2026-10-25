package dev.kiwicalendar.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.ui.calendar.CalendarScreen
import dev.kiwicalendar.ui.setup.OnboardingScreen
import dev.kiwicalendar.ui.setup.SettingsScreen
import dev.kiwicalendar.ui.sleep.SleepScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What the UI needs from the Activity window. */
interface DeviceControls {
    val lastTouch: StateFlow<Long>
    fun setDimmed(dimmed: Boolean)
    fun isHomeApp(): Boolean
    fun setHomeApp(enabled: Boolean)
}

private enum class Screen { Calendar, Sleep, Settings }

/** During quiet hours the screen goes back to the dim clock this soon after the last touch. */
private const val QUIET_IDLE_MS = 60_000L

@Composable
fun AppRoot(container: AppContainer, device: DeviceControls) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    KiwiTheme(dark = settings.darkTheme) {
        if (settings.onboarded) {
            MainScreens(container, settings, device)
        } else {
            OnboardingScreen(container, device)
        }
    }
}

@Composable
private fun MainScreens(container: AppContainer, settings: AppSettings, device: DeviceControls) {
    var screen by rememberSaveable { mutableStateOf(Screen.Calendar) }
    val now by rememberNow()
    val quiet = settings.quietEnabled && inQuietHours(now.toLocalTime(), settings.quietStart, settings.quietEnd)
    val idleLimit = when {
        quiet -> QUIET_IDLE_MS
        settings.idleMinutes > 0 -> settings.idleMinutes * 60_000L
        else -> 0L
    }

    LaunchedEffect(screen, idleLimit) {
        if (screen == Screen.Sleep || idleLimit == 0L) return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (SystemClock.elapsedRealtime() - device.lastTouch.value >= idleLimit) {
                screen = Screen.Sleep
                break
            }
        }
    }
    LaunchedEffect(screen, quiet) {
        device.setDimmed(screen == Screen.Sleep && quiet)
    }

    when (screen) {
        Screen.Calendar -> CalendarScreen(
            container = container,
            settings = settings,
            today = now.toLocalDate(),
            onSleep = { screen = Screen.Sleep },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Sleep -> SleepScreen(container, settings, quiet, onWake = { screen = Screen.Calendar })
        Screen.Settings -> SettingsScreen(container, device, onClose = { screen = Screen.Calendar })
    }
}
