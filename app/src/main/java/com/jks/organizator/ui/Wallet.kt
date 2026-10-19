package com.jks.organizator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.net.Gemini
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.pow

object Calc {
    val RATES = listOf(0.0, 0.02, 0.04, 0.07)
    val SCENARIOS = listOf("Današnja cijena", "Sporiji pad (~2%)", "Očekivani pad (~4%)", "Brži pad (~7%)")

    fun saved(goalId: Long, txns: List<Txn>) = txns.filter { it.kind == "saved" && it.goalId == goalId }.sumOf { it.amount }

    fun days(deadline: String, today: LocalDate = LocalDate.now()): Long =
        maxOf(1, ChronoUnit.DAYS.between(today, runCatching { LocalDate.parse(deadline) }.getOrDefault(today.plusYears(1))))

    fun target(price: Double, rate: Double, days: Long) = price * (1 - rate).pow(days / 365.0)

    fun daily(g: Goal, saved: Double, rate: Double): Double {
        val d = days(g.deadline)
        return maxOf(0.0, (target(g.price, rate, d) - saved) / d)
    }

    fun params(g: Goal): JSONObject = runCatching { JSONObject(g.params) }.getOrDefault(JSONObject())

    fun duration(daysCount: Long): String {
        val y = daysCount / 365; val m = (daysCount % 365) / 30
        return when {
            y > 0 -> "$y god ${m} mj"
            m > 0 -> "$m mj"
            else -> "$daysCount dana"
        }
    }
}

