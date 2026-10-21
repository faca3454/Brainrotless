package com.brainrotless.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity

/**
 * Pantalla principal de Lúcido (el ícono de la app): abrir Instagram o YouTube, cómo va el día,
 * modo dormir, compromiso y, si falta, el permiso que necesita cada uno. Se vuelve a armar al
 * volver a la pantalla.
 */
class HomeActivity : ComponentActivity() {
    private val notifAsk = NotifAsk(this)
    private lateinit var list: LinearLayout
    private var firstShow = true
    private var lang = "" // idioma con el que se armó la pantalla

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primera vez: la guía (idioma, qué hace, plan); al terminar vuelve acá
        if (OnboardingActivity.needed(this)) {
            startActivity(OnboardingActivity.intent(this))
            finish()
            return
        }
        lang = Lang.current(this)
        val pad = Brand.px(this, 20)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(list, ViewGroup.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        if (Lang.current(this) != lang) return recreate() // cambiaron el idioma en Ajustes
        render()
        Commitment.onEnd(this)?.let { text ->
            Commitment.markSummarySeen(this)
            Dialogs.summary(this, text, R.string.not_now,
                onAnother = { PanelActivity.open(this, PanelActivity.COMMIT) },
                onCharts = { PanelActivity.open(this, PanelActivity.STATS) }).show()
        }
    }

    private fun render() {
        list.removeAllViews()
        val screen = HomeScreen.build(this, state(), actions(), animate = firstShow)
        // Las tarjetas pasan a la lista de la pantalla, con sus márgenes
        while (screen.childCount > 0) screen.getChildAt(0).let { v ->
            val lp = v.layoutParams
            screen.removeViewAt(0)
            list.addView(v, lp)
        }

        // Entrada suave la primera vez (después, sin animar: que se sienta instantáneo)
        if (firstShow) {
            firstShow = false
            for (i in 0 until list.childCount) list.getChildAt(i).apply {
                alpha = 0f
                translationY = Brand.dp(context, 8f)
                animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(240).setInterpolator(Brand.EASE_OUT).start()
            }
        }
    }

    /** Los datos del inicio: preferencias, uso, compromiso y reloj. */
    private fun state(): HomeScreen.State {
        val yt = Lock.yt(this)
        val today = Usage.today(this)
        val history = Usage.history(this)
        return HomeScreen.State(
            now = TrustedClock.now(this),
            showMessages = !Setup.listenerOn(this) && !userPrefs().getBoolean("hide_messages_card", false),
            igLocked = Lock.ig(this) != null,
            reelsAllowed = Lock.reelsAllowed(this),
            igFeed = userPrefs().getString("ig_feed", "normal") ?: "normal",
            ytLockedUntil = yt?.let { Lock.until(this, it) },
            usedMs = Usage.todayMs(this),
            limitMs = Usage.limitMs(this),
            resetAt = Usage.nextReset(this).toLocalTime(),
            pauseUntil = Commitment.pauseUntil(this).takeIf { Commitment.pauseActive(this) },
            streak = StreakGoal.of(this)?.let { StatsMath.streaks(history, today, it.max, it.from).first } ?: 0,
            guardOn = Setup.guardOn(this),
            sleepOn = Sleep.enabled(this),
            sleepNow = Sleep.current(this),
            sleepNext = Sleep.nextNight(this),
            focusOn = Focus.enabled(this),
            focusNow = Focus.current(this),
            focusNext = Focus.next(this),
            commitActive = Commitment.active(this),
            daysLeft = Commitment.daysLeft(this).toInt(),
            pausesLeft = Commitment.pausesLeft(this),
        )
    }

    private fun actions() = HomeScreen.Actions(
        openApp = { alias -> startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(this, "$PKG.$alias"))) },
        messagesOn = { Setup.enableMessages(this, notifAsk) },
        messagesHide = {
            userPrefs().edit().putBoolean("hide_messages_card", true).apply()
            render()
        },
        stats = { PanelActivity.open(this, PanelActivity.STATS) },
        sleep = { PanelActivity.open(this, PanelActivity.SLEEP) },
        focus = { PanelActivity.open(this, PanelActivity.FOCUS) },
        commit = { PanelActivity.open(this, PanelActivity.COMMIT) },
        apps = { PanelActivity.open(this, PanelActivity.APPS) },
        settings = { startActivity(Intent(this, SettingsActivity::class.java)) },
    )

    private companion object { const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT }
}
