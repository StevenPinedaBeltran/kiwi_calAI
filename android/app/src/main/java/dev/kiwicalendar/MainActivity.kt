package dev.kiwicalendar

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.kiwicalendar.ui.AppRoot
import dev.kiwicalendar.ui.DeviceControls
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity :
    ComponentActivity(),
    DeviceControls {
    private val touches = MutableStateFlow(SystemClock.elapsedRealtime())
    override val lastTouch: StateFlow<Long> = touches.asStateFlow()

    private val homeAlias by lazy {
        ComponentName(this, MainActivity::class.java.name.substringBeforeLast('.') + ".HomeAlias")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val container = appContainer
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        container.repository.syncIfStale()
                        delay(POLL_INTERVAL_MS)
                    }
                }
                launch {
                    while (true) {
                        container.repository.refreshScores()
                        delay(SCORE_INTERVAL_MS)
                    }
                }
                launch {
                    while (true) {
                        container.repository.refreshWeather()
                        delay(WEATHER_INTERVAL_MS)
                    }
                }
            }
        }

        setContent { AppRoot(container, this) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        touches.value = SystemClock.elapsedRealtime()
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun setDimmed(dimmed: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (dimmed) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun isHomeApp(): Boolean =
        packageManager.getComponentEnabledSetting(homeAlias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    override fun setHomeApp(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(homeAlias, state, PackageManager.DONT_KILL_APP)
        if (enabled) runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        /** How often to ask the server whether anything changed (events added from a phone, etc). */
        const val POLL_INTERVAL_MS = 2 * 60 * 1000L
        const val WEATHER_INTERVAL_MS = 15 * 60 * 1000L
        const val SCORE_INTERVAL_MS = 2 * 60 * 1000L
    }
}
