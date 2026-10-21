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
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
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

    /**
     * Escala de tipos (sp): pocos tamaños con un papel cada uno, en vez de uno por pieza. Los
     * números grandes van con cifras tabulares para que no bailen al cambiar.
     */
    object Type {
        const val HERO = 56f // el número de la pantalla (días que quedan)
        const val DISPLAY = 34f // números grandes (uso de hoy, horarios)
        const val HEADLINE = 22f // título de la pantalla
        const val TITLE = 16f // título de fila o tarjeta
        const val BODY = 14f // estados y explicaciones
        const val LABEL = 12f // nombres chicos (días del gráfico, leyendas)
    }

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

    /** Tarjeta: superficie con esquinas de 24 dp; con [onClick], se puede tocar. */
    fun card(v: View, onClick: (() -> Unit)? = null) = v.apply {
        val base = rounded(context, R.color.brand_surface, 24f)
        background = if (onClick != null) pressable(context, base, 24f) else base
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

    /** Número grande (uso, horas, días): Medium con cifras tabulares. */
    fun number(c: Context, s: CharSequence, sp: Float = Type.DISPLAY, @ColorRes color: Int = R.color.brand_text) =
        text(c, s, sp, color, bold = true).apply {
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

    /** Ícono tocable de 48 dp (atrás, ajustes) con su descripción para TalkBack. */
    fun iconButton(c: Context, icon: Int, description: String, @ColorRes tint: Int = R.color.brand_text, onClick: () -> Unit) =
        ImageView(c).apply {
            setImageResource(icon)
            setColorFilter(color(c, tint))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            background = pressable(c, GradientDrawable(), 999f)
            minimumWidth = px(c, 48)
            minimumHeight = px(c, 48)
            setOnClickListener { onClick() }
        }

    /** Flecha de "entrar" al final de una fila (decorativa: la fila ya dice adónde va). */
    fun chevron(c: Context) = ImageView(c).apply {
        setImageResource(R.drawable.ic_chevron)
        setColorFilter(color(c, R.color.brand_text_muted))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Ícono en un círculo de color (filas del inicio y del panel). */
    fun badge(c: Context, icon: Int, @ColorRes fill: Int = R.color.brand_tonal, @ColorRes tint: Int = R.color.brand_primary, sizeDp: Int = 40) =
        ImageView(c).apply {
            setImageResource(icon)
            setColorFilter(color(c, tint))
            background = rounded(c, fill, 999f)
            val p = px(c, sizeDp / 4)
            setPadding(p, p, p, p)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(px(c, sizeDp), px(c, sizeDp))
        }

    /** Fondo de 40 dp de alto dentro de una zona táctil de 48 dp (pastillas, botón de pausa). */
    fun inset48(c: Context, d: Drawable): Drawable = InsetDrawable(d, 0, px(c, 4), 0, px(c, 4))

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
            minHeight = px(c, 52)
            minimumHeight = px(c, 52)
            setPadding(px(c, 24), 0, px(c, 24), 0)
            setOnClickListener { onClick() }
        }

    fun primaryButton(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, rounded(c, R.color.brand_primary, 999f), 999f), R.color.brand_on_primary, onClick)

    fun secondaryButton(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, rounded(c, R.color.brand_bg, 999f, stroke = R.color.brand_line), 999f), R.color.brand_primary, onClick)

    /** Botón chico ámbar (ej: "Pausa"): se ve de 40 dp, se toca en 48. */
    fun accentChip(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, inset48(c, pressable(c, rounded(c, R.color.brand_accent, 999f), 999f)), R.color.brand_on_accent, onClick).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Type.BODY)
            minHeight = px(c, 48)
            minimumHeight = px(c, 48)
            setPadding(px(c, 16), 0, px(c, 16), 0)
        }

    /** Botón sin fondo, al lado de uno que sí lo tiene (ej: "Ahora no"). */
    fun textButton(c: Context, label: String, onClick: () -> Unit = {}) =
        button(c, label, pressable(c, GradientDrawable(), 999f), R.color.brand_text_muted, onClick).apply {
            setPadding(px(c, 16), 0, px(c, 16), 0)
        }

    /**
     * Aviso suave dentro de una pantalla: qué falta y el botón para hacerlo. Opcional, un link de
     * ayuda al lado (ej: "¿Un permiso aparece gris?").
     */
    fun notice(c: Context, message: String, action: String, onAction: () -> Unit, help: String? = null, onHelp: () -> Unit = {}) =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c, R.color.brand_tonal, 20f)
            setPadding(px(c, 16), px(c, 14), px(c, 16), px(c, 8))
            addView(text(c, message, Type.BODY, R.color.brand_on_tonal))
            addView(LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(secondaryButton(c, action, onAction))
                help?.let {
                    addView(text(c, it, Type.BODY, R.color.brand_primary, bold = true).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        minHeight = px(c, 48)
                        setPadding(px(c, 14), 0, px(c, 4), 0)
                        background = pressable(c, GradientDrawable(), 12f)
                        setOnClickListener { onHelp() }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(c, 10) })
        }

    /** Etiqueta chica ámbar dentro de un texto (ej: "5 días"). */
    fun pill(c: Context, label: String) = text(c, label, Type.LABEL, R.color.brand_on_accent, bold = true).apply {
        background = rounded(c, R.color.brand_accent, 999f)
        setPadding(px(c, 10), px(c, 3), px(c, 10), px(c, 4))
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

/**
 * Interruptor dibujado con Canvas, al estilo de Material 3 (no depende de los drawables del tema,
 * así se ve igual en cualquier teléfono): 52×32 dp dentro de 48 dp de alto. Tocarlo lo cambia y
 * avisa; tocar la fila entera también, vía performClick. Con [describe], TalkBack lee la fila
 * entera como un interruptor con su estado.
 */
class ToggleView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val onTrack = Brand.color(c, R.color.brand_primary)
    private val offTrack = Brand.color(c, R.color.brand_surface_alt)
    private val outline = Brand.color(c, R.color.brand_outline)
    private val onKnob = Brand.color(c, R.color.brand_on_primary)
    private val box = RectF()

    var onChange: (Boolean) -> Unit = {}

    var checked = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            checked = !checked
            onChange(checked)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        }
    }

    /** La fila [row] (con el título y la explicación) pasa a ser el interruptor para TalkBack. */
    fun describe(row: View) {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        row.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Switch::class.java.name
                info.isCheckable = true
                info.isChecked = checked
            }
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Switch::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Brand.px(context, 52), Brand.px(context, 48))
    }

    override fun onDraw(canvas: Canvas) {
        val r = Brand.dp(context, 16f)
        val top = height / 2f - r
        box.set(0f, top, width.toFloat(), top + 2 * r)
        paint.style = Paint.Style.FILL
        paint.color = if (checked) onTrack else offTrack
        canvas.drawRoundRect(box, r, r, paint)
        if (!checked) {
            // Apagado: borde y perilla chica en el color del contorno (se distingue sin color)
            val sw = Brand.dp(context, 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.color = outline
            box.inset(sw / 2, sw / 2)
            canvas.drawRoundRect(box, r - sw / 2, r - sw / 2, paint)
            paint.style = Paint.Style.FILL
        }
        paint.color = if (checked) onKnob else outline
        val knob = Brand.dp(context, if (checked) 12f else 8f)
        canvas.drawCircle(if (checked) width - r else r, height / 2f, knob, paint)
    }
}
