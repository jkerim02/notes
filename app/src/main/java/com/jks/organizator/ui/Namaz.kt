package com.jks.organizator.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jks.organizator.data.*
import com.jks.organizator.net.Gemini
import com.jks.organizator.net.Vaktija
import com.jks.organizator.notif.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

fun streak(done: List<PrayerLog>, today: LocalDate): Int {
    val full = done.filter { isFarz(it.vakat) }.groupBy { it.date }.filter { it.value.size >= 5 }.keys
    var d = today
    if (d.key() !in full) d = d.minusDays(1)
    var n = 0
    while (d.key() in full) { n++; d = d.minusDays(1) }
    return n
}

@Composable
fun NamazScreen(onProfile: () -> Unit, onMonthly: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.prefs.flow.collectAsState(Settings())
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    val date = now.toLocalDate()
    val dao = Graph.db.prayer()
    val day by dao.observeDay(date.key(), s.cityId).collectAsState(null)
    val tomorrow by dao.observeDay(date.plusDays(1).key(), s.cityId).collectAsState(null)
    var back by remember { mutableStateOf(0) }
    val selDate = date.minusDays(back.toLong())
    val selDay by dao.observeDay(selDate.key(), s.cityId).collectAsState(null)
    val logs by dao.observeLogs(selDate.key(), selDate.key()).collectAsState(emptyList())
    val yLogs by dao.observeLogs(date.minusDays(1).key(), date.minusDays(1).key()).collectAsState(emptyList())
    val allDone by dao.observeDone().collectAsState(emptyList())
    var fetchError by remember { mutableStateOf(false) }

    LaunchedEffect(s.cityId) {
        val n = runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { Vaktija.ensure(s.cityId) } }
        fetchError = n.isFailure
        runCatching { Notifier.rescheduleAll(ctx) }
    }

    LaunchedEffect(selDate, s.cityId) {
        if (back > 0 && selDay == null) runCatching { Vaktija.ensureDay(s.cityId, selDate) }
    }

    val times = day?.parsed()
    val doneSet = logs.filter { it.done }.map { it.vakat }.toSet()

    Page {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(44, s, onProfile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(date.bs(), style = T.medium)
                Text(s.city, style = T.small)
            }
            IconButton(onClick = onMonthly) { Icon(Icons.Outlined.CalendarMonth, "Mjesečni pregled", tint = C.gold) }
        }
        Gap(12)
        ChipRow {
            listOf("Danas", "Jučer", "Prije 2 dana", "Prije 3 dana").forEachIndexed { i, n -> Chip(n, back == i, { back = i }) }
        }
        Gap(12)

        val zora = times?.get(0)
        val yIsha = yLogs.any { it.vakat == 5 && it.done }
        if (back == 0 && zora != null && now.toLocalTime().isBefore(zora) && !yIsha) {
            AppCard(color = C.surface2, border = C.gold) {
                Text("Jacija od jučer još nije označena", style = T.medium)
                Text("Ako si je klanjao poslije ponoći, označi je ovdje.", style = T.small)
                Gap(8)
                GoldButton("Označi jaciju za jučer") { scope.launch { dao.setLog(PrayerLog(date.minusDays(1).key(), 5, true)) } }
            }
            Gap(12)
        }

        if (back > 0) {
            val st = selDay?.parsed()
            AppCard(padding = 4.dp) {
                Text("${selDate.bs()}", style = T.medium, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
                VAKATS.indices.forEach { i ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(VAKATS[i], style = T.body, modifier = Modifier.weight(1f))
                        if (st != null) Text(fmtTime(st[i]), style = T.medium.copy(color = C.text2))
                        if (isFarz(i)) Circle(i in doneSet, false) { scope.launch { dao.setLog(PrayerLog(selDate.key(), i, i !in doneSet)) } }
                        else Spacer(Modifier.width(44.dp))
                    }
                    if (i < 5) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.line))
                }
            }
            Gap(8)
            Text("${doneSet.count { isFarz(it) }} od 5 klanjano. Možeš mijenjati zadnja 3 dana.", style = T.small)
        } else if (times == null) {
            AppCard {
                Text("Vremena namaza nisu učitana", style = T.h2)
                Gap(4)
                Text(
                    if (fetchError) "Provjeri internet vezu; vremena se čuvaju lokalno čim se jednom povuku."
                    else "Učitavam sa Vaktija.ba…", style = T.small
                )
            }
        } else {
            var nextIdx = times.indexOfFirst { LocalDateTime.of(date, it).isAfter(now) }
            val nextDt: LocalDateTime
            val prevDt: LocalDateTime
            if (nextIdx >= 0) {
                nextDt = LocalDateTime.of(date, times[nextIdx])
                prevDt = if (nextIdx > 0) LocalDateTime.of(date, times[nextIdx - 1]) else LocalDateTime.of(date, times[5]).minusDays(1)
            } else {
                nextIdx = 0
                val t0 = tomorrow?.parsed()?.get(0) ?: times[0]
                nextDt = LocalDateTime.of(date.plusDays(1), t0)
                prevDt = LocalDateTime.of(date, times[5])
            }
            val total = Duration.between(prevDt, nextDt).seconds.coerceAtLeast(1)
            val el = Duration.between(prevDt, now).seconds.coerceAtLeast(0)
            val left = Duration.between(now, nextDt).seconds.coerceAtLeast(0)

            AppCard(border = C.gold, color = C.surface2) {
                Label("Sljedeći vakat", color = C.gold)
                Gap(4)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(VAKATS[nextIdx], style = T.h2, modifier = Modifier.weight(1f))
                    Text(fmtTime(nextDt.toLocalTime()), style = T.big)
                }
                Gap(8)
                Bar(el.toFloat() / total, C.gold)
                Gap(6)
                Text("za ${left / 3600} h ${(left % 3600) / 60} min ${left % 60} s", style = T.small.copy(color = C.text2))
            }
            Gap(12)

            AppCard(padding = 4.dp) {
                times.forEachIndexed { i, t ->
                    val isNext = i == nextIdx
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(VAKATS[i], style = T.body.copy(color = if (isNext) C.gold else C.text), modifier = Modifier.weight(1f))
                        Text(fmtTime(t), style = T.medium.copy(color = if (isNext) C.gold else C.text2))
                        if (isFarz(i)) {
                            Circle(i in doneSet, isNext) {
                                scope.launch { dao.setLog(PrayerLog(date.key(), i, i !in doneSet)) }
                            }
                        } else Spacer(Modifier.width(44.dp))
                    }
                    if (i < 5) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.line))
                }
            }
            Gap(12)

            val passedFarz = times.indices.count { isFarz(it) && !LocalDateTime.of(date, times[it]).isAfter(now) }
            val doneFarz = doneSet.count { isFarz(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.weight(1f)) {
                    Label("Danas")
                    Gap(4)
                    Text("$doneFarz od $passedFarz", style = T.h2)
                    Text("do sada", style = T.small)
                }
                AppCard(Modifier.weight(1f)) {
                    Label("Niz")
                    Gap(4)
                    Text("${streak(allDone, date)} dana", style = T.h2)
                    Text("svi vakti", style = T.small)
                }
            }
        }

        if (s.gemHadith || true) {
            Gap(12)
            HadithCard(s, date)
        }
    }
}

