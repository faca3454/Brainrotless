package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Resumen semanal: los domingos a las 20, una notificación con los últimos 7 días contra los 7
 * anteriores (o contra el uso de antes de Lúcido) y la racha. Tocarla abre las estadísticas.
 */
object Weekly {
    const val ACTION = "com.brainrotless.app.WEEKLY"
    private const val CHANNEL = "weekly"
    private const val NOTIF_ID = 2004
    private const val HOUR = 20

    fun enabled(c: Context) = c.userPrefs().getBoolean("weekly_summary", true)

    /** Programa el próximo domingo (o lo cancela si está apagado). No hace falta que sea exacto. */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            c, 4, Intent(c, SleepAlarm::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!enabled(c)) return
        val at = StatsMath.nextSunday(LocalDateTime.now(), HOUR)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    fun notify(c: Context) {
        if (!enabled(c)) return
        val history = Usage.history(c)
        val today = Usage.today(c)
        val week = StatsMath.week(history, today) ?: return // muy pocos días para resumir

        val total = formatDuration(week.total)
        val title = if (week.days == 7) c.getString(R.string.weekly_title, total)
            else c.getString(R.string.weekly_title_days, week.days, total)
        val compare = week.previous?.takeIf { it > 0 }?.let { prev ->
            val change = StatsMath.change(prev, week.total)
            when {
                change < 0 -> c.getString(R.string.weekly_less_prev, -change)
                change > 0 -> c.getString(R.string.weekly_more_prev, change)
                else -> c.getString(R.string.weekly_same_prev)
            }
        } ?: UsageAccess.baseline(c)?.takeIf { it > 0 }?.let { before ->
            val change = StatsMath.change(before * week.days, week.total)
            if (change <= 0) c.getString(R.string.weekly_less_before, -change) else c.getString(R.string.weekly_more_before, change)
        }
        val streak = StreakGoal.of(c)?.let { goal ->
            val days = StatsMath.streaks(history, today, goal.max, goal.from).first
            if (days == 0) null else c.getString(R.string.streak_title) + ": " +
                c.resources.getQuantityString(R.plurals.streak_pill, days, days) + " " +
                c.getString(if (goal.isLimit) R.string.streak_limit else R.string.streak_before)
        }
        val text = listOfNotNull(compare, streak).ifEmpty { listOf(c.getString(R.string.weekly_tap)) }.joinToString("\n")

        val open = PanelActivity.intent(c, PanelActivity.STATS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(c, 5, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_weekly)).build()
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(c, R.color.brand_primary))
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}
