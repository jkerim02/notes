package com.jks.organizator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

data class WSum(val earned: Double, val spent: Double, val saved: Double) {
    val left get() = earned - spent - saved
}

data class GoalLine(val name: String, val inPeriod: Double, val total: Double, val price: Double)

data class WalletReport(
    val from: LocalDate, val to: LocalDate, val title: String,
    val sum: WSum, val carry: Double,
    val spentByCat: List<Pair<String, Double>>, val earnedBySource: List<Pair<String, Double>>,
    val goals: List<GoalLine>, val countEarned: Int, val countSpent: Int, val countSaved: Int,
    val prev: WSum?, val prevFrom: LocalDate?, val prevTo: LocalDate?, val prevSpentByCat: Map<String, Double>,
    val biggest: String?
)

private fun d(x: LocalDate) = "${x.dayOfMonth}.${x.monthValue}.${x.year}."

suspend fun earliestWalletDate(): LocalDate = withContext(Dispatchers.IO) {
    Graph.db.wallet().txns().mapNotNull { it.effDate().let { s -> runCatching { LocalDate.parse(s) }.getOrNull() } }.minOrNull() ?: LocalDate.now()
}

suspend fun buildWalletReport(from: LocalDate, to: LocalDate, title: String, prevFrom: LocalDate?, prevTo: LocalDate?): WalletReport = withContext(Dispatchers.IO) {
    val db = Graph.db.wallet()
    val all = db.txns()
    val cats = db.categories().associate { it.id to it.name }
    val goals = db.goals()
    fun inR(t: Txn, a: LocalDate, b: LocalDate) = t.effDate() >= a.toString() && t.effDate() <= b.toString()
    fun sum(l: List<Txn>) = WSum(l.filter { it.kind == "earned" }.sumOf { it.amount }, l.filter { it.kind == "spent" }.sumOf { it.amount }, l.filter { it.kind == "saved" }.sumOf { it.amount })
    fun byCat(l: List<Txn>, kind: String) = l.filter { it.kind == kind }.groupBy { cats[it.categoryId] ?: "Ostalo" }
        .mapValues { it.value.sumOf { t -> t.amount } }.toList().sortedByDescending { it.second }

    val cur = all.filter { inR(it, from, to) }
    val before = all.filter { it.effDate() < from.toString() }
    val carry = sum(before).left
    val prevList = if (prevFrom != null && prevTo != null) all.filter { inR(it, prevFrom, prevTo) } else null
    val goalLines = goals.map { g ->
        GoalLine(g.name, cur.filter { it.kind == "saved" && it.goalId == g.id }.sumOf { it.amount }, Calc.saved(g.id, all), g.price)
    }.filter { it.inPeriod > 0 || it.total > 0 }
    val big = cur.filter { it.kind == "spent" }.maxByOrNull { it.amount }?.let {
        "${money(it.amount)} KM · ${cats[it.categoryId] ?: "Ostalo"}" + if (it.place.isNotBlank()) " (${it.place})" else ""
    }
    WalletReport(
        from, to, title, sum(cur), carry, byCat(cur, "spent"), byCat(cur, "earned"), goalLines,
        cur.count { it.kind == "earned" }, cur.count { it.kind == "spent" }, cur.count { it.kind == "saved" },
        prevList?.let { sum(it) }, prevFrom, prevTo, prevList?.let { byCat(it, "spent").toMap() } ?: emptyMap(), big
    )
}

private fun pctChange(now: Double, before: Double): Int? = if (before <= 0.0) null else ((now - before) / before * 100).roundToInt()

