package com.jks.organizator.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

val VAKATS = listOf("Zora", "Izlazak sunca", "Podne", "Ikindija", "Akšam", "Jacija")
val DAYS_BS = listOf("ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota", "nedjelja")
val DAYS_SHORT = listOf("Pon", "Uto", "Sri", "Čet", "Pet", "Sub", "Ned")
val MONTHS_BS = listOf(
    "januar", "februar", "mart", "april", "maj", "juni", "juli", "august", "septembar", "oktobar", "novembar", "decembar"
)

fun isFarz(v: Int) = v != 1

fun LocalDate.bs(): String = "${DAYS_BS[dayOfWeek.value - 1]}, $dayOfMonth. ${MONTHS_BS[monthValue - 1]}"
fun LocalDate.bsShort(): String = "$dayOfMonth. ${MONTHS_BS[monthValue - 1].take(3)}."
fun LocalDate.key(): String = toString()

fun money(v: Double): String = String.format(Locale.GERMANY, "%,.2f", v)
fun moneyInt(v: Double): String = String.format(Locale.GERMANY, "%,.0f", v)

fun parseTime(s: String): LocalTime {
    val p = s.trim().split(":")
    return LocalTime.of(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59))
}

fun fmtTime(t: LocalTime): String = String.format(Locale.US, "%d:%02d", t.hour, t.minute)

fun VakatDay.parsed(): List<LocalTime> = times.split(",").map { parseTime(it) }

fun parseAmount(s: String): Double = s.trim().replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0

// ---------- Hadis ----------
data class Hadith(
    val book: String, val bookName: String, val grade: String, val number: Int,
    val arabic: String, val narrator: String, val english: String
) {
    val key get() = "$book:$number"
}

object Hadiths {
    private data class Book(val file: String, val name: String, val grade: String, val count: Int)
    private var books: List<Book>? = null

    private fun index(ctx: Context): List<Book> = books ?: run {
        val arr = JSONArray(ctx.assets.open("hadith/index.json").bufferedReader().readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Book(o.getString("file"), o.getString("name"), o.optString("grade"), o.getInt("count"))
        }.also { l -> books = l }
    }

    private fun lines(ctx: Context, b: Book): List<String> =
        java.util.zip.GZIPInputStream(ctx.assets.open("hadith/${b.file}.dat")).bufferedReader().readLines()

    private fun parse(b: Book, line: String): Hadith {
        val o = JSONObject(line)
        return Hadith(b.file, b.name, b.grade, o.optInt("n"), o.getString("a"), o.optString("r"), o.optString("e"))
    }

    fun total(ctx: Context) = index(ctx).sumOf { it.count }

    /** Jedan hadis po danu (shuffle > 0 daje drugi izbor); preskače predugačke i one bez engleskog teksta. */
    suspend fun pick(ctx: Context, d: LocalDate, shuffle: Int): Hadith = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bs = index(ctx)
        val total = bs.sumOf { it.count }
        var i = Math.floorMod(d.toEpochDay() * 2654435761L + shuffle * 7919L, total.toLong()).toInt()
        var book = bs.first()
        for (b in bs) { if (i < b.count) { book = b; break }; i -= b.count }
        val l = lines(ctx, book)
        var h: Hadith? = null
        for (k in 0 until l.size) {
            val c = parse(book, l[(i + k) % l.size])
            if (c.arabic.length <= 800 && c.english.isNotBlank()) { h = c; break }
        }
        h ?: parse(book, l[i])
    }

    private fun trPrefs(ctx: Context) = ctx.getSharedPreferences("hadith_bs", Context.MODE_PRIVATE)
    fun savedTranslation(ctx: Context, h: Hadith): String? = trPrefs(ctx).getString(h.key, null)
    fun saveTranslation(ctx: Context, h: Hadith, t: String) { trPrefs(ctx).edit().putString(h.key, t).apply() }
}

object Cities {
    fun all(ctx: Context): List<String> {
        val arr = JSONArray(ctx.assets.open("lokacije.json").bufferedReader().readText())
        return (0 until arr.length()).map { arr.getString(it) }
    }
    val popular = listOf("Visoko", "Sarajevo", "Zenica", "Tuzla", "Mostar", "Banja Luka", "Travnik", "Bihać")
}

