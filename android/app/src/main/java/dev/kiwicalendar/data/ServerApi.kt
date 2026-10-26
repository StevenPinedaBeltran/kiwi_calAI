package dev.kiwicalendar.data

import java.io.File
import android.graphics.BitmapFactory
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(message: String, val code: Int = 0) : IOException(message)

class ServerApi(private val settings: () -> AppSettings) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Used during setup, before the address and token are saved. */
    suspend fun check(serverUrl: String, token: String): Long =
        get("/api/revision", RevisionResponse.serializer(), AppSettings(serverUrl = serverUrl, token = token)).revision

    suspend fun revision(): Long = get("/api/revision", RevisionResponse.serializer()).revision

    suspend fun sync(): SyncResponse = get("/api/sync", SyncResponse.serializer())

    suspend fun photos(): PhotoList = get("/api/photos", PhotoList.serializer())

    suspend fun leagues(): List<League> = get("/api/sports/leagues", ListSerializer(League.serializer()))

    suspend fun teams(league: String): List<Team> =
        get("/api/sports/teams?league=$league", ListSerializer(Team.serializer()))

    suspend fun scores(teams: List<TeamRef>): List<Game> {
        val query = teams.joinToString(",") { "${it.league}:${it.id}" }
        return get("/api/scores?teams=$query", ListSerializer(Game.serializer()))
    }

    suspend fun weather(place: WeatherPlace, units: String): WeatherReport =
        get("/api/weather?lat=${place.latitude}&lon=${place.longitude}&units=$units", WeatherReport.serializer())

    suspend fun searchPlaces(query: String): List<WeatherPlace> =
        get("/api/weather/search?q=${URLEncoder.encode(query, "UTF-8")}", ListSerializer(WeatherPlace.serializer()))

    suspend fun putEvent(event: Event) {
        val body = AppJson.encodeToString(Event.serializer(), event).toRequestBody(JSON)
        call(request("/api/events/${event.id}").put(body).build())
    }

    suspend fun deleteEvent(id: String) {
        call(request("/api/events/$id").delete().build())
    }

    suspend fun downloadPhoto(id: String, dest: File) = withContext(Dispatchers.IO) {
        client.newCall(request("/api/photos/$id").get().build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("Photo download failed (${response.code})", response.code)
            val body = response.body ?: throw ApiException("Empty photo response")
            val partial = File(dest.parentFile, dest.name + ".part")
            try {
                partial.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(partial.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    throw IOException("Invalid image received for photo $id")
                }
                if (!partial.renameTo(dest)) {
                    // Some Android filesystems need the old destination removed first.
                    if (dest.exists() && !dest.delete()) throw IOException("Couldn't replace photo $id")
                    if (!partial.renameTo(dest)) throw IOException("Couldn't save photo $id")
                }
            } finally {
                partial.delete()
            }
        }
    }

    private suspend fun <T> get(
        path: String,
        deserializer: DeserializationStrategy<T>,
        target: AppSettings = settings()
    ): T {
        val text = call(request(path, target).get().build())
        return try {
            AppJson.decodeFromString(deserializer, text)
        } catch (e: IllegalArgumentException) {
            throw ApiException("The server sent something unexpected. Is the address right?")
        }
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "The access token was rejected"
                    404 -> "Not found on the server"
                    else -> "Server error ${response.code}"
                }
                throw ApiException(message, response.code)
            }
            text
        }
    }

    private fun request(path: String, target: AppSettings = settings()): Request.Builder {
        val url = (target.serverUrl.trimEnd('/') + path).toHttpUrlOrNull()
            ?: throw ApiException("That server address isn't valid")
        return Request.Builder().url(url).header("Authorization", "Bearer ${target.token}")
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