@Composable
fun WalletScreen(onNew: () -> Unit, onSavings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = Graph.db.wallet()
    val txns by dao.observeTxns().collectAsState(emptyList())
    val cats by dao.observeAllCategories().collectAsState(emptyList())
    val goals by dao.observeGoals().collectAsState(emptyList())
    val s by Graph.prefs.flow.collectAsState(Settings())
    val ym = YearMonth.now()
    val month = txns.filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == ym }.getOrDefault(false) }
    val earned = month.filter { it.kind == "earned" }.sumOf { it.amount }
    val spent = month.filter { it.kind == "spent" }.sumOf { it.amount }
    val saved = month.filter { it.kind == "saved" }.sumOf { it.amount }
    val left = earned - spent - saved
    val catName = cats.associate { it.id to it.name }
    val byCat = month.filter { it.kind == "spent" }.groupBy { catName[it.categoryId] ?: "Ostalo" }
        .mapValues { it.value.sumOf { t -> t.amount } }.toList().sortedByDescending { it.second }

    var insight by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<Txn?>(null) }
    var filter by remember { mutableStateOf("") }

    Page(
        title = "Novčanik",
        fab = { FloatingActionButton(onClick = onNew, containerColor = C.gold, contentColor = C.bg) { Icon(Icons.Outlined.Add, "Novi unos") } }
    ) {
        AppCard(border = C.gold, color = C.surface2) {
            Label("Ostalo ovaj mjesec", color = C.gold)
            Gap(4)
            Text("${money(left)} KM", style = T.big.copy(color = if (left < 0) C.danger else C.text))
            Gap(10)
            Row {
                Stat("Zarađeno", earned, C.teal, Modifier.weight(1f))
                Stat("Potrošeno", spent, C.danger, Modifier.weight(1f))
                Stat("Odvojeno", saved, C.gold, Modifier.weight(1f))
            }
        }
        Gap(12)
        if (byCat.isNotEmpty()) {
            AppCard {
                Label("Potrošnja po kategorijama")
                Gap(8)
                val max = byCat.first().second
                byCat.forEach { (n, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                        Text(n, style = T.body, modifier = Modifier.width(88.dp))
                        Bar((v / max).toFloat(), C.danger, modifier = Modifier.weight(1f))
                        Text("  ${moneyInt(v)}", style = T.small, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                    }
                }
            }
            Gap(12)
        }
        AppCard(onClick = onSavings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Štednja")
                    Text("${goals.size} ${if (goals.size == 1) "cilj" else "ciljeva"}", style = T.body)
                }
                Text("${money(txns.filter { it.kind == "saved" }.sumOf { it.amount })} KM", style = T.h2.copy(color = C.gold))
            }
        }
        Gap(12)
        Label("Unosi")
        Gap(8)
        ChipRow {
            listOf("" to "Svi", "spent" to "Potrošeno", "earned" to "Zarađeno", "saved" to "Odvojeno").forEach { (k, n) ->
                Chip(n, filter == k, { filter = k }, accent = when (k) { "spent" -> C.danger; "earned" -> C.teal; else -> C.gold })
            }
        }
        Gap(8)
        val listed = txns.filter { filter.isEmpty() || it.kind == filter }
        if (filter.isNotEmpty()) {
            Text("Ukupno: ${money(listed.sumOf { it.amount })} KM", style = T.medium.copy(color = when (filter) { "spent" -> C.danger; "earned" -> C.teal; else -> C.gold }))
            Gap(8)
        }
        if (listed.isEmpty()) Text("Još nema unosa.", style = T.small)
        (if (filter.isEmpty()) listed.take(10) else listed).forEach { t ->
            val (color, sign) = when (t.kind) { "spent" -> C.danger to "−"; "earned" -> C.teal to "+"; else -> C.gold to "" }
            val title = when (t.kind) {
                "saved" -> "Odvojeno · " + (goals.firstOrNull { it.id == t.goalId }?.name ?: "štednja")
                else -> catName[t.categoryId] ?: if (t.kind == "spent") "Potrošeno" else "Zarađeno"
            }
            AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { del = t }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = T.body)
                        Text(listOf(t.place, runCatching { LocalDate.parse(t.date).bsShort() }.getOrDefault(t.date)).filter { it.isNotBlank() }.joinToString(" · "), style = T.small)
                    }
                    Text("$sign${money(t.amount)}", style = T.medium.copy(color = color))
                }
            }
        }
        if (s.gemWallet) {
            Gap(8)
            AppCard(color = C.surface2) {
                Label("Uvid · Gemini", color = C.gold)
                Gap(6)
                if (insight == null && !loading && err == null)
                    Text("Kratak pregled gdje ide novac i šta bi promjena značila za tvoje ciljeve.", style = T.small)
                if (loading || err != null || insight != null) GeminiCard("Odgovor", insight, loading, err)
                Gap(10)
                GhostButton("Pitaj o troškovima", Modifier.fillMaxWidth(), enabled = !loading && month.isNotEmpty()) {
                    loading = true; err = null
                    scope.launch {
                        val summary = "Zarađeno ${money(earned)} KM, potrošeno ${money(spent)} KM, odvojeno ${money(saved)} KM. Potrošnja: " +
                            byCat.joinToString { "${it.first} ${money(it.second)}" } + ". Ciljevi štednje: " +
                            goals.joinToString { "${it.name} (cijena ${money(it.price)} KM, ušteđeno ${money(Calc.saved(it.id, txns))} KM, rok ${it.deadline})" }
                        Gemini.generate(
                            "Ti si savjetnik za lične finansije. Na bosanskom, u najviše 4 kratke rečenice, reci gdje ide novac ovog mjeseca " +
                                "i šta bi smanjenje najveće kategorije za 20% značilo za ciljeve. Bez moraliziranja.\nPodaci: $summary"
                        ).onSuccess { insight = it }.onFailure { err = it.message }
                        loading = false
                    }
                }
            }
        }
    }
    del?.let { t -> Confirm("Obrisati unos?", "${money(t.amount)} KM", onOk = { scope.launch { dao.deleteTxn(t) } }, onDismiss = { del = null }) }
}

@Composable
private fun Stat(label: String, v: Double, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = T.small)
        Text(moneyInt(v), style = T.medium.copy(color = color))
    }
}

