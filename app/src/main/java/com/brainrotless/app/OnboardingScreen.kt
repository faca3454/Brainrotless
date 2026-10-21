package com.brainrotless.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Cómo se dibuja cada paso de la guía del primer uso, separado de dónde salen los datos
 * (OnboardingActivity los junta). Se puede dibujar con datos de prueba (Paparazzi).
 */
object OnboardingScreen {
    const val STEPS = 3

    /** Lo que muestra la guía, ya calculado. */
    class State(
        val step: Int = 0,
        val chosenLang: String? = null, // null: todavía no eligió idioma
        val commitActive: Boolean = false,
        val guardOn: Boolean = true,
        val restricted: Boolean = false, // Android 13+: puede hacer falta destrabar el permiso gris
        val sleepOn: Boolean = false,
        val sleep: LocalTime = LocalTime.of(23, 30),
        val wake: LocalTime = LocalTime.of(7, 0),
        val focusOn: Boolean = false,
        val focusStart: LocalTime = LocalTime.of(9, 0),
        val focusEnd: LocalTime = LocalTime.of(13, 0),
        val focusDays: Set<DayOfWeek> = emptySet(),
    )

    /** Qué hace cada toque. */
    class Actions(
        val chooseLang: (String) -> Unit = {},
        val sleepOn: (Boolean) -> Unit = {},
        val pickSleep: () -> Unit = {},
        val openSleep: () -> Unit = {},
        val focusOn: (Boolean) -> Unit = {},
        val pickFocus: () -> Unit = {},
        val openFocus: () -> Unit = {},
        val turnOnGuard: () -> Unit = {},
        val explainRestricted: () -> Unit = {},
    )

    /** La barra de progreso de arriba (una rayita por paso). */
    fun progress(c: Context, into: LinearLayout, step: Int) {
        into.removeAllViews()
        for (i in 0 until STEPS) into.addView(View(c).apply {
            background = Brand.rounded(c, if (i <= step) R.color.brand_primary else R.color.brand_surface_alt, 999f)
        }, LinearLayout.LayoutParams(0, Brand.px(c, 4), 1f).apply { if (i > 0) marginStart = Brand.px(c, 6) })
    }

    /** El contenido del paso. */
    fun content(c: Context, into: LinearLayout, s: State, a: Actions) {
        into.removeAllViews()
        fun addLp(v: View, lp: LinearLayout.LayoutParams, top: Int) = into.addView(v, lp.apply { topMargin = Brand.px(c, top) })
        fun add(v: View, top: Int) = addLp(v, LinearLayout.LayoutParams(MATCH, WRAP), top)
        when (s.step) {
            0 -> language(c, s, a, ::addLp)
            1 -> welcome(c, ::add)
            else -> plan(c, s, a, ::add)
        }
    }

