package dev.kiwicalendar.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class Event(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    @SerialName("all_day") val allDay: Boolean = false,
    val location: String? = null,
    val notes: String? = null,
    val color: String? = null,
    val source: String = LOCAL_SOURCE,
    val calendar: String? = null
) {
    val isLocal: Boolean get() = source == LOCAL_SOURCE

    companion object {
        const val LOCAL_SOURCE = "local"
    }
}

@Serializable
data class Photo(val id: String, val width: Int = 0, val height: Int = 0)

@Serializable
data class Snapshot(
    val revision: Long = -1,
    val events: List<Event> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val syncedAt: Long = 0
)

@Serializable
data class SyncResponse(
    val revision: Long,
    val events: List<Event> = emptyList(),
    val photos: List<Photo> = emptyList()
)

@Serializable
data class RevisionResponse(val revision: Long)

@Serializable
data class PhotoList(val photos: List<Photo> = emptyList())

@Serializable
data class League(val id: String, val name: String)

@Serializable
data class Team(val id: String, val name: String, val abbr: String = "", val color: String? = null)

@Serializable
data class TeamRef(
    val league: String,
    val id: String,
    val name: String,
    val abbr: String = "",
    val color: String? = null
)

@Serializable
data class GameSide(
    val id: String = "",
    val abbr: String = "",
    val name: String = "",
    val color: String? = null,
    val score: String? = null
)

@Serializable
data class Game(
    val id: String,
    val league: String,
    val start: String,
    val state: String = "pre",
    val detail: String = "",
    val home: GameSide? = null,
    val away: GameSide? = null
)

/** A local change waiting to be sent to the server. */
@Serializable
data class PendingOp(val delete: Boolean, val event: Event)
