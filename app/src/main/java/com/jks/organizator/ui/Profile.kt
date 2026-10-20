package com.jks.organizator.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jks.organizator.net.Vaktija
import com.jks.organizator.notif.Notifier
import com.jks.organizator.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun Avatar(size: Int, s: Settings, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val bmp = remember(s.avatarVersion) {
        val f = File(ctx.filesDir, "avatar.jpg")
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() else null
    }
    var m = Modifier.size(size.dp).clip(CircleShape).background(C.surface2, CircleShape).border(2.dp, C.gold, CircleShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(
            (s.name.trim().firstOrNull() ?: s.nick.trim().firstOrNull() ?: 'J').uppercase(),
            style = T.h2.copy(color = C.gold)
        )
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onReport: (String, String, String, Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.prefs.flow.collectAsState(Settings())
    val allDone by Graph.db.prayer().observeDone().collectAsState(emptyList())
    val words by Graph.db.books().observeWords().collectAsState(emptyList())
    val goals by Graph.db.wallet().observeGoals().collectAsState(emptyList())
    val txns by Graph.db.wallet().observeTxns().collectAsState(emptyList())
    val cities = remember { Cities.all(ctx) }

    var edit by remember { mutableStateOf(false) }
    var name by remember(s.name, edit) { mutableStateOf(s.name) }
    var nick by remember(s.nick, edit) { mutableStateOf(s.nick) }
    var city by remember(s.city, edit) { mutableStateOf(s.city) }
    var cityQuery by remember { mutableStateOf("") }

    var period by remember { mutableStateOf("7") }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(6)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var mask by remember { mutableStateOf(M_ALL) }
    var key by remember { mutableStateOf(Graph.prefs.geminiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { i ->
                    val b = BitmapFactory.decodeStream(i)
                    val sc = 512f / maxOf(b.width, b.height)
                    val bb = if (sc < 1) android.graphics.Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
                    File(ctx.filesDir, "avatar.jpg").outputStream().use { o -> bb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, o) }
                }
            }
            Graph.prefs.set(K.avatarVersion, System.currentTimeMillis())
        }
    }
    val saveBackup = rememberBackupSaver()
    val restore = rememberBackupRestorer()
    val pdf = rememberPdfExporter { buildReport(from, to) to mask }

    val streakN = streak(allDone, today)
    val goalPct = goals.firstOrNull()?.let { g -> if (g.price > 0) (Calc.saved(g.id, txns) / g.price * 100).toInt() else 0 } ?: 0

    Page(title = "Profil", onBack = onBack) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(72, s)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name.ifBlank { "Tvoje ime" }, style = T.h2)
                    Text(if (s.nick.isBlank()) "@nadimak" else "@${s.nick}", style = T.small)
                    Text(s.city, style = T.small.copy(color = C.gold))
                }
                GhostButton(if (edit) "Zatvori" else "Uredi", onClick = { edit = !edit })
            }
            if (edit) {
                Gap(14)
                Field(name, { name = it }, "Ime")
                Gap(8); Field(nick, { nick = it.removePrefix("@") }, "Nadimak")
                Gap(8)
                GhostButton("Izaberi sliku", Modifier.fillMaxWidth()) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                Gap(10); Label("Grad za vaktiju"); Gap(6)
                ChipRow { Cities.popular.forEach { Chip(it, city == it, { city = it }) } }
                Gap(8)
                Field(cityQuery, { cityQuery = it }, "Traži grad")
                if (cityQuery.length >= 2) {
                    Gap(6)
                    ChipRow { cities.filter { it.contains(cityQuery, true) }.take(10).forEach { Chip(it, city == it, { city = it; cityQuery = "" }) } }
                }
                Gap(12)
                GoldButton("Sačuvaj", Modifier.fillMaxWidth()) {
                    scope.launch {
                        val p = Graph.prefs
                        p.set(K.name, name.trim()); p.set(K.nick, nick.trim())
                        val idx = cities.indexOf(city)
                        if (idx >= 0 && city != s.city) {
                            p.set(K.cityId, idx); p.set(K.city, city)
                            runCatching { Vaktija.ensure(idx) }
                            runCatching { Notifier.refreshPersistent(ctx) }
                        }
                        edit = false
                    }
                }
            }
        }
        Gap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppCard(Modifier.weight(1f), padding = 12.dp) { Label("Niz"); Text("$streakN", style = T.h2); Text("dana", style = T.small) }
            AppCard(Modifier.weight(1f), padding = 12.dp) { Label("Riječi"); Text("${words.size}", style = T.h2); Text("sačuvano", style = T.small) }
            AppCard(Modifier.weight(1f), padding = 12.dp) { Label("Cilj"); Text("$goalPct%", style = T.h2.copy(color = C.gold)); Text("štednje", style = T.small) }
        }

        Gap(20); Label("Izvještaj za period"); Gap(8)
        ChipRow {
            Chip("Zadnjih 7 dana", period == "7", { period = "7"; from = today.minusDays(6); to = today })
            Chip("Zadnjih 30 dana", period == "30", { period = "30"; from = today.minusDays(29); to = today })
            Chip("Ovaj mjesec", period == "m", { period = "m"; from = today.withDayOfMonth(1); to = today })
            Chip("Ova godina", period == "y", { period = "y"; from = today.withDayOfYear(1); to = today })
            Chip("Svoj period", period == "c", { period = "c" })
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (period == "c") {
                DateField("Od", from, { from = it }, Modifier.weight(1f)); DateField("Do", to, { to = it }, Modifier.weight(1f))
            } else {
                AppCard(Modifier.weight(1f), padding = 12.dp) { Text("Od", style = T.small); Text(from.toString(), style = T.body) }
                AppCard(Modifier.weight(1f), padding = 12.dp) { Text("Do", style = T.small); Text(to.toString(), style = T.body) }
            }
        }
        Gap(8)
        ChipRow {
            listOf("Namaz" to M_PRAYER, "Knjige" to M_BOOKS, "Podsjetnici" to M_REMINDERS, "Novčanik" to M_WALLET).forEach { (n, b) ->
                Chip(n, mask and b != 0, { mask = mask xor b }, accent = C.teal)
            }
        }
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton("Prikaži izvještaj", Modifier.weight(1f), enabled = mask != 0) { onReport("custom", from.toString(), to.toString(), mask) }
            GhostButton("Izvezi kao PDF", Modifier.weight(1f), enabled = mask != 0, onClick = pdf)
        }

        Gap(20); Label("Aplikacija"); Gap(4)
        AppCard(padding = 12.dp) {
            SwitchRow("Stalna notifikacija s vaktovima", checked = s.persistent, onChange = { v ->
                scope.launch { Graph.prefs.set(K.persistent, v); Notifier.refreshPersistent(ctx) }
            })
            SwitchRow("Večernje pitanje u 22:00", checked = s.evening, onChange = { v ->
                scope.launch { Graph.prefs.set(K.evening, v); Notifier.scheduleEvening(ctx, v) }
            })
            val canBio = remember { BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS }
            SwitchRow("Zaključavanje (otisak prsta)", if (canBio) null else "Nije podešeno na ovom uređaju", s.lock && canBio, { v ->
                if (canBio) scope.launch { Graph.prefs.set(K.lock, v) }
            })
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Valuta", style = T.body, modifier = Modifier.weight(1f)); Text("KM", style = T.small)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Jezik", style = T.body, modifier = Modifier.weight(1f)); Text("bosanski", style = T.small)
            }
            TextButton(onClick = {
                ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Dozvoli tačne alarme", color = C.gold) }
        }

        Gap(16); Label("Gemini: gdje se koristi"); Gap(4)
        AppCard(padding = 12.dp) {
            SwitchRow("Knjige: riječi", checked = s.gemBook, onChange = { v -> scope.launch { Graph.prefs.set(K.gemBook, v) } })
            SwitchRow("Štednja: procjena cijene", checked = s.gemSavings, onChange = { v -> scope.launch { Graph.prefs.set(K.gemSavings, v) } })
            SwitchRow("Novčanik: uvid", checked = s.gemWallet, onChange = { v -> scope.launch { Graph.prefs.set(K.gemWallet, v) } })
            SwitchRow("Bilješke: sređivanje", checked = s.gemNotes, onChange = { v -> scope.launch { Graph.prefs.set(K.gemNotes, v) } })
            SwitchRow("Namaz: objašnjenje hadisa", checked = s.gemHadith, onChange = { v -> scope.launch { Graph.prefs.set(K.gemHadith, v) } })
            Gap(8)
            OutlinedTextField(
                value = key, onValueChange = { key = it }, label = { Text("Gemini API ključ") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.gold, unfocusedBorderColor = C.line, focusedTextColor = C.text, unfocusedTextColor = C.text, cursorColor = C.gold, focusedLabelColor = C.gold, unfocusedLabelColor = C.muted)
            )
            Row {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Sakrij" else "Prikaži", color = C.muted) }
                TextButton(onClick = { Graph.prefs.setGeminiKey(key); android.widget.Toast.makeText(ctx, "Ključ sačuvan (šifrirano)", android.widget.Toast.LENGTH_SHORT).show() }) { Text("Sačuvaj ključ", color = C.gold) }
            }
        }

        Gap(16); Label("Podaci"); Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Sigurnosna kopija", Modifier.weight(1f), onClick = saveBackup)
            GhostButton("Vrati iz kopije", Modifier.weight(1f), onClick = restore)
        }
        Gap(8)
        GhostButton("Izvezi podatke (JSON)", Modifier.fillMaxWidth(), onClick = saveBackup)
        TextButton(onClick = { about = true }) { Text("O aplikaciji", color = C.muted) }

        Gap(8)
        Text("Product by JKs", style = T.small, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("2026 · verzija 1.1.0", style = T.small, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false }, containerColor = C.surface, title = { Text("Lični organizator", style = T.h2) },
        text = { Text("Namaz, podsjetnici, bilješke, knjige i novčanik na jednom mjestu. Podaci se čuvaju samo na ovom uređaju. Vremena namaza dolaze sa Vaktija.ba.", style = T.body) },
        confirmButton = { TextButton(onClick = { about = false }) { Text("U redu", color = C.gold) } }
    )
}
