package com.jks.organizator

import com.jks.organizator.data.*
import com.jks.organizator.notif.Rules
import com.jks.organizator.ui.Calc
import com.jks.organizator.ui.streak
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogicTest {
    private fun rem(repeat: String, rule: String = "", time: String = "08:00") = Reminder(repeat = repeat, dayRule = rule, time = time)

    @Test fun dailyOccursEveryDay() = assertTrue(Rules.occursOn(rem("daily"), LocalDate.of(2026, 3, 5)))

    @Test fun weeklyMatchesSelectedDays() {
        val r = rem("weekly", "1,3") // Pon, Sri
        assertTrue(Rules.occursOn(r, LocalDate.of(2026, 10, 19))) // ponedjeljak
        assertFalse(Rules.occursOn(r, LocalDate.of(2026, 10, 20)))
        assertTrue(Rules.occursOn(r, LocalDate.of(2026, 10, 21)))
    }

    @Test fun monthlyClampsToMonthEnd() {
        val r = rem("monthly", "31")
        assertTrue(Rules.occursOn(r, LocalDate.of(2026, 2, 28)))
        assertTrue(Rules.occursOn(r, LocalDate.of(2026, 10, 31)))
        assertFalse(Rules.occursOn(r, LocalDate.of(2026, 10, 30)))
    }

    @Test fun yearlyMatchesDate() {
        val r = rem("yearly", "03-21")
        assertTrue(Rules.occursOn(r, LocalDate.of(2027, 3, 21)))
        assertFalse(Rules.occursOn(r, LocalDate.of(2027, 3, 22)))
    }

    @Test fun nextTriggerTodayIfLater() {
        val n = Rules.nextTrigger(rem("daily", time = "10:00"), LocalDateTime.of(2026, 10, 19, 9, 0))
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), n)
    }

    @Test fun nextTriggerTomorrowIfPassed() {
        val n = Rules.nextTrigger(rem("daily", time = "10:00"), LocalDateTime.of(2026, 10, 19, 10, 0))
        assertEquals(LocalDateTime.of(2026, 10, 20, 10, 0), n)
    }

    @Test fun nextTriggerWeekly() {
        val n = Rules.nextTrigger(rem("weekly", "5", "07:30"), LocalDateTime.of(2026, 10, 19, 12, 0))
        assertEquals(LocalDateTime.of(2026, 10, 23, 7, 30), n)
    }

    @Test fun parseAndFormatTime() {
        assertEquals("5:30", fmtTime(parseTime("5:30")))
        assertEquals("18:03", fmtTime(parseTime("18:03")))
    }

    @Test fun parseAmountAcceptsCommaAndGarbage() {
        assertEquals(12.5, parseAmount("12,5"), 0.0)
        assertEquals(1000.0, parseAmount("1 000"), 0.0)
        assertEquals(0.0, parseAmount("abc"), 0.0)
    }

    @Test fun vakatDayParsing() {
        val d = VakatDay("2026-10-19", 97, "5:30,6:59,12:32,15:28,18:03,19:22").parsed()
        assertEquals(6, d.size)
        assertEquals("15:28", fmtTime(d[3]))
    }

    @Test fun farzExcludesSunrise() {
        assertFalse(isFarz(1)); assertTrue(isFarz(0)); assertTrue(isFarz(5))
    }

    @Test fun streakCountsConsecutiveFullDays() {
        val today = LocalDate.of(2026, 10, 19)
        fun full(d: LocalDate) = listOf(0, 2, 3, 4, 5).map { PrayerLog(d.toString(), it, true) }
        val logs = full(today) + full(today.minusDays(1)) + full(today.minusDays(2)) + full(today.minusDays(4))
        assertEquals(3, streak(logs, today))
    }

    @Test fun streakTodayIncompleteDoesNotBreak() {
        val today = LocalDate.of(2026, 10, 19)
        val y = today.minusDays(1)
        val logs = listOf(0, 2, 3, 4, 5).map { PrayerLog(y.toString(), it, true) } + PrayerLog(today.toString(), 0, true)
        assertEquals(1, streak(logs, today))
    }

    @Test fun savingsMath() {
        assertEquals(1000.0, Calc.target(1000.0, 0.0, 365), 0.001)
        assertEquals(960.0, Calc.target(1000.0, 0.04, 365), 0.001)
        assertEquals("20 dana", Calc.duration(20)); assertEquals("1 mj", Calc.duration(30))
        assertEquals("1 god 2 mj", Calc.duration(365 + 65))
    }

    @Test fun dailyNeededNeverNegative() {
        val g = Goal(price = 100.0, deadline = LocalDate.now().plusDays(10).toString())
        assertEquals(0.0, Calc.daily(g, 500.0, 0.0), 0.0)
        assertEquals(9.0, Calc.daily(g, 10.0, 0.0), 0.01)
    }
}