@Composable
fun NewEntryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = Graph.db.wallet()
    var kind by remember { mutableStateOf("spent") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var place by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf(0L) }
    var goalId by remember { mutableStateOf(0L) }
    var newCat by remember { mutableStateOf(false) }
    val cats by dao.observeCategories(if (kind == "earned") "earned" else "spent").collectAsState(emptyList())
    val goals by dao.observeGoals().collectAsState(emptyList())

    LaunchedEffect(kind) { catId = 0 }
    val ok = parseAmount(amount) > 0 && (kind == "saved" && goalId != 0L || kind != "saved")

    Page(title = "Novi unos", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("spent" to "Potrošeno", "earned" to "Zarađeno", "saved" to "Odvojeno").forEach { (k, n) ->
                Chip(n, kind == k, { kind = k }, accent = when (k) { "spent" -> C.danger; "earned" -> C.teal; else -> C.gold })
            }
        }
        Gap(14)
        Field(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Iznos", keyboard = KeyboardType.Decimal, suffix = "KM")
        Gap(10)
        DateField("Datum", date, { date = it })
        Gap(10)
        Field(place, { place = it }, when (kind) { "spent" -> "Gdje / napomena"; "earned" -> "Od koga / napomena"; else -> "Napomena" })
        Gap(14)
        when (kind) {
            "saved" -> {
                Label("Cilj štednje"); Gap(8)
                if (goals.isEmpty()) Text("Prvo napravi cilj u Štednji.", style = T.small)
                ChipRow { goals.forEach { Chip(it.name, goalId == it.id, { goalId = it.id }) } }
            }
            else -> {
                Label(if (kind == "spent") "Kategorija" else "Izvor"); Gap(8)
                ChipRow {
                    cats.forEach { Chip(it.name, catId == it.id, { catId = it.id }) }
                    Chip(if (kind == "spent") "+ Nova kategorija" else "+ Novi izvor", false, { newCat = true })
                }
            }
        }
        Gap(20)
        GoldButton("Sačuvaj", Modifier.fillMaxWidth(), enabled = ok) {
            scope.launch {
                dao.insertTxn(Txn(kind = kind, amount = parseAmount(amount), categoryId = if (kind == "saved") 0 else catId, goalId = if (kind == "saved") goalId else 0, date = date.key(), place = place.trim()))
                onBack()
            }
        }
    }
    if (newCat) InputDialog(if (kind == "spent") "Nova kategorija" else "Novi izvor", "Naziv", onOk = {
        scope.launch { catId = dao.insertCategory(Category(kind = if (kind == "earned") "earned" else "spent", name = it)) }
    }, onDismiss = { newCat = false })
}

