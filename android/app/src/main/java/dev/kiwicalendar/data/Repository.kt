package dev.kiwicalendar.data

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncStatus(val running: Boolean = false, val lastSuccess: Long = 0, val error: String? = null)

/**
 * Keeps a local copy of everything the server knows about, so the calendar works offline.
 *
 * The server is the source of truth. Edits made on the tablet are applied locally right away,
 * queued, and pushed on the next sync. Events use client-chosen ids, so retrying a push is safe.
 */
class Repository(
    private val settings: SettingsStore,
    private val store: LocalStore,
    private val api: ServerApi,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(store.loadSnapshot())
    private val pending = MutableStateFlow(store.loadPending())
    private val _scores = MutableStateFlow(store.loadScores())
    private val _status = MutableStateFlow(SyncStatus(lastSuccess = _snapshot.value.syncedAt))
    // A persisted score without a recent successful refresh must never be called LIVE.
    private val _scoresFetchedAt = MutableStateFlow(0L)
    private val _weather = MutableStateFlow(store.loadWeather())

    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
    val scores: StateFlow<List<Game>> = _scores.asStateFlow()
    val scoresFetchedAt: StateFlow<Long> = _scoresFetchedAt.asStateFlow()
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    val weather: StateFlow<WeatherReport?> = _weather.asStateFlow()

    fun launchSync() {
        scope.launch { sync() }
    }

    suspend fun sync(): Boolean = mutex.withLock {
        if (!settings.value.isPaired) return@withLock false
        _status.update { it.copy(running = true) }
        val error = try {
            pushPending()
            val remote = api.sync()
            val photos = syncPhotos(remote.photos)
            val events = applyOps(remote.events, pending.value)
            val next = Snapshot(remote.revision, events, photos, System.currentTimeMillis())
            store.saveSnapshot(next)
            _snapshot.value = next
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "Couldn't reach the server"
        }
        _status.update {
            SyncStatus(
                running = false,
                lastSuccess = if (error == null) System.currentTimeMillis() else it.lastSuccess,
                error = error
            )
        }
        error == null
    }

    /** Cheap check that only does a full sync when something changed, or once a day regardless. */
    suspend fun syncIfStale() {
        if (!settings.value.isPaired) return
        val current = _snapshot.value
        val remoteRevision = try {
            api.revision()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.update { it.copy(error = e.message ?: "Couldn't reach the server") }
            return
        }
        val dayOld = System.currentTimeMillis() - current.syncedAt > DAY_MS
        // Photos can be deleted by a cleanup, storage pressure, or a partial download
        // without changing the server revision. Repair these on the next poll.
        val missingPhoto = current.photos.any { photo ->
            val file = store.photoFile(photo.id)
            !file.isFile || file.length() < 100L
        }
        if (dayOld || missingPhoto || remoteRevision != current.revision || pending.value.isNotEmpty()) {
            sync()
        } else if (_status.value.error != null) {
            _status.update { it.copy(error = null) }
        }
    }

    fun saveEvent(event: Event) = enqueue(PendingOp(delete = false, event = event))

    fun deleteEvent(event: Event) = enqueue(PendingOp(delete = true, event = event))

    suspend fun refreshScores() {
        val current = settings.value
        if (!current.isPaired || current.teams.isEmpty()) {
            _scores.value = emptyList()
            _scoresFetchedAt.value = 0L
            store.saveScores(emptyList())
            return
        }
        try {
            val games = api.scores(current.teams)
            _scores.value = games
            _scoresFetchedAt.value = System.currentTimeMillis()
            store.saveScores(games)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last scores we had.
        }
    }

    suspend fun refreshWeather() {
        val current = settings.value
        val place = current.weatherPlace ?: run {
            _weather.value = null
            return
        }
        if (!current.isPaired) return
        // Don't show a previous city's temperature while a new city is loading.
        val cached = _weather.value
        if (cached != null && !cached.matches(place, current.units)) _weather.value = null
        try {
            val report = api.weather(place, current.units).copy(fetchedAt = System.currentTimeMillis())
            _weather.value = report
            store.saveWeather(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the last report; the UI shows how old it is.
        }
    }

    fun launchWeatherRefresh() {
        scope.launch { refreshWeather() }
    }

    private fun enqueue(op: PendingOp) {
        pending.update { ops -> ops.filterNot { it.event.id == op.event.id } + op }
        _snapshot.update { it.copy(events = applyOps(it.events, listOf(op))) }
        scope.launch {
            store.savePending(pending.value)
            store.saveSnapshot(_snapshot.value)
            sync()
        }
    }

    private suspend fun pushPending() {
        for (op in pending.value) {
            try {
                if (op.delete) api.deleteEvent(op.event.id) else api.putEvent(op.event)
            } catch (e: ApiException) {
                // A 4xx means the server will never accept this change; drop it instead of retrying forever.
                if (e.code !in 400..499) throw e
            }
            pending.update { ops -> ops - op }
            store.savePending(pending.value)
        }
    }

    private suspend fun syncPhotos(remote: List<Photo>): List<Photo> {
        val wanted = remote.mapTo(HashSet()) { it.id }
        // Don't delete cached photos until after remote metadata is available.
        store.photoDir.listFiles()?.forEach { file ->
            if (file.extension == "jpg" && file.nameWithoutExtension !in wanted) file.delete()
            if (file.extension == "part") file.delete()
        }
        remote.forEach { photo ->
            val file = store.photoFile(photo.id)
            if (!file.isFile || file.length() < 100L) {
                // One broken image must not abort the slideshow or lose metadata.
                runCatching { api.downloadPhoto(photo.id, file) }
            }
        }
        // Retain the server's photo list even for failed downloads. The next
        // syncIfStale() detects missing files and retries instead of waiting 24h.
        return remote
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

private fun applyOps(events: List<Event>, ops: List<PendingOp>): List<Event> {
    if (ops.isEmpty()) return events
    val byId = LinkedHashMap<String, Event>()
    events.forEach { byId[it.id] = it }
    for (op in ops) {
        if (op.delete) byId.remove(op.event.id) else byId[op.event.id] = op.event
    }
    return byId.values.toList()
}

/** Coordinate and unit changes must never display the previous forecast as current. */
fun WeatherReport.matches(place: WeatherPlace, units: String): Boolean =
    latitude != null && longitude != null && this.units == units &&
        kotlin.math.abs(latitude - place.latitude) < 0.011 &&
        kotlin.math.abs(longitude - place.longitude) < 0.011