@Composable
private fun HadithCard(s: Settings, date: LocalDate) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shuffle by remember(date) { mutableStateOf(0) }
    var h by remember(date) { mutableStateOf<Hadith?>(null) }
    LaunchedEffect(date, shuffle) { h = Hadiths.pick(ctx, date, shuffle) }
    var bs by remember { mutableStateOf<String?>(null) }
    var meaning by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(h) { bs = h?.let { Hadiths.savedTranslation(ctx, it) }; meaning = null; err = null }

    val cur = h
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Hadis dana", Modifier.weight(1f))
            if (cur != null && cur.grade.isNotBlank()) Box(Modifier.clip(RoundedCornerShape(50)).background(C.surface2).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(cur.grade, style = T.small.copy(color = C.teal))
            }
        }
        if (cur == null) { Gap(10); Text("Učitavam…", style = T.small); return@AppCard }
        Gap(10)
        Text(cur.arabic, style = T.arabic.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right), modifier = Modifier.fillMaxWidth())
        Gap(10)
        if (cur.narrator.isNotBlank()) Text(cur.narrator, style = T.small.copy(color = C.text2))
        Text(cur.english, style = T.body.copy(color = C.text2, lineHeight = 21.sp), modifier = Modifier.padding(top = 4.dp))
        Gap(6)
        Text("${cur.bookName} · br. ${cur.number}" + if (cur.grade.isBlank()) " · stepen nije naveden" else "", style = T.small)
        bs?.let {
            Gap(10)
            Label("Bosanski (Gemini prijevod)", color = C.gold)
            Text(it, style = T.body.copy(lineHeight = 21.sp), modifier = Modifier.padding(top = 4.dp))
            Text("Strojni prijevod; ne zamjenjuje provjereni prijevod.", style = T.small, modifier = Modifier.padding(top = 4.dp))
        }
        if (loading || err != null || meaning != null) {
            Gap(10)
            GeminiCard("Značenje · Gemini", meaning, loading, err)
            if (meaning != null) Text("Kratko pomoćno značenje; za dublje tumačenje pitaj učenjaka.", style = T.small, modifier = Modifier.padding(top = 6.dp))
        }
        Gap(12)
        if (s.gemHadith) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bs == null) GhostButton("Prevedi na bosanski", Modifier.weight(1f), enabled = !loading) {
                loading = true; err = null; meaning = null
                scope.launch {
                    Gemini.generate(
                        "Prevedi doslovno na bosanski jezik sljedeći hadis iz engleskog. Ne dodaj, ne izostavljaj i ne tumači ništa; " +
                            "vrati samo prijevod.\n${cur.narrator}\n${cur.english}"
                    ).onSuccess { Hadiths.saveTranslation(ctx, cur, it); bs = it }.onFailure { err = it.message }
                    loading = false
                }
            }
            GhostButton("Objasni", Modifier.weight(1f), enabled = !loading) {
                loading = true; err = null
                scope.launch {
                    Gemini.generate(
                        "Objasni kratko (najviše 3 rečenice) na bosanskom jeziku značenje ovog hadisa. Ne mijenjaj tekst hadisa, samo pojasni značenje.\n" +
                            "Hadis: ${cur.english}\nIzvor: ${cur.bookName} ${cur.number}"
                    ).onSuccess { meaning = it }.onFailure { err = it.message }
                    loading = false
                }
            }
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Drugi hadis", Modifier.weight(1f)) { shuffle++ }
            GhostButton("Sačuvaj u bilješke", Modifier.weight(1f)) {
                scope.launch {
                    Graph.db.notes().insert(
                        Note(
                            title = "Hadis · ${cur.bookName} ${cur.number}", tag = "Hadis", updatedAt = System.currentTimeMillis(),
                            body = cur.arabic + "\n\n" + cur.narrator + " " + cur.english + "\n\n(" + cur.bookName + " " + cur.number + ")" +
                                (bs?.let { "\n\nBosanski (Gemini): $it" } ?: "") + (meaning?.let { "\n\nZnačenje: $it" } ?: "")
                        )
                    )
                    Toast.makeText(ctx, "Sačuvano u bilješke", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun MonthlyScreen(onBack: () -> Unit, onReport: () -> Unit) {
    val dao = Graph.db.prayer()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()
    val first = ym.atDay(1)
    val logs by dao.observeLogs(first.key(), ym.atEndOfMonth().key()).collectAsState(emptyList())
    val byDay = logs.filter { it.done && isFarz(it.vakat) }.groupBy { it.date }
    val elapsedDays = when {
        ym.isBefore(YearMonth.from(today)) -> ym.lengthOfMonth()
        ym == YearMonth.from(today) -> today.dayOfMonth
        else -> 0
    }
    val done = (1..elapsedDays).sumOf { byDay[ym.atDay(it).key()]?.size ?: 0 }
    val totalP = elapsedDays * 5
    val pct = if (totalP == 0) 0 else done * 100 / totalP

    Page(title = "Mjesečni pregled", onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Prethodni", tint = C.text) }
            Text("${MONTHS_BS[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}", style = T.h2, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Sljedeći", tint = C.text) }
        }
        Gap(8)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$pct%", style = T.big)
            Spacer(Modifier.width(12.dp))
            Text("$done od $totalP namaza", style = T.small, modifier = Modifier.padding(bottom = 8.dp))
        }
        Gap(12)
        AppCard {
            Row(Modifier.fillMaxWidth()) {
                DAYS_SHORT.forEach { Text(it, style = T.small, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
            }
            Gap(6)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + ym.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    for (c in 0..6) {
                        val dayNum = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..ym.lengthOfMonth()) {
                                val d = ym.atDay(dayNum)
                                val n = byDay[d.key()]?.size ?: 0
                                val future = d.isAfter(today)
                                val shape = RoundedCornerShape(10.dp)
                                var m = Modifier.fillMaxSize().clip(shape)
                                m = if (future) m.border(BorderStroke(1.dp, C.line), shape) else m.background(C.scale[n.coerceIn(0, 5)], shape)
                                if (d == today) m = m.border(2.dp, C.gold, shape)
                                Box(m, contentAlignment = Alignment.Center) {
                                    Text(
                                        "$dayNum", style = T.small.copy(
                                            color = if (future) C.muted else if (n >= 4) C.bg else C.text, fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Gap(12)
        AppCard {
            Label("Po vaktu")
            Gap(8)
            listOf(0, 2, 3, 4, 5).forEach { v ->
                val c = (1..elapsedDays).count { d -> logs.any { it.done && it.vakat == v && it.date == ym.atDay(d).key() } }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Text(VAKATS[v], style = T.body, modifier = Modifier.width(80.dp))
                    Bar(if (elapsedDays == 0) 0f else c.toFloat() / elapsedDays, C.teal, modifier = Modifier.weight(1f))
                    Text("  $c/$elapsedDays", style = T.small, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }
        Gap(12)
        GoldButton("Izvještaj: sedmica, mjesec, godina", Modifier.fillMaxWidth(), onClick = onReport)
    }
}
