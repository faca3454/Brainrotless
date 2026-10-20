package com.brainrotless.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class StrictnessTest {
    private fun t(s: String) = LocalTime.parse(s)
    private fun every(sleep: String, wake: String) = DayOfWeek.values().associateWith { t(sleep) to t(wake) }
    private val base = Strictness(sleepOn = true, nights = every("00:00", "06:30"), browsers = true, limitMinutes = 60)

    @Test fun mismo_estado_no_afloja() = assertFalse(base.loosenedBy(base))

    @Test fun apagar_el_modo_dormir_afloja() = assertTrue(base.loosenedBy(base.copy(sleepOn = false)))

    @Test fun desbloquear_navegadores_afloja() = assertTrue(base.loosenedBy(base.copy(browsers = false)))

    @Test fun limite() {
        assertTrue(base.loosenedBy(base.copy(limitMinutes = 90)))
        assertTrue(base.loosenedBy(base.copy(limitMinutes = null)))
        assertFalse(base.loosenedBy(base.copy(limitMinutes = 30)))
        assertFalse(base.copy(limitMinutes = null).loosenedBy(base)) // poner un límite endurece
    }

    @Test fun acostarse_antes_o_levantarse_despues_endurece() {
        assertFalse(base.loosenedBy(base.copy(nights = every("23:00", "06:30"))))
        assertFalse(base.loosenedBy(base.copy(nights = every("00:00", "08:00"))))
        assertFalse(base.loosenedBy(base.copy(nights = every("22:30", "07:00"))))
    }

    @Test fun acostarse_mas_tarde_o_levantarse_antes_afloja() {
        assertTrue(base.loosenedBy(base.copy(nights = every("01:00", "06:30"))))
        assertTrue(base.loosenedBy(base.copy(nights = every("00:00", "06:00"))))
        assertTrue(base.loosenedBy(base.copy(nights = every("00:00", "00:00")))) // sin horario
    }

    @Test fun un_solo_dia_mas_flojo_afloja() {
        val viernesTarde = base.nights + (DayOfWeek.FRIDAY to (t("02:00") to t("06:30")))
        assertTrue(base.loosenedBy(base.copy(nights = viernesTarde)))
    }

    @Test fun sin_modo_dormir_se_puede_tocar_el_horario() {
        val off = base.copy(sleepOn = false)
        assertFalse(off.loosenedBy(off.copy(nights = every("03:00", "05:00"), browsers = false)))
        assertFalse(off.loosenedBy(base)) // prenderlo endurece
    }
}
