package com.jks.organizator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.notif.Notifier
import com.jks.organizator.notif.Rules
import kotlinx.coroutines.launch
import java.time.LocalDate

private val FILTERS = listOf("Sve" to "", "Dnevno" to "daily", "Sedmično" to "weekly", "Mjesečno" to "monthly", "Godišnje" to "yearly")

@Composable
fun RemindersScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by Graph.db.reminders().observeAll().collectAsState(emptyList())
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    Page(
        title = "Podsjetnici",
        fab = {
            FloatingActionButton(onClick = { editing = Reminder(createdDay = LocalDate.now().toEpochDay()) }, containerColor = C.gold, contentColor = C.bg) {
                Icon(Icons.Outlined.Add, "Novi podsjetnik")
            }
        }
    ) {
        ChipRow { FILTERS.forEach { (n, k) -> Chip(n, filter == k, { filter = k }) } }
        Gap(12)
        val shown = list.filter { filter.isEmpty() || it.repeat == filter }
        if (shown.isEmpty()) Text("Nema podsjetnika. Dodaj prvi dugmetom +.", style = T.small, modifier = Modifier.padding(top = 24.dp))
        listOf("daily" to "Dnevno", "weekly" to "Sedmično", "monthly" to "Mjesečno", "yearly" to "Godišnje").forEach { (k, name) ->
            val g = shown.filter { it.repeat == k }
            if (g.isNotEmpty()) {
                Label(name, Modifier.padding(top = 8.dp, bottom = 8.dp))
                AppCard(padding = 4.dp) {
                    g.forEachIndexed { i, r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { editing = r }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = T.body)
                                Text(Rules.describe(r), style = T.small)
                            }
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = { on ->
                                    scope.launch {
                                        val n = r.copy(enabled = on)
                                        Graph.db.reminders().insert(n); Notifier.scheduleReminder(ctx, n)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = C.gold, checkedThumbColor = C.bg, uncheckedTrackColor = C.surface2, uncheckedThumbColor = C.muted, uncheckedBorderColor = C.line)
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { r ->
        ReminderEditor(r, onDismiss = { editing = null }, onSave = { n ->
            scope.launch {
                val id = Graph.db.reminders().insert(n)
                Notifier.scheduleReminder(ctx, n.copy(id = id))
            }
        }, onDelete = {
            scope.launch { Notifier.cancelReminder(ctx, r); Graph.db.reminders().delete(r) }
        })
    }
}

@Composable
private fun ReminderEditor(r: Reminder, onDismiss: () -> Unit, onSave: (Reminder) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(r.title) }
    var time by remember { mutableStateOf(parseTime(r.time)) }
    var repeat by remember { mutableStateOf(r.repeat) }
    var days by remember { mutableStateOf(r.dayRule.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet().let { if (r.repeat == "weekly") it else emptySet() }) }
    var monthDay by remember { mutableStateOf(if (r.repeat == "monthly") r.dayRule else "1") }
    var yDate by remember {
        mutableStateOf(
            r.dayRule.split("-").takeIf { r.repeat == "yearly" && it.size == 2 }?.let { LocalDate.of(2024, it[0].toInt(), it[1].toInt()) }
                ?: LocalDate.now()
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(if (r.id == 0L) "Novi podsjetnik" else "Podsjetnik", style = T.h2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(title, { title = it }, "Naslov")
                TimeField("Vrijeme", time, { time = it })
                Label("Ponavljanje")
                ChipRow {
                    listOf("daily" to "Dnevno", "weekly" to "Sedmično", "monthly" to "Mjesečno", "yearly" to "Godišnje").forEach { (k, n) ->
                        Chip(n, repeat == k, { repeat = k })
                    }
                }
                when (repeat) {
                    "weekly" -> ChipRow {
                        DAYS_SHORT.forEachIndexed { i, n ->
                            Chip(n, (i + 1) in days, { days = if ((i + 1) in days) days - (i + 1) else days + (i + 1) })
                        }
                    }
                    "monthly" -> Field(monthDay, { monthDay = it.filter(Char::isDigit).take(2) }, "Dan u mjesecu (1–31)", keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                    "yearly" -> DateField("Datum (dan i mjesec)", yDate, { yDate = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                val rule = when (repeat) {
                    "weekly" -> days.sorted().joinToString(",")
                    "monthly" -> (monthDay.toIntOrNull() ?: 1).coerceIn(1, 31).toString()
                    "yearly" -> String.format("%02d-%02d", yDate.monthValue, yDate.dayOfMonth)
                    else -> ""
                }
                onSave(r.copy(title = title.trim(), time = String.format("%02d:%02d", time.hour, time.minute), repeat = repeat, dayRule = rule))
                onDismiss()
            }) { Text("Sačuvaj", color = C.gold) }
        },
        dismissButton = {
            Row {
                if (r.id != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Obriši", color = C.danger) }
                TextButton(onClick = onDismiss) { Text("Odustani", color = C.muted) }
            }
        }
    )
    if (confirmDelete) Confirm("Obrisati podsjetnik?", r.title, onOk = { onDelete(); onDismiss() }, onDismiss = { confirmDelete = false })
}
