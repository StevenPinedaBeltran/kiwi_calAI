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

@Serializable
data class WeatherPlace(val name: String, val region: String = "", val latitude: Double, val longitude: Double)

@Serializable
data class WeatherLabels(
    val temp: String = "°",
    val speed: String = "",
    val precip: String = "",
    val distance: String = "",
    val pressure: String = ""
)

// Numbers are Doubles throughout so an upstream change from 42 to 42.0 can't break decoding.
@Serializable
data class WeatherNow(
    val time: String = "",
    val temp: Double? = null,
    @SerialName("feels_like") val feelsLike: Double? = null,
    val humidity: Double? = null,
    @SerialName("dew_point") val dewPoint: Double? = null,
    val precip: Double? = null,
    val code: Int? = null,
    val condition: String = "",
    @SerialName("is_day") val isDay: Boolean = true,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    val pressure: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_gusts") val windGusts: Double? = null,
    @SerialName("wind_dir") val windDir: Double? = null,
    val uv: Double? = null,
    val visibility: Double? = null
)

@Serializable
data class WeatherHour(
    val time: String,
    val temp: Double? = null,
    val code: Int? = null,
    @SerialName("is_day") val isDay: Boolean = true,
    @SerialName("precip_prob") val precipProb: Double? = null
)

@Serializable
data class WeatherDay(
    val date: String,
    val code: Int? = null,
    val condition: String = "",
    val high: Double? = null,
    val low: Double? = null,
    @SerialName("precip_prob") val precipProb: Double? = null,
    @SerialName("precip_sum") val precipSum: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    @SerialName("uv_max") val uvMax: Double? = null,
    @SerialName("wind_max") val windMax: Double? = null
)

@Serializable
data class AirQuality(
    @SerialName("us_aqi") val usAqi: Double? = null,
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val pm10: Double? = null
)

@Serializable
data class WeatherReport(
    val units: String = "imperial",
    val labels: WeatherLabels = WeatherLabels(),
    val updated: String = "",
    val current: WeatherNow = WeatherNow(),
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList(),
    val air: AirQuality? = null,
    val fetchedAt: Long = 0
)
