package com.jks.organizator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.notif.Notifier
import com.jks.organizator.notif.Rules
import com.jks.organizator.notif.Track
import kotlinx.coroutines.launch
import java.time.LocalDate

private val FILTERS = listOf("Sve" to "", "Dnevno" to "daily", "Sedmično" to "weekly", "Mjesečno" to "monthly", "Godišnje" to "yearly", "Praćenje" to "track")

@Composable
fun RemindersScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by Graph.db.reminders().observeAll().collectAsState(emptyList())
    val logs by Graph.db.reminders().observeLogs().collectAsState(emptyList())
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var logging by remember { mutableStateOf<Reminder?>(null) }
    val today = LocalDate.now()

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

        if (filter == "track") {
            TrackingView(list.filter { it.track.isNotBlank() }, logs, onLog = { logging = it })
        } else {
            val shown = list.filter { filter.isEmpty() || it.repeat == filter }
            if (shown.isEmpty()) Text("Nema podsjetnika. Dodaj prvi dugmetom +.", style = T.small, modifier = Modifier.padding(top = 24.dp))
            listOf("daily" to "Dnevno", "weekly" to "Sedmično", "monthly" to "Mjesečno", "yearly" to "Godišnje").forEach { (k, name) ->
                val g = shown.filter { it.repeat == k }
                if (g.isNotEmpty()) {
                    Label(name, Modifier.padding(top = 8.dp, bottom = 8.dp))
                    AppCard(padding = 4.dp) {
                        g.forEach { r ->
                            val todayLog = logs.firstOrNull { it.reminderId == r.id && it.date == today.toString() && it.done }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { editing = r }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.title, style = T.body)
                                        if (r.track.isNotBlank()) Text("  · ${r.track}", style = T.small.copy(color = C.gold))
                                    }
                                    Text(Rules.describe(r) + if (todayLog != null) " · danas ✓" + (if (todayLog.amount > 0) " ${todayLog.amount} ${Track.unitName(r.unit)}" else "") else "", style = T.small)
                                }
                                if (r.track.isNotBlank()) TextButton(onClick = { logging = r }) { Text("Upiši", color = C.gold) }
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
    logging?.let { r -> LogDialog(r, onDismiss = { logging = null }) }
}

@Composable
private fun TrackingView(tracked: List<Reminder>, logs: List<ReminderLog>, onLog: (Reminder) -> Unit) {
    var period by remember { mutableStateOf("week") }
    val today = LocalDate.now()
    val from = when (period) {
        "week" -> today.minusDays(6)
        "month" -> today.withDayOfMonth(1)
        else -> today.withDayOfYear(1)
    }
    ChipRow {
        Chip("Zadnjih 7 dana", period == "week", { period = "week" })
        Chip("Ovaj mjesec", period == "month", { period = "month" })
        Chip("Ova godina", period == "year", { period = "year" })
    }
    Gap(12)
    if (tracked.isEmpty()) {
        AppCard {
            Text("Još nema praćenih navika", style = T.h2)
            Gap(4)
            Text("Dodaj podsjetnik i u polju „Vrsta“ izaberi Kuran, Knjiga ili Učenje. Onda možeš bilježiti koliko si uradio i vidjeti statistiku ovdje.", style = T.small)
        }
        return
    }
    tracked.forEach { r ->
        val rl = logs.filter { it.reminderId == r.id }
        val st = Track.stats(r, rl, from, today)
        val doneToday = rl.any { it.done && it.date == today.toString() }
        AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.track, style = T.h2)
                    Text(r.title, style = T.small)
                }
                GoldButton(if (doneToday) "Izmijeni danas" else "Upiši danas", onClick = { onLog(r) })
            }
            Gap(10)
            Row {
                Column(Modifier.weight(1f)) { Text("${st.daysDone}/${st.expected}", style = T.h2.copy(color = C.teal)); Text("dana urađeno", style = T.small) }
                if (r.unit.isNotBlank()) Column(Modifier.weight(1f)) { Text("${st.total}", style = T.h2); Text(Track.unitName(r.unit) + " ukupno", style = T.small) }
                Column(Modifier.weight(1f)) { Text("${st.streak}", style = T.h2.copy(color = C.gold)); Text("niz", style = T.small) }
            }
            if (st.expected > 0) { Gap(8); Bar(st.daysDone.toFloat() / st.expected, C.teal) }
            if (r.unit.isNotBlank() && st.daysDone > 0) Text("prosjek ${st.total / st.daysDone} ${Track.unitName(r.unit)} po danu", style = T.small, modifier = Modifier.padding(top = 6.dp))
            Gap(10)
            Label("Zadnjih 14 dana")
            Gap(6)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (13 downTo 0).forEach { back ->
                    val d = today.minusDays(back.toLong())
                    val on = rl.any { it.done && it.date == d.toString() }
                    val occurs = Rules.occursOn(r, d)
                    Box(
                        Modifier.weight(1f).height(22.dp)
                            .background(if (on) C.teal else if (occurs) C.surface2 else C.bg, RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun LogDialog(r: Reminder, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amount by remember { mutableStateOf("") }
    var hadLog by remember { mutableStateOf(false) }
    LaunchedEffect(date) {
        val l = Graph.db.reminders().log(r.id, date.toString())
        hadLog = l != null
        amount = if (l != null && l.amount > 0) l.amount.toString() else ""
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(r.track.ifBlank { r.title }, style = T.h2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Datum", date, { if (!it.isAfter(LocalDate.now())) date = it })
                if (r.unit.isNotBlank()) Field(
                    amount, { amount = it.filter(Char::isDigit).take(5) }, if (r.unit == "min") "Koliko minuta" else "Koliko stranica",
                    keyboard = KeyboardType.Number, suffix = Track.unitName(r.unit)
                )
                Text("Možeš unijeti i za prethodne dane.", style = T.small)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { Graph.db.reminders().setLog(ReminderLog(r.id, date.toString(), true, amount.toIntOrNull() ?: 0)) }
                onDismiss()
            }) { Text("Sačuvaj", color = C.gold) }
        },
        dismissButton = {
            Row {
                if (hadLog) TextButton(onClick = { scope.launch { Graph.db.reminders().deleteLog(r.id, date.toString()) }; onDismiss() }) { Text("Ukloni", color = C.danger) }
                TextButton(onClick = onDismiss) { Text("Odustani", color = C.muted) }
            }
        }
    )
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
    var track by remember { mutableStateOf(r.track) }
    var custom by remember { mutableStateOf(r.track.isNotBlank() && r.track !in Track.PRESETS) }
    var unit by remember { mutableStateOf(r.unit) }
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
                    "monthly" -> Field(monthDay, { monthDay = it.filter(Char::isDigit).take(2) }, "Dan u mjesecu (1–31)", keyboard = KeyboardType.Number)
                    "yearly" -> DateField("Datum (dan i mjesec)", yDate, { yDate = it })
                }
                Label("Vrsta")
                ChipRow {
                    Chip("Obični", track.isBlank() && !custom, { track = ""; custom = false; unit = "" })
                    Track.PRESETS.forEach { p ->
                        Chip(p, track == p && !custom, { track = p; custom = false; if (unit.isBlank()) unit = "min" })
                    }
                    Chip("Drugo", custom, { custom = true; if (track in Track.PRESETS || track.isBlank()) track = ""; if (unit.isBlank()) unit = "min" })
                }
                if (custom) Field(track, { track = it }, "Naziv navike (npr. Zikr, Vježbe)")
                if (track.isNotBlank() || custom) {
                    Text("Bilježi se kad uneseš ili klikneš „Jesam“, pa vidiš statistiku u Praćenju.", style = T.small)
                    Label("Mjeri se u")
                    ChipRow {
                        Chip("Minutama", unit == "min", { unit = "min" })
                        Chip("Stranicama", unit == "str", { unit = "str" })
                        Chip("Samo urađeno", unit == "", { unit = "" })
                    }
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
                val tr = track.trim()
                onSave(r.copy(title = title.trim(), time = String.format("%02d:%02d", time.hour, time.minute), repeat = repeat, dayRule = rule, track = tr, unit = if (tr.isBlank()) "" else unit))
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
