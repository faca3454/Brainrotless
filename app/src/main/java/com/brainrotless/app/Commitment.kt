package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** "48 min" o "2 h 15 min". */
fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

/** Lo que el compromiso no deja aflojar. Sin Android (lo prueba StrictnessTest). */
data class Strictness(
    val sleepOn: Boolean,
    val nights: Map<DayOfWeek, Pair<LocalTime, LocalTime>>,
    val browsers: Boolean,
    val limitMinutes: Int?,
) {
    /** true si [new] afloja algo: apagar, acostarse más tarde, despertarse antes, subir el límite. */
    fun loosenedBy(new: Strictness): Boolean {
        if (limitMinutes != null && (new.limitMinutes == null || new.limitMinutes > limitMinutes)) return true
        if (!sleepOn) return false
        if (!new.sleepOn || (browsers && !new.browsers)) return true
        return DayOfWeek.values().any { d ->
            val date = REF_MONDAY.plusDays(d.value - 1L)
            val (sleep, wake) = nights.getValue(d)
            val before = SleepSchedule.night(date, sleep, wake) ?: return@any false
            val (newSleep, newWake) = new.nights.getValue(d)
            val after = SleepSchedule.night(date, newSleep, newWake) ?: return@any true
            after.start.isAfter(before.start) || after.end.isBefore(before.end)
        }
    }

    private companion object {
        val REF_MONDAY: LocalDate = LocalDate.of(2024, 1, 1) // fue lunes
    }
}

/**
 * Reloj que compensa los cambios de hora manuales durante un compromiso: adelantar la hora
 * no termina el compromiso antes ni saltea la noche.
 */
object TrustedClock {
    private fun prefs(c: Context) = c.getSharedPreferences("clock", Context.MODE_PRIVATE)
    private fun skew(c: Context) = prefs(c).getLong("skew", 0)
    private fun offset() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun millis(c: Context) = System.currentTimeMillis() - skew(c)
    fun now(c: Context): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis(c)), ZoneId.systemDefault())

    /** Hora del sistema que corresponde a [t] (para programar alarmas). */
    fun systemMillis(c: Context, t: LocalDateTime) = systemMillis(c, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    fun systemMillis(c: Context, trustedMillis: Long) = trustedMillis + skew(c)

    /** Cambió la hora del sistema: durante un compromiso, el salto se descuenta. */
    fun onTimeSet(c: Context) {
        val p = prefs(c)
        val jump = offset() - p.getLong("offset", offset())
        // ¿Había compromiso antes del salto? (después, adelantando la hora, ya parecería terminado)
        val wasActive = System.currentTimeMillis() - jump - skew(c) < Commitment.endMillis(c)
        val e = p.edit().putLong("offset", offset())
        if (wasActive && abs(jump) > 60_000) e.putLong("skew", skew(c) + jump) // las correcciones de red son chicas
        e.apply()
    }

    /** Al reiniciar, el reloj interno vuelve a cero: solo se toma la referencia nueva. */
    fun onBoot(c: Context) = prefs(c).edit().putLong("offset", offset()).apply()

    fun reset(c: Context) = prefs(c).edit().putLong("offset", offset()).putLong("skew", 0).apply()
}

/**
 * Compromiso: durante N días (mínimo 7) el modo dormir, el bloqueo de navegadores y el
 * límite diario solo se pueden endurecer. Da una pausa de emergencia cada 2 días.
 */
object Commitment {
    const val MIN_DAYS = 7
    const val PAUSE_MINUTES = 15L
    const val PAUSES_PER_DAY = 2
    const val ACTION_END = "$PKG.COMMITMENT_END"
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val END_NOTIF_ID = 2004

    private fun prefs(c: Context) = c.getSharedPreferences("commitment", Context.MODE_PRIVATE)

    fun endMillis(c: Context) = prefs(c).getLong("end", 0)
    fun active(c: Context) = TrustedClock.millis(c) < endMillis(c)
    fun pausesFor(days: Int) = days / 2
    fun daysLeft(c: Context) = ((endMillis(c) - TrustedClock.millis(c) + DAY_MS - 1) / DAY_MS).coerceAtLeast(0)

