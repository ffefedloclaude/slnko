package sk.slnko.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class SunEvent(val code: Int) { SUNRISE(1), SUNSET(2) }

/** Plánovanie upozornení pred východom / západom slnka. */
object Scheduler {

    const val EXTRA_EVENT = "event"
    const val EXTRA_EVENT_TIME = "eventTime"

    data class Planned(val event: SunEvent, val eventTime: ZonedDateTime, val triggerMillis: Long)

    fun scheduleAll(context: Context) {
        schedule(context, SunEvent.SUNRISE)
        schedule(context, SunEvent.SUNSET)
    }

    private fun isEnabled(prefs: Prefs, event: SunEvent) =
        if (event == SunEvent.SUNRISE) prefs.notifySunrise else prefs.notifySunset

    private fun minutesBefore(prefs: Prefs, event: SunEvent) =
        if (event == SunEvent.SUNRISE) prefs.minutesBeforeSunrise else prefs.minutesBeforeSunset

    /** Nájde najbližšiu udalosť, ktorej upozornenie je ešte v budúcnosti. */
    fun findNext(context: Context, event: SunEvent): Planned? {
        val prefs = Prefs(context)
        if (!prefs.hasLocation) return null
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val offset = minutesBefore(prefs, event) * 60_000L
        val today = LocalDate.now(zone)
        // Hľadáme až 200 dní dopredu (kvôli polárnemu dňu / noci).
        for (d in -1L..200L) {
            val times = SunCalc.compute(today.plusDays(d), prefs.latitude, prefs.longitude, zone)
            val t = (if (event == SunEvent.SUNRISE) times.sunrise else times.sunset) ?: continue
            val trigger = t.toInstant().toEpochMilli() - offset
            if (trigger > now + 5_000L) return Planned(event, t, trigger)
        }
        return null
    }

    private fun pendingIntent(context: Context, event: SunEvent, eventTime: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("sk.slnko.app.ALARM_${event.name}")
            .putExtra(EXTRA_EVENT, event.name)
            .putExtra(EXTRA_EVENT_TIME, eventTime)
        return PendingIntent.getBroadcast(
            context, event.code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, event: SunEvent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = Prefs(context)
        val next = if (isEnabled(prefs, event)) findNext(context, event) else null
        if (next == null) {
            am.cancel(pendingIntent(context, event, 0L))
            return
        }
        val pi = pendingIntent(context, event, next.eventTime.toInstant().toEpochMilli())
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerMillis, pi)
        }
    }
}
