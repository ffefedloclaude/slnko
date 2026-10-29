package sk.slnko.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.format.DateTimeFormatter

object Notifications {
    private const val CHANNEL_ID = "sun_events"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = context.getString(R.string.channel_desc)
            nm.createNotificationChannel(ch)
        }
    }

    fun show(context: Context, event: SunEvent, eventTimeMillis: Long) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val zone = java.time.ZoneId.systemDefault()
        val time = java.time.Instant.ofEpochMilli(eventTimeMillis).atZone(zone)
        val timeText = time.format(DateTimeFormatter.ofPattern("H:mm"))
        val minutesLeft = ((eventTimeMillis - System.currentTimeMillis()) / 60_000.0)
            .let { kotlin.math.round(it).toLong() }.coerceAtLeast(0)

        val title = if (event == SunEvent.SUNRISE)
            context.getString(R.string.notif_sunrise_title, timeText)
        else
            context.getString(R.string.notif_sunset_title, timeText)

        val text = if (minutesLeft <= 0) context.getString(R.string.notif_now)
        else context.getString(R.string.notif_in_minutes, minutesLeft, MainActivity.minutesWord(minutesLeft))

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (event == SunEvent.SUNRISE) R.drawable.ic_sunrise else R.drawable.ic_sunset)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        try {
            nm.notify(event.code, n)
        } catch (_: SecurityException) {
            // Upozornenia nie sú povolené.
        }
    }
}