    fun start(c: Context, days: Int) {
        TrustedClock.reset(c)
        val now = TrustedClock.millis(c)
        prefs(c).edit().clear()
            .putLong("start", now)
            .putLong("end", now + days * DAY_MS)
            .putInt("days", days)
            .putInt("pauses", pausesFor(days))
            .apply()
    }

    fun strictness(c: Context) = Strictness(
        sleepOn = Sleep.enabled(c),
        nights = DayOfWeek.values().associateWith { Sleep.schedule(c, it) },
        browsers = c.userPrefs().getBoolean("sleep_browsers", true),
        limitMinutes = Usage.limitMinutes(c),
    )

    /** Durante el compromiso, ¿este cambio afloja algo? */
    fun forbids(c: Context, change: (Strictness) -> Strictness): Boolean {
        if (!active(c)) return false
        val now = strictness(c)
        return now.loosenedBy(change(now))
    }

    // ---------- Pausas de emergencia ----------

    private fun used(c: Context) =
        prefs(c).getString("used", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun pausesLeft(c: Context) = prefs(c).getInt("pauses", 0) - used(c).size

    private fun usedToday(c: Context): Int {
        val from = Usage.dayStart(c).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return used(c).count { it >= from }
    }

    fun pauseUntil(c: Context) = prefs(c).getLong("pause_until", 0)
    fun pauseActive(c: Context) = active(c) && TrustedClock.millis(c) < pauseUntil(c)

    /** Se puede usar con algo bloqueado: al llegar al límite o de noche. Máximo 2 por día. */
    fun canPause(c: Context) =
        active(c) && !pauseActive(c) && pausesLeft(c) > 0 && usedToday(c) < PAUSES_PER_DAY &&
            (Usage.overLimit(c) || Sleep.active(c))

    /** Solo en los APK de prueba (Ajustes → "Terminar compromiso"): termina ahora, con resumen. */
    fun cancelForTesting(c: Context) {
        prefs(c).edit().putLong("end", TrustedClock.millis(c) - 1).putLong("pause_until", 0).apply()
        TrustedClock.reset(c)
    }

    // ---------- Al terminar ----------

    /** Terminó y todavía no se mostró el resumen. */
    private fun finished(c: Context) =
        endMillis(c) > 0 && !active(c) && !prefs(c).getBoolean("summary_seen", false)

    /**
     * Al terminar: quita la protección contra desinstalación y guarda el resumen.
     * Devuelve el resumen si falta mostrarlo; se puede llamar varias veces.
     */
    fun onEnd(c: Context): String? {
        if (!finished(c)) return null
        Protection.removeAdmin(c)
        Protection.updateGuardNotice(c)
        val text = "${title(c)}\n\n${summary(c)}"
        c.getSharedPreferences("commitment_history", Context.MODE_PRIVATE).edit().putString("last", text).apply()
        return text
    }

    fun markSummarySeen(c: Context) = prefs(c).edit().putBoolean("summary_seen", true).apply()
    fun lastSummary(c: Context) = c.getSharedPreferences("commitment_history", Context.MODE_PRIVATE).getString("last", null)

    private fun title(c: Context) = c.getString(R.string.summary_title, prefs(c).getInt("days", 0))

    /** Uso por día antes y durante, días que llegó al límite y pausas usadas. */
    private fun summary(c: Context): String {
        val start = prefs(c).getLong("start", 0)
        val days = ((endMillis(c) - start + DAY_MS - 1) / DAY_MS).coerceAtLeast(1) // los que pasaron de verdad
        val perDayUsage = Usage.daysBetween(c, start, endMillis(c))
        val perDay = perDayUsage.sum() / days
        val before = UsageAccess.baseline(c)
        val change = before?.takeIf { it > 0 }?.let { ((perDay - it) * 100 / it).toInt() }
        val limit = Usage.limitMinutes(c)?.let { it * 60_000L }
        return buildString {
            append(c.getString(R.string.summary_usage))
            before?.let { append(c.getString(R.string.summary_before, formatDuration(it))) }
            append(c.getString(R.string.summary_during, formatDuration(perDay)))
            change?.let { append(if (it <= 0) c.getString(R.string.summary_down, -it) else c.getString(R.string.summary_up, it)) }
            if (before == null) append(c.getString(R.string.summary_no_baseline))
            limit?.let { l -> append(c.getString(R.string.summary_limit_days, perDayUsage.count { it >= l }, days)) }
            append(c.getString(R.string.summary_pauses, used(c).size, prefs(c).getInt("pauses", 0)))
        }
    }

    /** Alarma a la hora de terminar, para avisar aunque la app no esté abierta. */
    fun scheduleEnd(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            c, 3, Intent(c, SleepAlarm::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!active(c)) return
        val ms = TrustedClock.systemMillis(c, endMillis(c))
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    fun notifyEnd(c: Context) {
        onEnd(c) ?: return
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("commitment", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_commitment)).build()
        )
        val pi = PendingIntent.getActivity(
            c, 4, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, "commitment")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title(c))
            .setContentText(c.getString(R.string.end_notif_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(END_NOTIF_ID, n) }
    }

