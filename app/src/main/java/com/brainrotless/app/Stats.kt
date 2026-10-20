package com.brainrotless.app

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Uso de un día: el total y, desde que se separa, cuánto fue Instagram y cuánto YouTube. */
data class DayUsage(val total: Long, val ig: Long = 0, val yt: Long = 0)

/** Meta diaria de la racha: tope de uso, desde qué día cuenta y si es el límite del compromiso. */
data class StreakGoal(val max: Long, val from: LocalDate, val isLimit: Boolean) {
    companion object {
        /** El límite del compromiso (desde que empezó) o, si no hay, usar menos que antes de Lúcido. */
        fun of(c: Context): StreakGoal? =
            Usage.limitMs(c)?.let { StreakGoal(it, Commitment.startDay(c), isLimit = true) }
                ?: UsageAccess.baseline(c)?.let { StreakGoal(it, LocalDate.MIN, isLimit = false) }
    }
}

/**
 * Cuentas de la pantalla de estadísticas. Sin Android (las prueba StatsMathTest). Un "día" es
 * un día de uso: empieza al despertarse, como el límite diario. El último de la ventana es hoy,
 * que todavía no terminó, así que no entra en promedios ni en el mejor día.
 */
object StatsMath {
    /**
     * Los últimos [n] días hasta [today], del más viejo al más nuevo. null si es de antes del
     * primer día con datos (Lúcido todavía no contaba); un día sin uso después de eso vale 0.
     */
    fun window(history: Map<LocalDate, DayUsage>, today: LocalDate, n: Int): List<DayUsage?> {
        val first = history.keys.minOrNull() ?: return List(n) { null }
        return (n - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            if (day < first) null else history[day] ?: DayUsage(0)
        }
    }

    /** Los días terminados que tienen datos (todos menos hoy). */
    fun completed(window: List<DayUsage?>) = window.dropLast(1).filterNotNull()

    /** Promedio diario de los días terminados; null si todavía no terminó ninguno. */
    fun average(window: List<DayUsage?>): Long? =
        completed(window).takeIf { it.isNotEmpty() }?.let { days -> days.sumOf { it.total } / days.size }

    /** Posición y uso del día terminado con menos uso. */
    fun best(window: List<DayUsage?>): Pair<Int, Long>? =
        window.dropLast(1).withIndex()
            .mapNotNull { (i, d) -> d?.let { i to it.total } }
            .minByOrNull { it.second }

    /**
     * Días terminados desde la posición [from] (el día que empezó el compromiso: antes no había
     * límite) que quedaron por debajo del límite, y cuántos días terminados hay desde ahí.
     */
    fun within(window: List<DayUsage?>, limit: Long, from: Int = 0): Pair<Int, Int> {
        val days = completed(window.drop(from.coerceAtLeast(0)))
        return days.count { it.total < limit } to days.size
    }

    /** Cambio en por ciento de [before] a [now] (negativo: bajó). */
    fun change(before: Long, now: Long): Int = ((now - before) * 100 / before).toInt()

    /**
     * Tiempo recuperado desde el primer día: lo que se usaba por día antes de Lúcido menos lo
     * que se usó cada día terminado. null si no hay con qué comparar o no se recuperó nada.
     */
    fun recovered(history: Map<LocalDate, DayUsage>, today: LocalDate, before: Long): Long? {
        val first = history.keys.minOrNull() ?: return null
        var saved = 0L
        var day = first
        while (day < today) {
            saved += before - (history[day]?.total ?: 0)
            day = day.plusDays(1)
        }
        return saved.takeIf { it > 0 }
    }

    /** Uso de Instagram y de YouTube en los días de la ventana que ya lo tienen separado. */
    fun split(window: List<DayUsage?>): Pair<Long, Long> =
        window.filterNotNull().let { days -> days.sumOf { it.ig } to days.sumOf { it.yt } }

    /**
     * Racha actual (días terminados seguidos hasta ayer) y mejor racha con uso por debajo de
     * [goal], contando desde [from]: antes de esa fecha no había meta. Un día sin datos después
     * del primero vale 0 (no se usó), así que suma.
     */
    fun streaks(history: Map<LocalDate, DayUsage>, today: LocalDate, goal: Long, from: LocalDate): Pair<Int, Int> {
        var day = maxOf(from, history.keys.minOrNull() ?: return 0 to 0)
        var current = 0
        var best = 0
        while (day < today) {
            if ((history[day]?.total ?: 0) < goal) best = maxOf(best, ++current) else current = 0
            day = day.plusDays(1)
        }
        return current to best
    }

    /** Los últimos [n] días terminados: cumplió la meta, no la cumplió o null (no había meta o datos). */
    fun goalMarks(history: Map<LocalDate, DayUsage>, today: LocalDate, goal: Long, from: LocalDate, n: Int): List<Boolean?> {
        val first = maxOf(from, history.keys.minOrNull() ?: today)
        return (n downTo 1).map { back ->
            val day = today.minusDays(back.toLong())
            if (day < first) null else (history[day]?.total ?: 0) < goal
        }
    }

    /** Para el resumen semanal: uso de los últimos 7 días terminados, cuántos tienen datos, y los 7 anteriores. */
    data class Week(val total: Long, val days: Int, val previous: Long?)

    /**
     * Los últimos 7 días terminados y los 7 de antes (null si en esos Lúcido todavía no contaba
     * todos los días: una semana a medias no sirve para comparar). null si hay menos de 3 días.
     */
    fun week(history: Map<LocalDate, DayUsage>, today: LocalDate): Week? {
        val w = window(history, today, 15) // el último es hoy, que no entra
        val last = w.subList(7, 14).filterNotNull()
        if (last.size < 3) return null
        val prev = w.subList(0, 7)
        return Week(last.sumOf { it.total }, last.size, if (prev.all { it != null }) prev.sumOf { it!!.total } else null)
    }

    /** Próximo domingo a las [hour] (hoy, si es domingo y todavía no pasó la hora). */
    fun nextSunday(now: LocalDateTime, hour: Int): LocalDateTime {
        val at = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(hour, 0)
        return if (at.isAfter(now)) at else at.plusWeeks(1)
    }

    /** Posición de [day] en una ventana de [n] días que termina en [today] (0 si es de antes). */
    fun indexOf(day: LocalDate, today: LocalDate, n: Int): Int =
        (n - 1 - ChronoUnit.DAYS.between(day, today)).toInt().coerceAtLeast(0)

    /** Por ciento de Instagram y de YouTube, redondeados para que sumen 100. */
    fun percents(ig: Long, yt: Long): Pair<Int, Int> {
        val igPct = ((ig * 100.0) / (ig + yt)).roundToInt()
        return igPct to 100 - igPct
    }

    /** Una línea de referencia "redonda" para el gráfico, por debajo de [max]. */
    fun gridStep(max: Long): Long {
        val steps = listOf(15, 30, 60, 120, 180, 240, 360, 480, 720).map { it * 60_000L }
        return steps.lastOrNull { it <= max * 0.9 } ?: steps.first()
    }
}
