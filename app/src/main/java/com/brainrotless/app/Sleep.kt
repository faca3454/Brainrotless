package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** Una noche del modo dormir: de [start] a [end]. */
data class Night(val start: LocalDateTime, val end: LocalDateTime)

/** Cálculo del horario, sin Android (lo prueba SleepScheduleTest). */
object SleepSchedule {
    /**
     * La noche que empieza el día [date]. Una hora de dormir antes del mediodía cae pasada
     * la medianoche: "lunes a las 00:30" es la madrugada del martes.
     */
    fun night(date: LocalDate, sleep: LocalTime, wake: LocalTime): Night? {
        if (sleep == wake) return null
        val start = (if (sleep.hour < 12) date.plusDays(1) else date).atTime(sleep)
        var end = start.toLocalDate().atTime(wake)
        if (!end.isAfter(start)) end = end.plusDays(1)
        return Night(start, end)
    }

    private fun nights(from: LocalDate, days: Int, times: (DayOfWeek) -> Pair<LocalTime, LocalTime>) =
        (0L until days).mapNotNull {
            val d = from.plusDays(it)
            val (sleep, wake) = times(d.dayOfWeek)
            night(d, sleep, wake)
        }

    fun current(now: LocalDateTime, times: (DayOfWeek) -> Pair<LocalTime, LocalTime>) =
        nights(now.toLocalDate().minusDays(2), 3, times)
            .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    fun next(now: LocalDateTime, times: (DayOfWeek) -> Pair<LocalTime, LocalTime>) =
        nights(now.toLocalDate().minusDays(1), 9, times).firstOrNull { it.start.isAfter(now) }

    /** Última hora de despertar: ahí empieza el día de uso (y se reinicia el límite). */
    fun lastWake(now: LocalDateTime, times: (DayOfWeek) -> Pair<LocalTime, LocalTime>) =
        nights(now.toLocalDate().minusDays(3), 4, times).map { it.end }.filter { !it.isAfter(now) }.maxOrNull()

    fun nextWake(now: LocalDateTime, times: (DayOfWeek) -> Pair<LocalTime, LocalTime>) =
        nights(now.toLocalDate().minusDays(1), 9, times).map { it.end }.filter { it.isAfter(now) }.minOrNull()
}

/**
 * Modo dormir: en el horario configurado, Instagram queda solo para mensajes,
 * YouTube se bloquea y no hay pausa del bloqueo. Avisa unos minutos antes.
 */
object Sleep {
    private const val CHANNEL = "sleep"
    private const val NOTIF_ID = 2001
    private val DEFAULT_SLEEP: LocalTime = LocalTime.of(0, 0)
    private val DEFAULT_WAKE: LocalTime = LocalTime.of(6, 30)

    fun enabled(c: Context) = c.userPrefs().getBoolean("sleep_on", false)
    fun perDay(c: Context) = c.userPrefs().getBoolean("sleep_per_day", false)
    private fun warnMinutes(c: Context) = c.userPrefs().getString("sleep_warn", "15")?.toLongOrNull() ?: 15

    /** Horario general (day = null) o de la noche que empieza ese día; sin configurar, el general. */
    fun get(c: Context, day: DayOfWeek?): Pair<LocalTime, LocalTime> {
        val p = c.userPrefs()
        val sleep = parse(p.getString("sleep_start", null)) ?: DEFAULT_SLEEP
        val wake = parse(p.getString("sleep_end", null)) ?: DEFAULT_WAKE
        if (day == null) return sleep to wake
        return (parse(p.getString("sleep_start_${day.value}", null)) ?: sleep) to
            (parse(p.getString("sleep_end_${day.value}", null)) ?: wake)
    }

    fun set(c: Context, day: DayOfWeek?, sleep: LocalTime, wake: LocalTime) {
        val suffix = day?.let { "_${it.value}" } ?: ""
        c.userPrefs().edit()
            .putString("sleep_start$suffix", sleep.toString())
            .putString("sleep_end$suffix", wake.toString())
            .apply()
    }

