package com.jks.organizator.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jks.organizator.MainActivity
import com.jks.organizator.R
import com.jks.organizator.data.*
import com.jks.organizator.net.Vaktija
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Rules {
    fun occursOn(r: Reminder, d: LocalDate): Boolean = when (r.repeat) {
        "daily" -> true
        "weekly" -> {
            val days = r.dayRule.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (days.isEmpty()) true else d.dayOfWeek.value in days
        }
        "monthly" -> {
            val n = r.dayRule.trim().toIntOrNull() ?: 1
            d.dayOfMonth == minOf(n, d.lengthOfMonth())
        }
        "yearly" -> {
            val p = r.dayRule.split("-")
            p.size == 2 && d.monthValue == p[0].toIntOrNull() && d.dayOfMonth == p[1].toIntOrNull()
        }
        else -> false
    }

    fun nextTrigger(r: Reminder, after: LocalDateTime): LocalDateTime? {
        val t = parseTime(r.time)
        var d = after.toLocalDate()
        for (i in 0..800) {
            if (occursOn(r, d)) {
                val dt = LocalDateTime.of(d, t)
                if (dt.isAfter(after)) return dt
            }
            d = d.plusDays(1)
        }
        return null
    }

    fun describe(r: Reminder): String {
        val t = r.time
        return when (r.repeat) {
            "daily" -> "$t · svaki dan"
            "weekly" -> {
                val days = r.dayRule.split(",").mapNotNull { it.trim().toIntOrNull() }.map { DAYS_SHORT[it - 1] }
                "$t · " + if (days.isEmpty()) "sedmično" else days.joinToString(", ")
            }
            "monthly" -> "$t · svakog ${r.dayRule}. u mjesecu"
            "yearly" -> {
                val p = r.dayRule.split("-")
                if (p.size == 2) "$t · ${p[1]}. ${MONTHS_BS[(p[0].toInt() - 1).coerceIn(0, 11)]} svake godine" else "$t · godišnje"
            }
            else -> t
        }
    }
}

