package com.jks.organizator.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.notif.Rules
import com.jks.organizator.notif.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val M_PRAYER = 1
const val M_BOOKS = 2
const val M_REMINDERS = 4
const val M_WALLET = 8
const val M_ALL = 15

data class Report(
    val from: LocalDate, val to: LocalDate, val elapsed: Int,
    val prayerDone: Int, val prayerTotal: Int, val perVakat: List<Int>, val monthly: List<Pair<String, Int>>,
    val pages: Int, val newWords: Int, val finishedBooks: Int, val readDays: Int, val hasBooks: Boolean,
    val remDone: Int, val remTotal: Int,
    val earned: Double, val spent: Double, val saved: Double, val biggest: String?,
    val missed: List<String>, val good: List<String>,
    val tracked: List<String> = emptyList()
) {
    val left get() = earned - spent - saved
}

suspend fun buildReport(from: LocalDate, to: LocalDate): Report = withContext(Dispatchers.IO) {
    val db = Graph.db
    val today = LocalDate.now()
    val end = if (to.isAfter(today)) today else to
    val elapsed = if (end.isBefore(from)) 0 else (ChronoUnit.DAYS.between(from, end) + 1).toInt()
    val fk = from.key(); val ek = end.key()
    fun inRange(d: String) = d >= fk && d <= ek

    // namaz
    val logs = db.prayer().logs(fk, ek).filter { it.done && isFarz(it.vakat) }
    val prayerTotal = elapsed * 5
    val perVakat = listOf(0, 2, 3, 4, 5).map { v -> logs.count { it.vakat == v } }
    val monthly = if (elapsed > 60) {
        val out = mutableListOf<Pair<String, Int>>()
        var m = java.time.YearMonth.from(from)
        while (!m.isAfter(java.time.YearMonth.from(end))) {
            val a = maxOf(m.atDay(1), from); val b = minOf(m.atEndOfMonth(), end)
            val days = (ChronoUnit.DAYS.between(a, b) + 1).toInt()
            val done = logs.count { it.date >= a.key() && it.date <= b.key() }
            out.add(MONTHS_BS[m.monthValue - 1].take(3) to (if (days == 0) 0 else done * 100 / (days * 5)))
            m = m.plusMonths(1)
        }
        out
    } else emptyList()

    // knjige
    val books = db.books().books()
    val reads = db.books().readings().filter { inRange(it.date) }
    val pages = reads.sumOf { it.pages }
    val words = db.books().words().count { w ->
        val d = java.time.Instant.ofEpochMilli(w.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        !d.isBefore(from) && !d.isAfter(end)
    }
    val finished = books.count { it.finishedDay != 0L && it.finishedDay >= from.toEpochDay() && it.finishedDay <= end.toEpochDay() }
    val readDays = reads.map { it.date }.distinct().size

    // podsjetnici
    val rems = db.reminders().all().filter { it.enabled }
    var remTotal = 0
    for (r in rems) {
        var d = from
        while (!d.isAfter(end)) {
            if (d.toEpochDay() >= r.createdDay && Rules.occursOn(r, d)) remTotal++
            d = d.plusDays(1)
        }
    }
    val remDone = minOf(remTotal, db.reminders().logs(fk, ek).count { it.done })

    // novčanik
    val tx = db.wallet().txns().filter { inRange(it.effDate()) }
    val earned = tx.filter { it.kind == "earned" }.sumOf { it.amount }
    val spentL = tx.filter { it.kind == "spent" }
    val spent = spentL.sumOf { it.amount }
    val saved = tx.filter { it.kind == "saved" }.sumOf { it.amount }
    val cats = db.wallet().categories().associate { it.id to it.name }
    val big = spentL.maxByOrNull { it.amount }?.let { "${money(it.amount)} KM · ${cats[it.categoryId] ?: "Ostalo"}" + if (it.place.isNotBlank()) " (${it.place})" else "" }

    // praćene navike (Kur'an, knjige, učenje...)
    val trackedLines = mutableListOf<String>()
    val remLogs = db.reminders().logs(fk, ek)
    for (r in rems.filter { it.track.isNotBlank() }) {
        val st = Track.stats(r, remLogs.filter { it.reminderId == r.id }, from, end)
        trackedLines.add("${r.track}: ${st.daysDone} od ${st.expected} dana" + if (r.unit.isNotBlank() && st.total > 0) ", ukupno ${st.total} ${Track.unitName(r.unit)}" else "")
    }

    // promaklo / dobro
    val missed = mutableListOf<String>()
    val good = mutableListOf<String>()
    if (elapsed > 0) {
        val miss = prayerTotal - logs.size
        if (miss > 0) missed.add("$miss propuštenih vakata od $prayerTotal")
        if (prayerTotal > 0 && logs.size * 100 / prayerTotal >= 90) good.add("Klanjano ${logs.size * 100 / prayerTotal}% namaza")
        val allDone = db.prayer().allDone()
        val st = streak(allDone, today)
        if (st >= 3) good.add("Niz od $st dana s klanjanim svim vaktovima")
        if (remTotal - remDone > 0) missed.add("${remTotal - remDone} preskočenih podsjetnika")
        else if (remTotal > 0) good.add("Svi podsjetnici urađeni ($remTotal)")
        if (books.isNotEmpty()) {
            if (elapsed - readDays > 0) missed.add("${elapsed - readDays} dana bez čitanja")
            if (readDays >= elapsed / 2 && readDays > 0) good.add("Čitao si $readDays od $elapsed dana ($pages str.)")
        }
        if (finished > 0) good.add("Završeno knjiga: $finished")
        val goals = db.wallet().goals()
        val allTx = db.wallet().txns()
        for (g in goals) {
            val before = allTx.filter { it.kind == "saved" && it.goalId == g.id && it.date < fk }.sumOf { it.amount }
            val inP = tx.filter { it.kind == "saved" && it.goalId == g.id }.sumOf { it.amount }
            val rate = Calc.RATES[Calc.params(g).optInt("scenario", 0)]
            val need = Calc.daily(g, before, rate) * elapsed
            if (need > 1 && inP < need * 0.9) missed.add("Štednja ispod plana (${g.name}): ${money(inP)} od ${money(need)} KM")
            else if (need > 1) good.add("Štednja po planu (${g.name}): ${money(inP)} KM")
        }
        if (earned > 0 && spent + saved <= earned) good.add("Potrošnja unutar zarade, ostalo ${money(earned - spent - saved)} KM")
        if (earned > 0 && spent > earned) missed.add("Potrošeno više nego zarađeno (${money(spent - earned)} KM)")
    }
    Report(from, end, elapsed, logs.size, prayerTotal, perVakat, monthly, pages, words, finished, readDays, books.isNotEmpty(), remDone, remTotal, earned, spent, saved, big, missed, good, trackedLines)
}

fun reportLines(r: Report, mask: Int): List<Pair<String, Boolean>> {
    val l = mutableListOf<Pair<String, Boolean>>()
    l += "Izvještaj: ${r.from} do ${r.to}" to true
    if (mask and M_PRAYER != 0) {
        l += "Namaz" to true
        val pct = if (r.prayerTotal == 0) 0 else r.prayerDone * 100 / r.prayerTotal
        l += "$pct% (${r.prayerDone} od ${r.prayerTotal} namaza)" to false
        listOf(0, 2, 3, 4, 5).forEachIndexed { i, v -> l += "  ${VAKATS[v]}: ${r.perVakat[i]}/${r.elapsed}" to false }
        r.monthly.forEach { l += "  ${it.first}: ${it.second}%" to false }
    }
    if (mask and M_BOOKS != 0) {
        l += "Knjige i riječi" to true
        l += "Pročitano stranica: ${r.pages} · nove riječi: ${r.newWords} · završene knjige: ${r.finishedBooks} · dani čitanja: ${r.readDays}" to false
    }
    if (mask and M_REMINDERS != 0) { l += "Podsjetnici" to true; l += "Urađeno ${r.remDone} od ${r.remTotal}" to false; r.tracked.forEach { l += "  $it" to false } }
    if (mask and M_WALLET != 0) {
        l += "Novčanik" to true
        l += "Zarađeno ${money(r.earned)} KM · potrošeno ${money(r.spent)} KM · odvojeno ${money(r.saved)} KM · ostalo ${money(r.left)} KM" to false
        r.biggest?.let { l += "Najveći trošak: $it" to false }
    }
    l += "Šta ti je promaklo" to true
    if (r.missed.isEmpty()) l += "Ništa posebno." to false else r.missed.forEach { l += "• $it" to false }
    l += "Šta je išlo dobro" to true
    if (r.good.isEmpty()) l += "Još nema dovoljno podataka." to false else r.good.forEach { l += "• $it" to false }
    return l
}

suspend fun writePdf(ctx: Context, uri: Uri, r: Report, mask: Int) = withContext(Dispatchers.IO) {
    val doc = PdfDocument()
    val body = Paint().apply { textSize = 11f; color = 0xFF222222.toInt() }
    val head = Paint().apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF000000.toInt() }
    var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    var canvas = page.canvas
    var y = 56f
    var n = 1
    fun wrap(t: String, p: Paint): List<String> {
        val out = mutableListOf<String>(); var cur = ""
        for (w in t.split(" ")) {
            val tryS = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(tryS) > 480f && cur.isNotEmpty()) { out.add(cur); cur = w } else cur = tryS
        }
        out.add(cur); return out
    }
    for ((text, isHead) in reportLines(r, mask)) {
        val p = if (isHead) head else body
        if (isHead) y += 10f
        for (line in wrap(text, p)) {
            if (y > 790f) {
                doc.finishPage(page); n++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); canvas = page.canvas; y = 56f
            }
            canvas.drawText(line, 56f, y, p)
            y += if (isHead) 22f else 17f
        }
    }
    doc.finishPage(page)
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { doc.writeTo(it) }
    doc.close()
}

