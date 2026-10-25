package dev.kiwicalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kiwicalendar.data.AppContainer
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Event
import dev.kiwicalendar.ui.Occurrence
import dev.kiwicalendar.ui.components.Clock
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.occurrencesByDay
import dev.kiwicalendar.ui.parseHexColor
import dev.kiwicalendar.ui.relativeDay
import dev.kiwicalendar.ui.textOn
import dev.kiwicalendar.ui.timeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val LongDay = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val ShortDay = DateTimeFormatter.ofPattern("MMMM d")

@Composable
fun CalendarScreen(
    container: AppContainer,
    settings: AppSettings,
    today: LocalDate,
    onSleep: () -> Unit,
    onSettings: () -> Unit
) {
    val snapshot by container.repository.snapshot.collectAsStateWithLifecycle()
    val status by container.repository.status.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    // Keyed on today so the calendar jumps back to the current month at midnight.
    var month by rememberSaveable(today) { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable(today) { mutableStateOf(today) }
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Event?>(null) }
    var showPhoneCode by remember { mutableStateOf(false) }

    val firstDayOfWeek = if (settings.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val weeks = (ChronoUnit.DAYS.between(gridStart, month.atEndOfMonth()) / 7 + 1).toInt()
    val gridEnd = gridStart.plusDays(weeks * 7L - 1)
    val byDay = remember(snapshot.events, gridStart, gridEnd, selected) {
        occurrencesByDay(snapshot.events, minOf(gridStart, selected), maxOf(gridEnd, selected), zone)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Header(
                month = month,
                today = today,
                wide = wide,
                offline = status.error != null,
                use24h = settings.use24h,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = {
                    month = YearMonth.from(today)
                    selected = today
                },
                onAdd = { creating = true },
                onPhone = { showPhoneCode = true },
                onSleep = onSleep,
                onSettings = onSettings
            )
            Spacer(Modifier.height(16.dp))

            val grid: @Composable (Modifier) -> Unit = { modifier ->
                MonthGrid(
                    month = month,
                    gridStart = gridStart,
                    weeks = weeks,
                    today = today,
                    selected = selected,
                    byDay = byDay,
                    use24h = settings.use24h,
                    onSelect = { selected = it },
                    modifier = modifier
                )
            }
            val agenda: @Composable (Modifier) -> Unit = { modifier ->
                AgendaPanel(
                    day = selected,
                    today = today,
                    occurrences = byDay[selected].orEmpty(),
                    use24h = settings.use24h,
                    onOpen = { opened = it },
                    onAdd = { creating = true },
                    modifier = modifier
                )
            }
            if (wide) {
                Row(Modifier.weight(1f)) {
                    grid(Modifier.weight(2.4f).fillMaxHeight())
                    Spacer(Modifier.width(16.dp))
                    agenda(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                grid(Modifier.weight(1.3f).fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                agenda(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    val current = opened
    when {
        creating -> EventDialog(
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
        current != null && current.isLocal -> EventDialog(
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
        current != null -> EventDetailsDialog(current, settings.use24h, onDismiss = { opened = null })
    }
    if (showPhoneCode) PhoneCodeDialog(settings, onDismiss = { showPhoneCode = false })
}

@Composable
private fun Header(
    month: YearMonth,
    today: LocalDate,
    wide: Boolean,
    offline: Boolean,
    use24h: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    onPhone: () -> Unit,
    onSleep: () -> Unit,
    onSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(month.format(MonthTitle), style = MaterialTheme.typography.displaySmall, maxLines = 1)
            Text(
                text = if (offline) "Offline, showing saved events" else today.format(LongDay),
                style = MaterialTheme.typography.titleMedium,
                color = if (offline) colors.error else colors.onSurfaceVariant
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        TextButton(onClick = onToday) { Text("Today") }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
        Spacer(Modifier.width(8.dp))
        HeaderAction(Icons.Default.Add, "Add", wide, onAdd, primary = true)
        HeaderAction(Icons.Default.Phone, "From phone", wide, onPhone)
        HeaderAction(Icons.Default.PlayArrow, "Photos", wide, onSleep)
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        if (wide) {
            Spacer(Modifier.width(12.dp))
            Clock(use24h, style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    if (!showLabel) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
        return
    }
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
    val modifier = Modifier.padding(start = 8.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier, content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, content = content)
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
    use24h: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            repeat(7) { i ->
                Text(
                    text = gridStart.plusDays(
                        i.toLong()
                    ).dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
            }
        }
        repeat(weeks) { week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(7) { dayIndex ->
                    val date = gridStart.plusDays((week * 7 + dayIndex).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        occurrences = byDay[date].orEmpty(),
                        use24h = use24h,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

private val ChipSlot = 21.dp

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    occurrences: List<Occurrence>,
    use24h: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colors.primaryContainer
        inMonth -> colors.surface
        else -> colors.surface.copy(alpha = 0.45f)
    }
    BoxWithConstraints(
        modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        val slots = ((maxHeight - 32.dp) / ChipSlot).toInt().coerceAtLeast(0)
        val shown = if (occurrences.size > slots) (slots - 1).coerceAtLeast(0) else occurrences.size
        Column {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isToday) colors.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        isToday -> colors.onPrimary
                        inMonth -> colors.onSurface
                        else -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            occurrences.take(shown).forEach { EventChip(it, date, use24h) }
            if (occurrences.size > shown) {
                Text(
                    text = "${occurrences.size - shown} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EventChip(occurrence: Occurrence, day: LocalDate, use24h: Boolean) {
    val color = parseHexColor(occurrence.event.color) ?: MaterialTheme.colorScheme.primary
    if (occurrence.allDay) {
        Box(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .fillMaxWidth()
                .height(ChipSlot - 2.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = occurrence.event.title,
                color = textOn(color),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().height(ChipSlot), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            val time = if (occurrence.firstDay == day) formatTime(occurrence.start.toLocalTime(), use24h) + " " else ""
            Text(
                text = time + occurrence.event.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AgendaPanel(
    day: LocalDate,
    today: LocalDate,
    occurrences: List<Occurrence>,
    use24h: Boolean,
    onOpen: (Event) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier
) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Text(
                relativeDay(day, today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(day.format(ShortDay), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (occurrences.isEmpty()) {
                Text(
                    "Nothing planned.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onAdd, contentPadding = PaddingValues(0.dp)) { Text("Add an event") }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(occurrences, key = { it.event.id }) { occurrence ->
                        AgendaRow(occurrence, day, use24h, onClick = { onOpen(occurrence.event) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(occurrence: Occurrence, day: LocalDate, use24h: Boolean, onClick: () -> Unit) {
    val color = parseHexColor(occurrence.event.color) ?: MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(occurrence.event.title, style = MaterialTheme.typography.titleMedium)
            Text(timeRange(occurrence, day, use24h), style = MaterialTheme.typography.bodyMedium, color = muted)
            val where = listOfNotNull(occurrence.event.location, occurrence.event.calendar).joinToString(", ")
            if (where.isNotEmpty()) {
                Text(
                    text = where,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