/** Tekstualna analiza: šta je potrošeno najviše i šta je bolje/gore nego u prethodnom periodu. */
fun walletAnalysis(r: WalletReport): List<String> {
    val out = mutableListOf<String>()
    val spent = r.sum.spent
    if (r.spentByCat.isNotEmpty() && spent > 0) {
        val (n, v) = r.spentByCat.first()
        out += "Najviše je potrošeno na „$n“: ${money(v)} KM (${(v / spent * 100).roundToInt()}% svih troškova)."
        if (r.spentByCat.size > 1) out += "Zatim: " + r.spentByCat.drop(1).take(2).joinToString(", ") { "${it.first} (${money(it.second)} KM)" } + "."
    }
    r.biggest?.let { out += "Najveći pojedinačni trošak: $it." }
    val p = r.prev
    if (p != null && r.prevFrom != null && r.prevTo != null) {
        val rng = "${d(r.prevFrom)} – ${d(r.prevTo)}"
        pctChange(spent, p.spent)?.let { c ->
            out += if (c < 0) "Potrošnja je MANJA za ${abs(c)}% (${money(p.spent - spent)} KM) nego u prethodnom periodu ($rng). Bolje."
            else if (c > 0) "Potrošnja je VEĆA za $c% (${money(spent - p.spent)} KM) nego u prethodnom periodu ($rng)."
            else "Potrošnja je ista kao u prethodnom periodu ($rng)."
        } ?: run { if (spent > 0) out += "U prethodnom periodu ($rng) nije bilo potrošnje." }
        pctChange(r.sum.earned, p.earned)?.let { c ->
            out += if (c > 0) "Zarada je veća za $c% nego u prethodnom periodu." else if (c < 0) "Zarada je manja za ${abs(c)}% nego u prethodnom periodu." else "Zarada je ista kao u prethodnom periodu."
        }
        pctChange(r.sum.saved, p.saved)?.let { c ->
            out += if (c > 0) "Odvojeno za štednju je više za $c% nego u prethodnom periodu. Bolje." else if (c < 0) "Odvojeno za štednju je manje za ${abs(c)}% nego u prethodnom periodu." else "Odvojeno za štednju je isto."
        }
        val diffs = (r.spentByCat.map { it.first } + r.prevSpentByCat.keys).distinct()
            .map { it to (r.spentByCat.firstOrNull { c -> c.first == it }?.second ?: 0.0) - (r.prevSpentByCat[it] ?: 0.0) }
        diffs.filter { it.second > 0.5 }.maxByOrNull { it.second }?.let { out += "Najviše je porasla potrošnja na „${it.first}“ (+${money(it.second)} KM)." }
        diffs.filter { it.second < -0.5 }.minByOrNull { it.second }?.let { out += "Najviše je pala potrošnja na „${it.first}“ (−${money(-it.second)} KM). Bolje." }
    } else out += "Nema podataka za poređenje s prethodnim periodom."
    if (r.sum.earned > 0) {
        out += if (r.sum.left >= 0) "Ostalo je ${money(r.sum.left)} KM nakon troškova i štednje." else "Potrošeno i odvojeno je više nego zarađeno za ${money(-r.sum.left)} KM."
    }
    return out
}

fun walletLines(r: WalletReport, includeCarry: Boolean): List<Pair<String, Boolean>> {
    val l = mutableListOf<Pair<String, Boolean>>()
    l += "Izvještaj o novčaniku: ${r.title}" to true
    l += "Period: ${d(r.from)} – ${d(r.to)}" to false
    l += "Ukupno" to true
    l += "Zarađeno: ${money(r.sum.earned)} KM (${r.countEarned} unosa)" to false
    l += "Potrošeno: ${money(r.sum.spent)} KM (${r.countSpent} unosa)" to false
    l += "Odvojeno za štednju: ${money(r.sum.saved)} KM (${r.countSaved} unosa)" to false
    l += "Ostalo u periodu: ${money(r.sum.left)} KM" to false
    if (includeCarry) {
        l += "Preneseno iz ranijih mjeseci: ${money(r.carry)} KM" to false
        l += "Ostalo ukupno s prenesenim: ${money(r.sum.left + r.carry)} KM" to false
    }
    if (r.spentByCat.isNotEmpty()) {
        l += "Potrošnja po kategorijama" to true
        r.spentByCat.forEach { l += "  ${it.first}: ${money(it.second)} KM (${if (r.sum.spent > 0) (it.second / r.sum.spent * 100).roundToInt() else 0}%)" to false }
    }
    if (r.earnedBySource.isNotEmpty()) {
        l += "Zarada po izvorima" to true
        r.earnedBySource.forEach { l += "  ${it.first}: ${money(it.second)} KM" to false }
    }
    if (r.goals.isNotEmpty()) {
        l += "Štednja po ciljevima" to true
        r.goals.forEach { l += "  ${it.name}: odvojeno u periodu ${money(it.inPeriod)} KM; ukupno ${money(it.total)} od ${money(it.price)} KM" to false }
    }
    l += "Analiza" to true
    walletAnalysis(r).forEach { l += "• $it" to false }
    return l
}

