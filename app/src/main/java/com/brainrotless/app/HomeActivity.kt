package com.brainrotless.app

import android.app.AlertDialog
import android.content.ComponentName
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
import java.util.Date

/**
 * Pantalla principal de Lúcido (el ícono de la app): abrir Instagram o YouTube, cómo va el día,
 * modo dormir, compromiso y lo que falte configurar. Se vuelve a armar al volver a la pantalla.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var list: LinearLayout
    private var firstShow = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        render()
        Commitment.onEnd(this)?.let { text ->
            Commitment.markSummarySeen(this)
            AlertDialog.Builder(this)
                .setMessage(text)
                .setPositiveButton("Empezar otro") { _, _ -> openSettings(start = true) }
                .setNegativeButton("Ahora no", null)
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
        add(commitmentCard())
        // Ajustes al pie, empujado hacia abajo si sobra lugar
        list.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        add(Brand.secondaryButton(this, "Ajustes") { openSettings() }, top = 20)

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
            Brand.text(context, "Instagram y YouTube, sin piloto automático", 13.5f, R.color.brand_text_muted)
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Brand.px(context, 14)
        })
    }

    /** Lo que falta para que todo funcione; desaparece cuando está listo. */
    private fun setupCard(): View? {
        val notif = Setup.listenerOn(this)
        val guard = Setup.guardOn(this)
        if (notif && guard) return null
        val box = column(Brand.text(this, "Terminá de configurar", 16f, bold = true))
        box.background = Brand.rounded(this, R.color.brand_tonal, 20f)
        val pad = Brand.px(this, 16)
        box.setPadding(pad, pad, pad, Brand.px(this, 8))
        fun step(done: Boolean, label: String, action: () -> Unit) = box.addView(row(
            Brand.text(this, if (done) "✓" else "○", 16f, if (done) R.color.brand_primary else R.color.brand_text_muted, bold = true),
            Brand.text(this, label, 15f),
            onClick = if (done) null else action
        ))
        step(notif, "Notificaciones: que abran acá") { Setup.openNotifications(this) }
        step(guard, "Bloquear apps oficiales (Accesibilidad)") { Setup.openAccessibility(this) }
        if (Setup.restrictedApplies()) step(false, "¿Un permiso aparece gris?") { Setup.explainRestricted(this) }
        return box
    }

    private fun appsRow() = LinearLayout(this).apply {
        val ig = Lock.ig(context)
        val yt = Lock.yt(context)
        val reels = Lock.reelsAllowed(context)
        val igSub = when {
            ig != null -> "Solo mensajes por ahora"
            reels -> "Reels hasta tu límite"
            context.userPrefs().getString("ig_feed", "normal") == "following" -> "Mensajes y cuentas que seguís"
            context.userPrefs().getString("ig_feed", "normal") == "blocked" -> "Mensajes y perfiles"
            else -> "Sin Reels infinitos"
        }
        val ytSub = when {
            yt != null -> "Bloqueado hasta las ${Lock.until(context, yt)}"
            reels -> "Shorts hasta tu límite"
            else -> "Videos, sin Shorts infinitos"
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
            Commitment.pauseActive(this) -> "Pausa de emergencia hasta las " +
                DateFormat.getTimeFormat(this).format(Date(Commitment.pauseUntil(this)))
            over -> "Llegaste a tu límite · vuelve a las $reset"
            else -> "Se reinicia a las $reset"
        }
        val amount = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(Brand.text(context, formatDuration(used), 22f, bold = true))
            limit?.let { addView(Brand.text(context, "  de ${formatDuration(it)}", 13f, R.color.brand_text_muted).apply { setPadding(0, 0, 0, Brand.px(context, 3)) }) }
        }
        return Brand.card(row(
            ring,
            column(
                Brand.text(this, "Hoy en Instagram y YouTube", 13f, R.color.brand_text_muted),
                amount,
                Brand.text(this, detail, 13f, if (over) R.color.brand_warning else R.color.brand_text_muted)
            ),
            iconSize = 68
        ))
    }

    private fun sleepCard(): View {
        val sub = when {
            !Sleep.enabled(this) -> "Apagado · Tocá para elegir tu horario"
            Sleep.current(this) != null -> "Activo hasta las ${Sleep.fmt(this, Sleep.current(this)!!.end.toLocalTime())}"
            else -> Sleep.nextNight(this)?.let { n ->
                "Próxima noche, de ${Sleep.fmt(this, n.start.toLocalTime())} a ${Sleep.fmt(this, n.end.toLocalTime())}"
            } ?: "Sin horario"
        }
        return linkCard(R.drawable.ic_moon, "Modo dormir", sub, pill = null) { openSettings(focus = "sleep_on") }
    }

    private fun commitmentCard(): View {
        if (!Commitment.active(this))
            return linkCard(R.drawable.ic_flag, "Compromiso", "Proponete al menos ${Commitment.MIN_DAYS} días sin aflojar", pill = null) {
                openSettings(focus = "commit")
            }
        val left = Commitment.daysLeft(this)
        val pauses = Commitment.pausesLeft(this)
        return linkCard(
            R.drawable.ic_flag, "Compromiso",
            "$pauses ${if (pauses == 1) "pausa" else "pausas"} de emergencia · ${if (Protection.adminActive(this)) "protegido" else "sin protección"}",
            pill = "$left ${if (left == 1L) "día" else "días"}"
        ) { openSettings(focus = "commit") }
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

    private fun openSettings(focus: String? = null, start: Boolean = false) {
        startActivity(Intent(this, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_FOCUS, focus)
            .putExtra(SettingsActivity.EXTRA_START_COMMITMENT, start))
    }

    private companion object { const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT }
}
