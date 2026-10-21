package com.brainrotless.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat

/** Avisos de IG Lite y YT Lite (MainActivity): sin conexión, bloqueos, Accesibilidad apagada y la barra de "solo mensajes". */
object Notices {
    /**
     * Pantalla completa con el logo, título, texto y uno o dos botones (sin conexión, bloqueos).
     * Centrada si entra; con scroll si no (teléfono acostado, letra grande), en vez de aplastar los botones.
     */
    fun screen(
        c: Context, title: String, subtitle: String, button: String, button2: String? = null, onClick: () -> Unit
    ) = ScrollView(c).apply {
        setBackgroundColor(Brand.color(c, R.color.brand_bg))
        visibility = View.GONE
        isClickable = true // que no pase el toque a la WebView de atrás
        isFillViewport = true
        addView(body(c, title, subtitle, button, button2, onClick))
    }

    private fun body(
        c: Context, title: String, subtitle: String, button: String, button2: String?, onClick: () -> Unit
    ) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val pad = Brand.px(c, 32)
        setPadding(pad, pad, pad, pad)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val wide = Brand.px(c, 280) // los botones, del mismo ancho y fáciles de tocar
        addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(Brand.px(c, 56), Brand.px(c, 56)).apply { bottomMargin = Brand.px(c, 24) })
        addView(Brand.text(c, title, Brand.Type.HEADLINE + 4, bold = true).apply {
            tag = "title"
            gravity = Gravity.CENTER
            ViewCompat.setAccessibilityHeading(this, true)
        })
        addView(Brand.text(c, subtitle, Brand.Type.TITLE, R.color.brand_text_muted).apply {
            tag = "subtitle"
            gravity = Gravity.CENTER
            maxWidth = Brand.px(c, 360)
            setPadding(0, Brand.px(c, 10), 0, Brand.px(c, 32))
        })
        addView(Brand.primaryButton(c, button, onClick).apply { minWidth = wide }, LinearLayout.LayoutParams(wrap, wrap))
        if (button2 != null) addView(Brand.secondaryButton(c, button2).apply {
            tag = "button2"
            minWidth = wide
            visibility = View.GONE
        }, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = Brand.px(c, 12) })
    }

    /** Barra arriba de la página: texto y el botón de pausa. */
    fun lockBar(c: Context, onPause: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = Brand.px(c, 56)
        setPadding(Brand.px(c, 16), Brand.px(c, 4), Brand.px(c, 8), Brand.px(c, 4))
        setBackgroundColor(Brand.color(c, R.color.brand_tonal))
        visibility = View.GONE
        addView(Brand.text(c, "", Brand.Type.BODY, R.color.brand_on_tonal).apply {
            tag = "text"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // TalkBack avisa cuando cambia
        },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Brand.accentChip(c, "Pausa", onPause).apply { tag = "pause" })
    }
}