    fun startPause(c: Context): Boolean {
        if (!canPause(c)) return false
        val now = TrustedClock.millis(c)
        prefs(c).edit()
            .putString("used", (used(c) + now).joinToString(","))
            .putLong("pause_until", now + PAUSE_MINUTES * 60_000)
            .apply()
        return true
    }
}

/** Tiempo de uso de Instagram + YouTube por día, y el límite diario (que corre durante el compromiso). */
object Usage {
    private const val CHANNEL = "limit"
    private const val NOTIF_ID = 2002
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private fun prefs(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** Uso de cada día que se superpone con [from, to) (para la estadística final). */
    fun daysBetween(c: Context, from: Long, to: Long): List<Long> =
        prefs(c).all.mapNotNull { (key, value) ->
            if (!key.startsWith("d_") || value !is Long) return@mapNotNull null
            val day = runCatching { LocalDateTime.parse(key.removePrefix("d_")) }.getOrNull() ?: return@mapNotNull null
            val dayStart = day.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            value.takeIf { dayStart + DAY_MS > from && dayStart < to }
        }

    /** El día de uso empieza al despertarse (aunque el modo dormir esté apagado), no a medianoche. */
    fun dayStart(c: Context): LocalDateTime {
        val now = TrustedClock.now(c)
        return SleepSchedule.lastWake(now) { Sleep.schedule(c, it) } ?: now.toLocalDate().atStartOfDay()
    }

    fun nextReset(c: Context): LocalDateTime {
        val now = TrustedClock.now(c)
        return SleepSchedule.nextWake(now) { Sleep.schedule(c, it) } ?: now.toLocalDate().plusDays(1).atStartOfDay()
    }

    private fun dayKey(c: Context) = "d_${dayStart(c)}"

    fun todayMs(c: Context) = prefs(c).getLong(dayKey(c), 0)
    fun limitMinutes(c: Context) = c.userPrefs().getString("daily_limit", "0")?.toIntOrNull()?.takeIf { it > 0 }
    fun limitMs(c: Context) = if (Commitment.active(c)) limitMinutes(c)?.let { it * 60_000L } else null
    fun overLimit(c: Context) = limitMs(c)?.let { todayMs(c) >= it } ?: false
    fun remainingMs(c: Context) = limitMs(c)?.let { (it - todayMs(c)).coerceAtLeast(0) }

    /** Suma uso (salvo durante una pausa de emergencia) y avisa cuando queda poco o se termina. */
    fun add(c: Context, ms: Long) {
        if (ms <= 0 || Commitment.pauseActive(c)) return
        val before = remainingMs(c)
        val key = dayKey(c)
        prefs(c).edit().putLong(key, prefs(c).getLong(key, 0) + ms.coerceAtMost(10 * 60_000)).apply()
        TodayWidget.update(c)
        val after = remainingMs(c) ?: return
        if (before == null) return
        for (min in listOf(10, 2)) {
            if (before > min * 60_000 && after in 1..min * 60_000L)
                Toast.makeText(c, c.getString(R.string.minutes_left, min), Toast.LENGTH_LONG).show()
        }
        if (before > 0 && after == 0L) notifyLimit(c)
    }

    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    private fun notifyLimit(c: Context) {
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(c.getString(R.string.channel_limit)).build()
        )
        val text = c.getString(R.string.limit_notif_text, Sleep.fmt(c, nextReset(c).toLocalTime()))
        val b = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(c.getString(R.string.limit_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (Commitment.canPause(c)) {
            val open = Intent(c, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PAUSE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                c, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, c.getString(R.string.limit_notif_pause, Commitment.pausesLeft(c).let { c.resources.getQuantityString(R.plurals.left_n, it, it) }), pi)
        }
        runCatching { nm.notify(NOTIF_ID, b.build()) }
    }
}

/** Qué está bloqueado ahora y por qué (noche, foco o límite diario), contando la pausa de emergencia. */
object Lock {
    enum class Reason { NIGHT, FOCUS, LIMIT }

    /** Instagram solo para mensajes. La pausa de emergencia lo abre completo, de día y de noche. */
    fun ig(c: Context): Reason? = when {
        Commitment.pauseActive(c) -> null
        Sleep.active(c) -> Reason.NIGHT
        Focus.active(c) -> Reason.FOCUS
        Usage.overLimit(c) -> Reason.LIMIT
        else -> null
    }

    /** YouTube bloqueado. De noche, ni la pausa lo abre; en foco, solo si así se eligió. */
    fun yt(c: Context): Reason? = when {
        Sleep.active(c) -> Reason.NIGHT
        Focus.active(c) && Focus.blocksYouTube(c) -> Reason.FOCUS
        Usage.overLimit(c) && !Commitment.pauseActive(c) -> Reason.LIMIT
        else -> null
    }

    fun any(c: Context) = ig(c) != null || yt(c) != null

    /**
     * Reels con scroll: solo con un límite diario corriendo, mientras quede tiempo y no sea de noche.
     * Ni la pausa de emergencia los abre (se usa pasado el límite).
     */
    fun reelsAllowed(c: Context) =
        c.userPrefs().getBoolean("ig_reels_in_limit", true) && !Sleep.active(c) && !Focus.active(c) &&
            (Usage.remainingMs(c) ?: 0) > 0

    /** Hora en que termina el bloqueo. */
    fun until(c: Context, r: Reason): String {
        val end = when (r) {
            Reason.NIGHT -> Sleep.current(c)?.end ?: TrustedClock.now(c)
            Reason.FOCUS -> Focus.current(c)?.end ?: TrustedClock.now(c)
            Reason.LIMIT -> Usage.nextReset(c)
        }
        return Sleep.fmt(c, end.toLocalTime())
    }

    fun igMessage(c: Context, r: Reason) = when (r) {
        Reason.NIGHT -> c.getString(R.string.ig_night_msg, until(c, r))
        Reason.FOCUS -> c.getString(R.string.ig_focus_msg, until(c, r))
        Reason.LIMIT -> c.getString(R.string.ig_limit_msg, until(c, r))
    }

    /** Ms hasta el próximo cambio: noche, foco, fin de la pausa, avisos y fin del límite si se está usando, día nuevo. */
    fun msUntilChange(c: Context): Long {
        val now = TrustedClock.millis(c)
        val at = mutableListOf<Long>()
        Sleep.msUntilChange(c)?.let(at::add)
        Focus.msUntilChange(c)?.let(at::add)
        if (Commitment.pauseActive(c)) at += Commitment.pauseUntil(c) - now
        Usage.remainingMs(c)?.let { r -> at += listOf(r - 10 * 60_000, r - 2 * 60_000, r) }
        if (Usage.limitMs(c) != null) at += Duration.between(TrustedClock.now(c), Usage.nextReset(c)).toMillis()
        return at.filter { it > 0 }.minOrNull() ?: 60_000
    }
}
