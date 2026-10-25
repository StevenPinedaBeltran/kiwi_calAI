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

    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
    val scores: StateFlow<List<Game>> = _scores.asStateFlow()
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

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
        if (dayOld || remoteRevision != current.revision || pending.value.isNotEmpty()) {
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
            return
        }
        try {
            val games = api.scores(current.teams)
            _scores.value = games
            store.saveScores(games)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last scores we had.
        }
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
        store.photoDir.listFiles()?.forEach { file ->
            if (file.name.substringBefore('.') !in wanted) file.delete()
        }
        return remote.filter { photo ->
            val file = store.photoFile(photo.id)
            file.exists() || runCatching { api.downloadPhoto(photo.id, file) }.isSuccess
        }
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
