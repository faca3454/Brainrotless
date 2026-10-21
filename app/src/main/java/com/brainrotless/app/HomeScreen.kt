package com.brainrotless.app

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Date

/**
 * Cómo se dibuja el inicio, separado de dónde salen los datos (HomeActivity los junta): así se
 * puede dibujar con datos de prueba, sin preferencias ni reloj (pruebas de Paparazzi).
 */
object HomeScreen {
    /** Lo que muestra el inicio, ya calculado. */
    class State(
        val showMessages: Boolean = false,
        val igLocked: Boolean = false,
        val reelsAllowed: Boolean = false,
        val igFeed: String = "normal",
        val ytLockedUntil: String? = null,
        val usedMs: Long = 0,
        val limitMs: Long? = null,
        val resetAt: LocalTime = LocalTime.of(4, 0),
        val pauseUntil: Long? = null,
        val week: List<Long?> = emptyList(),
        val limitFrom: Int = 0,
        val streak: Int = 0,
        val guardOn: Boolean = true,
        val sleepOn: Boolean = false,
        val sleepNow: Night? = null,
        val sleepNext: Night? = null,
        val focusOn: Boolean = false,
        val focusNow: Block? = null,
        val focusNext: Block? = null,
        val commitActive: Boolean = false,
        val daysLeft: Int = 0,
        val pausesLeft: Int = 0,
    )

    /** Qué hace cada toque. */
    class Actions(
        val openApp: (alias: String) -> Unit = {},
        val messagesOn: () -> Unit = {},
        val messagesHide: () -> Unit = {},
        val stats: () -> Unit = {},
        val sleep: () -> Unit = {},
        val focus: () -> Unit = {},
        val commit: () -> Unit = {},
        val apps: () -> Unit = {},
        val settings: () -> Unit = {},
    )

