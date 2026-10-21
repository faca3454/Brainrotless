package com.brainrotless.app.screens

import com.brainrotless.app.OnboardingScreen
import org.junit.Test
import java.time.DayOfWeek

class OnboardingShots : Shots() {
    private fun guide(s: OnboardingScreen.State) = { c: android.content.Context -> OnboardingScreen.build(c, s) }

    @Test fun idioma() = shot("idioma", listOf(BASE, DARK, SMALL, WORST), guide(OnboardingScreen.State(step = 0)))

    @Test fun bienvenida() = shot("bienvenida", ALL, guide(OnboardingScreen.State(step = 1, chosenLang = "es")))

    @Test fun plan() = shot("plan", ALL, guide(OnboardingScreen.State(
        step = 2, sleepOn = true, focusOn = true, focusDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    )))

    /** Sin Accesibilidad (aparece el aviso), Android 13+ y todos los días de foco. */
    @Test fun planSinAccesibilidad() = shot("plan_sin_accesibilidad", listOf(BASE, DARK, WORST), guide(OnboardingScreen.State(
        step = 2, guardOn = false, restricted = true, sleepOn = true, focusOn = true, focusDays = DayOfWeek.values().toSet(),
    )))

    /** Con compromiso: los horarios se cambian en su pantalla. */
    @Test fun planConCompromiso() = shot("plan_con_compromiso", listOf(BASE, DARK), guide(OnboardingScreen.State(
        step = 2, commitActive = true, sleepOn = true,
    )))
}
