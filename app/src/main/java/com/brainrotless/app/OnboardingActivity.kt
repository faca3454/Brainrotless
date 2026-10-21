package com.brainrotless.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.time.LocalTime

/**
 * Guía del primer uso: idioma, qué hace Lúcido y con qué arrancar (modo dormir y modo foco).
 * No pide permisos de entrada: cada función pide el suyo cuando la activás. Quien ya usaba
 * Lúcido no la ve sola; se puede volver a ver desde Ajustes.
 */
class OnboardingActivity : ComponentActivity() {
    companion object {
        private const val KEY = "onboarding_done"
        private const val EXTRA_AGAIN = "again"
        private const val STEPS = OnboardingScreen.STEPS

        /** Primera vez. Quien ya había elegido idioma (usaba Lúcido antes de que hubiera guía) no la ve. */
        fun needed(c: Context): Boolean {
            val p = c.userPrefs()
            if (!p.contains(KEY)) p.edit().putBoolean(KEY, Lang.hasChosen(c)).apply()
            return !p.getBoolean(KEY, false)
        }

        /** [again]: vista desde Ajustes; empieza después del idioma y al terminar vuelve. */
        fun intent(c: Context, again: Boolean = false): Intent =
            Intent(c, OnboardingActivity::class.java).putExtra(EXTRA_AGAIN, again)
    }

    private var step = 0
    private val again get() = intent.getBooleanExtra(EXTRA_AGAIN, false)
    private lateinit var progress: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var back: View
    private lateinit var next: Button
    private val notifAsk = NotifAsk(this)

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: if (again) 1 else 0
        val pad = Brand.px(this, 20)
        progress = LinearLayout(this).apply { setPadding(pad, pad, pad, 0) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, pad)
        }
        scroll = ScrollView(this).apply { addView(content, ViewGroup.LayoutParams(MATCH, WRAP)) }
        val (bar, back, next) = OnboardingScreen.bottomBar(this, step, { go(step - 1) }, { go(step + 1) })
        this.back = back
        this.next = next
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = if (step > 0) go(step - 1) else finish()
        })
    }

    override fun onResume() {
        super.onResume()
        render() // al volver de los ajustes del sistema, las tildes quedan al día
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
    }

    private fun go(to: Int) {
        if (to >= STEPS) return finishGuide()
        val forward = to > step
        step = to.coerceAtLeast(0)
        render()
        scroll.scrollTo(0, 0)
        // Eje compartido: el paso nuevo entra desde el lado hacia el que se avanza
        content.alpha = 0f
        content.translationX = Brand.dp(this, if (forward) 24f else -24f) * if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        content.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(Brand.EASE_OUT).start()
    }

    private fun finishGuide() {
        userPrefs().edit().putBoolean(KEY, true).apply()
        if (!again) startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun render() {
        OnboardingScreen.progress(this, progress, step)
        OnboardingScreen.updateBar(this, step, back, next)
        OnboardingScreen.content(this, content, state(), actions())
    }

    /** Los datos de la guía: idioma, horarios y permisos. */
    private fun state(): OnboardingScreen.State {
        val (sleep, wake) = Sleep.get(this, null)
        val (start, end) = Focus.times(this)
        return OnboardingScreen.State(
            step = step,
            chosenLang = Lang.current(this).takeIf { Lang.hasChosen(this) },
            commitActive = Commitment.active(this),
            guardOn = Setup.guardOn(this),
            restricted = Setup.restrictedApplies(),
            sleepOn = Sleep.enabled(this), sleep = sleep, wake = wake,
            focusOn = Focus.enabled(this), focusStart = start, focusEnd = end, focusDays = Focus.days(this),
        )
    }

    private fun actions() = OnboardingScreen.Actions(
        chooseLang = { code ->
            step = 1
            Lang.set(this, code) // guarda, aplica y vuelve a crear la pantalla, ya en el paso siguiente
        },
        sleepOn = { on ->
            userPrefs().edit().putBoolean("sleep_on", on).apply()
            Sleep.reschedule(this)
            if (on) notifAsk.run() // para el aviso de antes de dormir
        },
        pickSleep = {
            val (sleep, wake) = Sleep.get(this, null)
            pickTime(R.string.pick_sleep, sleep) { s ->
                pickTime(R.string.pick_wake, wake) { w -> Sleep.set(this, null, s, w); Sleep.reschedule(this); render() }
            }
        },
        openSleep = { PanelActivity.open(this, PanelActivity.SLEEP) },
        focusOn = { on ->
            userPrefs().edit().putBoolean("focus_on", on).apply()
            Sleep.reschedule(this)
        },
        pickFocus = {
            val (start, end) = Focus.times(this)
            pickTime(R.string.pick_focus_start, start) { s ->
                pickTime(R.string.pick_focus_end, end) { e -> Focus.setTimes(this, s, e); render() }
            }
        },
        openFocus = { PanelActivity.open(this, PanelActivity.FOCUS) },
        turnOnGuard = { Setup.openAccessibility(this) },
        explainRestricted = { Setup.explainRestricted(this) },
    )

    private fun pickTime(title: Int, t: LocalTime, done: (LocalTime) -> Unit) =
        TimePickerDialog(this, { _, h, m -> done(LocalTime.of(h, m)) }, t.hour, t.minute, DateFormat.is24HourFormat(this))
            .apply { setTitle(title) }.show()
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
