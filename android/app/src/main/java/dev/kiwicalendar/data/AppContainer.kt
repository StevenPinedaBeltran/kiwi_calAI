package dev.kiwicalendar.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies. The app is small enough that a DI framework would be overhead. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.w("KiwiCalendar", "Background task failed", error)
        }
    )
    val settings = SettingsStore(context)
    val store = LocalStore(context.filesDir)
    val api = ServerApi { settings.value }
    val repository = Repository(settings, store, api, scope)
}
