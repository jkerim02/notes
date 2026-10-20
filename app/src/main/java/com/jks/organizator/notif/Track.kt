package com.jks.organizator.notif

import com.jks.organizator.data.Reminder
import com.jks.organizator.data.ReminderLog
import java.time.LocalDate

/** Statistika za praćene podsjetnike (Kuran, knjiga, učenje...). */
object Track {
    data class Stats(val daysDone: Int, val expected: Int, val total: Int, val streak: Int)

    val PRESETS = listOf("Kuran", "Knjiga", "Učenje")

    fun unitName(u: String) = when (u) { "min" -> "min"; "str" -> "str."; else -> "" }

    fun stats(r: Reminder, logs: List<ReminderLog>, from: LocalDate, to: LocalDate, today: LocalDate = LocalDate.now()): Stats {
        val end = if (to.isAfter(today)) today else to
        val done = logs.filter { it.done }.associateBy { it.date }
        var expected = 0
        var daysDone = 0
        var total = 0
        var d = maxOf(from, LocalDate.ofEpochDay(r.createdDay.coerceAtLeast(0)))
        while (!d.isAfter(end)) {
            if (Rules.occursOn(r, d)) {
                expected++
                done[d.toString()]?.let { daysDone++; total += it.amount }
            }
            d = d.plusDays(1)
        }
        return Stats(daysDone, expected, total, streak(r, done, today))
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