@Composable
fun SavingsScreen(onBack: () -> Unit, onGoal: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = Graph.db.wallet()
    val goals by dao.observeGoals().collectAsState(emptyList())
    val txns by dao.observeTxns().collectAsState(emptyList())
    var add by remember { mutableStateOf(false) }
    val total = txns.filter { it.kind == "saved" }.sumOf { it.amount }

    Page(title = "Štednja", onBack = onBack) {
        AppCard(border = C.gold, color = C.surface2) {
            Label("Ukupno ušteđeno", color = C.gold)
            Text("${money(total)} KM", style = T.big)
        }
        Gap(12)
        goals.forEach { g ->
            val sv = Calc.saved(g.id, txns)
            val daily = Calc.daily(g, sv, Calc.RATES[Calc.params(g).optInt("scenario", 0)])
            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = { onGoal(g.id) }) {
                Text(g.name, style = T.h2)
                Gap(8)
                Bar(if (g.price <= 0) 0f else (sv / g.price).toFloat(), C.gold)
                Gap(6)
                Row {
                    Text("${moneyInt(sv)} / ${moneyInt(g.price)} KM", style = T.small.copy(color = C.text2), modifier = Modifier.weight(1f))
                    Text("rok ${runCatching { LocalDate.parse(g.deadline).let { "${it.monthValue}/${it.year}" } }.getOrDefault("—")}", style = T.small)
                }
                Text("potrebno dnevno ${money(daily)} KM", style = T.small.copy(color = C.gold), modifier = Modifier.padding(top = 4.dp))
            }
        }
        GoldButton("Novi cilj", Modifier.fillMaxWidth()) { add = true }
    }

    if (add) {
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var dl by remember { mutableStateOf(LocalDate.now().plusYears(1)) }
        AlertDialog(
            onDismissRequest = { add = false }, containerColor = C.surface, title = { Text("Novi cilj", style = T.h2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(name, { name = it }, "Naziv (npr. Automobil)")
                    Field(price, { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Cijena", keyboard = KeyboardType.Decimal, suffix = "KM")
                    DateField("Rok", dl, { dl = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { dao.insertGoal(Goal(name = name.trim(), price = parseAmount(price), deadline = dl.key())) }
                        add = false
                    }
                }) { Text("Sačuvaj", color = C.gold) }
            },
            dismissButton = { TextButton(onClick = { add = false }) { Text("Odustani", color = C.muted) } }
        )
    }
}

@Composable
fun GoalScreen(id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = Graph.db.wallet()
    val goals by dao.observeGoals().collectAsState(emptyList())
    val txns by dao.observeTxns().collectAsState(emptyList())
    val prices by dao.observePrices(id).collectAsState(emptyList())
    val s by Graph.prefs.flow.collectAsState(Settings())
    val g = goals.firstOrNull { it.id == id }

    var manual by remember { mutableStateOf(false) }
    var ad by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    if (g == null) { Page(title = "Cilj", onBack = onBack) { Text("Cilj ne postoji.", style = T.small) }; return }
    val p = Calc.params(g)
    val sv = Calc.saved(g.id, txns)
    val scenario = p.optInt("scenario", 0)
    val rate = Calc.RATES[scenario]
    val days = Calc.days(g.deadline)
    val target = Calc.target(g.price, rate, days)
    val daily = Calc.daily(g, sv, rate)
    fun saveParams(block: JSONObject.() -> Unit) = scope.launch { dao.insertGoal(g.copy(params = Calc.params(g).apply(block).toString())) }

    Page(title = g.name, onBack = onBack, actions = {
        IconButton(onClick = { delete = true }) { Icon(Icons.Outlined.Delete, "Obriši", tint = C.danger) }
    }) {
        AppCard(border = C.gold, color = C.surface2) {
            Label("Procijenjena cijena", color = C.gold)
            Text("${money(g.price)} KM", style = T.big)
            if (p.has("low") && p.has("high")) Text("raspon ${moneyInt(p.optDouble("low"))} – ${moneyInt(p.optDouble("high"))} KM", style = T.small)
            Gap(10)
            Bar(if (g.price <= 0) 0f else (sv / g.price).toFloat(), C.gold)
            Gap(6)
            Text("ušteđeno ${money(sv)} KM · rok ${g.deadline}", style = T.small.copy(color = C.text2))
        }
        prices.firstOrNull()?.let {
            val age = ChronoUnit.DAYS.between(LocalDate.parse(it.date), LocalDate.now())
            if (age > 90) Text("Cijena nije osvježavana $age dana. Vrijeme je za novu provjeru.", style = T.small.copy(color = C.gold), modifier = Modifier.padding(top = 8.dp))
        }
        Gap(14)
        val deps = txns.filter { it.kind == "saved" && it.goalId == g.id }.sortedByDescending { it.date }
        val remaining = maxOf(0.0, g.price - sv)
        val todayD = LocalDate.now()
        val firstDep = deps.lastOrNull()?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val winStart = if (firstDep == null) todayD else maxOf(firstDep, todayD.minusDays(29))
        val winDays = ChronoUnit.DAYS.between(winStart, todayD) + 1
        val winSum = deps.filter { it.date >= winStart.toString() }.sumOf { it.amount }
        val pace = if (firstDep == null) 0.0 else winSum / winDays
        AppCard {
            Label("Koliko još treba", color = C.gold)
            Gap(4)
            Text("${moneyInt(g.price)} − ${moneyInt(sv)} = ${moneyInt(remaining)} KM", style = T.h2)
            Gap(6)
            if (remaining <= 0) Text("Cilj je ostvaren! 🎉", style = T.body.copy(color = C.teal))
            else if (pace > 0) {
                val need = ceil(remaining / pace).toLong()
                Text("Tvoj tempo: ${money(pace)} KM dnevno (zadnjih $winDays dana)", style = T.small.copy(color = C.text2))
                Text("Ovim tempom treba još ~$need dana (${Calc.duration(need)}), oko ${todayD.plusDays(need).let { "${it.dayOfMonth}.${it.monthValue}.${it.year}." }}", style = T.body.copy(color = C.gold), modifier = Modifier.padding(top = 4.dp))
                if (daily > 0) Text("Za rok ${g.deadline} treba ${money(daily)} KM dnevno", style = T.small, modifier = Modifier.padding(top = 4.dp))
            } else Text("Upiši prvu uplatu da vidiš tempo.", style = T.small)
        }
        Gap(12)
        var dAmt by remember { mutableStateOf("") }
        var dDate by remember { mutableStateOf(LocalDate.now()) }
        var delDep by remember { mutableStateOf<Txn?>(null) }
        AppCard {
            Label("Dodaj uplatu")
            Gap(8)
            Field(dAmt, { dAmt = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Iznos", keyboard = KeyboardType.Decimal, suffix = "KM")
            Gap(8)
            DateField("Datum (danas, jučer, ...)", dDate, { dDate = it })
            Gap(10)
            GoldButton("Dodaj", Modifier.fillMaxWidth(), enabled = parseAmount(dAmt) > 0) {
                scope.launch { dao.insertTxn(Txn(kind = "saved", amount = parseAmount(dAmt), goalId = g.id, date = dDate.toString())) }
                dAmt = ""
            }
            if (deps.isNotEmpty()) {
                Gap(14)
                Label("Uplate")
                deps.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { delDep = t }, verticalAlignment = Alignment.CenterVertically) {
                        Text(runCatching { LocalDate.parse(t.date).let { "${it.dayOfMonth}.${it.monthValue}.${it.year}." } }.getOrDefault(t.date), style = T.body, modifier = Modifier.weight(1f))
                        Text("+${money(t.amount)} KM", style = T.medium.copy(color = C.gold))
                    }
                }
                Text("Dodirni uplatu da je obrišeš.", style = T.small)
            }
        }
        delDep?.let { t -> Confirm("Obrisati uplatu?", "${money(t.amount)} KM", onOk = { scope.launch { dao.deleteTxn(t) } }, onDismiss = { delDep = null }) }
        Gap(14)
        Label("Parametri")
        Gap(8)
        var year by remember(g.params) { mutableStateOf(p.optString("year")) }
        var km by remember(g.params) { mutableStateOf(p.optString("km")) }
        var cond by remember(g.params) { mutableStateOf(p.optString("condition")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(year, { year = it }, "Godište", Modifier.weight(1f), keyboard = KeyboardType.Number)
            Field(km, { km = it }, "Kilometraža", Modifier.weight(1f), keyboard = KeyboardType.Number)
        }
        Gap(8)
        Field(cond, { cond = it }, "Stanje")
        Gap(6)
        TextButton(onClick = { saveParams { put("year", year); put("km", km); put("condition", cond) } }) { Text("Sačuvaj parametre", color = C.gold) }

        Gap(8)
        Label("Kako cijena može promijeniti plan")
        Gap(8)
        ChipRow { Calc.SCENARIOS.forEachIndexed { i, n -> Chip(n, scenario == i, { saveParams { put("scenario", i) } }) } }
        Text("Preporuka: planiraj po današnjoj cijeni.", style = T.small, modifier = Modifier.padding(top = 6.dp))

        Gap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.gemSavings) GoldButton("Izračunaj iz oglasa (Gemini)", Modifier.weight(1f), enabled = !loading) { ad = true }
            GhostButton("Upiši cijenu ručno", Modifier.weight(1f)) { manual = true }
        }
        if (loading || err != null || note != null) { Gap(8); GeminiCard("Procjena", note, loading, err) }
        Text("Procjena je raspon, ne obećanje: pouzdana je samo cijena iz stvarnih oglasa.", style = T.small, modifier = Modifier.padding(top = 6.dp))

        Gap(16)
        AppCard {
            Label("Kalkulator do roka")
            Gap(4)
            Text("cijena do roka: ${money(target)} KM · još $days dana", style = T.small)
            Gap(10)
            Row {
                Calc3("Dnevno", daily, Modifier.weight(1f))
                Calc3("Sedmično", daily * 7, Modifier.weight(1f))
                Calc3("Mjesečno", daily * 365 / 12, Modifier.weight(1f))
            }
        }
        Gap(12)
        AppCard {
            Label("Ako odvajaš svaki dan")
            Gap(8)
            listOf(20.0, 30.0, 50.0).forEach { a ->
                val d = if (target - sv <= 0) 0L else ceil((target - sv) / a).toLong()
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${a.toInt()} KM", style = T.medium, modifier = Modifier.weight(1f))
                    Text("$d dana", style = T.body, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(Calc.duration(d), style = T.small.copy(color = C.text2), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
        if (prices.isNotEmpty()) {
            Gap(12)
            Label("Historija cijene"); Gap(6)
            prices.take(6).forEach { Text("${it.date} · ${money(it.price)} KM · ${if (it.source == "gemini") "Gemini" else "ručno"}", style = T.small) }
        }
    }

    fun setPrice(v: Double, src: String, low: Double? = null, high: Double? = null) = scope.launch {
        val pj = Calc.params(g)
        if (low != null && high != null) { pj.put("low", low); pj.put("high", high) } else { pj.remove("low"); pj.remove("high") }
        dao.insertGoal(g.copy(price = v, params = pj.toString()))
        dao.insertPrice(PriceEntry(goalId = g.id, date = LocalDate.now().key(), price = v, source = src))
    }

    if (manual) InputDialog("Cijena", "Cijena u KM", g.price.toString(), KeyboardType.Decimal, onOk = { setPrice(parseAmount(it), "manual") }, onDismiss = { manual = false })
    if (ad) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { ad = false }, containerColor = C.surface, title = { Text("Procjena cijene", style = T.h2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zalijepi tekst oglasa ili opiši cilj. Koriste se i parametri iznad.", style = T.small)
                    Field(text, { text = it }, "Tekst oglasa", singleLine = false, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ad = false; loading = true; err = null; note = null
                    scope.launch {
                        Gemini.generateJson(
                            "Procijeni tržišnu cijenu u KM (BiH) za: ${g.name}. Godište: ${p.optString("year")}, kilometraža: ${p.optString("km")}, stanje: ${p.optString("condition")}. " +
                                "Dodatni opis/oglas: $text. Vrati JSON: {\"price\": broj, \"low\": broj, \"high\": broj, \"note\": \"jedna kratka rečenica na bosanskom\"}."
                        ).onSuccess {
                            val pr = it.optDouble("price", 0.0)
                            if (pr > 0) { setPrice(pr, "gemini", it.optDouble("low", pr), it.optDouble("high", pr)); note = it.optString("note") } else err = "Gemini nije vratio cijenu."
                        }.onFailure { err = it.message }
                        loading = false
                    }
                }) { Text("Izračunaj", color = C.gold) }
            },
            dismissButton = { TextButton(onClick = { ad = false }) { Text("Odustani", color = C.muted) } }
        )
    }
    if (delete) Confirm("Obrisati cilj?", g.name, onOk = { scope.launch { dao.deleteGoal(g) }; onBack() }, onDismiss = { delete = false })
}

@Composable
private fun Calc3(label: String, v: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = T.small)
        Text(money(v), style = T.medium.copy(color = C.gold))
    }
}
