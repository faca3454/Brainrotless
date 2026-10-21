package com.brainrotless.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout

/** Avisos de IG Lite y YT Lite (MainActivity): sin conexión, bloqueos, Accesibilidad apagada y la barra de "solo mensajes". */
object Notices {
    /** Pantalla completa con el logo, título, texto y uno o dos botones (sin conexión, bloqueos). */
    fun screen(
        c: Context, title: String, subtitle: String, button: String, button2: String? = null, onClick: () -> Unit
    ) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Brand.color(c, R.color.brand_bg))
        visibility = View.GONE
        isClickable = true // que no pase el toque a la WebView de atrás
        val pad = Brand.px(c, 32)
        setPadding(pad, pad, pad, pad)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) },
            LinearLayout.LayoutParams(Brand.px(c, 56), Brand.px(c, 56)).apply { bottomMargin = Brand.px(c, 20) })
        addView(Brand.text(c, title, 22f, bold = true).apply { tag = "title"; gravity = Gravity.CENTER })
        addView(Brand.text(c, subtitle, 15f, R.color.brand_text_muted).apply {
            tag = "subtitle"
            gravity = Gravity.CENTER
            setPadding(0, Brand.px(c, 8), 0, Brand.px(c, 24))
        })
        addView(Brand.primaryButton(c, button, onClick), LinearLayout.LayoutParams(wrap, wrap))
        if (button2 != null) addView(Brand.secondaryButton(c, button2).apply {
            tag = "button2"
            visibility = View.GONE
        }, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = Brand.px(c, 10) })
    }

    /** Barra arriba de la página: texto y el botón de pausa. */
    fun lockBar(c: Context, onPause: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Brand.px(c, 16), Brand.px(c, 6), Brand.px(c, 10), Brand.px(c, 6))
        setBackgroundColor(Brand.color(c, R.color.brand_surface_alt))
        visibility = View.GONE
        addView(Brand.text(c, "", 13f).apply { tag = "text" },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Brand.accentChip(c, "Pausa", onPause).apply { tag = "pause" })
    }
}
