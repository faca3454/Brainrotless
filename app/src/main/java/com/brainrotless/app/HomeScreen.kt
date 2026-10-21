package com.brainrotless.app

import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import java.time.LocalDateTime
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
        val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        val showMessages: Boolean = false,
        val igLocked: Boolean = false,
        val reelsAllowed: Boolean = false,
        val igFeed: String = "normal",
        val ytLockedUntil: String? = null,
        val usedMs: Long = 0,
        val limitMs: Long? = null,
        val resetAt: LocalTime = LocalTime.of(4, 0),
        val pauseUntil: Long? = null,
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

    /** La columna de la pantalla (va dentro de un ScrollView). */
    fun build(c: Context, s: State, a: Actions): LinearLayout {
        val pad = Brand.px(c, 16)
        val list = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, Brand.px(c, 8), pad, Brand.px(c, 24))
        }
        fun add(v: View, top: Int = 12) =
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = Brand.px(c, top) })

        add(header(c, a), top = 0)
        add(today(c, s, a), top = 4)
        add(appsRow(c, s, a), top = 20)
        if (s.showMessages) add(messagesCard(c, a))
        add(Brand.group(c, listOf(sleepRow(c, s, a), focusRow(c, s, a), commitmentRow(c, s, a),
            Brand.row(c, Brand.badge(c, R.drawable.ic_settings, R.color.brand_surface_alt, R.color.brand_text_muted),
                c.getString(R.string.panel_apps), c.getString(R.string.panel_apps_summary), onClick = a.apps))))
        return list
    }

    // ---------- Piezas ----------

    /** Logo, nombre y Ajustes (arriba a la derecha, como en cualquier app). */
    private fun header(c: Context, a: Actions) = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Brand.px(c, 64)
        addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_logo)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(Brand.px(c, 32), Brand.px(c, 32)))
        addView(Brand.text(c, c.getString(R.string.app_name), Brand.Type.HEADLINE, bold = true).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = Brand.px(c, 12) })
        addView(Brand.iconButton(c, R.drawable.ic_settings, c.getString(R.string.settings), R.color.brand_text_muted, a.settings))
    }

    /**
     * El día en el dial: la noche y el foco que vienen, el sol en la hora de ahora y, adentro,
     * lo usado hoy. Abajo, cuándo se reinicia (o hasta cuándo dura la pausa) y la racha. Tocarlo
     * lleva a las estadísticas.
     */
    private fun today(c: Context, s: State, a: Actions): View {
        val limit = s.limitMs
        val over = limit != null && s.usedMs >= limit
        val reset = Sleep.fmt(c, s.resetAt)
        val detail = when {
            s.pauseUntil != null -> c.getString(R.string.home_pause_until, DateFormat.getTimeFormat(c).format(Date(s.pauseUntil)))
            over -> c.getString(R.string.home_over, reset)
            else -> c.getString(R.string.home_resets, reset)
        }
        fun hours(t: LocalDateTime) = t.hour + t.minute / 60f
        val night = (s.sleepNow ?: s.sleepNext).takeIf { s.sleepOn }
        // El foco, solo si cae hoy (el de otro día confundiría el dial)
        val focus = (s.focusNow ?: s.focusNext).takeIf { s.focusOn && it?.start?.toLocalDate() == s.now.toLocalDate() }
        val dial = DayDial(c).apply {
            this.night = night?.let { hours(it.start) to hours(it.end) }
            this.focus = focus?.let { hours(it.start) to hours(it.end) }
            now = hours(s.now)
            usage = limit?.let { s.usedMs.toFloat() / it }
            this.over = over
            animateIn()
        }
        // Lo de adentro entra en el anillo de uso: el número se achica si hace falta (letra grande)
        val amount = Brand.number(c, formatDuration(s.usedMs)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 16, Brand.Type.DISPLAY.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
        }
        fun small(text: String) = Brand.text(c, text, Brand.Type.BODY, R.color.brand_text_muted).apply { gravity = Gravity.CENTER; maxLines = 2 }
        val size = Brand.px(c, 288)
        val center = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(small(c.getString(R.string.widget_title)), LinearLayout.LayoutParams(MATCH, WRAP))
            addView(amount, LinearLayout.LayoutParams(MATCH, Brand.px(c, 44)))
            limit?.let { addView(small(c.getString(R.string.home_of, formatDuration(it)).trim()), LinearLayout.LayoutParams(MATCH, WRAP)) }
        }
        val face = FrameLayout(c).apply {
            addView(dial, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            addView(center, FrameLayout.LayoutParams(size * 9 / 20, WRAP, Gravity.CENTER))
        }
        // Qué es cada arco, con el mismo ícono que su fila (no solo por el color)
        val legend = FlowLayout(c).apply {
            fun item(icon: Int, color: Int, label: String) = addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(c).apply { setImageResource(icon); setColorFilter(Brand.color(c, color)) },
                    LinearLayout.LayoutParams(Brand.px(c, 16), Brand.px(c, 16)))
                addView(Brand.text(c, label, Brand.Type.BODY, R.color.brand_text_muted).apply { setPadding(Brand.px(c, 6), 0, Brand.px(c, 4), 0) })
            })
            night?.let { item(R.drawable.ic_moon, R.color.brand_night, c.getString(R.string.night_range, Sleep.fmt(c, it.start.toLocalTime()), Sleep.fmt(c, it.end.toLocalTime()))) }
            focus?.let { item(R.drawable.ic_focus, R.color.brand_primary, c.getString(R.string.night_range, Sleep.fmt(c, it.start.toLocalTime()), Sleep.fmt(c, it.end.toLocalTime()))) }
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(face, LinearLayout.LayoutParams(MATCH, size))
            if (legend.childCount > 0) addView(legend, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = Brand.px(c, 4) })
            addView(Brand.text(c, detail, Brand.Type.BODY, if (over) R.color.brand_warning else R.color.brand_text_muted).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = Brand.px(c, 8) })
            // Racha de 2 días o más: una pastilla para festejarla
            if (s.streak >= 2) addView(Brand.pill(c, c.resources.getQuantityString(R.plurals.streak_pill, s.streak, s.streak)),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = Brand.px(c, 8) })
            background = Brand.pressable(c, android.graphics.drawable.ColorDrawable(0), 24f)
            setPadding(0, 0, 0, Brand.px(c, 8))
            contentDescription = listOfNotNull(c.getString(R.string.home_today), amount.text,
                limit?.let { c.getString(R.string.home_of, formatDuration(it)).trim() }, detail, c.getString(R.string.stats_open)).joinToString(". ")
            setOnClickListener { a.stats() }
        }
    }

    /** Sugerencia de que los mensajes abran acá; desaparece al activarlo o con "Ahora no". */
    private fun messagesCard(c: Context, a: Actions): View {
        val actions = LinearLayout(c).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, Brand.px(c, 12), Brand.px(c, 4))
            addView(Brand.textButton(c, c.getString(R.string.not_now), a.messagesHide))
            addView(Brand.primaryButton(c, c.getString(R.string.turn_on), a.messagesOn),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = Brand.px(c, 8) })
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = Brand.rounded(c, R.color.brand_surface, 24f)
            setPadding(0, Brand.px(c, 4), 0, Brand.px(c, 8))
            addView(Brand.row(c, Brand.badge(c, R.drawable.ic_message), c.getString(R.string.messages_title), c.getString(R.string.messages_sub)))
            addView(actions, LinearLayout.LayoutParams(MATCH, WRAP))
        }
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
            val size = Brand.px(c, 40)
            addView(ImageView(c).apply { setImageResource(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(size, size))
            addView(Brand.text(c, name, Brand.Type.TITLE, bold = true).apply { setPadding(0, Brand.px(c, 10), 0, Brand.px(c, 2)) })
            addView(Brand.text(c, sub, Brand.Type.LABEL + 1, if (warning) R.color.brand_warning else R.color.brand_text_muted))
        }, onClick
    )

    private fun sleepRow(c: Context, s: State, a: Actions): View {
        val needsGuard = s.sleepOn && !s.guardOn
        val sub = when {
            !s.sleepOn -> c.getString(R.string.home_sleep_off)
            needsGuard -> c.getString(R.string.home_needs_guard)
            s.sleepNow != null -> c.getString(R.string.home_sleep_now, Sleep.fmt(c, s.sleepNow.end.toLocalTime()))
            else -> s.sleepNext?.let { n ->
                c.getString(R.string.home_sleep_next, Sleep.fmt(c, n.start.toLocalTime()), Sleep.fmt(c, n.end.toLocalTime()))
            } ?: c.getString(R.string.home_sleep_none)
        }
        return Brand.row(c, modeBadge(c, R.drawable.ic_moon, R.color.brand_night, s.sleepOn), c.getString(R.string.home_sleep), sub,
            warn = needsGuard, onClick = a.sleep)
    }

    private fun focusRow(c: Context, s: State, a: Actions): View {
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
        return Brand.row(c, modeBadge(c, R.drawable.ic_focus, R.color.brand_primary, s.focusOn), c.getString(R.string.home_focus), sub,
            warn = needsGuard, onClick = a.focus)
    }

    private fun commitmentRow(c: Context, s: State, a: Actions): View {
        if (!s.commitActive)
            return Brand.row(c, modeBadge(c, R.drawable.ic_flag, R.color.brand_accent, false), c.getString(R.string.home_commit),
                c.getString(R.string.home_commit_idle, Commitment.MIN_DAYS), onClick = a.commit)
        val needsGuard = !s.guardOn
        return Brand.row(c, modeBadge(c, R.drawable.ic_flag, R.color.brand_accent, true), c.getString(R.string.home_commit),
            if (needsGuard) c.getString(R.string.home_needs_guard) else c.resources.getQuantityString(R.plurals.pauses_n, s.pausesLeft, s.pausesLeft),
            warn = needsGuard, pill = c.resources.getQuantityString(R.plurals.days_n, s.daysLeft, s.daysLeft), onClick = a.commit)
    }

    /** El ícono de cada modo: lleno con su color si está prendido, suave si no. */
    private fun modeBadge(c: Context, icon: Int, color: Int, on: Boolean) =
        if (on) Brand.badge(c, icon, color, if (color == R.color.brand_accent) R.color.brand_on_accent else R.color.brand_bg)
        else Brand.badge(c, icon, R.color.brand_surface_alt, R.color.brand_text_muted)

    // ---------- Ayudas de armado ----------

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
