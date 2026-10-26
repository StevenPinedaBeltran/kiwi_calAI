package dev.kiwicalendar.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.BuildConfig
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.ui.calendar.CalendarContent
import dev.kiwicalendar.ui.calendar.PhoneCodeDialog
import dev.kiwicalendar.ui.setup.OnboardingScreen
import dev.kiwicalendar.ui.setup.SettingsContent
import dev.kiwicalendar.ui.sleep.SleepScreen
import dev.kiwicalendar.ui.weather.WeatherContent
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What the UI needs from the Activity window. */
interface DeviceControls {
    val lastTouch: StateFlow<Long>
    fun setDimmed(dimmed: Boolean)
    fun isHomeApp(): Boolean
    fun setHomeApp(enabled: Boolean)
}

private enum class Screen { Calendar, Weather, Settings, Sleep }

/** During quiet hours the screen goes back to the dim clock this soon after the last touch. */
private const val QUIET_IDLE_MS = 60_000L

@Composable
fun AppRoot(container: AppContainer, device: DeviceControls) {
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    KiwiTheme {
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
    var showPhoneCode by remember { mutableStateOf(false) }
    val now by rememberNow()
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val status by container.repository.status.collectAsStateWithLifecycle()
    val weather by container.repository.weather.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = now.toLocalDate()

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
                showPhoneCode = false
                screen = Screen.Sleep
                break
            }
        }
    }
    LaunchedEffect(screen, quiet) {
        device.setDimmed(screen == Screen.Sleep && quiet)
    }
    BackHandler(enabled = screen == Screen.Weather || screen == Screen.Settings) { screen = Screen.Calendar }

    if (screen == Screen.Sleep) {
        SleepScreen(container, settings, quiet, onWake = { screen = Screen.Calendar })
        return
    }

    val todayCount = remember(snapshot.events, today) {
        occurrencesByDay(snapshot.events, today, today, zone)[today]?.size ?: 0
    }
    val (title, subtitle) = when (screen) {
        Screen.Weather -> "Weather" to (
            settings.weatherPlace?.let { listOf(it.name, it.region).filter(String::isNotBlank).joinToString(", ") }
                ?: "No location chosen yet"
            )
        Screen.Settings -> "Settings" to "Kiwi Calendar ${BuildConfig.VERSION_NAME}"
        else -> greeting(now.toLocalTime(), settings.householdName) to when {
            status.error != null -> "Offline. Showing saved events."
            todayCount == 0 -> "Nothing on the agenda today."
            else -> "Here's what's on your agenda today."
        }
    }

    Shell(
        current = when (screen) {
            Screen.Weather -> Destination.Weather
            Screen.Settings -> Destination.Settings
            else -> Destination.Calendar
        },
        title = title,
        subtitle = subtitle,
        weather = weather.takeIf { settings.weatherPlace != null },
        use24h = settings.use24h,
        onNavigate = { destination ->
            when (destination) {
                Destination.Calendar -> screen = Screen.Calendar
                Destination.Weather -> screen = Screen.Weather
                Destination.Settings -> screen = Screen.Settings
                Destination.Photos -> screen = Screen.Sleep
                Destination.Phone -> showPhoneCode = true
            }
        }
    ) {
        when (screen) {
            Screen.Weather -> WeatherContent(container, settings, onOpenSettings = { screen = Screen.Settings })
            Screen.Settings -> SettingsContent(container, device)
            else -> CalendarContent(container, settings, today)
        }
    }
    if (showPhoneCode) PhoneCodeDialog(settings, onDismiss = { showPhoneCode = false })
}
