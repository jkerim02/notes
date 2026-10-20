package com.jks.organizator.net

import com.jks.organizator.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

object Http {
    fun get(url: String, timeout: Int = 12000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    fun postJson(url: String, headers: Map<String, String>, body: String, timeout: Int = 60000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP ${c.responseCode}: " + (runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err)).take(200))
            }
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
}

object Vaktija {
    const val BASE = "https://api.vaktija.ba/vaktija/v1"

    /** Povlači vremena za jedan (npr. prošli) dan ako ih nema lokalno. */
    suspend fun ensureDay(cityId: Int, d: LocalDate) = withContext(Dispatchers.IO) {
        val dao = Graph.db.prayer()
        if (dao.day(d.key(), cityId) != null) return@withContext
        val j = JSONObject(Http.get("$BASE/$cityId/${d.year}/${d.monthValue}/${d.dayOfMonth}"))
        val a: JSONArray = j.getJSONArray("vakat")
        dao.insertDays(listOf(VakatDay(d.key(), cityId, (0 until a.length()).joinToString(",") { a.getString(it) })))
    }

    /** Povlači vremena od danas do [days] dana unaprijed i čuva ih lokalno. Vraća broj novih dana. */
    suspend fun ensure(cityId: Int, days: Int = 35): Int = withContext(Dispatchers.IO) {
        val dao = Graph.db.prayer()
        val today = LocalDate.now()
        val have = dao.datesFrom(today.key(), cityId).toSet()
        val out = mutableListOf<VakatDay>()
        for (i in 0 until days) {
            val d = today.plusDays(i.toLong())
            if (d.key() in have) continue
            try {
                val j = JSONObject(Http.get("$BASE/$cityId/${d.year}/${d.monthValue}/${d.dayOfMonth}"))
                val a: JSONArray = j.getJSONArray("vakat")
                out.add(VakatDay(d.key(), cityId, (0 until a.length()).joinToString(",") { a.getString(it) }))
            } catch (e: Exception) {
                break // vjerovatno nema mreže; pokušaćemo kasnije
            }
        }
        if (out.isNotEmpty()) dao.insertDays(out)
        out.size
    }
}

object Gemini {
    private const val MODEL = "gemini-3.8-flash"

    fun available(): Boolean = Graph.prefs.geminiKey().isNotBlank()

    suspend fun generate(prompt: String, json: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = Graph.prefs.geminiKey()
            require(key.isNotBlank()) { "Upiši Gemini API ključ u Profilu." }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            if (json) body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            val res = Http.postJson(
                "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent",
                mapOf("x-goog-api-key" to key), body.toString()
            )
            val parts = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
        }.recoverCatching { e ->
            throw Exception(
                when {
                    e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "Nema interneta."
                    else -> e.message ?: "Greška"
                }
            )
        }
    }

    suspend fun generateJson(prompt: String): Result<JSONObject> =
        generate(prompt, json = true).mapCatching { JSONObject(it.removePrefix("```json").removeSuffix("```").trim()) }
}
