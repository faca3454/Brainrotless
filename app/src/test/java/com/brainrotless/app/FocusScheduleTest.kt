package com.brainrotless.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.LocalDateTime
import java.time.LocalTime

class FocusScheduleTest {
    // 2026-10-19 es lunes
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)
    private val weekdays = (1..5).map { DayOfWeek.of(it) }.toSet()
    private val nine = LocalTime.of(9, 0)
    private val one = LocalTime.of(13, 0)

    @Test fun dentro_y_fuera_del_horario() {
        assertEquals(Block(at(19, 9), at(19, 13)), FocusSchedule.current(at(19, 10), weekdays, nine, one))
        assertNull(FocusSchedule.current(at(19, 13), weekdays, nine, one))
        assertNull(FocusSchedule.current(at(19, 8, 59), weekdays, nine, one))
    }

    @Test fun solo_los_dias_elegidos() {
        assertNull(FocusSchedule.current(at(24, 10), weekdays, nine, one)) // sábado
        assertEquals(at(26, 9), FocusSchedule.next(at(24, 10), weekdays, nine, one)?.start) // el lunes siguiente
        assertNull(FocusSchedule.current(at(19, 10), setOf(FRIDAY), nine, one))
    }

    @Test fun bloque_que_cruza_la_medianoche() {
        val days = setOf(MONDAY)
        val b = Block(at(19, 22), at(20, 2))
        assertEquals(b, FocusSchedule.current(at(19, 23), days, LocalTime.of(22, 0), LocalTime.of(2, 0)))
        assertEquals(b, FocusSchedule.current(at(20, 1), days, LocalTime.of(22, 0), LocalTime.of(2, 0)))
    }

    @Test fun sin_dias_o_misma_hora_no_hay_foco() {
        assertNull(FocusSchedule.current(at(19, 10), emptySet(), nine, one))
        assertNull(FocusSchedule.next(at(19, 10), weekdays, nine, nine))
    }
}
