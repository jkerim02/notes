package com.jks.organizator.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vakat_day", primaryKeys = ["date", "cityId"])
data class VakatDay(var date: String = "", var cityId: Int = 0, var times: String = "")

@Entity(tableName = "prayer_log", primaryKeys = ["date", "vakat"])
data class PrayerLog(var date: String = "", var vakat: Int = 0, var done: Boolean = false)

@Entity(tableName = "reminder")
data class Reminder(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var title: String = "",
    var time: String = "08:00",
    var repeat: String = "daily", // daily|weekly|monthly|yearly
    var dayRule: String = "",     // weekly: "1,3" (Pon=1), monthly: "15", yearly: "03-21"
    var enabled: Boolean = true,
    var createdDay: Long = 0,
    var track: String = "", // "" = obični podsjetnik, inače naziv praćene navike (Kuran, Knjiga, ...)
    var unit: String = ""   // "" samo urađeno, "min" ili "str"
)

@Entity(tableName = "reminder_log", primaryKeys = ["reminderId", "date"])
data class ReminderLog(var reminderId: Long = 0, var date: String = "", var done: Boolean = false, var amount: Int = 0)

@Entity(tableName = "note")
data class Note(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var title: String = "",
    var body: String = "",
    var tag: String = "Lično",
    var updatedAt: Long = 0,
    var originalBody: String = ""
)

@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var title: String = "",
    var author: String = "",
    var totalPages: Int = 0,
    var currentPage: Int = 0,
    var finishedDay: Long = 0
)

@Entity(tableName = "reading_log")
data class ReadingLog(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var date: String = "",
    var bookId: Long = 0,
    var pages: Int = 0
)

@Entity(tableName = "word")
data class Word(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var bookId: Long = 0,
    var word: String = "",
    var pronunciation: String = "",
    var pos: String = "",
    var translationBs: String = "",
    var definitionEn: String = "",
    var example: String = "",
    var page: Int = 0,
    var createdAt: Long = 0,
    var reviewCount: Int = 0
)

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var kind: String = "spent", // spent|earned
    var name: String = ""
)

@Entity(tableName = "txn")
data class Txn(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var kind: String = "spent", // spent|earned|saved
    var amount: Double = 0.0,
    var categoryId: Long = 0,
    var goalId: Long = 0,
    var date: String = "",
    var place: String = "",
    var forMonth: String = "" // "yyyy-MM"; prazno = mjesec datuma
)

@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: String = "",
    var price: Double = 0.0,
    var deadline: String = "",
    var params: String = "{}"
)

@Entity(tableName = "price_entry")
data class PriceEntry(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var goalId: Long = 0,
    var date: String = "",
    var price: Double = 0.0,
    var source: String = "manual"
)

@Dao
interface PrayerDao {
    @Query("SELECT * FROM vakat_day WHERE date=:d AND cityId=:c") suspend fun day(d: String, c: Int): VakatDay?
    @Query("SELECT * FROM vakat_day WHERE date=:d AND cityId=:c") fun observeDay(d: String, c: Int): Flow<VakatDay?>
    @Query("SELECT date FROM vakat_day WHERE cityId=:c AND date>=:from") suspend fun datesFrom(from: String, c: Int): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDays(l: List<VakatDay>)
    @Query("SELECT * FROM prayer_log WHERE date BETWEEN :a AND :b") fun observeLogs(a: String, b: String): Flow<List<PrayerLog>>
    @Query("SELECT * FROM prayer_log WHERE date BETWEEN :a AND :b") suspend fun logs(a: String, b: String): List<PrayerLog>
    @Query("SELECT * FROM prayer_log WHERE done=1") fun observeDone(): Flow<List<PrayerLog>>
    @Query("SELECT * FROM prayer_log WHERE done=1") suspend fun allDone(): List<PrayerLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setLog(l: PrayerLog)
    @Query("SELECT * FROM prayer_log") suspend fun allLogs(): List<PrayerLog>
    @Query("SELECT * FROM vakat_day") suspend fun allDays(): List<VakatDay>
    @Query("DELETE FROM prayer_log") suspend fun clearLogs()
    @Query("DELETE FROM vakat_day") suspend fun clearDays()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY time") fun observeAll(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminder") suspend fun all(): List<Reminder>
    @Query("SELECT * FROM reminder WHERE id=:id") suspend fun get(id: Long): Reminder?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(r: Reminder): Long
    @Delete suspend fun delete(r: Reminder)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setLog(l: ReminderLog)
    @Query("SELECT * FROM reminder_log WHERE reminderId=:id AND date=:d") suspend fun log(id: Long, d: String): ReminderLog?
    @Query("DELETE FROM reminder_log WHERE reminderId=:id") suspend fun deleteLogsFor(id: Long)
    @Query("DELETE FROM reminder_log WHERE reminderId=:id AND date=:d") suspend fun deleteLog(id: Long, d: String)
    @Query("SELECT * FROM reminder_log WHERE date BETWEEN :a AND :b") suspend fun logs(a: String, b: String): List<ReminderLog>
    @Query("SELECT * FROM reminder_log") suspend fun allLogs(): List<ReminderLog>
    @Query("SELECT * FROM reminder_log") fun observeLogs(): Flow<List<ReminderLog>>
    @Query("DELETE FROM reminder") suspend fun clear()
    @Query("DELETE FROM reminder_log") suspend fun clearLogs()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note ORDER BY updatedAt DESC") fun observeAll(): Flow<List<Note>>
    @Query("SELECT * FROM note") suspend fun all(): List<Note>
    @Query("SELECT * FROM note WHERE id=:id") suspend fun get(id: Long): Note?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(n: Note): Long
    @Delete suspend fun delete(n: Note)
    @Query("DELETE FROM note") suspend fun clear()
}

