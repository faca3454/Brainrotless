package com.brainrotless.app.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.brainrotless.app.Brand
import com.brainrotless.app.R
import com.brainrotless.app.Widgets
import org.junit.Test

/** Los tres widgets sobre un fondo de pantalla de inicio, con el tamaño de su celda. */
class WidgetShots : Shots() {
    private fun widgets(c: Context, d: Widgets.Data) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(96, 110, 130))
        val p = Brand.px(c, 16)
        setPadding(p, p, p, p)
        fun cell(w: Int, h: Int, rv: android.widget.RemoteViews) = FrameLayout(c).apply {
            val v = rv.apply(c, this)
            forgetIconErrors()
            // Los íconos del XML, como en las pantallas (ver IconFix)
            v.findViewById<ImageView>(R.id.w_ig)?.setImageDrawable(c.resources.getDrawable(R.mipmap.ic_ig, null))
            v.findViewById<ImageView>(R.id.w_yt)?.setImageDrawable(c.resources.getDrawable(R.mipmap.ic_yt, null))
            addView(v, FrameLayout.LayoutParams(Brand.px(c, w), Brand.px(c, h), Gravity.START))
        }
        addView(LinearLayout(c).apply {
            addView(cell(76, 76, Widgets.mini(c, d)))
            addView(cell(164, 164, Widgets.today(c, d)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = p })
        })
        addView(cell(344, 76, Widgets.bar(c, d)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = p })
    }
    private val min = 60_000L
    private val night = 23.5f to 7f
    private val focus = 9f to 13f

    @Test fun conLimite() = shot("con_limite", listOf(BASE, DARK, EN, BIG_FONT)) { c ->
        widgets(c, Widgets.data(c, 38 * min, 60 * min, false, c.getString(R.string.home_resets, "4:00"), night, focus, 10f + 20 / 60f))
    }
    @Test fun sinLimite() = shot("sin_limite", listOf(BASE, DARK)) { c ->
        widgets(c, Widgets.data(c, 47 * min, null, false, c.getString(R.string.home_resets, "4:00"), night, null, 17f + 5 / 60f))
    }
    @Test fun limite() = shot("limite", listOf(BASE, DARK)) { c ->
        widgets(c, Widgets.data(c, 75 * min, 60 * min, true, c.getString(R.string.home_over, "4:00"), night, null, 20.5f))
    }
    @Test fun noche() = shot("noche", listOf(BASE, DARK)) { c ->
        widgets(c, Widgets.data(c, 41 * min, 60 * min, false, c.getString(R.string.widget_night, "7:00"), night, null, 23f + 50 / 60f))
    }
    @Test fun extremos() = shot("extremos", listOf(BASE, WORST)) { c ->
        widgets(c, Widgets.data(c, 1439 * min, 30 * min, true, c.getString(R.string.home_over, "4:00"), night, focus, 23f + 59 / 60f))
    }
}

/** layoutlib no infla íconos adaptativos desde un XML y lo anota como error: se descarta solo ese. */
private fun forgetIconErrors() {
    val log = Class.forName("com.android.layoutlib.bridge.Bridge").getMethod("getLog").invoke(null)
    val errors = log.javaClass.getDeclaredField("errors").apply { isAccessible = true }.get(log) as MutableList<*>
    errors.removeAll { it is IllegalArgumentException && it.message == "Path string can not be null." }
}
