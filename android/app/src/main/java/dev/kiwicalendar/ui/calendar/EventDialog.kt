package dev.kiwicalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Event
import dev.kiwicalendar.ui.DottedDivider
import dev.kiwicalendar.ui.EventTypes
import dev.kiwicalendar.ui.GlassField
import dev.kiwicalendar.ui.Glyph
import dev.kiwicalendar.ui.GlyphIcon
import dev.kiwicalendar.ui.Glyphs
import dev.kiwicalendar.ui.Mono
import dev.kiwicalendar.ui.MonoSwitch
import dev.kiwicalendar.ui.PrimaryButton
import dev.kiwicalendar.ui.RoundButton
import dev.kiwicalendar.ui.components.QrCode
import dev.kiwicalendar.ui.components.TimePickerDialog
import dev.kiwicalendar.ui.components.webLink
import dev.kiwicalendar.ui.eventTone
import dev.kiwicalendar.ui.eventTypeFor
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.glass
import dev.kiwicalendar.ui.occurrence
import dev.kiwicalendar.ui.timeRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val DialogDate = DateTimeFormatter.ofPattern("EEE, MMMM d, yyyy")

private enum class Picking { Date, Start, End }

/** A floating card in the style of the dashboard; Material dialogs look out of place here. */
@Composable
private fun Sheet(onDismiss: () -> Unit, width: Int = 520, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .width(width.dp)
                .heightIn(max = 720.dp)
                .clip(shape)
                .background(Mono.Sheet)
                .border(1.dp, Mono.Border, shape)
                .verticalScroll(rememberScrollState())
                .padding(28.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Mono.Muted, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun StepButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Mono.CardStrong).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, 14.dp)
    }
}

@Composable
private fun Stepper(
    text: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.height(46.dp).glass(CircleShape).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(Glyphs.ChevronLeft, "Earlier", onPrevious)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f).clickable(onClick = onTap)
        )
        StepButton(Glyphs.ChevronRight, "Later", onNext)
    }
}

