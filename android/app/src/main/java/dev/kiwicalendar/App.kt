package dev.kiwicalendar

import android.app.Application
import android.content.Context
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.SyncWorker

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as App).container
