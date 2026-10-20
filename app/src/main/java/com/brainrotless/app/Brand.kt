package com.brainrotless.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

/**
 * Identidad visual de Lúcido para las pantallas propias: colores (claro y oscuro salen de
 * res/values y res/values-night), tipografía y piezas comunes. Todo con vistas simples, sin
 * librerías extra, para que la app pese poco y arranque rápido.
 */
object Brand {
    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun color(c: Context, @ColorRes id: Int) = ContextCompat.getColor(c, id)
    fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density
    fun px(c: Context, v: Int) = dp(c, v.toFloat()).toInt()

    fun rounded(c: Context, @ColorRes fill: Int, radiusDp: Float, @ColorRes stroke: Int? = null) =
        GradientDrawable().apply {
            cornerRadius = dp(c, radiusDp)
            setColor(color(c, fill))
            stroke?.let { setStroke(px(c, 1), color(c, it)) }
        }

    /** Fondo con onda al tocar, recortada a las mismas esquinas. */
    fun pressable(c: Context, base: Drawable, radiusDp: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(color(c, R.color.brand_ripple)), base, rounded(c, R.color.brand_text, radiusDp))

    /** Tarjeta: superficie con esquinas de 20 dp; con [onClick], se puede tocar. */
    fun card(v: View, onClick: (() -> Unit)? = null) = v.apply {
        val base = rounded(context, R.color.brand_surface, 20f)
        background = if (onClick != null) pressable(context, base, 20f) else base
        val pad = px(context, 16)
        setPadding(pad, pad, pad, pad)
        onClick?.let { f -> isClickable = true; setOnClickListener { f() } }
    }

    fun text(c: Context, s: CharSequence, sp: Float, @ColorRes color: Int = R.color.brand_text, bold: Boolean = false) =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color(c, color))
            if (bold) typeface = MEDIUM
        }

    private fun button(c: Context, label: String, background: Drawable, @ColorRes textColor: Int, onClick: () -> Unit) =
        Button(c).apply {
            text = label
            isAllCaps = false
            typeface = MEDIUM
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(color(c, textColor))
            this.background = background
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = px(c, 48)
            minimumHeight = px(c, 48)
            setPadding(px(c, 24), 0, px(c, 24), 0)
            setOnClickListener { onClick() }
        }

    fun primaryButton(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, rounded(c, R.color.brand_primary, 999f), 999f), R.color.brand_on_primary, onClick)

    fun secondaryButton(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, rounded(c, R.color.brand_bg, 999f, stroke = R.color.brand_line), 999f), R.color.brand_primary, onClick)

    /** Botón chico ámbar (ej: "Pausa"). */
    fun accentChip(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, rounded(c, R.color.brand_accent, 999f), 999f), R.color.brand_on_accent, onClick).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minHeight = px(c, 36)
            minimumHeight = px(c, 36)
            setPadding(px(c, 14), 0, px(c, 14), 0)
        }

    /** Etiqueta chica ámbar dentro de un texto (ej: "5 días"). */
    fun pill(c: Context, label: String) = text(c, label, 11.5f, R.color.brand_on_accent, bold = true).apply {
        background = rounded(c, R.color.brand_accent, 999f)
        setPadding(px(c, 8), px(c, 1), px(c, 8), px(c, 2))
        gravity = Gravity.CENTER
    }
}

/** Anillo de progreso (uso del día contra el límite). Se dibuja solo, sin imágenes. */
class RingView(c: Context) : View(c) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Brand.dp(c, 7f)
        color = Brand.color(c, R.color.brand_surface_alt)
    }
    private val arc = Paint(track).apply { strokeCap = Paint.Cap.ROUND }
    private val box = RectF()
    private var shown = 0f

    /** Progreso de 0 a 1 (null: sin límite, solo el aro). Anima desde el valor anterior. */
    fun setProgress(p: Float?, warning: Boolean) {
        arc.color = Brand.color(context, if (warning) R.color.brand_warning else R.color.brand_primary)
        val target = (p ?: 0f).coerceIn(0f, 1f)
        ValueAnimator.ofFloat(shown, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = track.strokeWidth / 2
        box.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(box, track)
        if (shown > 0f) canvas.drawArc(box, -90f, 360f * shown, false, arc)
    }
}
