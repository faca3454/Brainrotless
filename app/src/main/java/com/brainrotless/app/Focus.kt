package com.brainrotless.app

import android.content.Context
import android.os.SystemClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Un bloque de foco: de [start] a [end]. */
data class Block(val start: LocalDateTime, val end: LocalDateTime)

/** Cálculo del horario de foco, sin Android (lo prueba FocusScheduleTest). */
object FocusSchedule {
    /** El bloque del día [date], si ese día tiene foco. Si termina antes de empezar, sigue al día siguiente. */
    fun block(date: LocalDate, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime): Block? {
        if (date.dayOfWeek !in days || start == end) return null
        val s = date.atTime(start)
        var e = date.atTime(end)
        if (!e.isAfter(s)) e = e.plusDays(1)
        return Block(s, e)
    }

    private fun blocks(from: LocalDate, n: Long, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
        (0L until n).mapNotNull { block(from.plusDays(it), days, start, end) }

    fun current(now: LocalDateTime, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
        blocks(now.toLocalDate().minusDays(1), 2, days, start, end)
            .firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    fun next(now: LocalDateTime, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime) =
        blocks(now.toLocalDate(), 8, days, start, end).firstOrNull { it.start.isAfter(now) }
}

/**
 * Modo foco: en las horas elegidas, Instagram queda solo para mensajes y (si se quiere) YouTube
 * se bloquea. Los navegadores no, que se usan para trabajar. No lo traba el compromiso.
 */
object Focus {
    private val DEFAULT_START: LocalTime = LocalTime.of(9, 0)
    private val DEFAULT_END: LocalTime = LocalTime.of(13, 0)
    private val WEEKDAYS = setOf("1", "2", "3", "4", "5")

    fun enabled(c: Context) = c.userPrefs().getBoolean("focus_on", false)
    fun blocksYouTube(c: Context) = c.userPrefs().getBoolean("focus_youtube", true)

    fun days(c: Context): Set<DayOfWeek> =
        (c.userPrefs().getStringSet("focus_days", WEEKDAYS) ?: WEEKDAYS)
            .mapNotNull { v -> v.toIntOrNull()?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } }.toSet()

    fun times(c: Context): Pair<LocalTime, LocalTime> {
        val p = c.userPrefs()
        fun parse(s: String?) = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return (parse(p.getString("focus_start", null)) ?: DEFAULT_START) to (parse(p.getString("focus_end", null)) ?: DEFAULT_END)
    }

    fun setTimes(c: Context, start: LocalTime, end: LocalTime) =
        c.userPrefs().edit().putString("focus_start", start.toString()).putString("focus_end", end.toString()).apply()

    fun current(c: Context, now: LocalDateTime = TrustedClock.now(c)): Block? {
        if (!enabled(c)) return null
        val (s, e) = times(c)
        return FocusSchedule.current(now, days(c), s, e)
    }

    fun active(c: Context) = current(c) != null

    fun next(c: Context): Block? {
        if (!enabled(c)) return null
        val (s, e) = times(c)
        return FocusSchedule.next(TrustedClock.now(c), days(c), s, e)
    }

    /** Ms hasta que empiece o termine un bloque; null si está apagado. */
    fun msUntilChange(c: Context): Long? {
        val now = TrustedClock.now(c)
        val at = listOfNotNull(current(c, now)?.end, next(c)?.start).minOrNull() ?: return null
        return Duration.between(now, at).toMillis()
    }
}

/**
 * Aviso de sesión larga: después de X minutos seguidos entre Instagram y YouTube, propone cortar.
 * Salir más de 2 minutos empieza una sesión nueva. Se guarda para sobrevivir si se recrea la pantalla.
 */
object Session {
    private const val GAP_MS = 2 * 60_000L
    const val SNOOZE_MS = 5 * 60_000L
    private fun prefs(c: Context) = c.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun limitMs(c: Context) = (c.userPrefs().getString("session_warn", "20")?.toLongOrNull() ?: 20) * 60_000

    fun enabled(c: Context) = limitMs(c) > 0

    /** Volvió a la app: misma sesión si se fue hace poco; si no, una nueva. */
    fun resume(c: Context) {
        val p = prefs(c)
        val now = SystemClock.elapsedRealtime()
        val last = p.getLong("last_seen", 0)
        val start = p.getLong("start", 0)
        if (last == 0L || now - last > GAP_MS || start > now) // start > now: se reinició el teléfono
            p.edit().putLong("start", now).putLong("warn_at", now + limitMs(c)).apply()
        p.edit().putLong("last_seen", now).apply()
    }

    fun pause(c: Context) = prefs(c).edit().putLong("last_seen", SystemClock.elapsedRealtime()).apply()

    fun msUntilWarn(c: Context): Long? =
        if (enabled(c)) prefs(c).getLong("warn_at", 0) - SystemClock.elapsedRealtime() else null

    fun due(c: Context) = (msUntilWarn(c) ?: 1) <= 0

    fun minutes(c: Context) = ((SystemClock.elapsedRealtime() - prefs(c).getLong("start", 0)) / 60_000).toInt()

    /** "5 minutos más" (o salir y volver enseguida): avisar de nuevo en 5 minutos. */
    fun snooze(c: Context) =
        prefs(c).edit().putLong("warn_at", SystemClock.elapsedRealtime() + SNOOZE_MS).apply()
}