@Composable
fun WalletReportScreen(initial: String, onBack: () -> Unit) {
    val today = LocalDate.now()
    var kind by remember { mutableStateOf(if (initial == "lastyear") "year" else initial) }
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var year by remember { mutableStateOf(if (initial == "lastyear") today.year - 1 else today.year) }
    var cFrom by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var cTo by remember { mutableStateOf(today) }
    var earliest by remember { mutableStateOf(today) }
    var includeCarry by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<WalletReport?>(null) }
    LaunchedEffect(Unit) { earliest = earliestWalletDate() }

    data class Spec(val from: LocalDate, val to: LocalDate, val title: String, val pf: LocalDate?, val pt: LocalDate?)
    fun spec(): Spec = when (kind) {
        "week" -> Spec(today.minusDays(6), today, "zadnjih 7 dana", today.minusDays(13), today.minusDays(7))
        "month" -> Spec(ym.atDay(1), ym.atEndOfMonth(), "${MONTHS_BS[ym.monthValue - 1]} ${ym.year}", ym.minusMonths(1).atDay(1), ym.minusMonths(1).atEndOfMonth())
        "year" -> Spec(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "godišnji izvještaj $year", LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31))
        "all" -> Spec(earliest, today, "od početka", null, null)
        else -> {
            val len = java.time.temporal.ChronoUnit.DAYS.between(cFrom, cTo) + 1
            Spec(cFrom, cTo, "od ${d(cFrom)} do ${d(cTo)}", cFrom.minusDays(len), cFrom.minusDays(1))
        }
    }
    val sp = spec()
    LaunchedEffect(kind, ym, year, cFrom, cTo, earliest) { report = buildWalletReport(sp.from, sp.to, sp.title, sp.pf, sp.pt) }
    val export = rememberLinesPdfExporter("novcanik-${today}.pdf") { buildWalletReport(sp.from, sp.to, sp.title, sp.pf, sp.pt).let { walletLines(it, includeCarry) } }

    Page(title = "Izvještaj: novčanik", onBack = onBack) {
        ChipRow {
            Chip("Sedmica", kind == "week", { kind = "week" })
            Chip("Mjesec", kind == "month", { kind = "month" })
            Chip("Godina", kind == "year", { kind = "year" })
            Chip("Od početka", kind == "all", { kind = "all" })
            Chip("Od – do", kind == "custom", { kind = "custom" })
        }
        Gap(8)
        when (kind) {
            "month" -> Arrows("${MONTHS_BS[ym.monthValue - 1].replaceFirstChar { it.uppercase() }} ${ym.year}", { ym = ym.minusMonths(1) }, { ym = ym.plusMonths(1) })
            "year" -> Arrows("$year", { year-- }, { year++ })
            "custom" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Od", cFrom, { cFrom = it }, Modifier.weight(1f)); DateField("Do", cTo, { cTo = it }, Modifier.weight(1f))
            }
            else -> Text("${d(sp.from)} – ${d(sp.to)}", style = T.small)
        }
        Gap(12)
        val r = report
        if (r == null) { Text("Računam…", style = T.small); return@Page }

        AppCard(border = C.gold, color = C.surface2) {
            Label("Ostalo u periodu", color = C.gold)
            val shown = r.sum.left + if (includeCarry) r.carry else 0.0
            Text("${money(shown)} KM", style = T.big.copy(color = if (shown < 0) C.danger else C.text))
            Gap(10)
            Row {
                Tot("Zarađeno", r.sum.earned, "${r.countEarned} unosa", C.teal, Modifier.weight(1f))
                Tot("Potrošeno", r.sum.spent, "${r.countSpent} unosa", C.danger, Modifier.weight(1f))
                Tot("Odvojeno", r.sum.saved, "${r.countSaved} unosa", C.gold, Modifier.weight(1f))
            }
            Gap(10)
            SwitchRow("Uključi preneseno iz ranijih mjeseci", "Preneseno: ${money(r.carry)} KM", includeCarry, { includeCarry = it })
        }
        Gap(12)

        if (r.spentByCat.isNotEmpty()) {
            AppCard {
                Label("Potrošnja po kategorijama"); Gap(8)
                val max = r.spentByCat.first().second
                r.spentByCat.forEach { (n, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(n, style = T.body, modifier = Modifier.width(90.dp))
                        Bar((v / max).toFloat(), C.danger, modifier = Modifier.weight(1f))
                        Text("  ${moneyInt(v)}", style = T.small, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                    }
                }
            }
            Gap(12)
        }
        if (r.earnedBySource.isNotEmpty()) {
            AppCard {
                Label("Zarada po izvorima"); Gap(8)
                r.earnedBySource.forEach { (n, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) { Text(n, style = T.body, modifier = Modifier.weight(1f)); Text("${money(v)} KM", style = T.medium.copy(color = C.teal)) }
                }
            }
            Gap(12)
        }
        if (r.goals.isNotEmpty()) {
            AppCard {
                Label("Štednja po ciljevima"); Gap(8)
                r.goals.forEach { g ->
                    Text(g.name, style = T.body)
                    Text("u periodu ${money(g.inPeriod)} KM · ukupno ${money(g.total)} od ${money(g.price)} KM", style = T.small)
                    Gap(4); Bar(if (g.price <= 0) 0f else (g.total / g.price).toFloat(), C.gold); Gap(8)
                }
            }
            Gap(12)
        }
        r.prev?.let { p ->
            AppCard {
                Label("Poređenje s prethodnim periodom"); Gap(4)
                Text("${d(r.prevFrom!!)} – ${d(r.prevTo!!)}", style = T.small)
                Gap(8)
                Cmp("Zarađeno", r.sum.earned, p.earned, goodWhenUp = true)
                Cmp("Potrošeno", r.sum.spent, p.spent, goodWhenUp = false)
                Cmp("Odvojeno", r.sum.saved, p.saved, goodWhenUp = true)
            }
            Gap(12)
        }
        AppCard {
            Label("Analiza", color = C.gold); Gap(6)
            walletAnalysis(r).forEach { Text("• $it", style = T.body.copy(color = C.text2), modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Gap(14)
        GoldButton("Preuzmi PDF", Modifier.fillMaxWidth(), onClick = export)
    }
}

@Composable
private fun Arrows(label: String, back: () -> Unit, next: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Outlined.ChevronLeft, "Nazad", tint = C.text) }
        Text(label, style = T.h2, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = next) { Icon(Icons.Outlined.ChevronRight, "Naprijed", tint = C.text) }
    }
}

@Composable
private fun Tot(label: String, v: Double, sub: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = T.small)
        Text(moneyInt(v), style = T.h2.copy(color = color))
        Text(sub, style = T.small)
    }
}

@Composable
private fun Cmp(label: String, now: Double, before: Double, goodWhenUp: Boolean) {
    val diff = now - before
    val good = if (goodWhenUp) diff > 0 else diff < 0
    val color = if (diff == 0.0) C.muted else if (good) C.teal else C.danger
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, modifier = Modifier.weight(1f))
        Text("${money(before)} → ${money(now)}", style = T.small, modifier = Modifier.padding(end = 8.dp))
        Text((if (diff > 0) "+" else if (diff < 0) "−" else "") + money(abs(diff)) + " KM", style = T.medium.copy(color = color))
    }
}
