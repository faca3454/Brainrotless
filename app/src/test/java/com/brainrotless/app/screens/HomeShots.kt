package com.brainrotless.app.screens

import com.brainrotless.app.Block
import com.brainrotless.app.HomeScreen
import com.brainrotless.app.Night
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeShots : Shots() {
    private val day = LocalDate.of(2026, 10, 21)
    private val night = Night(day.atTime(23, 30), day.plusDays(1).atTime(7, 0))
    private val focus = Block(day.plusDays(1).atTime(9, 0), day.plusDays(1).atTime(13, 0))
    private val week = listOf(52L, 38L, 71L, 44L, 29L, 61L, 47L).map { it * 60_000 }
    private val min = 60_000L

    private fun home(s: HomeScreen.State) = { c: android.content.Context -> HomeScreen.build(c, s, HomeScreen.Actions()) }

    @Test fun sinCompromiso() = shot("sin_compromiso", ALL, home(HomeScreen.State(
        showMessages = true, usedMs = 47 * min, week = week, sleepOn = true, sleepNext = night,
    )))

    @Test fun conCompromiso() = shot("con_compromiso", ALL, home(HomeScreen.State(
        usedMs = 38 * min, limitMs = 60 * min, week = week, limitFrom = 2, streak = 4, sleepOn = true, sleepNext = night,
        focusOn = true, focusNext = focus, commitActive = true, daysLeft = 12, pausesLeft = 2, reelsAllowed = true,
    )))

    @Test fun pausa() = shot("pausa", listOf(BASE, DARK), home(HomeScreen.State(
        usedMs = 64 * min, limitMs = 60 * min, week = week, pauseUntil = day.atTime(18, 15).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        sleepOn = true, sleepNext = night, commitActive = true, daysLeft = 12, pausesLeft = 1,
    )))

    @Test fun noche() = shot("noche", listOf(BASE, DARK), home(HomeScreen.State(
        igLocked = true, ytLockedUntil = "7:00", usedMs = 41 * min, limitMs = 60 * min, week = week, sleepOn = true, sleepNow = night,
        focusOn = true, focusNext = focus, commitActive = true, daysLeft = 12, pausesLeft = 2,
    )))

    @Test fun limite() = shot("limite", listOf(BASE, DARK), home(HomeScreen.State(
        igLocked = true, ytLockedUntil = "4:00", usedMs = 75 * min, limitMs = 60 * min, resetAt = LocalTime.of(4, 0), week = week,
        sleepOn = true, sleepNext = night, commitActive = true, daysLeft = 12, pausesLeft = 0,
    )))

    /** Lo peor: sin Accesibilidad, números enormes, todo prendido, primer día (sin semana). */
    @Test fun extremos() = shot("extremos", listOf(BASE, DARK, WORST, Look("chico_letra130", small = true, font = 1.3f)), home(HomeScreen.State(
        showMessages = true, igLocked = true, ytLockedUntil = "11:59 p. m.", usedMs = 1439 * min, limitMs = 30 * min,
        week = listOf(null, null, null, null, null, null, 1439 * min), streak = 365, guardOn = false, sleepOn = true, sleepNow = night,
        focusOn = true, focusNow = focus, commitActive = true, daysLeft = 365, pausesLeft = 99,
    )))

    /** Primer uso: nada prendido, sin datos. */
    @Test fun vacio() = shot("vacio", listOf(BASE, DARK, EN), home(HomeScreen.State(showMessages = true)))
}