    private fun parse(s: String?) = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun times(c: Context): (DayOfWeek) -> Pair<LocalTime, LocalTime> {
        if (perDay(c)) return { d -> get(c, d) }
        val all = get(c, null)
        return { _ -> all }
    }

    /** Horario que rige la noche que empieza ese día (aunque el modo esté apagado). */
    fun schedule(c: Context, d: DayOfWeek) = times(c)(d)

    // La hora viene de TrustedClock: durante un compromiso, cambiar la hora del teléfono no saltea la noche
    fun current(c: Context, now: LocalDateTime = TrustedClock.now(c)) =
        if (enabled(c)) SleepSchedule.current(now, times(c)) else null

    fun active(c: Context) = current(c) != null

    fun blocksBrowsers(c: Context) = c.userPrefs().getBoolean("sleep_browsers", true) && active(c)

    private fun next(c: Context, now: LocalDateTime) =
        if (enabled(c)) SleepSchedule.next(now, times(c)) else null

    fun nextNight(c: Context) = next(c, TrustedClock.now(c))

    /** Milisegundos hasta que empiece o termine una noche; null si el modo está apagado. */
    fun msUntilChange(c: Context): Long? {
        val now = TrustedClock.now(c)
        val at = listOfNotNull(current(c, now)?.end, next(c, now)?.start).minOrNull() ?: return null
        return Duration.between(now, at).toMillis()
    }

    /** Hora con el formato del teléfono (24 h o a.m./p.m.). */
    fun fmt(c: Context, t: LocalTime): String =
        DateFormat.getTimeFormat(c).format(Date.from(LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant()))

    // ---------- Alarmas ----------

    private fun alarmIntent(c: Context) = PendingIntent.getBroadcast(
        c, 0, Intent(c, SleepAlarm::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Programa la próxima alarma: aviso previo, inicio o fin de la noche. */
    fun reschedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        Commitment.scheduleEnd(c)
        val pi = alarmIntent(c)
        am.cancel(pi)
        if (!enabled(c)) { NotificationManagerCompat.from(c).cancel(NOTIF_ID); return }

        val now = TrustedClock.now(c)
        val next = next(c, now)
        val warnAt = next?.start?.minusMinutes(warnMinutes(c))?.takeIf { warnMinutes(c) > 0 && it.isAfter(now) }
        val at = listOfNotNull(current(c, now)?.end, warnAt, next?.start).minOrNull() ?: return
        val ms = TrustedClock.systemMillis(c, at)

        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    /** Sonó la alarma: decide qué hacer mirando el horario, no qué alarma era. */
    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    fun onAlarm(c: Context) {
        val nm = NotificationManagerCompat.from(c)
        val now = TrustedClock.now(c)
        if (current(c, now) != null) {
            Bypass.stop(c)
            nm.cancel(NOTIF_ID) // ya empezó: el aviso sobra
            return
        }
        val night = next(c, now) ?: return
        // Margen de 30 s: la alarma suena justo a la hora o un poco después
        if (Duration.between(now, night.start).seconds > warnMinutes(c) * 60 + 30) return

        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_sleep)).build()
        )
        val start = fmt(c, night.start.toLocalTime())
        val end = fmt(c, night.end.toLocalTime())
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(c.getString(R.string.sleep_warn_title, start))
            .setContentText(c.getString(R.string.sleep_warn_text, end))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(c.getString(R.string.sleep_warn_text, end)))
            .setTimeoutAfter(Duration.between(now, night.start).toMillis())
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}

/** Alarmas del modo dormir; también reprograma al reiniciar, actualizar la app o cambiar la hora. */
class SleepAlarm : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            null -> Sleep.onAlarm(c) // la nuestra no tiene acción; las del sistema sí
            Commitment.ACTION_END -> Commitment.notifyEnd(c)
            Intent.ACTION_TIME_CHANGED -> TrustedClock.onTimeSet(c)
            Intent.ACTION_BOOT_COMPLETED -> TrustedClock.onBoot(c)
        }
        Sleep.reschedule(c)
        Widgets.update(c)
        Protection.applyIconVisibility(c)
    }
}
