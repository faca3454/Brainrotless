package com.brainrotless.app

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Pantalla principal de Lúcido (el ícono de la app): abrir Instagram o YouTube, cómo va el día,
 * modo dormir, compromiso y lo que falte configurar. Se vuelve a armar al volver a la pantalla.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var list: LinearLayout
    private var firstShow = true
    private var lang = "" // idioma con el que se armó la pantalla

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        Lang.askIfNeeded(this)
    }

    override fun onResume() {
        super.onResume()
        if (Lang.current(this) != lang) return recreate() // cambiaron el idioma en Ajustes
        render()
        Commitment.onEnd(this)?.let { text ->
            Commitment.markSummarySeen(this)
            AlertDialog.Builder(this)
                .setMessage(text)
                .setPositiveButton(R.string.start_another) { _, _ -> PanelActivity.open(this, PanelActivity.COMMIT) }
                .setNeutralButton(R.string.summary_charts) { _, _ -> PanelActivity.open(this, PanelActivity.STATS) }
                .setNegativeButton(R.string.not_now, null)
                .show()
        }
    }

    private fun render() {
        list.removeAllViews()
        add(header(), top = 0)
        setupCard()?.let { add(it) }
        add(appsRow())
        add(todayCard())
        add(sleepCard())
        add(focusCard())
        add(commitmentCard())
        add(linkCard(R.drawable.ic_settings, getString(R.string.panel_apps), getString(R.string.panel_apps_summary), pill = null) {
            PanelActivity.open(this, PanelActivity.APPS)
        })
        // Ajustes al pie, empujado hacia abajo si sobra lugar
        list.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        add(Brand.secondaryButton(this, getString(R.string.settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }, top = 20)

        // Entrada suave la primera vez (después, sin animar: que se sienta instantáneo)
        if (firstShow) {
            firstShow = false
            for (i in 0 until list.childCount) list.getChildAt(i).apply {
                alpha = 0f
                translationY = Brand.dp(context, 12f)
                animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(260).start()
            }
        }
    }

    private fun add(v: View, top: Int = 12) =
        list.addView(v, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Brand.px(this@HomeActivity, top)
        })

    // ---------- Piezas ----------

    private fun header() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_logo)
            contentDescription = getString(R.string.app_name)
        }, LinearLayout.LayoutParams(Brand.px(context, 48), Brand.px(context, 48)))
        addView(column(
            Brand.text(context, getString(R.string.app_name), 26f, bold = true),
            Brand.text(context, getString(R.string.tagline), 13.5f, R.color.brand_text_muted)
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Brand.px(context, 14)
        })
    }

    /** Lo que falta para que todo funcione; desaparece cuando está listo. */
    private fun setupCard(): View? {
        val notif = Setup.listenerOn(this)
        val guard = Setup.guardOn(this)
        if (notif && guard) return null
        val box = column(Brand.text(this, getString(R.string.home_setup_title), 16f, bold = true))
        box.background = Brand.rounded(this, R.color.brand_tonal, 20f)
        val pad = Brand.px(this, 16)
        box.setPadding(pad, pad, pad, Brand.px(this, 8))
        fun step(done: Boolean, label: String, action: () -> Unit) = box.addView(row(
            Brand.text(this, if (done) "✓" else "○", 16f, if (done) R.color.brand_primary else R.color.brand_text_muted, bold = true),
            Brand.text(this, label, 15f),
            onClick = if (done) null else action
        ))
        step(notif, getString(R.string.home_step_notif)) { Setup.openNotifications(this) }
        step(guard, getString(R.string.home_step_guard)) { Setup.openAccessibility(this) }
        if (Setup.restrictedApplies()) step(false, getString(R.string.gray_title)) { Setup.explainRestricted(this) }
        return box
    }

    private fun appsRow() = LinearLayout(this).apply {
        val ig = Lock.ig(context)
        val yt = Lock.yt(context)
        val reels = Lock.reelsAllowed(context)
        val igSub = when {
            ig != null -> getString(R.string.ig_sub_restricted)
            reels -> getString(R.string.ig_sub_reels)
            context.userPrefs().getString("ig_feed", "normal") == "following" -> getString(R.string.ig_sub_following)
            context.userPrefs().getString("ig_feed", "normal") == "blocked" -> getString(R.string.ig_sub_blocked)
            else -> getString(R.string.ig_sub_normal)
        }
        val ytSub = when {
            yt != null -> getString(R.string.yt_sub_blocked, Lock.until(context, yt))
            reels -> getString(R.string.yt_sub_shorts)
            else -> getString(R.string.yt_sub_normal)
        }
        addView(appTile(R.mipmap.ic_ig, "Instagram", igSub, "Instagram"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(appTile(R.mipmap.ic_yt, "YouTube", ytSub, "YouTube", warning = yt != null),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = Brand.px(context, 12) })
    }

    private fun appTile(icon: Int, name: String, sub: String, alias: String, warning: Boolean = false) = Brand.card(
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val size = Brand.px(context, 48)
            addView(ImageView(context).apply { setImageResource(icon); contentDescription = name }, LinearLayout.LayoutParams(size, size))
            addView(Brand.text(context, name, 16f, bold = true).apply { setPadding(0, Brand.px(context, 10), 0, Brand.px(context, 2)) })
            addView(Brand.text(context, sub, 12.5f, if (warning) R.color.brand_warning else R.color.brand_text_muted))
        }
    ) {
        startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(this, "$PKG.$alias")))
    }

    private fun todayCard(): View {
        val used = Usage.todayMs(this)
        val limit = Usage.limitMs(this)
        val over = limit != null && used >= limit
        val ring = RingView(this)
        ring.post { ring.setProgress(limit?.let { used.toFloat() / it }, warning = over) }
        val reset = Sleep.fmt(this, Usage.nextReset(this).toLocalTime())
        val detail = when {
            Commitment.pauseActive(this) ->
                getString(R.string.home_pause_until, DateFormat.getTimeFormat(this).format(Date(Commitment.pauseUntil(this))))
            over -> getString(R.string.home_over, reset)
            else -> getString(R.string.home_resets, reset)
        }
        val amount = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(Brand.text(context, formatDuration(used), 22f, bold = true))
            limit?.let { addView(Brand.text(context, getString(R.string.home_of, formatDuration(it)), 13f, R.color.brand_text_muted).apply { setPadding(0, 0, 0, Brand.px(context, 3)) }) }
        }
        // Mini gráfico de la semana: avisa que tocando se ven las estadísticas
        val today = Usage.today(this)
        val week = StatsMath.window(Usage.history(this), today, 7)
        val spark = DayBars(this, mini = true).apply {
            values = week.map { it?.total }
            this.limit = limit
            if (limit != null) limitFrom = StatsMath.indexOf(Commitment.startDay(context), today, 7)
            selected = week.size - 1
            contentDescription = getString(R.string.stats_open)
        }
        val body = column(
            Brand.text(this, getString(R.string.home_today), 13f, R.color.brand_text_muted),
            amount,
            Brand.text(this, detail, 13f, if (over) R.color.brand_warning else R.color.brand_text_muted)
        )
        // Racha de 2 días o más: una pastilla para festejarla
        StreakGoal.of(this)?.let { goal ->
            val streak = StatsMath.streaks(Usage.history(this), today, goal.max, goal.from).first
            if (streak >= 2) body.addView(Brand.pill(this, resources.getQuantityString(R.plurals.streak_pill, streak, streak)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = Brand.px(this@HomeActivity, 6) })
        }
        return Brand.card(row(
            ring,
            body,
            chevron = true,
            iconSize = 68
        ).apply {
            // Con un solo día no hay forma que mostrar
            if (week.count { it != null } >= 2)
                addView(spark, 2, LinearLayout.LayoutParams(Brand.px(context, 52), ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = Brand.px(context, 8); marginEnd = Brand.px(context, 4) })
        }) { PanelActivity.open(this, PanelActivity.STATS) }
    }

    private fun sleepCard(): View {
        val sub = when {
            !Sleep.enabled(this) -> getString(R.string.home_sleep_off)
            Sleep.current(this) != null -> getString(R.string.home_sleep_now, Sleep.fmt(this, Sleep.current(this)!!.end.toLocalTime()))
            else -> Sleep.nextNight(this)?.let { n ->
                getString(R.string.home_sleep_next, Sleep.fmt(this, n.start.toLocalTime()), Sleep.fmt(this, n.end.toLocalTime()))
            } ?: getString(R.string.home_sleep_none)
        }
        return linkCard(R.drawable.ic_moon, getString(R.string.home_sleep), sub, pill = null) { PanelActivity.open(this, PanelActivity.SLEEP) }
    }

    private fun focusCard(): View {
        val sub = when {
            !Focus.enabled(this) -> getString(R.string.home_focus_off)
            Focus.current(this) != null -> getString(R.string.home_sleep_now, Sleep.fmt(this, Focus.current(this)!!.end.toLocalTime()))
            else -> Focus.next(this)?.let { b ->
                val day = b.start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale(Lang.current(this)))
                getString(R.string.home_focus_next, day, Sleep.fmt(this, b.start.toLocalTime()), Sleep.fmt(this, b.end.toLocalTime()))
            } ?: getString(R.string.focus_no_days)
        }
        return linkCard(R.drawable.ic_focus, getString(R.string.home_focus), sub, pill = null) { PanelActivity.open(this, PanelActivity.FOCUS) }
    }

    private fun commitmentCard(): View {
        if (!Commitment.active(this))
            return linkCard(R.drawable.ic_flag, getString(R.string.home_commit), getString(R.string.home_commit_idle, Commitment.MIN_DAYS), pill = null) {
                PanelActivity.open(this, PanelActivity.COMMIT)
            }
        val left = Commitment.daysLeft(this)
        val pauses = Commitment.pausesLeft(this)
        return linkCard(
            R.drawable.ic_flag, getString(R.string.home_commit),
            resources.getQuantityString(R.plurals.pauses_n, pauses, pauses) + " · " +
                getString(if (Protection.adminActive(this)) R.string.home_protected else R.string.home_unprotected),
            pill = resources.getQuantityString(R.plurals.days_n, left.toInt(), left.toInt())
        ) { PanelActivity.open(this, PanelActivity.COMMIT) }
    }

    /** Tarjeta con ícono redondo, título, detalle y flecha, que lleva a Ajustes. */
    private fun linkCard(icon: Int, title: String, sub: String, pill: String?, onClick: () -> Unit): View {
        val badge = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(Brand.color(context, R.color.brand_primary))
            background = Brand.rounded(context, R.color.brand_tonal, 999f)
            val p = Brand.px(context, 10)
            setPadding(p, p, p, p)
        }
        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Brand.text(context, title, 16f, bold = true))
            pill?.let {
                addView(Brand.pill(context, it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = Brand.px(context, 8) })
            }
        }
        return Brand.card(row(badge, column(titleRow, Brand.text(this, sub, 13f, R.color.brand_text_muted)), chevron = true), onClick)
    }

    // ---------- Ayudas de armado ----------

    private fun column(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

    /** Fila: ícono a la izquierda, contenido que ocupa el resto y, opcional, una flecha. */
    private fun row(icon: View, body: View, chevron: Boolean = false, iconSize: Int = 40, onClick: (() -> Unit)? = null) =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Brand.px(context, 44)
            val size = if (icon is ImageView || icon is RingView) Brand.px(context, iconSize) else ViewGroup.LayoutParams.WRAP_CONTENT
            addView(icon, LinearLayout.LayoutParams(size, size))
            addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Brand.px(context, 14) })
            if (chevron || onClick != null) addView(Brand.text(context, "›", 22f, R.color.brand_text_muted))
            onClick?.let { f ->
                background = Brand.pressable(context, android.graphics.drawable.ColorDrawable(0), 12f)
                setOnClickListener { f() }
            }
        }

    private companion object { const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT }
}