@Composable
private fun TypePicker(color: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val type = eventTypeFor(color)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .glass(RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(eventTone(color)))
            Spacer(Modifier.width(12.dp))
            Text(type.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            GlyphIcon(Glyphs.ChevronDown, 14.dp, Mono.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EventTypes.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(eventTone(option.hex)))
                            Spacer(Modifier.width(12.dp))
                            Text(option.name)
                        }
                    },
                    onClick = {
                        onPick(option.hex)
                        open = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(
    initial: Event?,
    defaultDate: LocalDate,
    use24h: Boolean,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (Event) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val existing = remember(initial) { initial?.occurrence(zone) }

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var date by remember { mutableStateOf(existing?.firstDay ?: defaultDate) }
    var start by remember {
        mutableStateOf(existing?.takeIf { !it.allDay }?.start?.toLocalTime() ?: LocalTime.of(9, 0))
    }
    var end by remember { mutableStateOf(existing?.takeIf { !it.allDay }?.end?.toLocalTime() ?: start.plusHours(1)) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: EventTypes.first().hex) }
    var picking by remember { mutableStateOf<Picking?>(null) }

    fun moveStart(to: LocalTime) {
        val length = Duration.between(start, end).let { if (it.isNegative) it.plusDays(1) else it }
        start = to
        end = to.plus(length)
    }

    fun build(): Event {
        val common = Event(
            id = initial?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            start = "",
            end = "",
            location = location.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            color = color
        )
        return if (allDay) {
            // Keep the length of an existing multi-day event when it's moved.
            val days = existing?.takeIf { it.allDay }
                ?.let { ChronoUnit.DAYS.between(it.firstDay, it.lastDay) + 1 } ?: 1L
            common.copy(start = date.toString(), end = date.plusDays(days).toString(), allDay = true)
        } else {
            val endDate = if (end.isAfter(start) || end == start) date else date.plusDays(1)
            common.copy(
                start = date.atTime(start).atZone(zone).toOffsetDateTime().toString(),
                end = endDate.atTime(end).atZone(zone).toOffsetDateTime().toString(),
                allDay = false
            )
        }
    }

    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = Mono.Ink, fontSize = 24.sp),
                cursorBrush = SolidColor(Mono.Ink),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                "Event name",
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                                color = Mono.Faint
                            )
                        }
                        inner()
                    }
                }
            )
            RoundButton(Glyphs.Close, "Close", onDismiss, 38.dp)
        }
        Spacer(Modifier.height(18.dp))
        DottedDivider()
        Spacer(Modifier.height(10.dp))

        FormRow("Date") {
            Stepper(
                text = date.format(DialogDate),
                onPrevious = { date = date.minusDays(1) },
                onNext = { date = date.plusDays(1) },
                onTap = { picking = Picking.Date },
                modifier = Modifier.fillMaxWidth()
            )
        }
        FormRow("All day") { MonoSwitch(allDay) { allDay = it } }
        if (!allDay) {
            FormRow("Hour") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stepper(
                        text = formatTime(start, use24h),
                        onPrevious = { moveStart(start.minusMinutes(15)) },
                        onNext = { moveStart(start.plusMinutes(15)) },
                        onTap = { picking = Picking.Start },
                        modifier = Modifier.weight(1f)
                    )
                    Stepper(
                        text = formatTime(end, use24h),
                        onPrevious = { end = end.minusMinutes(15) },
                        onNext = { end = end.plusMinutes(15) },
                        onTap = { picking = Picking.End },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        FormRow("Type") { TypePicker(color) { color = it } }
        FormRow("Where") { GlassField(location, { location = it }, "Add place") }
        FormRow("Note") { GlassField(notes, { notes = it }, "Add note", singleLine = false) }

        Spacer(Modifier.height(10.dp))
        DottedDivider()
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth()) {
            if (initial != null) {
                Text(
                    text = "Delete event",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.align(Alignment.CenterStart).clickable { onDelete(initial) }
                )
            }
            PrimaryButton(
                text = "Save",
                onClick = { onSave(build()) },
                enabled = title.isNotBlank(),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    when (picking) {
        Picking.Date -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        picking = null
                    }) { Text("Set date") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
            ) {
                DatePicker(state = state)
            }
        }
        Picking.Start -> TimePickerDialog(start, use24h, onDismiss = { picking = null }) {
            moveStart(it)
            picking = null
        }
        Picking.End -> TimePickerDialog(end, use24h, onDismiss = { picking = null }) {
            end = it
            picking = null
        }
        null -> Unit
    }
}

/** Events from a subscribed calendar are read-only; they change at the source. */
@Composable
fun EventDetails(event: Event, use24h: Boolean, onDismiss: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val occurrence = remember(event) { event.occurrence(zone) }
    Sheet(onDismiss, width = 480) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                modifier = Modifier.weight(1f)
            )
            RoundButton(Glyphs.Close, "Close", onDismiss, 38.dp)
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(eventTone(event.color)))
        Spacer(Modifier.height(10.dp))
        if (occurrence != null) {
            FormRow("Date") { Text(occurrence.firstDay.format(DialogDate), style = MaterialTheme.typography.bodyLarge) }
            FormRow("Time") {
                Text(timeRange(occurrence, occurrence.firstDay, use24h), style = MaterialTheme.typography.bodyLarge)
            }
        }
        event.location?.let { FormRow("Where") { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        event.notes?.let { FormRow("Note") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        Spacer(Modifier.height(10.dp))
        DottedDivider()
        Spacer(Modifier.height(14.dp))
        Text(
            text = "From ${event.calendar ?: "a subscribed calendar"}. Edit it there and it updates here.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mono.Muted
        )
    }
}

@Composable
fun PhoneCodeDialog(settings: AppSettings, onDismiss: () -> Unit) {
    Sheet(onDismiss, width = 600) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add from your phone",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                modifier = Modifier.weight(1f)
            )
            RoundButton(Glyphs.Close, "Close", onDismiss, 38.dp)
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(webLink(settings), Modifier.size(220.dp))
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Point your phone's camera at the code.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Events you add there show up here within two minutes. On a computer, open " +
                        settings.serverUrl + " and enter the access token.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mono.Muted
                )
            }
        }
    }
}