    /** Toda la pantalla de un paso (para las pruebas: la Activity arma la suya con estas mismas piezas). */
    fun build(c: Context, s: State, a: Actions = Actions()): View {
        val pad = Brand.px(c, 20)
        val progress = LinearLayout(c).apply { setPadding(pad, pad, pad, 0) }
        val content = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, pad)
        }
        progress(c, progress, s.step)
        content(c, content, s, a)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(content, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(bottomBar(c, s.step, {}, {}).first, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    /** Volver y Siguiente/Empezar. Devuelve la barra y los dos botones. */
    fun bottomBar(c: Context, step: Int, onBack: () -> Unit, onNext: () -> Unit): Triple<View, View, android.widget.Button> {
        val pad = Brand.px(c, 20)
        val back = Brand.secondaryButton(c, c.getString(R.string.onb_back), onBack)
        val next = Brand.primaryButton(c, "", onNext)
        updateBar(c, step, back, next)
        val bar = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, Brand.px(c, 8), pad, pad)
            addView(back, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(Space(c), LinearLayout.LayoutParams(0, 1, 1f))
            addView(next, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        return Triple(bar, back, next)
    }

    fun updateBar(c: Context, step: Int, back: View, next: android.widget.Button) {
        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE // el idioma avanza solo al elegirlo
        next.text = c.getString(if (step == STEPS - 1) R.string.onb_start else R.string.onb_next)
    }

    // ---------- Pasos ----------

    private fun language(c: Context, s: State, a: Actions, add: (View, LinearLayout.LayoutParams, Int) -> Unit) {
        val full = { LinearLayout.LayoutParams(MATCH, WRAP) }
        add(ImageView(c).apply { setImageResource(R.drawable.ic_logo) },
            LinearLayout.LayoutParams(Brand.px(c, 72), Brand.px(c, 72)).apply { gravity = Gravity.CENTER_HORIZONTAL }, 40)
        add(title(c, c.getString(R.string.app_name)).apply { gravity = Gravity.CENTER }, full(), 16)
        add(muted(c, "Elegí tu idioma · Choose your language").apply { gravity = Gravity.CENTER }, full(), 6)
        for ((code, name) in listOf("es" to "Español", "en" to "English")) {
            val chosen = s.chosenLang == code
            add(Brand.card(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Brand.text(c, name, 18f, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
                if (chosen) addView(Brand.text(c, "✓", 20f, R.color.brand_primary, bold = true))
            }) { a.chooseLang(code) }, full(), if (code == "es") 28 else 12)
        }
    }

    private fun welcome(c: Context, add: (View, Int) -> Unit) {
        add(title(c, c.getString(R.string.onb_welcome_title)), 28)
        add(muted(c, c.getString(R.string.tagline)), 6)
        add(feature(c, R.mipmap.ic_ig, tint = false, R.string.onb_f1_title, R.string.onb_f1_sub), 24)
        add(feature(c, R.drawable.ic_moon, tint = true, R.string.home_sleep, R.string.onb_f2_sub), 16)
        add(feature(c, R.drawable.ic_focus, tint = true, R.string.home_focus, R.string.onb_f3_sub), 16)
        add(feature(c, R.drawable.ic_flag, tint = true, R.string.onb_f4_title, R.string.onb_f4_sub), 16)
    }

    private fun plan(c: Context, s: State, a: Actions, add: (View, Int) -> Unit) {
        add(title(c, c.getString(R.string.onb_plan_title)), 28)
        add(muted(c, c.getString(R.string.onb_plan_sub)), 6)
        val locked = s.commitActive // con compromiso, esto se cambia en su pantalla (que no deja aflojar)
        // Con modo dormir o foco, la Accesibilidad para que también frenen las apps oficiales
        val guard = Brand.notice(c, c.getString(R.string.need_guard_plan), c.getString(R.string.turn_on), a.turnOnGuard,
            help = if (s.restricted) c.getString(R.string.gray_title) else null, onHelp = a.explainRestricted)
        var sleepOn = s.sleepOn
        var focusOn = s.focusOn
        fun showGuard() {
            guard.visibility = if ((sleepOn || focusOn) && !s.guardOn) View.VISIBLE else View.GONE
        }

        val sleepHours = c.getString(R.string.night_range, Sleep.fmt(c, s.sleep), Sleep.fmt(c, s.wake))
        add(Brand.card(if (locked) linkRow(c, c.getString(R.string.home_sleep), sleepHours, a.openSleep)
            else column(c,
                switchRow(c, c.getString(R.string.home_sleep), c.getString(R.string.onb_f2_sub), s.sleepOn) { on ->
                    sleepOn = on
                    a.sleepOn(on)
                    showGuard()
                },
                space(c, 6),
                linkRow(c, c.getString(R.string.onb_schedule), sleepHours, a.pickSleep)
            )), 20)

        val names = c.resources.getStringArray(R.array.weekday_entries)
        val days = s.focusDays.sorted().joinToString(" ") { names[it.value - 1].take(1) }.ifEmpty { c.getString(R.string.focus_no_days) }
        val focusHours = c.getString(R.string.night_range, Sleep.fmt(c, s.focusStart), Sleep.fmt(c, s.focusEnd)) + " · " + days
        add(Brand.card(if (locked) linkRow(c, c.getString(R.string.home_focus), focusHours, a.openFocus)
            else column(c,
                switchRow(c, c.getString(R.string.home_focus), c.getString(R.string.onb_f3_sub), s.focusOn) { on ->
                    focusOn = on
                    a.focusOn(on)
                    showGuard()
                },
                space(c, 6),
                linkRow(c, c.getString(R.string.onb_schedule), focusHours, a.pickFocus)
            )), 12)
        showGuard()
        add(guard, 12)
        add(muted(c, c.getString(R.string.onb_commit_hint)), 16)
    }

    // ---------- Piezas ----------

    private fun title(c: Context, s: String) = Brand.text(c, s, 26f, bold = true)
    private fun muted(c: Context, s: String) = Brand.text(c, s, 15f, R.color.brand_text_muted)
    private fun space(c: Context, dp: Int) = Space(c).apply { minimumHeight = Brand.px(c, dp) }

    private fun column(c: Context, vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
    }

    /** Ícono en un círculo suave (o el de la app tal cual), título y explicación. */
    private fun feature(c: Context, icon: Int, tint: Boolean, title: Int, sub: Int): View {
        val size = Brand.px(c, 44)
        val badge = ImageView(c).apply {
            setImageResource(icon)
            if (tint) {
                setColorFilter(Brand.color(c, R.color.brand_primary))
                background = Brand.rounded(c, R.color.brand_tonal, 999f)
                val p = Brand.px(c, 11)
                setPadding(p, p, p, p)
            }
        }
        return LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(size, size))
            addView(column(c, Brand.text(c, c.getString(title), 16f, bold = true), Brand.text(c, c.getString(sub), 14f, R.color.brand_text_muted)),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(c, 14) })
        }
    }

    private fun switchRow(c: Context, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = ToggleView(c).apply {
            this.checked = checked
            this.onChange = onChange
        }
        return LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(column(c, Brand.text(c, title, 16f, bold = true), Brand.text(c, sub, 14f, R.color.brand_text_muted)),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sw, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(c, 12) })
            setOnClickListener { sw.performClick() }
        }
    }

    private fun linkRow(c: Context, title: String, sub: String, onClick: () -> Unit) = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Brand.px(c, 44)
        addView(column(c, Brand.text(c, title, 15f, bold = true), Brand.text(c, sub, 14f, R.color.brand_text_muted)),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(Brand.text(c, "›", 22f, R.color.brand_text_muted))
        background = Brand.pressable(c, android.graphics.drawable.ColorDrawable(0), 12f)
        setOnClickListener { onClick() }
    }

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
