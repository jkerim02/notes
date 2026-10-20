package com.jks.organizator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.net.Gemini
import kotlinx.coroutines.launch
import java.time.LocalDate

private val LANGS = listOf("Bosanski", "Engleski", "Arapski")
private val GENRES = listOf("Roman", "Islam", "Nauka", "Biografija", "Poezija", "Udžbenik", "Historija")

/** Jezik riječi za rječnik: "bs" za bosanske knjige, inače "en". */
private fun langOf(b: Book?): String = if (b != null && b.language.contains("osan", ignoreCase = true)) "bs" else "en"

private fun bookMeta(b: Book): String =
    listOf(b.author, if (b.year > 0) b.year.toString() else "", b.genre, b.language, b.publisher).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun BooksScreen() {
    val scope = rememberCoroutineScope()
    val dao = Graph.db.books()
    val books by dao.observeBooks().collectAsState(emptyList())
    val words by dao.observeWords().collectAsState(emptyList())
    val s by Graph.prefs.flow.collectAsState(Settings())
    val book = books.firstOrNull { it.id == s.currentBookId } ?: books.firstOrNull { it.finishedDay == 0L } ?: books.firstOrNull()

    var showAdd by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showPage by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var lang by remember(book?.id) { mutableStateOf(langOf(book)) }
    var pron by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var tr by remember { mutableStateOf("") }
    var def by remember { mutableStateOf("") }
    var ex by remember { mutableStateOf("") }
    var cardWord by remember { mutableStateOf<String?>(null) }
    var cardLang by remember { mutableStateOf("en") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun lookup(w: String, l: String) {
        cardWord = w; cardLang = l; err = null; loading = true
        scope.launch {
            val prompt = if (l == "bs")
                "Za bosansku riječ ili izraz \"$w\"" + (book?.let { " iz knjige \"${it.title}\"" } ?: "") +
                    " vrati JSON sa ključevima: pronunciation (naglasak/izgovor ako je koristan), pos (vrsta riječi), " +
                    "translationBs (jasno objašnjenje značenja na bosanskom, uključujući sinonime ili porijeklo ako je riječ strana, npr. turcizam), " +
                    "definitionEn (prijevod na engleski), example (jedna rečenica upotrebe u kontekstu na bosanskom)."
            else
                "Za englesku riječ ili izraz \"$w\"" + (book?.let { " iz knjige \"${it.title}\"" } ?: "") +
                    " vrati JSON sa ključevima: pronunciation (izgovor), pos (vrsta riječi na bosanskom), translationBs (prijevod na bosanski), " +
                    "definitionEn (kratka definicija na engleskom), example (jedna rečenica upotrebe u kontekstu na engleskom)."
            Gemini.generateJson(prompt).onSuccess {
                pron = it.optString("pronunciation"); pos = it.optString("pos"); tr = it.optString("translationBs")
                def = it.optString("definitionEn"); ex = it.optString("example")
            }.onFailure { err = (it.message ?: "Greška") + " Možeš ručno popuniti polja." }
            loading = false
        }
    }

    Page(title = "Knjige") {
        if (book == null) {
            AppCard {
                Text("Još nema knjiga", style = T.h2)
                Gap(4); Text("Dodaj knjigu koju trenutno čitaš da bi pratio stranice i riječi.", style = T.small)
                Gap(10); GoldButton("Dodaj knjigu", onClick = { showAdd = true })
            }
        } else {
            val wc = words.count { it.bookId == book.id }
            AppCard(border = C.gold, color = C.surface2) {
                Label("Trenutna knjiga", color = C.gold)
                Gap(4)
                Text(book.title, style = T.h2)
                bookMeta(book).takeIf { it.isNotBlank() }?.let { Text(it, style = T.small) }
                Gap(10)
                Bar(if (book.totalPages == 0) 0f else book.currentPage.toFloat() / book.totalPages, C.gold)
                Gap(6)
                Row {
                    Text("stranica ${book.currentPage} od ${book.totalPages}", style = T.small.copy(color = C.text2), modifier = Modifier.weight(1f))
                    Text("$wc riječi", style = T.small.copy(color = C.text2))
                }
                Gap(10)
                GoldButton("Ažuriraj stranicu", Modifier.fillMaxWidth(), onClick = { showPage = true })
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GhostButton("Uredi knjigu", Modifier.weight(1f), onClick = { showEdit = true })
                    GhostButton("Nova", Modifier.weight(1f), onClick = { showAdd = true })
                    IconButton(onClick = { confirmDel = true }) { Icon(Icons.Outlined.Delete, "Obriši knjigu", tint = C.danger) }
                }
            }
            if (books.size > 1) {
                Gap(8)
                ChipRow { books.forEach { Chip(it.title, it.id == book.id, { scope.launch { Graph.prefs.set(K.currentBookId, it.id) } }) } }
            }

            Gap(16)
            Label("Rječnik")
            Gap(8)
            ChipRow {
                Chip("Engleska riječ", lang == "en", { lang = "en" })
                Chip("Bosanska riječ", lang == "bs", { lang = "bs" })
            }
            Gap(8)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(query, { query = it }, if (lang == "bs") "Unesi bosansku riječ" else "Unesi riječ", Modifier.weight(1f))
                GoldButton("Traži", enabled = query.isNotBlank() && !loading) {
                    if (s.gemBook) lookup(query.trim(), lang)
                    else { cardWord = query.trim(); cardLang = lang; pron = ""; pos = ""; tr = ""; def = ""; ex = ""; err = "Gemini za knjige je isključen. Popuni ručno." }
                }
            }

            if (cardWord != null) {
                Gap(10)
                val bsWord = cardLang == "bs"
                AppCard {
                    Text(cardWord!!, style = T.title)
                    if (loading) {
                        Gap(8); GeminiCard("Gemini", null, true, null)
                    } else {
                        err?.let { Gap(6); Text(it, style = T.small.copy(color = C.danger)) }
                        Gap(8)
                        Field(pron, { pron = it }, if (bsWord) "Naglasak / izgovor" else "Izgovor")
                        Gap(8); Field(pos, { pos = it }, "Vrsta riječi")
                        Gap(8); Field(tr, { tr = it }, if (bsWord) "Objašnjenje (bosanski)" else "Prijevod (bosanski)", singleLine = false, minLines = if (bsWord) 2 else 1)
                        Gap(8); Field(def, { def = it }, if (bsWord) "Prijevod (engleski)" else "Definicija (engleski)", singleLine = false, minLines = 2)
                        Gap(8); Field(ex, { ex = it }, "Upotreba u kontekstu", singleLine = false, minLines = 2)
                        Gap(12)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GoldButton("Sačuvaj uz str. ${book.currentPage}", Modifier.weight(1f)) {
                                scope.launch {
                                    dao.insertWord(
                                        Word(
                                            bookId = book.id, word = cardWord!!, pronunciation = pron, pos = pos, translationBs = tr,
                                            definitionEn = def, example = ex, page = book.currentPage, createdAt = System.currentTimeMillis(), lang = cardLang
                                        )
                                    )
                                    cardWord = null; query = ""
                                }
                            }
                            if (s.gemBook) GhostButton("Ponovo generiši", Modifier.weight(1f), onClick = { lookup(cardWord!!, cardLang) })
                        }
                    }
                }
            }

            val recent = words.filter { it.bookId == book.id }
            if (recent.isNotEmpty()) {
                Gap(16)
                Label("Nedavno sačuvane riječi")
                Gap(8)
                recent.take(30).forEach { w ->
                    var open by remember(w.id) { mutableStateOf(false) }
                    AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { open = !open }, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.word, style = T.medium, modifier = Modifier.weight(1f))
                            Text((if (w.lang == "bs") "bs · " else "") + "str. ${w.page}", style = T.small)
                        }
                        if (w.translationBs.isNotBlank()) Text(w.translationBs, style = T.body.copy(color = C.gold), maxLines = if (open) Int.MAX_VALUE else 2)
                        if (open) {
                            if (w.pronunciation.isNotBlank() || w.pos.isNotBlank()) Text(listOf(w.pronunciation, w.pos).filter { it.isNotBlank() }.joinToString(" · "), style = T.small, modifier = Modifier.padding(top = 4.dp))
                            if (w.definitionEn.isNotBlank()) Text(w.definitionEn, style = T.body.copy(color = C.text2), modifier = Modifier.padding(top = 6.dp))
                            if (w.example.isNotBlank()) Text("“${w.example}”", style = T.small.copy(color = C.text2), modifier = Modifier.padding(top = 6.dp))
                            TextButton(onClick = { scope.launch { dao.deleteWord(w) } }) { Text("Obriši", color = C.danger) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) BookDialog(null, onDismiss = { showAdd = false }) { b ->
        scope.launch { val id = dao.insertBook(b); Graph.prefs.set(K.currentBookId, id) }
    }
    if (showEdit && book != null) BookDialog(book, onDismiss = { showEdit = false }) { b ->
        scope.launch { dao.insertBook(b) }
    }
    if (showPage && book != null) InputDialog("Na kojoj si stranici?", "Stranica", book.currentPage.toString(), KeyboardType.Number, onOk = {
        val p = (it.toIntOrNull() ?: book.currentPage).coerceIn(0, if (book.totalPages > 0) book.totalPages else Int.MAX_VALUE)
        scope.launch {
            val delta = p - book.currentPage
            if (delta > 0) dao.insertReading(ReadingLog(date = LocalDate.now().key(), bookId = book.id, pages = delta))
            dao.insertBook(book.copy(currentPage = p, finishedDay = if (book.totalPages > 0 && p >= book.totalPages) LocalDate.now().toEpochDay() else 0))
        }
    }, onDismiss = { showPage = false })
    if (confirmDel && book != null) Confirm("Obrisati knjigu?", book.title, onOk = { scope.launch { dao.deleteBook(book) } }, onDismiss = { confirmDel = false })
}

/** Dodavanje (initial == null) ili uređivanje knjige; uređivanje traži potvrdu prije spremanja. */
@Composable
private fun BookDialog(initial: Book?, onDismiss: () -> Unit, onOk: (Book) -> Unit) {
    var t by remember { mutableStateOf(initial?.title ?: "") }
    var a by remember { mutableStateOf(initial?.author ?: "") }
    var p by remember { mutableStateOf(initial?.totalPages?.takeIf { it > 0 }?.toString() ?: "") }
    var y by remember { mutableStateOf(initial?.year?.takeIf { it > 0 }?.toString() ?: "") }
    var pub by remember { mutableStateOf(initial?.publisher ?: "") }
    var g by remember { mutableStateOf(initial?.genre ?: "") }
    var l by remember { mutableStateOf(initial?.language ?: "") }
    var confirm by remember { mutableStateOf(false) }

    fun build() = (initial ?: Book()).copy(
        title = t.trim(), author = a.trim(), totalPages = p.toIntOrNull() ?: 0, year = y.toIntOrNull() ?: 0,
        publisher = pub.trim(), genre = g.trim(), language = l.trim()
    )
    fun finish() { onOk(build()); onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(if (initial == null) "Nova knjiga" else "Uredi knjigu", style = T.h2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(t, { t = it }, "Naslov"); Field(a, { a = it }, "Autor")
                Field(p, { p = it.filter(Char::isDigit).take(5) }, "Broj stranica", keyboard = KeyboardType.Number)
                Field(y, { y = it.filter(Char::isDigit).take(4) }, "Godina izdanja", keyboard = KeyboardType.Number)
                Field(pub, { pub = it }, "Izdavač")
                Label("Jezik")
                ChipRow { LANGS.forEach { Chip(it, l == it, { l = it }) } }
                Field(l, { l = it }, "Jezik (ili upiši drugi)")
                Label("Žanr")
                ChipRow { GENRES.forEach { Chip(it, g == it, { g = it }) } }
                Field(g, { g = it }, "Žanr (ili upiši drugi)")
            }
        },
        confirmButton = {
            TextButton(onClick = { if (t.isNotBlank()) { if (initial == null) finish() else confirm = true } }) { Text("Sačuvaj", color = C.gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani", color = C.muted) } }
    )
    if (confirm) Confirm(
        "Sačuvati izmjene?", "Jesi li siguran da želiš sačuvati izmjene knjige „${t.trim()}“?",
        ok = "Da, sačuvaj", okColor = C.gold, onOk = { finish() }, onDismiss = { confirm = false }
    )
}
