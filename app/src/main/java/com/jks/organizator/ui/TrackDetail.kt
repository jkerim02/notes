package com.jks.organizator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jks.organizator.data.*
import com.jks.organizator.notif.Rules
import com.jks.organizator.notif.Track
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Cijela historija jedne praćene navike: od dana kreiranja (ili prvog zapisa) do danas. */
@Composable
fun TrackDetailScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by Graph.db.reminders().observeAll().collectAsState(null)
    val allLogs by Graph.db.reminders().observeLogs().collectAsState(emptyList())
    var logging by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf(false) }
    val r = all?.firstOrNull { it.id == id }
    val today = LocalDate.now()

    if (all == null) { Page(title = "Praćenje", onBack = onBack) {}; return }
    if (r == null) { Page(title = "Praćenje", onBack = onBack) { Text("Navika ne postoji.", style = T.small) }; return }

    val logs = allLogs.filter { it.reminderId == id && it.done }
    val created = if (r.createdDay > 0) LocalDate.ofEpochDay(r.createdDay) else today
    val firstLog = logs.minOfOrNull { it.date }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val start = listOfNotNull(created, firstLog).min()
    val st = Track.stats(r, logs, start, today, ignoreCreated = true)
    val best = Track.bestStreak(r, logs, start, today)
    val daysSince = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1

    Page(title = r.track.ifBlank { r.title }, onBack = onBack, actions = {
        GhostButton("Uredi", onClick = { editing = true })
    }) {
        Text(r.title + " · " + Rules.describe(r), style = T.small)
        Gap(12)
        AppCard(border = C.gold, color = C.surface2) {
            Label("Od ${start.dayOfMonth}.${start.monthValue}.${start.year}. do danas ($daysSince dana)", color = C.gold)
            Gap(8)
            Row {
                Column(Modifier.weight(1f)) { Text("${st.daysDone}/${st.expected}", style = T.h2.copy(color = C.teal)); Text("dana urađeno", style = T.small) }
                if (r.unit.isNotBlank()) Column(Modifier.weight(1f)) { Text("${st.total}", style = T.h2); Text(Track.unitName(r.unit) + " ukupno", style = T.small) }
                Column(Modifier.weight(1f)) { Text("${st.streak}", style = T.h2.copy(color = C.gold)); Text("trenutni niz", style = T.small) }
            }
            Gap(8)
            if (st.expected > 0) Bar(st.daysDone.toFloat() / st.expected, C.teal)
            Gap(6)
            Text(
                "najduži niz $best" + if (r.unit.isNotBlank() && st.daysDone > 0) " · prosjek ${st.total / st.daysDone} ${Track.unitName(r.unit)} po danu" else "",
                style = T.small.copy(color = C.text2)
            )
        }
        Gap(12)
        GoldButton("Upiši danas", Modifier.fillMaxWidth()) { logging = today }

        Gap(16)
        Label("Zadnjih 14 dana")
        Gap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (13 downTo 0).forEach { back ->
                val d = today.minusDays(back.toLong())
                val on = logs.any { it.date == d.toString() }
                Box(Modifier.weight(1f).height(22.dp).background(if (on) C.teal else if (Rules.occursOn(r, d)) C.surface2 else C.bg, RoundedCornerShape(5.dp)))
            }
        }

        Gap(16)
        Label("Cijela historija po mjesecima")
        Gap(8)
        var ym = YearMonth.from(today)
        val first = YearMonth.from(start)
        while (!ym.isBefore(first)) {
            MonthBlock(r, logs, ym, start, today) { logging = it }
            Gap(10)
            ym = ym.minusMonths(1)
        }
        Text("Dodirni dan da upišeš ili izmijeniš.", style = T.small)
    }

    logging?.let { d -> LogDialog(r, d, onDismiss = { logging = null }) }
    if (editing) ReminderEditor(
        r, onDismiss = { editing = false },
        onSave = { n -> scope.launch { saveReminder(ctx, n) } },
        onDelete = { scope.launch { deleteReminder(ctx, r) }; onBack() }
    )
}

@Composable
private fun MonthBlock(r: Reminder, logs: List<ReminderLog>, ym: YearMonth, start: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val byDate = logs.associateBy { it.date }
    val from = maxOf(ym.atDay(1), start)
    val to = minOf(ym.atEndOfMonth(), today)
    var expected = 0; var done = 0; var total = 0
    var d = from
    while (!d.isAfter(to)) {
        if (Rules.occursOn(r, d)) { expected++; byDate[d.toString()]?.let { done++; total += it.amount } }
        d = d.plusDays(1)
    }
    AppCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${MONTHS_BS[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}", style = T.medium, modifier = Modifier.weight(1f))
            Text("$done/$expected dana" + if (r.unit.isNotBlank() && total > 0) " · $total ${Track.unitName(r.unit)}" else "", style = T.small.copy(color = C.text2))
        }
        Gap(8)
        val offset = ym.atDay(1).dayOfWeek.value - 1
        val rows = (offset + ym.lengthOfMonth() + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val dayNum = row * 7 + c - offset + 1
                    Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum)
                            val inRange = !date.isBefore(start) && !date.isAfter(today)
                            val log = byDate[date.toString()]
                            val occurs = Rules.occursOn(r, date)
                            val bg = when {
                                log != null -> C.teal
                                inRange && occurs -> C.scale[1]
                                else -> C.bg
                            }
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                Modifier.fillMaxSize().clip(shape).background(bg, shape).then(if (inRange) Modifier.clickable { onDay(date) } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$dayNum", style = T.small.copy(color = if (log != null) C.bg else if (inRange) C.text2 else C.line, fontSize = 12.sp), textAlign = TextAlign.Center)
                                    if (log != null && log.amount > 0) Text("${log.amount}", style = T.small.copy(color = C.bg, fontSize = 9.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
