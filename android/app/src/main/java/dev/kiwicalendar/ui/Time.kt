package dev.kiwicalendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import dev.kiwicalendar.data.Event
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** An event placed on the device's local clock. All-day events end at midnight of the day after. */
data class Occurrence(val event: Event, val allDay: Boolean, val start: LocalDateTime, val end: LocalDateTime) {
    val firstDay: LocalDate get() = start.toLocalDate()
    val lastDay: LocalDate
        get() = when {
            allDay -> end.toLocalDate().minusDays(1)
            end.isAfter(start) -> end.minusNanos(1).toLocalDate()
            else -> start.toLocalDate()
        }
}

fun Event.occurrence(zone: ZoneId): Occurrence? = try {
    if (allDay) {
        val first = LocalDate.parse(start.take(10))
        val after = LocalDate.parse(end.take(10)).let { if (it.isAfter(first)) it else first.plusDays(1) }
        Occurrence(this, true, first.atStartOfDay(), after.atStartOfDay())
    } else {
        val from = OffsetDateTime.parse(start).atZoneSameInstant(zone).toLocalDateTime()
        val until = OffsetDateTime.parse(end).atZoneSameInstant(zone).toLocalDateTime()
        Occurrence(this, false, from, if (until.isBefore(from)) from else until)
    }
} catch (ex: DateTimeException) {
    null
}

fun occurrencesByDay(
    events: List<Event>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId
): Map<LocalDate, List<Occurrence>> {
    val days = HashMap<LocalDate, MutableList<Occurrence>>()
    for (occurrence in events.mapNotNull { it.occurrence(zone) }) {
        var day = maxOf(occurrence.firstDay, from)
        val last = minOf(occurrence.lastDay, to)
        while (!day.isAfter(last)) {
            days.getOrPut(day) { mutableListOf() }.add(occurrence)
            day = day.plusDays(1)
        }
    }
    val order = compareBy<Occurrence>({ !it.allDay }, { it.start }, { it.event.title })
    return days.mapValues { (_, list) -> list.sortedWith(order) }
}

private val Time24 = DateTimeFormatter.ofPattern("HH:mm")
private val Time12 = DateTimeFormatter.ofPattern("h:mm a")
private val Time12Hour = DateTimeFormatter.ofPattern("h a")
private val Clock12 = DateTimeFormatter.ofPattern("h:mm")

fun formatTime(time: LocalTime, use24h: Boolean): String = when {
    use24h -> time.format(Time24)
    time.minute == 0 -> time.format(Time12Hour)
    else -> time.format(Time12)
}

fun formatClock(time: LocalTime, use24h: Boolean): String = time.format(if (use24h) Time24 else Clock12)

fun timeRange(occurrence: Occurrence, day: LocalDate, use24h: Boolean): String {
    if (occurrence.allDay) {
        return if (occurrence.firstDay == occurrence.lastDay) {
            "All day"
        } else {
            "All day, until " + occurrence.lastDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
    }
    val startsToday = occurrence.firstDay == day
    val endsToday = occurrence.lastDay == day
    val start = formatTime(occurrence.start.toLocalTime(), use24h)
    val end = formatTime(occurrence.end.toLocalTime(), use24h)
    return when {
        startsToday && endsToday -> if (occurrence.start == occurrence.end) start else "$start – $end"
        startsToday -> "From $start"
        endsToday -> "Until $end"
        else -> "All day"
    }
}

fun relativeDay(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

fun minutesToTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60) % 24, minutes % 60)

fun inQuietHours(now: LocalTime, startMinutes: Int, endMinutes: Int): Boolean {
    val minute = now.hour * 60 + now.minute
    return if (startMinutes <= endMinutes) {
        minute in startMinutes until endMinutes
    } else {
        minute >= startMinutes || minute < endMinutes
    }
}

/** Current time, updated on each boundary of [stepMillis] (the top of every minute by default). */
@Composable
fun rememberNow(stepMillis: Long = 60_000): State<LocalDateTime> = produceState(LocalDateTime.now(), stepMillis) {
    while (true) {
        delay(stepMillis - System.currentTimeMillis() % stepMillis)
        value = LocalDateTime.now()
    }
}