@Composable
fun rememberPdfExporter(build: suspend () -> Pair<Report, Int>): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            try {
                val (r, m) = build()
                writePdf(ctx, uri, r, m)
                Toast.makeText(ctx, "PDF sačuvan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(ctx, "Greška: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    return { l.launch("izvjestaj-${LocalDate.now()}.pdf") }
}

@Composable
fun ReportScreen(kind: String, fromArg: String, toArg: String, mask: Int, onBack: () -> Unit) {
    val today = LocalDate.now()
    var k by remember { mutableStateOf(kind) }
    var from by remember { mutableStateOf(runCatching { LocalDate.parse(fromArg) }.getOrDefault(today.minusDays(6))) }
    var to by remember { mutableStateOf(runCatching { LocalDate.parse(toArg) }.getOrDefault(today)) }
    var report by remember { mutableStateOf<Report?>(null) }
    LaunchedEffect(k, from, to) { report = buildReport(from, to) }
    val export = rememberPdfExporter { buildReport(from, to) to mask }

    Page(title = "Izvještaji", onBack = onBack) {
        ChipRow {
            Chip("Sedmica", k == "week", { k = "week"; from = today.minusDays(6); to = today })
            Chip("Mjesec", k == "month", { k = "month"; from = today.withDayOfMonth(1); to = today })
            Chip("Godina", k == "year", { k = "year"; from = today.withDayOfYear(1); to = today })
            Chip("Svoj period", k == "custom", { k = "custom" })
        }
        if (k == "custom") {
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Od", from, { from = it }, Modifier.weight(1f))
                DateField("Do", to, { to = it }, Modifier.weight(1f))
            }
        }
        Gap(12)
        val r = report
        if (r == null) Text("Računam…", style = T.small)
        else {
            if (mask and M_PRAYER != 0) {
                AppCard {
                    Label("Namaz")
                    val pct = if (r.prayerTotal == 0) 0 else r.prayerDone * 100 / r.prayerTotal
                    Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        Text("$pct%", style = T.big)
                        Text("  ${r.prayerDone} od ${r.prayerTotal} namaza", style = T.small, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Gap(6)
                    listOf(0, 2, 3, 4, 5).forEachIndexed { i, v ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(VAKATS[v], style = T.body, modifier = Modifier.width(80.dp))
                            Bar(if (r.elapsed == 0) 0f else r.perVakat[i].toFloat() / r.elapsed, C.teal, modifier = Modifier.weight(1f))
                            Text("  ${r.perVakat[i]}/${r.elapsed}", style = T.small, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        }
                    }
                    if (r.monthly.isNotEmpty()) {
                        Gap(8); Label("Po mjesecima"); Gap(4)
                        r.monthly.forEach {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(it.first, style = T.small, modifier = Modifier.width(40.dp))
                                Bar(it.second / 100f, C.teal, 6.dp, Modifier.weight(1f))
                                Text("  ${it.second}%", style = T.small, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }
                Gap(12)
            }
            if (mask and M_BOOKS != 0) {
                AppCard {
                    Label("Knjige i riječi"); Gap(8)
                    Row { MiniStat("${r.pages}", "stranica", Modifier.weight(1f)); MiniStat("${r.newWords}", "novih riječi", Modifier.weight(1f)); MiniStat("${r.finishedBooks}", "završeno", Modifier.weight(1f)); MiniStat("${r.readDays}", "dana čitanja", Modifier.weight(1f)) }
                }
                Gap(12)
            }
            if (mask and M_REMINDERS != 0) {
                AppCard {
                    Label("Podsjetnici"); Gap(4)
                    Text("${r.remDone} od ${r.remTotal} urađeno", style = T.h2)
                    Gap(6); Bar(if (r.remTotal == 0) 0f else r.remDone.toFloat() / r.remTotal, C.gold)
                    r.tracked.forEach { Text(it, style = T.small.copy(color = C.text2), modifier = Modifier.padding(top = 6.dp)) }
                }
                Gap(12)
            }
            if (mask and M_WALLET != 0) {
                AppCard {
                    Label("Novčanik"); Gap(8)
                    Row {
                        MiniStat(moneyInt(r.earned), "zarađeno", Modifier.weight(1f), C.teal)
                        MiniStat(moneyInt(r.spent), "potrošeno", Modifier.weight(1f), C.danger)
                        MiniStat(moneyInt(r.saved), "odvojeno", Modifier.weight(1f), C.gold)
                        MiniStat(moneyInt(r.left), "ostalo", Modifier.weight(1f))
                    }
                    r.biggest?.let { Gap(8); Text("Najveći trošak: $it", style = T.small.copy(color = C.text2)) }
                }
                Gap(12)
            }
            AppCard {
                Text("Šta ti je promaklo", style = T.h2.copy(color = C.danger)); Gap(6)
                if (r.missed.isEmpty()) Text("Ništa posebno.", style = T.small) else r.missed.forEach { Text("• $it", style = T.body, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Gap(12)
            AppCard {
                Text("Šta je išlo dobro", style = T.h2.copy(color = C.teal)); Gap(6)
                if (r.good.isEmpty()) Text("Još nema dovoljno podataka.", style = T.small) else r.good.forEach { Text("• $it", style = T.body, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Gap(14)
            GhostButton("Izvezi kao PDF", Modifier.fillMaxWidth(), onClick = export)
        }
    }
}

@Composable
private fun MiniStat(v: String, label: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = C.text) {
    Column(modifier) {
        Text(v, style = T.h2.copy(color = color))
        Text(label, style = T.small)
    }
}
