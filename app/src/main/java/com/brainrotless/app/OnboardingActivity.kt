package com.brainrotless.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalTime

/**
 * Guía del primer uso: idioma, qué hace Lúcido, permisos (cada uno con su explicación y una
 * tilde cuando está listo) y con qué arrancar (modo dormir y modo foco). Quien ya usaba Lúcido
 * no la ve sola; se puede volver a ver desde Ajustes.
 */
class OnboardingActivity : ComponentActivity() {
    companion object {
        private const val KEY = "onboarding_done"
        private const val EXTRA_AGAIN = "again"
        private const val STEPS = 4

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
    private lateinit var back: Button
    private lateinit var next: Button
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

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
        back = Brand.secondaryButton(this, getString(R.string.onb_back)) { go(step - 1) }
        next = Brand.primaryButton(this, "") { go(step + 1) }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, Brand.px(context, 8), pad, pad)
            addView(back, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(next, LinearLayout.LayoutParams(WRAP, WRAP))
        }
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
        step = to.coerceAtLeast(0)
        render()
        scroll.scrollTo(0, 0)
    }

    private fun finishGuide() {
        userPrefs().edit().putBoolean(KEY, true).apply()
        if (!again) startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun render() {
        progress.removeAllViews()
        for (i in 0 until STEPS) progress.addView(View(this).apply {
            background = Brand.rounded(context, if (i <= step) R.color.brand_primary else R.color.brand_surface_alt, 999f)
        }, LinearLayout.LayoutParams(0, Brand.px(this, 4), 1f).apply { if (i > 0) marginStart = Brand.px(this@OnboardingActivity, 6) })

        back.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        next.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE // el idioma avanza solo al elegirlo
        next.text = getString(if (step == STEPS - 1) R.string.onb_start else R.string.onb_next)
        content.removeAllViews()
        when (step) {
            0 -> language()
            1 -> welcome()
            2 -> permissions()
            else -> plan()
        }
    }

    // ---------- Pasos ----------

    private fun language() {
        add(ImageView(this).apply { setImageResource(R.drawable.ic_logo) },
            LinearLayout.LayoutParams(Brand.px(this, 72), Brand.px(this, 72)).apply { gravity = Gravity.CENTER_HORIZONTAL }, top = 40)
        add(title(getString(R.string.app_name)).apply { gravity = Gravity.CENTER }, top = 16)
        add(muted("Elegí tu idioma · Choose your language").apply { gravity = Gravity.CENTER }, top = 6)
        for ((code, name) in listOf("es" to "Español", "en" to "English")) {
            val chosen = Lang.hasChosen(this) && Lang.current(this) == code
            add(Brand.card(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Brand.text(context, name, 18f, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
                if (chosen) addView(Brand.text(context, "✓", 20f, R.color.brand_primary, bold = true))
            }) {
                step = 1
                Lang.set(this, code) // guarda, aplica y vuelve a crear la pantalla, ya en el paso siguiente
            }, top = if (code == "es") 28 else 12)
        }
    }

    private fun welcome() {
        add(title(getString(R.string.onb_welcome_title)), top = 28)
        add(muted(getString(R.string.tagline)), top = 6)
        feature(R.mipmap.ic_ig, tint = false, R.string.onb_f1_title, R.string.onb_f1_sub, top = 24)
        feature(R.drawable.ic_moon, tint = true, R.string.home_sleep, R.string.onb_f2_sub)
        feature(R.drawable.ic_focus, tint = true, R.string.home_focus, R.string.onb_f3_sub)
        feature(R.drawable.ic_flag, tint = true, R.string.onb_f4_title, R.string.onb_f4_sub)
    }

    private fun permissions() {
        add(title(getString(R.string.onb_perm_title)), top = 28)
        add(muted(getString(R.string.onb_perm_sub)), top = 6)
        if (Build.VERSION.SDK_INT >= 33) permission(R.string.onb_p_notif_title, R.string.onb_p_notif_sub, R.string.stats_allow,
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, top = 20
        ) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        permission(R.string.onb_p_listener_title, R.string.onb_p_listener_sub, R.string.turn_on, Setup.listenerOn(this),
            top = if (Build.VERSION.SDK_INT >= 33) 12 else 20) { Setup.openNotifications(this) }
        val guard = Setup.guardOn(this)
        permission(R.string.onb_p_guard_title, R.string.onb_p_guard_sub, R.string.turn_on, guard,
            gray = !guard && Setup.restrictedApplies()) { Setup.openAccessibility(this) }
        permission(R.string.onb_p_links_title, R.string.onb_p_links_sub, R.string.onb_set_up, Setup.linksOn(this), optional = true) {
            Setup.openLinks(this)
        }
        permission(R.string.onb_p_usage_title, R.string.onb_p_usage_sub, R.string.stats_allow, UsageAccess.granted(this), optional = true) {
            UsageAccess.open(this)
        }
        if (!Setup.listenerOn(this) || !guard) add(muted(getString(R.string.onb_later)), top = 14)
    }

    private fun plan() {
        add(title(getString(R.string.onb_plan_title)), top = 28)
        add(muted(getString(R.string.onb_plan_sub)), top = 6)
        val locked = Commitment.active(this) // con compromiso, esto se cambia en su pantalla (que no deja aflojar)

        val (sleep, wake) = Sleep.get(this, null)
        val sleepHours = getString(R.string.night_range, Sleep.fmt(this, sleep), Sleep.fmt(this, wake))
        add(Brand.card(if (locked) linkRow(getString(R.string.home_sleep), sleepHours) { PanelActivity.open(this, PanelActivity.SLEEP) }
            else column(
                switchRow(getString(R.string.home_sleep), getString(R.string.onb_f2_sub), Sleep.enabled(this)) { on ->
                    userPrefs().edit().putBoolean("sleep_on", on).apply()
                    Sleep.reschedule(this)
                },
                space(6),
                linkRow(getString(R.string.onb_schedule), sleepHours) {
                    pickTime(R.string.pick_sleep, sleep) { s ->
                        pickTime(R.string.pick_wake, wake) { w -> Sleep.set(this, null, s, w); Sleep.reschedule(this); render() }
                    }
                }
            )), top = 20)

        val (start, end) = Focus.times(this)
        val names = resources.getStringArray(R.array.weekday_entries)
        val days = Focus.days(this).sorted().joinToString(" ") { names[it.value - 1].take(1) }.ifEmpty { getString(R.string.focus_no_days) }
        val focusHours = getString(R.string.night_range, Sleep.fmt(this, start), Sleep.fmt(this, end)) + " · " + days
        add(Brand.card(if (locked) linkRow(getString(R.string.home_focus), focusHours) { PanelActivity.open(this, PanelActivity.FOCUS) }
            else column(
                switchRow(getString(R.string.home_focus), getString(R.string.onb_f3_sub), Focus.enabled(this)) { on ->
                    userPrefs().edit().putBoolean("focus_on", on).apply()
                    Sleep.reschedule(this)
                },
                space(6),
                linkRow(getString(R.string.onb_schedule), focusHours) {
                    pickTime(R.string.pick_focus_start, start) { s ->
                        pickTime(R.string.pick_focus_end, end) { e -> Focus.setTimes(this, s, e); render() }
                    }
                }
            )))
        add(muted(getString(R.string.onb_commit_hint)), top = 16)
    }

    // ---------- Piezas ----------

    private fun add(v: View, lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(MATCH, WRAP), top: Int = 12) =
        content.addView(v, lp.apply { topMargin = Brand.px(this@OnboardingActivity, top) })

    private fun title(s: String) = Brand.text(this, s, 26f, bold = true)
    private fun muted(s: String) = Brand.text(this, s, 15f, R.color.brand_text_muted)
    private fun space(dp: Int) = Space(this).apply { minimumHeight = Brand.px(context, dp) }

    private fun column(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
    }

    /** Ícono en un círculo suave (o el de la app tal cual), título y explicación. */
    private fun feature(icon: Int, tint: Boolean, title: Int, sub: Int, top: Int = 16) {
        val size = Brand.px(this, 44)
        val badge = ImageView(this).apply {
            setImageResource(icon)
            if (tint) {
                setColorFilter(Brand.color(context, R.color.brand_primary))
                background = Brand.rounded(context, R.color.brand_tonal, 999f)
                val p = Brand.px(context, 11)
                setPadding(p, p, p, p)
            }
        }
        add(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(size, size))
            addView(column(Brand.text(context, getString(title), 16f, bold = true), Brand.text(context, getString(sub), 14f, R.color.brand_text_muted)),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 14) })
        }, top = top)
    }

    /**
     * Un permiso: qué es y para qué sirve, y a la derecha una tilde si ya está o el botón para
     * activarlo. [done] null: Android no deja saberlo (se muestra solo el botón).
     */
    private fun permission(title: Int, sub: Int, action: Int, done: Boolean?, optional: Boolean = false, gray: Boolean = false,
                           top: Int = 12, onClick: () -> Unit) {
        val name = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Brand.text(context, getString(title), 16f, bold = true))
            if (optional) addView(Brand.text(context, getString(R.string.onb_optional), 11.5f, R.color.brand_text_muted, bold = true).apply {
                background = Brand.rounded(context, R.color.brand_surface_alt, 999f)
                setPadding(Brand.px(context, 8), Brand.px(context, 1), Brand.px(context, 8), Brand.px(context, 2))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(context, 8) })
        }
        val text = column(name, Brand.text(this, getString(sub), 14f, R.color.brand_text_muted))
        if (gray) text.addView(Brand.text(this, getString(R.string.gray_title), 14f, R.color.brand_primary, bold = true).apply {
            setPadding(0, Brand.px(context, 8), 0, 0)
            setOnClickListener { Setup.explainRestricted(this@OnboardingActivity) }
        })
        val status: View = if (done == true) Brand.text(this, "✓", 22f, R.color.brand_primary, bold = true)
            else Brand.secondaryButton(this, getString(action)) { onClick() }
        add(Brand.card(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(status, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(context, 12) })
        }), top = top)
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = ToggleView(this).apply {
            this.checked = checked
            this.onChange = onChange
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(column(Brand.text(context, title, 16f, bold = true), Brand.text(context, sub, 14f, R.color.brand_text_muted)),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sw, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(context, 12) })
            setOnClickListener { sw.performClick() }
        }
    }

    private fun linkRow(title: String, sub: String, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Brand.px(context, 44)
        addView(column(Brand.text(context, title, 15f, bold = true), Brand.text(context, sub, 14f, R.color.brand_text_muted)),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(Brand.text(context, "›", 22f, R.color.brand_text_muted))
        background = Brand.pressable(context, android.graphics.drawable.ColorDrawable(0), 12f)
        setOnClickListener { onClick() }
    }

    private fun pickTime(title: Int, t: LocalTime, done: (LocalTime) -> Unit) =
        TimePickerDialog(this, { _, h, m -> done(LocalTime.of(h, m)) }, t.hour, t.minute, DateFormat.is24HourFormat(this))
            .apply { setTitle(title) }.show()
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