object Notifier {
    const val CH_PERSIST = "persistent"
    const val CH_ALERT = "alerts"
    const val A_REFRESH = "com.jks.organizator.REFRESH"
    const val A_EVENING = "com.jks.organizator.EVENING"
    const val A_EVENING_YES = "com.jks.organizator.EVENING_YES"
    const val A_EVENING_NO = "com.jks.organizator.EVENING_NO"
    const val A_REMINDER = "com.jks.organizator.REMINDER"
    const val A_DONE = "com.jks.organizator.DONE"
    const val A_SNOOZE = "com.jks.organizator.SNOOZE"
    const val ID_PERSIST = 1
    const val ID_EVENING = 2

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PERSIST, "Vakti namaza", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stalna notifikacija s vremenima namaza"; setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Podsjetnici i pitanja", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun alarmIntent(ctx: Context, action: String, id: Long = 0, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openApp(ctx: Context, route: String? = null): PendingIntent =
        PendingIntent.getActivity(
            ctx, 77, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun setAlarm(ctx: Context, code: Int, millis: Long, action: String, id: Long = 0) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, action, id, code)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancelAlarm(ctx: Context, code: Int, action: String, id: Long = 0) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, action, id, code))
    }

    private fun millis(dt: LocalDateTime) = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun canNotify(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    // ---- stalna notifikacija ----
    suspend fun refreshPersistent(ctx: Context) {
        channels(ctx)
        val s = Graph.prefs.get()
        val nm = NotificationManagerCompat.from(ctx)
        if (!s.persistent) { nm.cancel(ID_PERSIST); cancelAlarm(ctx, 1, A_REFRESH); return }
        val dao = Graph.db.prayer()
        val today = LocalDate.now()
        var dayRow = dao.day(today.key(), s.cityId)
        if (dayRow == null) { runCatching { Vaktija.ensure(s.cityId) }; dayRow = dao.day(today.key(), s.cityId) }
        val now = LocalDateTime.now()
        if (dayRow == null) {
            setAlarm(ctx, 1, millis(now.plusHours(1)), A_REFRESH)
            return
        }
        val times = dayRow.parsed()
        var nextIdx = times.indexOfFirst { LocalDateTime.of(today, it).isAfter(now) }
        var nextDt: LocalDateTime
        var nextTime: LocalTime
        if (nextIdx >= 0) { nextTime = times[nextIdx]; nextDt = LocalDateTime.of(today, nextTime) }
        else {
            val tom = dao.day(today.plusDays(1).key(), s.cityId)?.parsed()
            nextIdx = 0
            nextTime = tom?.get(0) ?: times[0]
            nextDt = LocalDateTime.of(today.plusDays(1), nextTime)
        }
        val body = times.indices.joinToString("\n") { i ->
            (if (i == nextIdx) "▶ " else "   ") + VAKATS[i] + "  " + fmtTime(times[i])
        }
        val n = NotificationCompat.Builder(ctx, CH_PERSIST)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Do ${VAKATS[nextIdx]} (${fmtTime(nextTime)}) preostalo")
            .setContentText(s.city + " · " + times.indices.joinToString("  ") { fmtTime(times[it]) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setShowWhen(true).setWhen(millis(nextDt)).setUsesChronometer(true).setChronometerCountDown(true)
            .setContentIntent(openApp(ctx))
            .build()
        if (canNotify(ctx)) try { nm.notify(ID_PERSIST, n) } catch (_: SecurityException) {}
        setAlarm(ctx, 1, millis(nextDt.plusSeconds(1)), A_REFRESH)
    }

    // ---- večernje pitanje ----
    fun scheduleEvening(ctx: Context, enabled: Boolean) {
        if (!enabled) { cancelAlarm(ctx, 2, A_EVENING); return }
        val now = LocalDateTime.now()
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(22, 0))
        if (!at.isAfter(now)) at = at.plusDays(1)
        setAlarm(ctx, 2, millis(at), A_EVENING)
    }

    fun showEvening(ctx: Context) {
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Jesi li danas klanjao sve vakate?")
            .setContentText("Označi u aplikaciji ili odgovori ovdje.")
            .setAutoCancel(true).setContentIntent(openApp(ctx))
            .addAction(0, "Da, sve", alarmIntent(ctx, A_EVENING_YES, 0, 20))
            .addAction(0, "Nisam sve", alarmIntent(ctx, A_EVENING_NO, 0, 21))
            .build()
        if (canNotify(ctx)) try { NotificationManagerCompat.from(ctx).notify(ID_EVENING, n) } catch (_: SecurityException) {}
    }

    // ---- podsjetnici ----
    private fun rCode(id: Long) = 1000 + (id % 100000).toInt()
    private fun snoozeCode(id: Long) = 200000 + (id % 100000).toInt()

    fun scheduleReminder(ctx: Context, r: Reminder) {
        cancelAlarm(ctx, rCode(r.id), A_REMINDER, r.id)
        if (!r.enabled) return
        val next = Rules.nextTrigger(r, LocalDateTime.now()) ?: return
        setAlarm(ctx, rCode(r.id), millis(next), A_REMINDER, r.id)
    }

    fun cancelReminder(ctx: Context, r: Reminder) {
        cancelAlarm(ctx, rCode(r.id), A_REMINDER, r.id)
        cancelAlarm(ctx, snoozeCode(r.id), A_REMINDER + ".SNOOZED", r.id)
        NotificationManagerCompat.from(ctx).cancel(1000 + (r.id % 100000).toInt())
    }

    fun showReminder(ctx: Context, r: Reminder) {
        channels(ctx)
        val nid = 1000 + (r.id % 100000).toInt()
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(r.title).setContentText(Rules.describe(r))
            .setAutoCancel(true).setContentIntent(openApp(ctx, "reminders"))
            .addAction(0, "Gotovo", alarmIntent(ctx, A_DONE, r.id, nid))
            .addAction(0, "Odgodi 10 min", alarmIntent(ctx, A_SNOOZE, r.id, nid + 100000))
            .build()
        if (canNotify(ctx)) try { NotificationManagerCompat.from(ctx).notify(nid, n) } catch (_: SecurityException) {}
    }

    suspend fun rescheduleAll(ctx: Context) {
        channels(ctx)
        refreshPersistent(ctx)
        scheduleEvening(ctx, Graph.prefs.get().evening)
        Graph.db.reminders().all().forEach { scheduleReminder(ctx, it) }
    }

    fun snooze(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(1000 + (id % 100000).toInt())
        setAlarm(ctx, snoozeCode(id), System.currentTimeMillis() + 10 * 60_000, A_REMINDER + ".SNOOZED", id)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Graph.init(ctx)
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { handle(ctx, intent) } catch (_: Exception) {} finally { pr.finish() }
        }
    }

    private suspend fun handle(ctx: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        val db = Graph.db
        when (i.action) {
            Notifier.A_REFRESH -> Notifier.refreshPersistent(ctx)
            Notifier.A_EVENING -> {
                if (Graph.prefs.get().evening) Notifier.showEvening(ctx)
                Notifier.scheduleEvening(ctx, Graph.prefs.get().evening)
            }
            Notifier.A_EVENING_YES -> {
                val d = LocalDate.now().key()
                for (v in 0..5) if (isFarz(v)) db.prayer().setLog(PrayerLog(d, v, true))
                NotificationManagerCompat.from(ctx).cancel(Notifier.ID_EVENING)
            }
            Notifier.A_EVENING_NO -> {
                NotificationManagerCompat.from(ctx).cancel(Notifier.ID_EVENING)
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("route", "namaz")
                )
            }
            Notifier.A_REMINDER -> {
                val r = db.reminders().get(id) ?: return
                if (r.enabled) Notifier.showReminder(ctx, r)
                Notifier.scheduleReminder(ctx, r)
            }
            Notifier.A_REMINDER + ".SNOOZED" -> {
                val r = db.reminders().get(id) ?: return
                Notifier.showReminder(ctx, r)
            }
            Notifier.A_DONE -> {
                db.reminders().setLog(ReminderLog(id, LocalDate.now().key(), true))
                NotificationManagerCompat.from(ctx).cancel(1000 + (id % 100000).toInt())
            }
            Notifier.A_SNOOZE -> Notifier.snooze(ctx, id)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                Notifier.rescheduleAll(ctx)
        }
    }
}
