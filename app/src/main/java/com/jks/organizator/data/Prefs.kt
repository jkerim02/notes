package com.jks.organizator.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class Settings(
    val name: String = "",
    val nick: String = "",
    val cityId: Int = 97,
    val city: String = "Visoko",
    val lock: Boolean = false,
    val persistent: Boolean = true,
    val evening: Boolean = true,
    val gemBook: Boolean = true,
    val gemSavings: Boolean = true,
    val gemWallet: Boolean = true,
    val gemNotes: Boolean = true,
    val gemHadith: Boolean = true,
    val lastBackup: Long = 0,
    val avatarVersion: Long = 0,
    val currentBookId: Long = 0
)

object K {
    val name = stringPreferencesKey("name")
    val nick = stringPreferencesKey("nick")
    val cityId = intPreferencesKey("cityId")
    val city = stringPreferencesKey("city")
    val lock = booleanPreferencesKey("lock")
    val persistent = booleanPreferencesKey("persistent")
    val evening = booleanPreferencesKey("evening")
    val gemBook = booleanPreferencesKey("gemBook")
    val gemSavings = booleanPreferencesKey("gemSavings")
    val gemWallet = booleanPreferencesKey("gemWallet")
    val gemNotes = booleanPreferencesKey("gemNotes")
    val gemHadith = booleanPreferencesKey("gemHadith")
    val lastBackup = longPreferencesKey("lastBackup")
    val avatarVersion = longPreferencesKey("avatarVersion")
    val currentBookId = longPreferencesKey("currentBookId")
}

class Prefs(private val ctx: Context) {
    val flow: Flow<Settings> = ctx.store.data.map { p ->
        val d = Settings()
        Settings(
            p[K.name] ?: d.name, p[K.nick] ?: d.nick, p[K.cityId] ?: d.cityId, p[K.city] ?: d.city,
            p[K.lock] ?: d.lock, p[K.persistent] ?: d.persistent, p[K.evening] ?: d.evening,
            p[K.gemBook] ?: true, p[K.gemSavings] ?: true, p[K.gemWallet] ?: true, p[K.gemNotes] ?: true, p[K.gemHadith] ?: true,
            p[K.lastBackup] ?: 0, p[K.avatarVersion] ?: 0, p[K.currentBookId] ?: 0
        )
    }

    suspend fun get(): Settings = flow.first()
    suspend fun <T> set(key: Preferences.Key<T>, v: T) { ctx.store.edit { it[key] = v } }

    private val secrets: SharedPreferences by lazy {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "secrets", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    fun geminiKey(): String = try { secrets.getString("gemini", "") ?: "" } catch (e: Exception) { "" }
    fun setGeminiKey(k: String) { try { secrets.edit().putString("gemini", k.trim()).apply() } catch (_: Exception) {} }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE txn ADD COLUMN forMonth TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminder ADD COLUMN track TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reminder ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reminder_log ADD COLUMN amount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE book ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE book ADD COLUMN language TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE book ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE book ADD COLUMN publisher TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE word ADD COLUMN lang TEXT NOT NULL DEFAULT 'en'")
    }
}

object Graph {
    lateinit var db: AppDb
    lateinit var prefs: Prefs
    lateinit var app: Context
    fun init(ctx: Context) {
        if (::db.isInitialized) return
        app = ctx.applicationContext
        db = Room.databaseBuilder(app, AppDb::class.java, "organizator.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        prefs = Prefs(app)
    }
}