@Dao
interface BookDao {
    @Query("SELECT * FROM book ORDER BY id DESC") fun observeBooks(): Flow<List<Book>>
    @Query("SELECT * FROM book") suspend fun books(): List<Book>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBook(b: Book): Long
    @Delete suspend fun deleteBook(b: Book)
    @Query("SELECT * FROM word ORDER BY createdAt DESC") fun observeWords(): Flow<List<Word>>
    @Query("SELECT * FROM word") suspend fun words(): List<Word>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWord(w: Word): Long
    @Delete suspend fun deleteWord(w: Word)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReading(r: ReadingLog)
    @Query("SELECT * FROM reading_log") suspend fun readings(): List<ReadingLog>
    @Query("DELETE FROM book") suspend fun clearBooks()
    @Query("DELETE FROM word") suspend fun clearWords()
    @Query("DELETE FROM reading_log") suspend fun clearReadings()
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM category WHERE kind=:kind ORDER BY id") fun observeCategories(kind: String): Flow<List<Category>>
    @Query("SELECT * FROM category") fun observeAllCategories(): Flow<List<Category>>
    @Query("SELECT * FROM category") suspend fun categories(): List<Category>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategory(c: Category): Long
    @Query("SELECT * FROM txn ORDER BY date DESC, id DESC") fun observeTxns(): Flow<List<Txn>>
    @Query("SELECT * FROM txn") suspend fun txns(): List<Txn>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTxn(t: Txn): Long
    @Delete suspend fun deleteTxn(t: Txn)
    @Query("SELECT * FROM goal ORDER BY id") fun observeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goal") suspend fun goals(): List<Goal>
    @Query("SELECT * FROM goal WHERE id=:id") suspend fun goal(id: Long): Goal?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoal(g: Goal): Long
    @Delete suspend fun deleteGoal(g: Goal)
    @Query("SELECT * FROM price_entry WHERE goalId=:id ORDER BY date DESC, id DESC") fun observePrices(id: Long): Flow<List<PriceEntry>>
    @Query("SELECT * FROM price_entry") suspend fun prices(): List<PriceEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPrice(p: PriceEntry)
    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM txn") suspend fun clearTxns()
    @Query("DELETE FROM goal") suspend fun clearGoals()
    @Query("DELETE FROM price_entry") suspend fun clearPrices()
}

@Database(
    entities = [VakatDay::class, PrayerLog::class, Reminder::class, ReminderLog::class, Note::class, Book::class,
        ReadingLog::class, Word::class, Category::class, Txn::class, Goal::class, PriceEntry::class],
    version = 3, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun prayer(): PrayerDao
    abstract fun reminders(): ReminderDao
    abstract fun notes(): NoteDao
    abstract fun books(): BookDao
    abstract fun wallet(): WalletDao
}
