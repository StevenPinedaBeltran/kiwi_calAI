package dev.kiwicalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Event
import dev.kiwicalendar.data.Photo
import dev.kiwicalendar.data.LocalStore
import dev.kiwicalendar.ui.DottedDivider
import dev.kiwicalendar.ui.GlyphIcon
import dev.kiwicalendar.ui.Glyphs
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.Occurrence
import dev.kiwicalendar.ui.PrimaryButton
import dev.kiwicalendar.ui.RoundButton
import dev.kiwicalendar.ui.dashedBorder
import dev.kiwicalendar.ui.eventTone
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.glass
import dev.kiwicalendar.ui.occurrencesByDay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val PanelDate = DateTimeFormatter.ofPattern("MMMM d, yyyy")
private val Hour24 = DateTimeFormatter.ofPattern("HH:00")

@Composable
fun CalendarContent(container: AppContainer, settings: AppSettings, today: LocalDate) {
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    // Keyed on today so the calendar returns to the current month at midnight.
    var month by rememberSaveable(today) { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable(today) { mutableStateOf(today) }
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Event?>(null) }

    val firstDayOfWeek = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val weeks = (ChronoUnit.DAYS.between(gridStart, month.atEndOfMonth()) / 7 + 1).toInt()
    val gridEnd = gridStart.plusDays(weeks * 7L - 1)
    val byDay = remember(snapshot.events, gridStart, gridEnd, selected) {
        occurrencesByDay(snapshot.events, minOf(gridStart, selected), maxOf(gridEnd, selected), zone)
    }

    fun select(day: LocalDate) {
        selected = day
        month = YearMonth.from(day)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp
        val panelShape = RoundedCornerShape(30.dp)
        val monthPanel = @Composable { modifier: Modifier ->
            Column(modifier.padding(24.dp)) {
                MonthHeader(
                    month = month,
                    onMonth = { month = it },
                    onAdd = { creating = true },
                    onPrevious = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${snapshot.events.size} events", color = Mono.Muted,
                        style = MaterialTheme.typography.labelMedium)
                    Text("•", color = Mono.Faint)
                    Text("${snapshot.photos.size} family photos", color = Mono.Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                MonthGrid(month, gridStart, weeks, today, selected, byDay, onSelect = {
                    selected = it
                }, modifier = Modifier.weight(1f))
            }
        }
        val schedule = @Composable { modifier: Modifier ->
            SchedulePanel(
                day = selected,
                occurrences = byDay[selected].orEmpty(),
                photos = snapshot.photos,
                photoStore = container.store,
                use24h = settings.use24h,
                onToday = { select(today) },
                onPrevious = { select(selected.minusDays(1)) },
                onNext = { select(selected.plusDays(1)) },
                onOpen = { opened = it },
                onAdd = { creating = true },
                modifier = modifier
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize().glass(panelShape, fill = Mono.Ink.copy(alpha = 0.035f))) {
                monthPanel(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(Mono.Hairline))
                schedule(Modifier.width(340.dp).fillMaxHeight().padding(22.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().glass(panelShape, fill = Mono.Ink.copy(alpha = 0.035f))) {
                monthPanel(Modifier.weight(1.4f).fillMaxWidth())
                Box(Modifier.height(1.dp).fillMaxWidth().background(Mono.Hairline))
                schedule(Modifier.weight(1f).fillMaxWidth().padding(22.dp))
            }
        }
    }

    val current = opened
    when {
        creating -> EventEditor(
            initial = null,
            defaultDate = selected,
            use24h = settings.use24h,
            onDismiss = { creating = false },
            onSave = {
                container.repository.saveEvent(it)
                creating = false
            },
            onDelete = {}
        )
        current != null && current.isLocal -> EventEditor(
            initial = current,
            defaultDate = selected,
            use24h = settings.use24h,
            onDismiss = { opened = null },
            onSave = {
                container.repository.saveEvent(it)
                opened = null
            },
            onDelete = {
                container.repository.deleteEvent(it)
                opened = null
            }
        )
        current != null -> EventDetails(current, settings.use24h, onDismiss = { opened = null })
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onAdd: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Picker(
            label = month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
            options = Month.values().map { it.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) to it.value },
            onPick = { onMonth(month.withMonth(it)) }
        )
        Spacer(Modifier.width(28.dp))
        Picker(
            label = month.year.toString(),
            options = (month.year - 3..month.year + 5).map { it.toString() to it },
            onPick = { onMonth(month.withYear(it)) }
        )
        Spacer(Modifier.weight(1f))
        RoundButton(Glyphs.Plus, "New event", onAdd, 44.dp)
        Spacer(Modifier.width(10.dp))
        RoundButton(Glyphs.ChevronLeft, "Previous month", onPrevious, 44.dp)
        Spacer(Modifier.width(10.dp))
        RoundButton(Glyphs.ChevronRight, "Next month", onNext, 44.dp)
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<String, Int>>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp))
            Spacer(Modifier.width(8.dp))
            GlyphIcon(Glyphs.ChevronDown, 18.dp, Mono.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onPick(value)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    gridStart: LocalDate,
    weeks: Int,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<Occurrence>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            repeat(7) { i ->
                Text(
                    text = gridStart.plusDays(
                        i.toLong()
                    ).dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Faint,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
        }
        repeat(weeks) { week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(7) { dayIndex ->
                    val date = gridStart.plusDays((week * 7 + dayIndex).toLong())
                    DayCard(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        occurrences = byDay[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

private val LabelSlot = 17.dp

@Composable
private fun DayCard(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    occurrences: List<Occurrence>,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.padding(5.dp).clip(shape)
    val surface = when {
        isToday -> base.background(Brush.linearGradient(listOf(Mono.Ink, Color(0xFFB8B8B8))))
        isSelected -> base.background(
            Mono.Ink.copy(alpha = 0.09f)
        ).dashedBorder(Mono.Ink.copy(alpha = 0.8f), 16.dp)
        else ->
            base
                .background(Mono.Ink.copy(alpha = if (inMonth) 0.055f else 0.02f))
                .border(1.dp, Mono.Ink.copy(alpha = if (inMonth) 0.10f else 0.05f), shape)
    }
    val ink = if (isToday) Mono.Paper else Mono.Ink
    BoxWithConstraints(surface.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
            color = ink.copy(alpha = if (inMonth || isToday) 1f else 0.3f),
            modifier = Modifier.align(Alignment.TopStart)
        )
        val slots = ((maxHeight - 38.dp) / LabelSlot).toInt().coerceAtLeast(0)
        val shown = if (occurrences.size > slots) (slots - 1).coerceAtLeast(0) else occurrences.size
        Column(Modifier.align(Alignment.BottomStart)) {
            occurrences.take(shown).forEach { occurrence ->
                Row(Modifier.height(LabelSlot), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(10.dp)
                            .background(
                                if (isToday) Mono.Paper.copy(alpha = 0.7f) else eventTone(occurrence.event.color)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = occurrence.event.title,
                        fontSize = 11.sp,
                        color = ink.copy(alpha = if (inMonth || isToday) 0.85f else 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (occurrences.size > shown) {
                Text(
                    text = "${occurrences.size - shown} more",
                    fontSize = 11.sp,
                    color = ink.copy(alpha = 0.55f),
                    modifier = Modifier.height(LabelSlot)
                )
            }
        }
    }
}

@Composable
private fun SchedulePanel(
    day: LocalDate,
    occurrences: List<Occurrence>,
    photos: List<Photo>,
    photoStore: LocalStore,
    use24h: Boolean,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: (Event) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scheduled", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp))
                Text(day.format(PanelDate), style = MaterialTheme.typography.bodyMedium, color = Mono.Muted)
            }
            RoundButton(Glyphs.Calendar, "Go to today", onToday, 40.dp)
            Spacer(Modifier.width(8.dp))
            RoundButton(Glyphs.ChevronLeft, "Previous day", onPrevious, 40.dp)
            Spacer(Modifier.width(8.dp))
            RoundButton(Glyphs.ChevronRight, "Next day", onNext, 40.dp)
        }
        Spacer(Modifier.height(18.dp))
        if (occurrences.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nothing scheduled", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap + or scan the phone code to add something.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton("New event", onAdd)
            }
        } else {
            val groups = remember(occurrences, use24h) {
                occurrences.groupBy { occurrence ->
                    when {
                        occurrence.allDay || occurrence.firstDay != day -> "All day"
                        use24h -> occurrence.start.format(Hour24)
                        else -> formatTime(occurrence.start.toLocalTime().withMinute(0), false)
                    }
                }.toList()
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (label, group) ->
                    item(key = "label-$label") { TimeLabel(label) }
                    items(group, key = { it.event.id }) { occurrence ->
                        ScheduleCard(occurrence, day, use24h, onClick = { onOpen(occurrence.event) })
                    }
                }
            }
        }
        // Recheck files after sync even when the list of photo IDs is unchanged.
        val previews = photos.asSequence().map { photoStore.photoFile(it.id) }
            .filter { it.isFile && it.length() >= 100L }.take(4).toList()
        Spacer(Modifier.height(12.dp))
        DottedDivider()
        Row(Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Family photos", style = MaterialTheme.typography.labelLarge)
                Text("${previews.size} preview${if (previews.size == 1) "" else "s"} · ${photos.size} saved",
                    color = Mono.Muted, style = MaterialTheme.typography.labelSmall)
            }
            previews.forEach { file ->
                AsyncImage(model = file, contentDescription = "Family photo preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(51.dp).clip(RoundedCornerShape(9.dp)))
            }
        }
    }
}

@Composable
private fun TimeLabel(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Mono.Muted)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Mono.Hairline))
    }
}

private fun durationLabel(occurrence: Occurrence): String {
    val minutes = Duration.between(occurrence.start, occurrence.end).toMinutes()
    return when {
        minutes <= 0 -> ""
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> if (minutes == 60L) "1 hour" else "${minutes / 60} hours"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}

@Composable
private fun ScheduleCard(occurrence: Occurrence, day: LocalDate, use24h: Boolean, onClick: () -> Unit) {
    val event = occurrence.event
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(eventTone(event.color)))
        Spacer(Modifier.height(14.dp))
        Text(
            event.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = event.location ?: event.calendar ?: event.notes?.lineSequence()?.firstOrNull()
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Mono.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(14.dp))
        DottedDivider()
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyphs.Clock, 16.dp, Mono.Muted)
            Spacer(Modifier.width(8.dp))
            val range = if (occurrence.allDay || occurrence.firstDay != day) {
                "All day"
            } else {
                formatTime(occurrence.start.toLocalTime(), use24h) + " – " +
                    formatTime(occurrence.end.toLocalTime(), use24h)
            }
            Text(range, style = MaterialTheme.typography.bodyMedium, color = Mono.Muted, modifier = Modifier.weight(1f))
            if (!occurrence.allDay) {
                Text(durationLabel(occurrence), style = MaterialTheme.typography.bodyMedium, color = Mono.Muted)
            }
        }
    }
}
