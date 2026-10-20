package com.brainrotless.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class SleepScheduleTest {
    // 2026-10-19 es lunes
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)
    private fun every(sleep: String, wake: String): (DayOfWeek) -> Pair<LocalTime, LocalTime> =
        { _ -> LocalTime.parse(sleep) to LocalTime.parse(wake) }

    @Test fun medianoche_es_la_madrugada_del_dia_siguiente() {
        val t = every("00:00", "06:30")
        assertEquals(Night(at(20, 0), at(20, 6, 30)), SleepSchedule.current(at(20, 3), t))
        assertNull(SleepSchedule.current(at(20, 6, 30), t))
        assertNull(SleepSchedule.current(at(19, 23, 59), t))
        assertEquals(at(21, 0), SleepSchedule.next(at(20, 7), t)?.start)
    }

    @Test fun noche_que_cruza_la_medianoche() {
        val t = every("23:00", "07:00")
        assertEquals(Night(at(19, 23), at(20, 7)), SleepSchedule.current(at(19, 23, 30), t))
        assertEquals(Night(at(19, 23), at(20, 7)), SleepSchedule.current(at(20, 6, 59), t))
        assertNull(SleepSchedule.current(at(20, 7), t))
        assertEquals(at(19, 23), SleepSchedule.next(at(19, 12), t)?.start)
    }

    @Test fun horario_distinto_por_dia() {
        // Noche de viernes a sábado: 01:00 a 10:00; el resto, 23:00 a 07:00
        val t: (DayOfWeek) -> Pair<LocalTime, LocalTime> = { d ->
            if (d == DayOfWeek.FRIDAY) LocalTime.of(1, 0) to LocalTime.of(10, 0)
            else LocalTime.of(23, 0) to LocalTime.of(7, 0)
        }
        assertNull(SleepSchedule.current(at(23, 23, 30), t)) // viernes 23:30: todavía no
        assertEquals(Night(at(24, 1), at(24, 10)), SleepSchedule.current(at(24, 8), t)) // sábado 08:00
        assertEquals(Night(at(24, 23), at(25, 7)), SleepSchedule.current(at(24, 23, 30), t)) // sábado a la noche
    }

    @Test fun misma_hora_de_dormir_y_despertar_es_sin_horario() {
        val t = every("22:00", "22:00")
        assertNull(SleepSchedule.current(at(20, 22), t))
        assertNull(SleepSchedule.next(at(20, 12), t))
    }

    @Test fun el_dia_de_uso_empieza_al_despertarse() {
        val t = every("00:00", "06:30")
        assertEquals(at(20, 6, 30), SleepSchedule.lastWake(at(20, 15), t))
        assertEquals(at(20, 6, 30), SleepSchedule.lastWake(at(21, 3), t)) // de madrugada sigue siendo el mismo día
        assertEquals(at(21, 6, 30), SleepSchedule.nextWake(at(20, 15), t))
        assertEquals(at(20, 6, 30), SleepSchedule.nextWake(at(20, 6), t))
    }

    @Test fun siesta_de_tarde() {
        val t = every("14:00", "15:30")
        assertEquals(Night(at(20, 14), at(20, 15, 30)), SleepSchedule.current(at(20, 15), t))
        assertNull(SleepSchedule.current(at(20, 16), t))
    }
}