    /** La columna con todas las tarjetas (va dentro de un ScrollView). */
    fun build(c: Context, s: State, a: Actions): LinearLayout {
        val pad = Brand.px(c, 20)
        val list = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun add(v: View, top: Int = 12) =
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = Brand.px(c, top) })

        add(header(c), top = 0)
        add(appsRow(c, s, a))
        if (s.showMessages) add(messagesCard(c, a))
        add(todayCard(c, s, a))
        add(sleepCard(c, s, a))
        add(focusCard(c, s, a))
        add(commitmentCard(c, s, a))
        add(linkCard(c, R.drawable.ic_settings, c.getString(R.string.panel_apps), c.getString(R.string.panel_apps_summary), pill = null, onClick = a.apps))
        // Ajustes al pie, empujado hacia abajo si sobra lugar
        list.addView(View(c), LinearLayout.LayoutParams(MATCH, 0, 1f))
        add(Brand.secondaryButton(c, c.getString(R.string.settings), a.settings), top = 20)
        return list
    }

    // ---------- Piezas ----------

    private fun header(c: Context) = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_logo)
            contentDescription = c.getString(R.string.app_name)
        }, LinearLayout.LayoutParams(Brand.px(c, 48), Brand.px(c, 48)))
        addView(column(c,
            Brand.text(c, c.getString(R.string.app_name), 26f, bold = true),
            Brand.text(c, c.getString(R.string.tagline), 13.5f, R.color.brand_text_muted)
        ), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(c, 14) })
    }

    /** Sugerencia de que los mensajes abran acá; desaparece al activarlo o con "Ahora no". */
    private fun messagesCard(c: Context, a: Actions): View {
        val badge = ImageView(c).apply {
            setImageResource(R.drawable.ic_message)
            setColorFilter(Brand.color(c, R.color.brand_primary))
            background = Brand.rounded(c, R.color.brand_tonal, 999f)
            val p = Brand.px(c, 10)
            setPadding(p, p, p, p)
        }
        val actions = LinearLayout(c).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(Brand.textButton(c, c.getString(R.string.not_now), a.messagesHide))
            addView(Brand.secondaryButton(c, c.getString(R.string.turn_on), a.messagesOn),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(c, 8) })
        }
        return Brand.card(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(row(c, badge, column(c,
                Brand.text(c, c.getString(R.string.messages_title), 16f, bold = true),
                Brand.text(c, c.getString(R.string.messages_sub), 13f, R.color.brand_text_muted)
            )), LinearLayout.LayoutParams(MATCH, WRAP))
            addView(actions, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = Brand.px(c, 8) })
        })
    }

    private fun appsRow(c: Context, s: State, a: Actions) = LinearLayout(c).apply {
        val igSub = when {
            s.igLocked -> c.getString(R.string.ig_sub_restricted)
            s.reelsAllowed -> c.getString(R.string.ig_sub_reels)
            s.igFeed == "following" -> c.getString(R.string.ig_sub_following)
            s.igFeed == "blocked" -> c.getString(R.string.ig_sub_blocked)
            else -> c.getString(R.string.ig_sub_normal)
        }
        val ytSub = when {
            s.ytLockedUntil != null -> c.getString(R.string.yt_sub_blocked, s.ytLockedUntil)
            s.reelsAllowed -> c.getString(R.string.yt_sub_shorts)
            else -> c.getString(R.string.yt_sub_normal)
        }
        addView(appTile(c, R.mipmap.ic_ig, "Instagram", igSub) { a.openApp("Instagram") }, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(appTile(c, R.mipmap.ic_yt, "YouTube", ytSub, warning = s.ytLockedUntil != null) { a.openApp("YouTube") },
            LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = Brand.px(c, 12) })
    }

    private fun appTile(c: Context, icon: Int, name: String, sub: String, warning: Boolean = false, onClick: () -> Unit) = Brand.card(
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val size = Brand.px(c, 48)
            addView(ImageView(c).apply { setImageResource(icon); contentDescription = name }, LinearLayout.LayoutParams(size, size))
            addView(Brand.text(c, name, 16f, bold = true).apply { setPadding(0, Brand.px(c, 10), 0, Brand.px(c, 2)) })
            addView(Brand.text(c, sub, 12.5f, if (warning) R.color.brand_warning else R.color.brand_text_muted))
        }, onClick
    )

    private fun todayCard(c: Context, s: State, a: Actions): View {
        val limit = s.limitMs
        val over = limit != null && s.usedMs >= limit
        val ring = RingView(c)
        ring.setProgress(limit?.let { s.usedMs.toFloat() / it }, warning = over)
        val reset = Sleep.fmt(c, s.resetAt)
        val detail = when {
            s.pauseUntil != null -> c.getString(R.string.home_pause_until, DateFormat.getTimeFormat(c).format(Date(s.pauseUntil)))
            over -> c.getString(R.string.home_over, reset)
            else -> c.getString(R.string.home_resets, reset)
        }
        val amount = LinearLayout(c).apply {
            gravity = Gravity.BOTTOM
            addView(Brand.text(c, formatDuration(s.usedMs), 22f, bold = true))
            limit?.let { addView(Brand.text(c, c.getString(R.string.home_of, formatDuration(it)), 13f, R.color.brand_text_muted).apply { setPadding(0, 0, 0, Brand.px(c, 3)) }) }
        }
        // Mini gráfico de la semana: avisa que tocando se ven las estadísticas
        val spark = DayBars(c, mini = true).apply {
            values = s.week
            this.limit = limit
            if (limit != null) limitFrom = s.limitFrom
            selected = s.week.size - 1
            contentDescription = c.getString(R.string.stats_open)
        }
        val body = column(c,
            Brand.text(c, c.getString(R.string.home_today), 13f, R.color.brand_text_muted),
            amount,
            Brand.text(c, detail, 13f, if (over) R.color.brand_warning else R.color.brand_text_muted)
        )
        // Racha de 2 días o más: una pastilla para festejarla
        if (s.streak >= 2) body.addView(Brand.pill(c, c.resources.getQuantityString(R.plurals.streak_pill, s.streak, s.streak)),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = Brand.px(c, 6) })
        return Brand.card(row(c, ring, body, chevron = true, iconSize = 68).apply {
            // Con un solo día no hay forma que mostrar
            if (s.week.count { it != null } >= 2)
                addView(spark, 2, LinearLayout.LayoutParams(Brand.px(c, 52), WRAP)
                    .apply { marginStart = Brand.px(c, 8); marginEnd = Brand.px(c, 4) })
        }, a.stats)
    }

    private fun sleepCard(c: Context, s: State, a: Actions): View {
        val needsGuard = s.sleepOn && !s.guardOn
        val sub = when {
            !s.sleepOn -> c.getString(R.string.home_sleep_off)
            needsGuard -> c.getString(R.string.home_needs_guard)
            s.sleepNow != null -> c.getString(R.string.home_sleep_now, Sleep.fmt(c, s.sleepNow.end.toLocalTime()))
            else -> s.sleepNext?.let { n ->
                c.getString(R.string.home_sleep_next, Sleep.fmt(c, n.start.toLocalTime()), Sleep.fmt(c, n.end.toLocalTime()))
            } ?: c.getString(R.string.home_sleep_none)
        }
        return linkCard(c, R.drawable.ic_moon, c.getString(R.string.home_sleep), sub, pill = null, warn = needsGuard, onClick = a.sleep)
    }

    private fun focusCard(c: Context, s: State, a: Actions): View {
        val needsGuard = s.focusOn && !s.guardOn
        val sub = when {
            !s.focusOn -> c.getString(R.string.home_focus_off)
            needsGuard -> c.getString(R.string.home_needs_guard)
            s.focusNow != null -> c.getString(R.string.home_sleep_now, Sleep.fmt(c, s.focusNow.end.toLocalTime()))
            else -> s.focusNext?.let { b ->
                // El idioma de la pantalla (Lang.wrap lo pone en la configuración)
                val day = b.start.dayOfWeek.getDisplayName(TextStyle.FULL, c.resources.configuration.locales[0])
                c.getString(R.string.home_focus_next, day, Sleep.fmt(c, b.start.toLocalTime()), Sleep.fmt(c, b.end.toLocalTime()))
            } ?: c.getString(R.string.focus_no_days)
        }
        return linkCard(c, R.drawable.ic_focus, c.getString(R.string.home_focus), sub, pill = null, warn = needsGuard, onClick = a.focus)
    }

    private fun commitmentCard(c: Context, s: State, a: Actions): View {
        if (!s.commitActive)
            return linkCard(c, R.drawable.ic_flag, c.getString(R.string.home_commit),
                c.getString(R.string.home_commit_idle, Commitment.MIN_DAYS), pill = null, onClick = a.commit)
        val needsGuard = !s.guardOn
        return linkCard(c,
            R.drawable.ic_flag, c.getString(R.string.home_commit),
            if (needsGuard) c.getString(R.string.home_needs_guard) else c.resources.getQuantityString(R.plurals.pauses_n, s.pausesLeft, s.pausesLeft),
            pill = c.resources.getQuantityString(R.plurals.days_n, s.daysLeft, s.daysLeft),
            warn = needsGuard, onClick = a.commit
        )
    }

    /** Tarjeta con ícono redondo, título, detalle (en color de alerta con [warn]) y flecha, que lleva a Ajustes. */
    private fun linkCard(c: Context, icon: Int, title: String, sub: String, pill: String?, warn: Boolean = false, onClick: () -> Unit): View {
        val badge = ImageView(c).apply {
            setImageResource(icon)
            setColorFilter(Brand.color(c, R.color.brand_primary))
            background = Brand.rounded(c, R.color.brand_tonal, 999f)
            val p = Brand.px(c, 10)
            setPadding(p, p, p, p)
        }
        val titleRow = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Brand.text(c, title, 16f, bold = true))
            pill?.let {
                addView(Brand.pill(c, it), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(c, 8) })
            }
        }
        val detail = Brand.text(c, sub, 13f, if (warn) R.color.brand_warning else R.color.brand_text_muted)
        return Brand.card(row(c, badge, column(c, titleRow, detail), chevron = true), onClick)
    }

    // ---------- Ayudas de armado ----------

    private fun column(c: Context, vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

    /** Fila: ícono a la izquierda, contenido que ocupa el resto y, opcional, una flecha. */
    private fun row(c: Context, icon: View, body: View, chevron: Boolean = false, iconSize: Int = 40) =
        LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Brand.px(c, 44)
            val size = if (icon is ImageView || icon is RingView) Brand.px(c, iconSize) else WRAP
            addView(icon, LinearLayout.LayoutParams(size, size))
            addView(body, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(c, 14) })
            if (chevron) addView(Brand.text(c, "›", 22f, R.color.brand_text_muted))
        }

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
