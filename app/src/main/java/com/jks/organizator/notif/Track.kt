package com.jks.organizator.notif

import com.jks.organizator.data.Reminder
import com.jks.organizator.data.ReminderLog
import java.time.LocalDate

/** Statistika za praćene podsjetnike (Kur'an, knjiga, učenje...). */
object Track {
    data class Stats(val daysDone: Int, val expected: Int, val total: Int, val streak: Int)

    val PRESETS = listOf("Kur'an", "Knjiga", "Učenje")

    fun unitName(u: String) = when (u) { "min" -> "min"; "str" -> "str."; else -> "" }

    fun stats(r: Reminder, logs: List<ReminderLog>, from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now(), ignoreCreated: Boolean = false): Stats {
        val end = if (to.isAfter(today)) today else to
        val done = logs.filter { it.done }.associateBy { it.date }
        var expected = 0
        var daysDone = 0
        var total = 0
        var d = if (ignoreCreated) from else maxOf(from, LocalDate.ofEpochDay(r.createdDay.coerceAtLeast(0)))
        while (!d.isAfter(end)) {
            if (Rules.occursOn(r, d)) {
                expected++
                done[d.toString()]?.let { daysDone++; total += it.amount }
            }
            d = d.plusDays(1)
        }
        return Stats(daysDone, expected, total, streak(r, done, today))
    }

    /** Najduži niz uzastopnih pojavljivanja s unosom u cijeloj historiji. */
    fun bestStreak(r: Reminder, logs: List<ReminderLog>, from: LocalDate, to: LocalDate): Int {
        val done = logs.filter { it.done }.map { it.date }.toSet()
        var best = 0; var cur = 0
        var d = from
        while (!d.isAfter(to)) {
            if (Rules.occursOn(r, d)) {
                if (d.toString() in done) { cur++; if (cur > best) best = cur } else if (d != to) cur = 0
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** Uzastopna pojavljivanja podsjetnika s unosom; današnji neurađen dan ne prekida niz. */
    fun streak(r: Reminder, done: Map<String, ReminderLog>, today: LocalDate): Int {
        var d = today
        var n = 0
        var first = true
        val floor = LocalDate.ofEpochDay(r.createdDay.coerceAtLeast(0)).minusDays(1)
        while (d.isAfter(floor) && n < 5000) {
            if (Rules.occursOn(r, d)) {
                if (done.containsKey(d.toString())) n++
                else if (!(first && d == today)) break
            }
            first = false
            d = d.minusDays(1)
        }
        return n
    }
}
