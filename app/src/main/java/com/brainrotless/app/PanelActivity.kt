package com.brainrotless.app

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlin.math.abs

/**
 * Pantallas de configuración con el estilo del inicio: Modo dormir, Modo foco, Compromiso e
 * Instagram y YouTube. Escriben en los mismos ajustes que la pantalla de Ajustes; cada cambio
 * vuelve a armar la pantalla (es chica y se arma al instante).
 */
class PanelActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA = "panel"
        const val SLEEP = "sleep"
        const val FOCUS = "focus"
        const val COMMIT = "commit"
        const val APPS = "apps"
        const val STATS = "stats"

        fun intent(c: Context, panel: String): Intent = Intent(c, PanelActivity::class.java).putExtra(EXTRA, panel)
        fun open(c: Context, panel: String) = c.startActivity(intent(c, panel))
    }

    private lateinit var list: LinearLayout
    private val panel get() = intent.getStringExtra(EXTRA) ?: SLEEP
    private var draftDays = Commitment.MIN_DAYS // días elegidos antes de empezar un compromiso
    private var statsRange = 7 // días que muestran las estadísticas

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Brand.px(this, 18)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Brand.px(this@PanelActivity, 12), pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(list, ViewGroup.LayoutParams(MATCH, WRAP)) })
    }

    override fun onResume() {
        super.onResume()
        render() // al volver de los ajustes del sistema (permisos) también
    }

    private fun render() {
        list.removeAllViews()
        when (panel) {
            SLEEP -> sleepPanel()
            FOCUS -> focusPanel()
            COMMIT -> if (Commitment.active(this)) commitmentActive() else commitmentNew()
            APPS -> appsPanel()
            STATS -> statsPanel()
        }
    }

    // ---------- Modo dormir ----------

    private fun sleepPanel() {
        header(getString(R.string.home_sleep), toggle(Sleep.enabled(this)) { on ->
            change({ it.copy(sleepOn = on) }) { putBoolean("sleep_on", on) }
        })
        if (Commitment.active(this)) add(banner(getString(R.string.panel_locked)))
        add(muted(getString(R.string.pref_sleep_on_summary)), top = 4)

        val perDay = Sleep.perDay(this)
        add(Brand.card(column(
            if (!perDay) {
                val (sleep, wake) = Sleep.get(this, null)
                timePair(getString(R.string.panel_bed), sleep, getString(R.string.panel_wake), wake) { pickNight(null) }
            } else column(*DayOfWeek.values().map { d ->
                val (s, w) = Sleep.get(this, d)
                linkRow(getString(nightTitle(d)), range(s, w)) { pickNight(d) }
            }.toTypedArray()),
            space(10),
            switchRow(getString(R.string.pref_per_day), getString(R.string.panel_per_day_sub), perDay) { on ->
                change({ s -> s.copy(nights = DayOfWeek.values().associateWith { Sleep.get(this, if (on) it else null) }) }) {
                    putBoolean("sleep_per_day", on)
                }
            }
        )))

        val browsers = when {
            !Setup.guardOn(this) -> getString(R.string.browsers_need_guard)
            else -> Browsers.names(this).takeIf { it.isNotEmpty() }?.joinToString(", ") ?: getString(R.string.browsers_none)
        }
        add(Brand.card(switchRow(getString(R.string.pref_browsers), browsers,
            userPrefs().getBoolean("sleep_browsers", true)) { on ->
            change({ it.copy(browsers = on) }) { putBoolean("sleep_browsers", on) }
        }))

        add(label(getString(R.string.pref_warn)), top = 18)
        val warnValues = listOf("0", "5", "10", "15", "30")
        val warn = userPrefs().getString("sleep_warn", "15")
        add(Brand.card(chips(warnValues.map { minutesChip(it.toInt(), getString(R.string.panel_no_warning)) },
            warnValues.indexOf(warn)) { i -> change { putString("sleep_warn", warnValues[i]) } }))
    }

    private fun nightTitle(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> R.string.pref_night_1
        DayOfWeek.TUESDAY -> R.string.pref_night_2
        DayOfWeek.WEDNESDAY -> R.string.pref_night_3
        DayOfWeek.THURSDAY -> R.string.pref_night_4
        DayOfWeek.FRIDAY -> R.string.pref_night_5
        DayOfWeek.SATURDAY -> R.string.pref_night_6
        DayOfWeek.SUNDAY -> R.string.pref_night_7
    }

    /** Hora de dormir y después de despertar, del horario general (null) o de un día. */
    private fun pickNight(day: DayOfWeek?) {
        val (sleep, wake) = Sleep.get(this, day)
        pickTime(getString(R.string.pick_sleep), sleep) { newSleep ->
            pickTime(getString(R.string.pick_wake), wake) { newWake ->
                // El horario general rige todos los días; el de un día, solo ese
                val days = day?.let { listOf(it) } ?: DayOfWeek.values().toList()
                if (Commitment.forbids(this) { s -> s.copy(nights = s.nights + days.associateWith { newSleep to newWake }) }) {
                    toast(getString(R.string.loosen_forbidden))
                } else {
                    Sleep.set(this, day, newSleep, newWake)
                    changed()
                }
            }
        }
    }

    // ---------- Modo foco ----------

    private fun focusPanel() {
        header(getString(R.string.home_focus), toggle(Focus.enabled(this)) { on -> change { putBoolean("focus_on", on) } })
        add(muted(getString(R.string.pref_focus_on_summary)), top = 4)

        val (start, end) = Focus.times(this)
        val days = Focus.days(this)
        add(Brand.card(column(
            timePair(getString(R.string.pick_focus_start), start, getString(R.string.pick_focus_end), end) {
                pickTime(getString(R.string.pick_focus_start), start) { s ->
                    pickTime(getString(R.string.pick_focus_end), end) { e -> Focus.setTimes(this, s, e); changed() }
                }
            },
            space(12),
            dayChips(days) { d ->
                val now = if (d in days) days - d else days + d
                change { putStringSet("focus_days", now.map { it.value.toString() }.toSet()) }
            }
        )))

        add(Brand.card(switchRow(getString(R.string.pref_focus_youtube), getString(R.string.pref_focus_youtube_summary),
            Focus.blocksYouTube(this)) { on -> change { putBoolean("focus_youtube", on) } }))

        add(label(getString(R.string.pref_session)), top = 18)
        val values = listOf("0", "10", "15", "20", "30", "45", "60")
        val current = userPrefs().getString("session_warn", "20")
        add(Brand.card(column(
            muted(getString(R.string.panel_session_sub)),
            space(10),
            chips(values.map { minutesChip(it.toInt(), getString(R.string.panel_off)) }, values.indexOf(current)) { i ->
                change { putString("session_warn", values[i]) }
            }
        )))
    }

    // ---------- Compromiso ----------

    private fun commitmentNew() {
        header(getString(R.string.home_commit))
        add(muted(getString(R.string.panel_commit_intro)), top = 4)

        add(label(getString(R.string.panel_duration)), top = 18)
        val pauses = Commitment.pausesFor(draftDays)
        add(Brand.card(column(
            stepper(draftDays.toString(), resources.getQuantityString(R.plurals.days_word, draftDays) +
                " · " + resources.getQuantityString(R.plurals.pauses_n, pauses, pauses),
                onMinus = { draftDays = (draftDays - 1).coerceAtLeast(Commitment.MIN_DAYS); render() },
                onPlus = { draftDays = (draftDays + 1).coerceAtMost(365); render() }),
            space(10),
            chips(listOf(7, 14, 30, 60, 90).map { resources.getQuantityString(R.plurals.days_n, it, it) },
                listOf(7, 14, 30, 60, 90).indexOf(draftDays)) { i -> draftDays = listOf(7, 14, 30, 60, 90)[i]; render() }
        )))

        add(label(getString(R.string.pref_limit)), top = 18)
        val limits = listOf(0, 30, 45, 60, 90, 120, 180)
        val limit = Usage.limitMinutes(this) ?: 0
        add(Brand.card(chips(limits.map { if (it == 0) getString(R.string.widget_no_limit) else formatDuration(it * 60_000L) },
            limits.indexOf(limit)) { i -> change { putString("daily_limit", limits[i].toString()) } }))

        add(Brand.card(column(
            Brand.text(this, getString(R.string.panel_pauses), 15.5f, bold = true),
            space(4),
            muted(getString(R.string.panel_pauses_explain, pauses))
        )))

        val rules = commitmentRules()
        add(Brand.card(column(
            Brand.text(this, getString(R.string.panel_you_wont), 15.5f, bold = true),
            space(6),
            if (rules.isEmpty()) muted(getString(R.string.commit_needs))
            else muted(rules.joinToString("\n"))
        )))
        add(Brand.primaryButton(this, getString(R.string.commit_start)) { confirmCommitment(draftDays) }.apply {
            isEnabled = rules.isNotEmpty()
            alpha = if (rules.isNotEmpty()) 1f else 0.4f
        }, top = 20)

        Commitment.lastSummary(this)?.let { last ->
            add(Brand.card(linkRow(getString(R.string.pref_last), last.lines().first()) { showSummary(last, markSeen = false) }), top = 16)
        }
    }

    private fun commitmentActive() {
        header(getString(R.string.home_commit))
        val left = Commitment.daysLeft(this).toInt()
        val end = Date(Commitment.endMillis(this))
        val total = userPrefsCommitDays().coerceAtLeast(1)

        // Anillo grande con los días que faltan
        val ring = RingView(this)
        ring.post { ring.setProgress(1f - left.toFloat() / total, warning = false) }
        val center = column(
            Brand.text(this, left.toString(), 34f, bold = true).apply { gravity = Gravity.CENTER },
            muted(resources.getQuantityString(R.plurals.days_remaining, left)).apply { gravity = Gravity.CENTER }
        ).apply { gravity = Gravity.CENTER }
        val size = Brand.px(this, 128)
        val ringBox = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            addView(center, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }
        val protectedText = getString(if (Protection.adminActive(this)) R.string.home_protected else R.string.home_unprotected)
        add(Brand.card(column(
            ringBox,
            space(8),
            muted(getString(R.string.panel_until, DateFormat.getDateFormat(this).format(end), DateFormat.getTimeFormat(this).format(end)) +
                " · " + protectedText).apply { gravity = Gravity.CENTER }
        )))

        // Hoy y pausas
        val usage = formatDuration(Usage.todayMs(this))
        val limitText = Usage.limitMs(this)?.let { getString(R.string.widget_of_limit, formatDuration(it)) } ?: getString(R.string.widget_no_limit)
        val pausesLeft = Commitment.pausesLeft(this)
        add(LinearLayout(this).apply {
            addView(stat(getString(R.string.widget_title), usage, limitText), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(stat(getString(R.string.panel_pauses), pausesLeft.toString(), getString(R.string.panel_pauses_sub)),
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 12) })
        })

        when {
            Commitment.pauseActive(this) -> add(banner(getString(R.string.pause_running,
                DateFormat.getTimeFormat(this).format(Date(Commitment.pauseUntil(this))))))
            Commitment.canPause(this) -> add(Brand.primaryButton(this, getString(R.string.pref_pause)) {
                if (Commitment.startPause(this)) toast(getString(R.string.pause_15))
                changed()
            })
        }

        // Endurecer el límite (subirlo no se puede)
        add(label(getString(R.string.pref_limit)), top = 18)
        val limits = listOf(0, 30, 45, 60, 90, 120, 180)
        add(Brand.card(chips(limits.map { if (it == 0) getString(R.string.widget_no_limit) else formatDuration(it * 60_000L) },
            limits.indexOf(Usage.limitMinutes(this) ?: 0)) { i ->
            val v = limits[i].takeIf { it > 0 }
            change({ it.copy(limitMinutes = v) }) { putString("daily_limit", limits[i].toString()) }
        }))

        add(label(getString(R.string.panel_extras)), top = 18)
        add(Brand.card(column(
            if (Protection.adminActive(this)) infoRow(getString(R.string.pref_protect), getString(R.string.protect_on))
            else linkRow(getString(R.string.pref_protect), getString(R.string.protect_off)) { Protection.requestAdmin(this) },
            space(8),
            if (UsageAccess.granted(this)) infoRow(getString(R.string.pref_compare), getString(R.string.usage_access_on))
                .also { UsageAccess.baseline(this) } // calcularlo ya, antes de que Android borre los datos viejos
            else linkRow(getString(R.string.pref_compare), getString(R.string.usage_access_off)) { UsageAccess.open(this) }
        )))

        if (BuildConfig.DEBUG) add(Brand.secondaryButton(this, getString(R.string.pref_debug_end)) {
            Commitment.cancelForTesting(this)
            Sleep.reschedule(this)
            Widgets.update(this)
            Commitment.onEnd(this)?.let { showSummary(it, markSeen = true) }
            render()
        }, top = 20)
    }

    private fun userPrefsCommitDays() = getSharedPreferences("commitment", MODE_PRIVATE).getInt("days", Commitment.MIN_DAYS)

    private fun commitmentRules() = buildList {
        if (Sleep.enabled(this@PanelActivity)) {
            add(getString(R.string.rule_sleep))
            if (userPrefs().getBoolean("sleep_browsers", true)) add(getString(R.string.rule_browsers))
        }
        Usage.limitMinutes(this@PanelActivity)?.let { add(getString(R.string.rule_limit, formatDuration(it * 60_000L))) }
    }

    private fun confirmCommitment(days: Int) {
        val end = Date(System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L)
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.commit_confirm_title, days))
            .setMessage(getString(R.string.commit_confirm_msg, DateFormat.getDateFormat(this).format(end),
                commitmentRules().joinToString("\n"), Commitment.pausesFor(days)))
            .setPositiveButton(R.string.start) { _, _ ->
                Commitment.start(this, days)
                Sleep.reschedule(this)
                Widgets.update(this)
                toast(getString(R.string.commit_started, days))
                render()
                offerProtection()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        // 5 segundos para leerlo antes de poder confirmar
        val start = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        start.isEnabled = false
        for (left in 5 downTo 0) start.postDelayed({
            start.text = if (left > 0) getString(R.string.start_countdown, left) else getString(R.string.start)
            start.isEnabled = left == 0
        }, (5 - left) * 1000L)
    }

    /** Recién empezado el compromiso: ofrecer la protección y el permiso para la estadística final. */
    private fun offerProtection() {
        val protect = !Protection.adminActive(this)
        if (!protect && UsageAccess.granted(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.offer_title)
            .setMessage(R.string.offer_msg)
            .setPositiveButton(if (protect) R.string.offer_protect else R.string.offer_compare) { _, _ ->
                if (protect) Protection.requestAdmin(this) else UsageAccess.open(this)
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun showSummary(text: String, markSeen: Boolean) {
        if (markSeen) Commitment.markSummarySeen(this)
        AlertDialog.Builder(this)
            .setMessage(text)
            .setPositiveButton(R.string.start_another) { _, _ -> if (panel == COMMIT) render() else open(this, COMMIT) }
            .setNegativeButton(R.string.done, null)
            .apply { if (panel != STATS) setNeutralButton(R.string.summary_charts) { _, _ -> open(this@PanelActivity, STATS) } }
            .show()
    }

    // ---------- Instagram y YouTube ----------

    private fun appsPanel() {
        header(getString(R.string.panel_apps))
        add(label(getString(R.string.pref_feed)), top = 8)
        val feeds = listOf("normal", "following", "blocked")
        add(Brand.card(chips(resources.getStringArray(R.array.ig_feed_entries).toList(),
            feeds.indexOf(userPrefs().getString("ig_feed", "normal"))) { i -> change { putString("ig_feed", feeds[i]) } }))

        add(Brand.card(column(
            prefSwitch("ig_block_explore", true, R.string.pref_explore, R.string.pref_explore_summary),
            space(12),
            prefSwitch("ig_tap_to_play", false, R.string.pref_tap, R.string.pref_tap_summary),
            space(12),
            prefSwitch("ig_reels_in_limit", true, R.string.pref_reels, R.string.pref_reels_summary)
        )))

        add(label(getString(R.string.pref_cat_comfort)), top = 18)
        add(Brand.card(column(
            prefSwitch("smooth_transitions", false, R.string.pref_smooth, R.string.pref_smooth_summary),
            space(12),
            prefSwitch("swipe_reply", true, R.string.pref_swipe, R.string.pref_swipe_summary)
        )))
    }

    private fun prefSwitch(key: String, default: Boolean, title: Int, sub: Int) =
        switchRow(getString(title), getString(sub), userPrefs().getBoolean(key, default)) { on -> change { putBoolean(key, on) } }

    // ---------- Estadísticas ----------

    private fun statsPanel() {
        header(getString(R.string.stats_title))
        val history = Usage.history(this)
        if (history.isEmpty()) {
            add(Brand.card(muted(getString(R.string.stats_empty))), top = 8)
            return
        }
        val today = Usage.today(this)

        // Un solo filtro, arriba de todo lo que cambia con él
        val ranges = listOf(7, 30)
        add(chips(listOf(getString(R.string.stats_range_7), getString(R.string.stats_range_30)), ranges.indexOf(statsRange)) { i ->
            statsRange = ranges[i]
            render()
        }, top = 4)

        val window = StatsMath.window(history, today, statsRange)
        val avg = StatsMath.average(window)
        val before = UsageAccess.baseline(this)
        add(compareCard(history, today, window, avg, before))
        add(streakCard(history, today))

        add(label(getString(R.string.stats_by_day)), top = 18)
        add(Brand.card(dayChart(window, today, before)))
        add(statTiles(window, today, avg))
        splitCard(history, today, window)?.let { add(it) }
        add(Brand.card(prefSwitch("weekly_summary", true, R.string.weekly_pref, R.string.weekly_pref_sub)))

        Commitment.lastSummary(this)?.let { last ->
            add(Brand.card(linkRow(getString(R.string.pref_last), last.lines().first()) { showSummary(last, markSeen = false) }), top = 16)
        }
    }

    /** Antes de Lúcido contra el promedio de ahora: el número grande es el cambio. */
    private fun compareCard(history: Map<LocalDate, DayUsage>, today: LocalDate, window: List<DayUsage?>, avg: Long?, before: Long?): View {
        val days = StatsMath.completed(window).size
        val title = Brand.text(this, getString(R.string.stats_compare), 15.5f, bold = true)
        if (before == null) {
            val granted = UsageAccess.granted(this)
            val box = column(title, space(4), muted(getString(if (granted) R.string.stats_no_before else R.string.stats_need_access)))
            if (!granted) box.addView(Brand.secondaryButton(this, getString(R.string.stats_allow)) { UsageAccess.open(this) },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = Brand.px(this@PanelActivity, 12) })
            return Brand.card(box)
        }
        if (avg == null) return Brand.card(column(title, space(4),
            muted(getString(R.string.stats_before) + ": " + getString(R.string.stats_per_day, formatDuration(before))),
            muted(getString(R.string.stats_wait))))

        val change = StatsMath.change(before, avg)
        val sign = if (change < 0) "−" else if (change > 0) "+" else ""
        val box = column(
            title,
            space(8),
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Brand.text(context, "$sign${abs(change)}%", 40f, if (change <= 0) R.color.brand_primary else R.color.brand_warning, bold = true))
                addView(muted(getString(when {
                    change < 0 -> R.string.stats_less
                    change > 0 -> R.string.stats_more
                    else -> R.string.stats_same
                })), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 12) })
            },
            space(14),
            compareRow(getString(R.string.stats_before), null, before, maxOf(before, avg), strong = false),
            space(14),
            compareRow(getString(R.string.stats_now), resources.getQuantityString(R.plurals.stats_avg_days, days, days),
                avg, maxOf(before, avg), strong = true)
        )
        val extras = listOfNotNull(
            (before - avg).takeIf { it > 0 }?.let { getString(R.string.stats_week_less, rough(it * 7)) },
            StatsMath.recovered(history, today, before)?.let { getString(R.string.stats_recovered, rough(it)) }
        )
        if (extras.isNotEmpty()) {
            box.addView(space(14))
            box.addView(banner(extras.joinToString("\n")))
        }
        return Brand.card(box)
    }

    /** Días seguidos cumpliendo la meta (límite del compromiso o usar menos que antes) y los últimos 14. */
    private fun streakCard(history: Map<LocalDate, DayUsage>, today: LocalDate): View {
        val title = Brand.text(this, getString(R.string.streak_title), 15.5f, bold = true)
        val goal = StreakGoal.of(this) ?: return Brand.card(column(title, space(4), muted(getString(R.string.streak_none))))
        val (current, best) = StatsMath.streaks(history, today, goal.max, goal.from)
        return Brand.card(column(
            title,
            space(6),
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Brand.text(context, current.toString(), 32f, bold = true))
                addView(column(
                    Brand.text(context, resources.getQuantityString(R.plurals.streak_days, current), 15f, bold = true),
                    muted(getString(if (goal.isLimit) R.string.streak_limit else R.string.streak_before))
                ), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 12) })
            },
            space(12),
            StreakDots(this).apply { marks = StatsMath.goalMarks(history, today, goal.max, goal.from, 14) },
            space(8),
            LinearLayout(this).apply {
                addView(muted(getString(R.string.streak_last)), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(muted(getString(R.string.streak_best, resources.getQuantityString(R.plurals.days_n, best, best))))
            }
        ))
    }

    private fun compareRow(name: String, sub: String?, value: Long, max: Long, strong: Boolean) = column(
        LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(Brand.text(context, if (sub != null) "$name · $sub" else name, 13.5f, R.color.brand_text_muted), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Brand.text(context, getString(R.string.stats_per_day, formatDuration(value)), 14f, bold = true))
        },
        space(6),
        FillBar(this, strong).apply { post { setFraction(value.toFloat() / max) } }
    )

    /** Columnas por día; tocar una (o deslizar) muestra ese día arriba del gráfico. */
    private fun dayChart(window: List<DayUsage?>, today: LocalDate, before: Long?): View {
        val n = window.size
        val days = (0 until n).map { today.minusDays((n - 1 - it).toLong()) }
        val names = resources.getStringArray(R.array.weekday_entries)
        val callout = Brand.text(this, "", 16f, bold = true)
        fun show(i: Int) {
            val d = days[i]
            val day = if (i == n - 1) getString(R.string.stats_today) else "${names[d.dayOfWeek.value - 1]} ${d.dayOfMonth}"
            callout.text = "$day · ${formatDuration(window[i]?.total ?: 0)}"
        }
        val chart = DayBars(this).apply {
            values = window.map { it?.total }
            labels = days.mapIndexed { i, d ->
                when {
                    n <= 7 -> names[d.dayOfWeek.value - 1].take(1)
                    (n - 1 - i) % 7 == 0 -> d.dayOfMonth.toString()
                    else -> ""
                }
            }
            limit = Usage.limitMs(this@PanelActivity)
            if (limit != null) limitFrom = StatsMath.indexOf(Commitment.startDay(this@PanelActivity), today, n)
            this.before = before
            limitLabel = getString(R.string.stats_limit_line)
            beforeLabel = getString(R.string.stats_before_line)
            selected = n - 1
            onSelect = { show(it) }
            contentDescription = getString(R.string.stats_by_day)
            post { animateIn() }
        }
        show(n - 1)
        return column(callout, muted(getString(R.string.stats_hint)), space(14), chart)
    }

    /** Promedio, total, mejor día y días dentro del límite (o hoy, si no hay límite). */
    private fun statTiles(window: List<DayUsage?>, today: LocalDate, avg: Long?): View {
        val names = resources.getStringArray(R.array.weekday_entries)
        val completed = StatsMath.completed(window).size
        val best = StatsMath.best(window)
        val limit = Usage.limitMs(this)
        val tiles = listOf(
            stat(getString(R.string.stats_avg), avg?.let(::formatDuration) ?: "—",
                resources.getQuantityString(R.plurals.stats_finished_days, completed, completed)),
            stat(getString(R.string.stats_total), formatDuration(window.filterNotNull().sumOf { it.total }),
                getString(if (statsRange == 7) R.string.stats_range_7 else R.string.stats_range_30)),
            stat(getString(R.string.stats_best), best?.let { formatDuration(it.second) } ?: "—",
                best?.let { (i, _) -> today.minusDays((window.size - 1 - i).toLong()).let { "${names[it.dayOfWeek.value - 1]} ${it.dayOfMonth}" } } ?: ""),
            if (limit != null) StatsMath.within(window, limit, StatsMath.indexOf(Commitment.startDay(this), today, window.size)).let { (ok, of) ->
                stat(getString(R.string.stats_within), getString(R.string.stats_of, ok, of), getString(R.string.stats_days_sub))
            } else stat(getString(R.string.stats_today), formatDuration(window.last()?.total ?: 0),
                getString(R.string.home_resets, Sleep.fmt(this, Usage.nextReset(this).toLocalTime())))
        )
        val gap = Brand.px(this, 12)
        return column(*tiles.chunked(2).mapIndexed { r, pair ->
            LinearLayout(this).apply {
                if (r > 0) setPadding(0, gap, 0, 0)
                pair.forEachIndexed { i, t -> addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) marginStart = gap }) }
            }
        }.toTypedArray())
    }

    /** Cuánto fue Instagram y cuánto YouTube (desde que Lúcido los separa). */
    private fun splitCard(history: Map<LocalDate, DayUsage>, today: LocalDate, window: List<DayUsage?>): View? {
        if (window.filterNotNull().sumOf { it.total } == 0L) return null
        val (ig, yt) = StatsMath.split(window)
        val box = column(Brand.text(this, getString(R.string.panel_apps), 15.5f, bold = true))
        if (ig + yt == 0L) {
            box.addView(space(4))
            box.addView(muted(getString(R.string.stats_split_none)))
            return Brand.card(box)
        }
        val max = maxOf(ig, yt).toFloat()
        val (igPct, ytPct) = StatsMath.percents(ig, yt)
        fun appRow(icon: Int, name: String, ms: Long, pct: Int) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply { setImageResource(icon); contentDescription = name },
                LinearLayout.LayoutParams(Brand.px(context, 28), Brand.px(context, 28)))
            addView(column(
                LinearLayout(context).apply {
                    addView(Brand.text(context, name, 14f, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
                    addView(Brand.text(context, "${formatDuration(ms)} · $pct%", 13.5f, R.color.brand_text_muted))
                },
                space(6),
                FillBar(context, strong = true).apply { post { setFraction(ms / max) } }
            ), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 12) })
        }
        box.addView(space(12))
        box.addView(appRow(R.mipmap.ic_ig, "Instagram", ig, igPct))
        box.addView(space(12))
        box.addView(appRow(R.mipmap.ic_yt, "YouTube", yt, ytPct))

        // Si en la ventana hay días de antes de separarlos, decir desde cuándo cuenta
        val windowStart = today.minusDays(window.size - 1L)
        val firstSplit = history.filterValues { it.ig + it.yt > 0 }.keys.minOrNull()
        if (firstSplit != null && history.keys.any { it >= windowStart && it < firstSplit }) {
            val millis = firstSplit.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            box.addView(space(10))
            box.addView(muted(getString(R.string.stats_split_since,
                DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR))))
        }
        return Brand.card(box)
    }

    /** Duración aproximada para textos como "≈ 13 h menos por semana". */
    private fun rough(ms: Long) = "≈ " + if (ms >= 2 * 3_600_000L) "${(ms + 1_800_000) / 3_600_000} h"
        else formatDuration(((ms + 150_000) / 300_000).coerceAtLeast(1) * 300_000)

    // ---------- Cambios ----------

    /** Guarda un cambio, salvo que afloje un compromiso activo, y vuelve a armar la pantalla. */
    private fun change(strict: ((Strictness) -> Strictness)? = null, write: SharedPreferences.Editor.() -> Unit) {
        if (strict != null && Commitment.forbids(this, strict)) {
            toast(getString(R.string.loosen_forbidden))
            render()
            return
        }
        val e = userPrefs().edit()
        e.write()
        e.apply()
        changed()
    }

    private fun changed() {
        Sleep.reschedule(this)
        Widgets.update(this)
        render()
    }

    private fun pickTime(title: String, t: LocalTime, done: (LocalTime) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(LocalTime.of(h, m)) }, t.hour, t.minute, DateFormat.is24HourFormat(this))
            .apply { setTitle(title) }.show()
    }

    private fun range(a: LocalTime, b: LocalTime) = getString(R.string.night_range, Sleep.fmt(this, a), Sleep.fmt(this, b))

    private fun minutesChip(min: Int, zero: String) = if (min == 0) zero else formatDuration(min * 60_000L)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ---------- Piezas ----------

    private fun add(v: View, top: Int = 12) =
        list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = Brand.px(this@PanelActivity, top) })

    /** Flecha para volver, título y, opcional, el interruptor principal de la pantalla. */
    private fun header(title: String, switch: ToggleView? = null) {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Brand.px(context, 52)
            addView(Brand.text(context, "←", 24f).apply {
                contentDescription = getString(R.string.panel_back)
                val p = Brand.px(context, 8)
                setPadding(0, p, p * 2, p)
                background = Brand.pressable(context, GradientDrawable(), 999f)
                setOnClickListener { finish() }
            })
            addView(Brand.text(context, title, 24f, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
            switch?.let { addView(it) }
        }
        add(bar, top = 0)
    }

    private fun column(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
    }

    // Space y no View: una View común con alto WRAP ocupa todo el lugar que le ofrecen y empuja
    // afuera lo que viene después; Space mide exactamente su alto mínimo.
    private fun space(dp: Int) = Space(this).apply { minimumHeight = Brand.px(context, dp) }

    private fun muted(s: String) = Brand.text(this, s, 13.5f, R.color.brand_text_muted)

    private fun label(s: String) = Brand.text(this, s.uppercase(), 12f, R.color.brand_text_muted, bold = true).apply {
        letterSpacing = 0.04f
        setPadding(Brand.px(context, 4), 0, 0, 0)
    }

    private fun banner(s: String) = Brand.text(this, s, 13f).apply {
        background = Brand.rounded(context, R.color.brand_tonal, 14f)
        val p = Brand.px(context, 12)
        setPadding(p, Brand.px(context, 9), p, Brand.px(context, 9))
    }

    /** Interruptor dibujado con Canvas (ToggleView): se ve seguro en cualquier teléfono. */
    private fun toggle(checked: Boolean, onChange: (Boolean) -> Unit) = ToggleView(this).apply {
        this.checked = checked
        this.onChange = { on -> post { onChange(on) } }
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = toggle(checked, onChange)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(column(Brand.text(context, title, 15.5f, bold = true), muted(sub)), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(sw, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(context, 12) })
            setOnClickListener { sw.performClick() }
        }
    }

    private fun linkRow(title: String, sub: String, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Brand.px(context, 48)
        addView(column(Brand.text(context, title, 15f, bold = true), muted(sub)), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(Brand.text(context, "›", 22f, R.color.brand_text_muted))
        background = Brand.pressable(context, GradientDrawable(), 12f)
        setOnClickListener { onClick() }
    }

    private fun infoRow(title: String, sub: String) =
        column(Brand.text(this, title, 15f, bold = true), muted(sub)).apply { minimumHeight = Brand.px(context, 44) }

    /** Dos horas grandes lado a lado (dormir/despertar, empieza/termina); tocarlas las cambia. */
    private fun timePair(l1: String, t1: LocalTime, l2: String, t2: LocalTime, onClick: () -> Unit) = LinearLayout(this).apply {
        fun box(l: String, t: LocalTime) = column(
            Brand.text(context, l, 12.5f, R.color.brand_text_muted),
            Brand.text(context, Sleep.fmt(context, t), 28f, bold = true)
        ).apply {
            background = Brand.pressable(context, Brand.rounded(context, R.color.brand_tonal, 16f), 16f)
            val p = Brand.px(context, 12)
            setPadding(p, Brand.px(context, 10), p, Brand.px(context, 10))
            setOnClickListener { onClick() }
        }
        addView(box(l1, t1), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(box(l2, t2), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(context, 10) })
    }

    /** Opciones como pastillas; la elegida, llena. Pasan a la línea siguiente si no entran. */
    private fun chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) = FlowLayout(this).apply {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            addView(Brand.text(context, l, 13.5f, if (on) R.color.brand_on_primary else R.color.brand_text, bold = true).apply {
                val base = if (on) Brand.rounded(context, R.color.brand_primary, 999f)
                    else Brand.rounded(context, R.color.brand_surface, 999f, stroke = R.color.brand_line)
                background = Brand.pressable(context, base, 999f)
                setPadding(Brand.px(context, 14), Brand.px(context, 8), Brand.px(context, 14), Brand.px(context, 8))
                isSelected = on
                setOnClickListener { if (!on) onSelect(i) }
            })
        }
    }

    /** Los siete días como círculos (L M M J V S D): tocar uno lo prende o apaga. */
    private fun dayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) = LinearLayout(this).apply {
        val initials = resources.getStringArray(R.array.weekday_entries)
        for (d in DayOfWeek.values()) {
            val on = d in selected
            val size = Brand.px(context, 38)
            val dot = Brand.text(context, initials[d.value - 1].take(1), 13.5f, if (on) R.color.brand_on_primary else R.color.brand_text, bold = true).apply {
                gravity = Gravity.CENTER
                contentDescription = initials[d.value - 1]
                val base = if (on) Brand.rounded(context, R.color.brand_primary, 999f)
                    else Brand.rounded(context, R.color.brand_surface, 999f, stroke = R.color.brand_line)
                background = Brand.pressable(context, base, 999f)
                setOnClickListener { onToggle(d) }
            }
            addView(FrameLayout(context).apply { addView(dot, FrameLayout.LayoutParams(size, size, Gravity.CENTER)) },
                LinearLayout.LayoutParams(0, size, 1f))
        }
    }

    private fun stepper(value: String, sub: String, onMinus: () -> Unit, onPlus: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        fun round(s: String, f: () -> Unit) = Brand.text(context, s, 22f, R.color.brand_primary).apply {
            gravity = Gravity.CENTER
            background = Brand.pressable(context, Brand.rounded(context, R.color.brand_surface, 999f, stroke = R.color.brand_line), 999f)
            setOnClickListener { f() }
        }
        val size = Brand.px(context, 44)
        addView(round("−", onMinus), LinearLayout.LayoutParams(size, size))
        addView(column(
            Brand.text(context, value, 28f, bold = true).apply { gravity = Gravity.CENTER },
            muted(sub).apply { gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END; maxLines = 1 }
        ), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(round("+", onPlus), LinearLayout.LayoutParams(size, size))
    }

    private fun stat(title: String, value: String, sub: String) = column(
        Brand.text(this, title, 12.5f, R.color.brand_text_muted),
        Brand.text(this, value, 20f, bold = true),
        Brand.text(this, sub, 12.5f, R.color.brand_text_muted)
    ).apply {
        background = Brand.rounded(context, R.color.brand_tonal, 16f)
        val p = Brand.px(context, 12)
        setPadding(p, Brand.px(context, 10), p, Brand.px(context, 10))
    }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Fila que pasa a la línea siguiente cuando no entra (para las pastillas). */
class FlowLayout(c: Context) : ViewGroup(c) {
    private val gap = Brand.px(c, 8)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val max = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            measureChild(v, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + v.measuredWidth > max) { x = 0; y += rowH + gap; rowH = 0 }
            x += v.measuredWidth + gap
            rowH = maxOf(rowH, v.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val max = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (x > 0 && x + v.measuredWidth > max) { x = 0; y += rowH + gap; rowH = 0 }
            v.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + v.measuredWidth, paddingTop + y + v.measuredHeight)
            x += v.measuredWidth + gap
            rowH = maxOf(rowH, v.measuredHeight)
        }
    }
}