// ---------- JSON (de)serialization via reflection for backup ----------
object Json {
    fun toJson(o: Any): JSONObject {
        val j = JSONObject()
        for (f in o.javaClass.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.isSynthetic) continue
            f.isAccessible = true
            j.put(f.name, f.get(o))
        }
        return j
    }

    fun <T : Any> fromJson(cls: Class<T>, j: JSONObject): T {
        val o = cls.getDeclaredConstructor().newInstance()
        for (f in cls.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.isSynthetic || !j.has(f.name)) continue
            f.isAccessible = true
            when (f.type) {
                java.lang.Long.TYPE -> f.setLong(o, j.getLong(f.name))
                Integer.TYPE -> f.setInt(o, j.getInt(f.name))
                java.lang.Double.TYPE -> f.setDouble(o, j.getDouble(f.name))
                java.lang.Boolean.TYPE -> f.setBoolean(o, j.getBoolean(f.name))
                String::class.java -> f.set(o, j.getString(f.name))
            }
        }
        return o
    }

    fun list(l: List<Any>): JSONArray = JSONArray().also { a -> l.forEach { a.put(toJson(it)) } }
    fun <T : Any> read(cls: Class<T>, a: JSONArray?): List<T> =
        if (a == null) emptyList() else (0 until a.length()).map { fromJson(cls, a.getJSONObject(it)) }
}

object Backup {
    suspend fun export(): String {
        val db = Graph.db
        val root = JSONObject()
        root.put("app", "licni-organizator"); root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("prayerLogs", Json.list(db.prayer().allLogs()))
        root.put("reminders", Json.list(db.reminders().all()))
        root.put("reminderLogs", Json.list(db.reminders().allLogs()))
        root.put("notes", Json.list(db.notes().all()))
        root.put("books", Json.list(db.books().books()))
        root.put("words", Json.list(db.books().words()))
        root.put("readings", Json.list(db.books().readings()))
        root.put("categories", Json.list(db.wallet().categories()))
        root.put("txns", Json.list(db.wallet().txns()))
        root.put("goals", Json.list(db.wallet().goals()))
        root.put("prices", Json.list(db.wallet().prices()))
        val s = Graph.prefs.get()
        root.put("profile", JSONObject().put("name", s.name).put("nick", s.nick).put("cityId", s.cityId).put("city", s.city))
        return root.toString(1)
    }

    suspend fun import(text: String) {
        val r = JSONObject(text)
        require(r.optString("app") == "licni-organizator") { "Nije sigurnosna kopija ove aplikacije" }
        val db = Graph.db
        db.prayer().clearLogs(); Json.read(PrayerLog::class.java, r.optJSONArray("prayerLogs")).forEach { db.prayer().setLog(it) }
        db.reminders().clear(); db.reminders().clearLogs()
        Json.read(Reminder::class.java, r.optJSONArray("reminders")).forEach { db.reminders().insert(it) }
        Json.read(ReminderLog::class.java, r.optJSONArray("reminderLogs")).forEach { db.reminders().setLog(it) }
        db.notes().clear(); Json.read(Note::class.java, r.optJSONArray("notes")).forEach { db.notes().insert(it) }
        db.books().clearBooks(); db.books().clearWords(); db.books().clearReadings()
        Json.read(Book::class.java, r.optJSONArray("books")).forEach { db.books().insertBook(it) }
        Json.read(Word::class.java, r.optJSONArray("words")).forEach { db.books().insertWord(it) }
        Json.read(ReadingLog::class.java, r.optJSONArray("readings")).forEach { db.books().insertReading(it) }
        db.wallet().clearCategories(); db.wallet().clearTxns(); db.wallet().clearGoals(); db.wallet().clearPrices()
        Json.read(Category::class.java, r.optJSONArray("categories")).forEach { db.wallet().insertCategory(it) }
        Json.read(Txn::class.java, r.optJSONArray("txns")).forEach { db.wallet().insertTxn(it) }
        Json.read(Goal::class.java, r.optJSONArray("goals")).forEach { db.wallet().insertGoal(it) }
        Json.read(PriceEntry::class.java, r.optJSONArray("prices")).forEach { db.wallet().insertPrice(it) }
        r.optJSONObject("profile")?.let {
            val p = Graph.prefs
            p.set(K.name, it.optString("name")); p.set(K.nick, it.optString("nick"))
            p.set(K.cityId, it.optInt("cityId", 97)); p.set(K.city, it.optString("city", "Visoko"))
        }
    }
}
