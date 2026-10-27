package io.github.deganegane.wortschatz

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime

/** One daily reminder (inexact alarm), skipped when you have already practised that day. */
object Reminders {

    private const val CHANNEL_ID = "daily"
    private const val NOTIFICATION_ID = 1

    fun reschedule(ctx: Context, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
        if (!s.reminderOn) return
        ensureChannel(ctx)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(s.reminderHour, s.reminderMinute), pending(ctx))
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dnevni podsjetnik", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Podsjetnik na dnevno ponavljanje riječi" }
            )
        }
    }

    fun notify(ctx: Context, due: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_START_REVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (due > 0) {
            "Čeka te $due ${plural(due, "riječ", "riječi", "riječi")} za ponavljanje. Pet minuta i gotovo."
        } else {
            "Danas još nisi vježbao — par novih riječi i niz ostaje živ."
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Zeit für Deutsch!")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, ReminderReceiver::class.java).setAction("io.github.deganegane.wortschatz.REMIND"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val settings = store.loadSettings()
        val t = today()
        val practised = store.loadDays()[t]?.active == true
        if (settings.reminderOn && !practised) {
            Reminders.notify(context, Srs.dueCount(store.loadCards(), t))
        }
        Reminders.reschedule(context, settings)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.reschedule(context, Store(context).loadSettings())
        }
    }
}

/** Croatian plural: 1 riječ, 2–4 riječi, 5+ riječi (with the 11–14 exception). */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}
