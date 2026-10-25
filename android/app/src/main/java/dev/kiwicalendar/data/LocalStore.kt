package dev.kiwicalendar.data

import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/** Everything the calendar needs to keep working while the server is unreachable. */
class LocalStore(private val root: File) {
    val photoDir: File = File(root, "photos").apply { mkdirs() }

    fun photoFile(id: String) = File(photoDir, "$id.jpg")

    fun loadSnapshot(): Snapshot = read(SNAPSHOT, Snapshot.serializer()) ?: Snapshot()
    fun saveSnapshot(value: Snapshot) = write(SNAPSHOT, Snapshot.serializer(), value)

    fun loadScores(): List<Game> = read(SCORES, ListSerializer(Game.serializer())) ?: emptyList()
    fun saveScores(value: List<Game>) = write(SCORES, ListSerializer(Game.serializer()), value)

    fun loadPending(): List<PendingOp> = read(PENDING, ListSerializer(PendingOp.serializer())) ?: emptyList()
    fun savePending(value: List<PendingOp>) = write(PENDING, ListSerializer(PendingOp.serializer()), value)

    @Synchronized
    private fun <T> read(name: String, serializer: KSerializer<T>): T? {
        val file = File(root, name)
        if (!file.exists()) return null
        return runCatching { AppJson.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    @Synchronized
    private fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        val tmp = File(root, "$name.tmp")
        tmp.writeText(AppJson.encodeToString(serializer, value))
        if (!tmp.renameTo(File(root, name))) tmp.delete()
    }

    private companion object {
        const val SNAPSHOT = "snapshot.json"
        const val SCORES = "scores.json"
        const val PENDING = "pending.json"
    }
}
