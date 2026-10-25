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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import dev.kiwicalendar.data.AppSettings
import dev.kiwicalendar.data.Event
import dev.kiwicalendar.ui.EventPalette
import dev.kiwicalendar.ui.components.QrCode
import dev.kiwicalendar.ui.components.SettingRow
import dev.kiwicalendar.ui.components.TimePickerDialog
import dev.kiwicalendar.ui.components.webLink
import dev.kiwicalendar.ui.formatTime
import dev.kiwicalendar.ui.occurrence
import dev.kiwicalendar.ui.parseHexColor
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

private val DialogDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

private enum class Picking { Date, Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
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
    var color by remember { mutableStateOf(initial?.color ?: EventPalette.first()) }
    var picking by remember { mutableStateOf<Picking?>(null) }

    fun build(): Event {
        val id = initial?.id ?: UUID.randomUUID().toString()
        val common = Event(
            id = id,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New event" else "Edit event") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingRow("All day") { Switch(checked = allDay, onCheckedChange = { allDay = it }) }
                OutlinedButton(onClick = { picking = Picking.Date }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.format(DialogDate))
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { picking = Picking.Start }, modifier = Modifier.weight(1f)) {
                            Text("Starts " + formatTime(start, use24h))
                        }
                        OutlinedButton(onClick = { picking = Picking.End }, modifier = Modifier.weight(1f)) {
                            Text("Ends " + formatTime(end, use24h))
                        }
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Where") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorRow(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(build()) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

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
        Picking.Start -> TimePickerDialog(start, use24h, onDismiss = { picking = null }) { picked ->
            val length = Duration.between(start, end).let { if (it.isNegative) it.plusDays(1) else it }
            start = picked
            end = picked.plus(length)
            picking = null
        }
        Picking.End -> TimePickerDialog(end, use24h, onDismiss = { picking = null }) { picked ->
            end = picked
            picking = null
        }
        null -> Unit
    }
}

@Composable
private fun ColorRow(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        EventPalette.forEach { hex ->
            val color = parseHexColor(hex) ?: return@forEach
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                Modifier
                    .size(30.dp)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(if (isSelected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

/** Events from a subscribed calendar are read-only here; they change at the source. */
@Composable
fun EventDetailsDialog(event: Event, use24h: Boolean, onDismiss: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val occurrence = remember(event) { event.occurrence(zone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (occurrence != null) {
                    Text(occurrence.firstDay.format(DialogDate), style = MaterialTheme.typography.bodyLarge)
                    Text(timeRange(occurrence, occurrence.firstDay, use24h), style = MaterialTheme.typography.bodyLarge)
                }
                event.location?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                event.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "From ${event.calendar ?: "a subscribed calendar"}. Edit it there and it updates here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun PhoneCodeDialog(settings: AppSettings, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add events from your phone") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(webLink(settings), Modifier.size(220.dp))
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Point your phone's camera at the code.", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Events you add there show up here within two minutes. On a computer, open " +
                            settings.serverUrl + " and use the access token.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
