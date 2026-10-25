package dev.kiwicalendar.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val serverUrl: String = "",
    val token: String = "",
    val onboarded: Boolean = false,
    val teams: List<TeamRef> = emptyList(),
    val idleMinutes: Int = 5,
    val photoSeconds: Int = 30,
    val quietEnabled: Boolean = false,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val use24h: Boolean = false,
    val weekStartsMonday: Boolean = false,
    val darkTheme: Boolean = false
) {
    val isPaired: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = state.updateAndGet(transform)
        prefs.edit().putString(KEY, AppJson.encodeToString(AppSettings.serializer(), next)).apply()
    }

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY, null) ?: return AppSettings()
        return runCatching { AppJson.decodeFromString(AppSettings.serializer(), raw) }.getOrDefault(AppSettings())
    }

    private companion object {
        const val KEY = "settings"
    }
}
