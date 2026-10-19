package com.jks.organizator.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jks.organizator.data.*
import com.jks.organizator.net.Gemini
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable
fun rememberBackupSaver(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val t = Backup.export()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(t.toByteArray()) } }
                Graph.prefs.set(K.lastBackup, System.currentTimeMillis())
                Toast.makeText(ctx, "Kopija sačuvana", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(ctx, "Greška: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    return { l.launch("licni-organizator-${LocalDate.now()}.json") }
}

@Composable
fun rememberBackupRestorer(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }
    val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending = uri }
    pending?.let { uri ->
        Confirm("Vratiti podatke?", "Trenutni podaci bit će zamijenjeni sadržajem kopije.", ok = "Vrati", onOk = {
            scope.launch {
                try {
                    val t = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }
                    Backup.import(t)
                    com.jks.organizator.notif.Notifier.rescheduleAll(ctx)
                    Toast.makeText(ctx, "Podaci vraćeni", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(ctx, "Greška: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }, onDismiss = { pending = null })
    }
    return { l.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }
}

private fun fmtDate(ms: Long) = SimpleDateFormat("d. MMM", Locale("bs")).format(Date(ms))

@Composable
fun NotesScreen(onOpen: (Long) -> Unit) {
    val notes by Graph.db.notes().observeAll().collectAsState(emptyList())
    val s by Graph.prefs.flow.collectAsState(Settings())
    var q by remember { mutableStateOf("") }
    val save = rememberBackupSaver()
    val shown = notes.filter { q.isBlank() || it.title.contains(q, true) || it.body.contains(q, true) || it.tag.contains(q, true) }

    Page(
        title = "Bilješke",
        fab = {
            FloatingActionButton(onClick = { onOpen(0) }, containerColor = C.gold, contentColor = C.bg) { Icon(Icons.Outlined.Add, "Nova bilješka") }
        }
    ) {
        Field(q, { q = it }, "Pretraga")
        Gap(12)
        AppCard(color = C.surface2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("Sigurnosna kopija")
                    Gap(2)
                    Text(
                        if (s.lastBackup == 0L) "Još nije sačuvana"
                        else "Zadnja: " + SimpleDateFormat("d. MMM 'u' HH:mm", Locale("bs")).format(Date(s.lastBackup)), style = T.body
                    )
                    Text("Izaberi Google Drive u izborniku", style = T.small)
                }
                GoldButton("Sačuvaj sad", onClick = save)
            }
        }
        Gap(12)
        if (shown.isEmpty()) Text("Nema bilješki.", style = T.small)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in 0..1) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.filterIndexed { i, _ -> i % 2 == col }.forEach { n ->
                        AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(n.id) }) {
                            Label(n.tag, color = C.gold)
                            Gap(4)
                            Text(n.title.ifBlank { "Bez naslova" }, style = T.medium)
                            if (n.body.isNotBlank()) Text(n.body.take(110), style = T.small.copy(color = C.text2), modifier = Modifier.padding(top = 4.dp), maxLines = 4)
                            Text(fmtDate(n.updatedAt), style = T.small, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditorScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.prefs.flow.collectAsState(Settings())
    var loaded by remember { mutableStateOf(id == 0L) }
    var note by remember { mutableStateOf(Note()) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("Lično") }
    var original by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var customTag by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) Graph.db.notes().get(id)?.let {
            note = it; title = it.title; body = it.body; tag = it.tag; original = it.originalBody; loaded = true
        }
    }

    fun persist() {
        if (!loaded || deleted) return
        if (note.id == 0L && title.isBlank() && body.isBlank()) return
        val n = note.copy(title = title.trim(), body = body, tag = tag, updatedAt = System.currentTimeMillis(), originalBody = original)
        scope.launch { Graph.db.notes().insert(n) }
    }

    fun leave() { persist(); onBack() }
    BackHandler { leave() }

    fun run(task: String, prompt: String) {
        if (body.isBlank()) { err = "Bilješka je prazna."; return }
        loading = true; err = null; suggestion = null
        scope.launch {
            Gemini.generate("$prompt\n\nPiši na istom jeziku kao bilješka, osim ako zadatak traži prevod. Vrati samo rezultat, bez uvoda.\n\nBilješka:\n$body")
                .onSuccess { suggestion = it }.onFailure { err = it.message }
            loading = false
        }
    }

    Page(title = if (id == 0L) "Nova bilješka" else "Bilješka", onBack = ::leave, actions = {
        if (id != 0L) IconButton(onClick = { confirmDel = true }) { Icon(Icons.Outlined.Delete, "Obriši", tint = C.danger) }
    }) {
        Field(title, { title = it }, "Naslov")
        Gap(8)
        ChipRow {
            (listOf("Lično", "Učenje", "Studij", "Hadis") + if (tag !in listOf("Lično", "Učenje", "Studij", "Hadis")) listOf(tag) else emptyList()).forEach {
                Chip(it, tag == it, { tag = it })
            }
            Chip("+ Oznaka", false, { customTag = true })
        }
        Gap(8)
        Field(body, { body = it }, "Tekst", singleLine = false, minLines = 10)
        Gap(12)

        if (s.gemNotes) {
            AppCard(color = C.surface2) {
                Label("✦ Sredi uz Gemini", color = C.gold)
                Gap(8)
                ChipRow {
                    Chip("Sredi", onClick = { run("Sredi", "Sredi i formatiraj ovu bilješku: jasni naslovi i liste, ali zadrži sav smisao i činjenice.") })
                    Chip("Sažmi", onClick = { run("Sažmi", "Sažmi ovu bilješku u nekoliko kratkih tačaka.") })
                    Chip("Lista zadataka", onClick = { run("Lista", "Izvuci iz ove bilješke listu zadataka, svaki u novom redu sa '- [ ]'.") })
                    Chip("Ispravi tekst", onClick = { run("Ispravi", "Ispravi pravopisne i gramatičke greške, ne mijenjaj smisao ni stil.") })
                    Chip("Prevedi", onClick = { run("Prevedi", "Prevedi ovu bilješku: ako je na bosanskom prevedi na engleski, inače na bosanski.") })
                }
            }
            if (loading || err != null || suggestion != null) {
                Gap(10)
                GeminiCard("Prijedlog", suggestion, loading, err)
                if (suggestion != null) {
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Primijeni", Modifier.weight(1f)) {
                            if (original.isBlank()) original = body
                            body = suggestion!!; suggestion = null
                        }
                        GhostButton("Odbaci", Modifier.weight(1f)) { suggestion = null }
                    }
                }
            }
            if (original.isNotBlank()) {
                Gap(8)
                GhostButton("Vrati original", Modifier.fillMaxWidth()) { body = original; original = "" }
            }
        }
    }

    if (customTag) InputDialog("Nova oznaka", "Oznaka", onOk = { tag = it }, onDismiss = { customTag = false })
    if (confirmDel) Confirm("Obrisati bilješku?", title.ifBlank { "Bez naslova" }, onOk = {
        deleted = true
        scope.launch { Graph.db.notes().delete(note); }
        onBack()
    }, onDismiss = { confirmDel = false })
}
