package sk.slnko.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Spustí sa v čase upozornenia: zobrazí ho a naplánuje ďalšie. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getStringExtra(Scheduler.EXTRA_EVENT)
            ?.let { runCatching { SunEvent.valueOf(it) }.getOrNull() } ?: return
        val eventTime = intent.getLongExtra(Scheduler.EXTRA_EVENT_TIME, 0L)
        if (eventTime > 0L) Notifications.show(context, event, eventTime)
        Scheduler.schedule(context, event)
    }
}

/** Po reštarte telefónu, aktualizácii aplikácie alebo zmene času znovu naplánuje upozornenia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context)
    }
}
