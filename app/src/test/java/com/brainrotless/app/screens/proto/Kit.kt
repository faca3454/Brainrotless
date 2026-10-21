package com.brainrotless.app.screens.proto

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Piezas mínimas para dibujar las direcciones de rediseño (solo pruebas, no entran en la app).
 * Cada dirección trae su paleta en claro y oscuro.
 */
class Pal(
    val bg: Int, val surface: Int, val surface2: Int, val text: Int, val muted: Int,
    val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int,
    val accent: Int, val onAccent: Int, val error: Int, val line: Int, val night: Int,
)

fun isNight(c: Context) = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
fun Int.argb() = (0xFF000000.toInt() or this)
fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density
fun px(c: Context, v: Int) = dp(c, v.toFloat()).toInt()

val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
val CONDENSED: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

fun tv(c: Context, s: CharSequence, sp: Float, color: Int, face: Typeface = REGULAR, tnum: Boolean = false, track: Float = 0f) =
    TextView(c).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = face
        if (tnum) fontFeatureSettings = "tnum"
        letterSpacing = track
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

fun box(c: Context, fill: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = dp(c, radiusDp)
    stroke?.let { setStroke(px(c, 1), it) }
}

fun col(c: Context, vararg v: View) = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    v.forEach { addView(it, it.layoutParams ?: LinearLayout.LayoutParams(-1, -2)) }
}

fun LinearLayout.add(v: View, top: Int = 0, w: Int = -1, h: Int = -2, weight: Float = 0f) =
    addView(v, LinearLayout.LayoutParams(w, h, weight).apply { topMargin = px(context, top) })

fun gap(c: Context, w: Int = 0, h: Int = 0) = android.widget.Space(c).apply { minimumWidth = px(c, w); minimumHeight = px(c, h) }

fun icon(c: Context, res: Int, tint: Int, sizeDp: Int = 24) = ImageView(c).apply {
    setImageResource(res)
    setColorFilter(tint)
    layoutParams = LinearLayout.LayoutParams(px(c, sizeDp), px(c, sizeDp))
}

/** Ícono en un círculo de color. */
fun badge(c: Context, res: Int, fill: Int, tint: Int, sizeDp: Int = 40, padDp: Int = 10) = ImageView(c).apply {
    setImageResource(res)
    setColorFilter(tint)
    background = box(c, fill, 999f)
    val p = px(c, padDp)
    setPadding(p, p, p, p)
    layoutParams = LinearLayout.LayoutParams(px(c, sizeDp), px(c, sizeDp))
}

/** Flechas dibujadas (atrás y "ir"), en el mismo trazo que los íconos. */
class Glyph(c: Context, private val kind: String, private val color: Int, private val sizeDp: Int = 24) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(c, 2f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        this.color = this@Glyph.color
    }
    private val path = Path()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px(context, sizeDp), px(context, sizeDp))
    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        path.reset()
        when (kind) {
            "back" -> { path.moveTo(s * .8f, s * .5f); path.lineTo(s * .2f, s * .5f); path.moveTo(s * .45f, s * .25f); path.lineTo(s * .2f, s * .5f); path.lineTo(s * .45f, s * .75f) }
            else -> { path.moveTo(s * .4f, s * .25f); path.lineTo(s * .65f, s * .5f); path.lineTo(s * .4f, s * .75f) }
        }
        canvas.drawPath(path, p)
    }
}

/** Fila de lista de 64 dp: lo de la izquierda, título y estado, y la flecha. */
fun row(c: Context, p: Pal, lead: View, title: String, sub: String, subColor: Int = p.muted, trail: View? = null, titleSp: Float = 16f, face: Typeface = MEDIUM) =
    LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(c, 64)
        setPadding(px(c, 16), px(c, 10), px(c, 12), px(c, 10))
        addView(lead)
        add(col(c, tv(c, title, titleSp, p.text, face), gap(c, h = 2), tv(c, sub, 14f, subColor)).apply {
            setPadding(px(c, 16), 0, px(c, 8), 0)
        }, w = 0, weight = 1f)
        addView(trail ?: Glyph(c, "go", p.muted))
    }

/** Línea fina de 1 dp, con sangría a la izquierda. */
fun divider(c: Context, color: Int, insetDp: Int = 0): View = object : View(c) {
    private val paint = Paint().apply { this.color = color }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), px(c, 1))
    override fun onDraw(canvas: Canvas) = canvas.drawRect(dp(c, insetDp.toFloat()), 0f, width.toFloat(), height.toFloat(), paint)
}

/** Pastilla de opción de 48 dp de alto (la elegida, llena). */
fun choice(c: Context, p: Pal, label: String, on: Boolean) = tv(c, label, 14f, if (on) p.onPrimary else p.text, MEDIUM).apply {
    gravity = Gravity.CENTER
    minHeight = px(c, 40)
    setPadding(px(c, 16), 0, px(c, 16), 0)
    background = if (on) box(c, p.primary, 12f) else box(c, 0, 12f, p.line)
}

fun button(c: Context, label: String, fill: Int, textColor: Int, stroke: Int? = null) = tv(c, label, 15f, textColor, MEDIUM).apply {
    gravity = Gravity.CENTER
    minHeight = px(c, 52)
    setPadding(px(c, 24), 0, px(c, 24), 0)
    background = box(c, fill, 999f, stroke)
}

/** Interruptor al estilo Material 3 (52×32 dp, dentro de 48 dp de alto). */
class Switch3(c: Context, private val p: Pal, private val on: Boolean) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px(context, 52), px(context, 48))
    override fun onDraw(canvas: Canvas) {
        val top = (height - dp(context, 32f)) / 2
        val r = dp(context, 16f)
        val rect = android.graphics.RectF(0f, top, width.toFloat(), top + 2 * r)
        if (on) { paint.style = Paint.Style.FILL; paint.color = p.primary; canvas.drawRoundRect(rect, r, r, paint) }
        else {
            paint.style = Paint.Style.FILL; paint.color = p.surface2; canvas.drawRoundRect(rect, r, r, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(context, 2f); paint.color = p.muted
            rect.inset(dp(context, 1f), dp(context, 1f)); canvas.drawRoundRect(rect, r, r, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = if (on) p.onPrimary else p.muted
        val cx = if (on) width - r else r
        canvas.drawCircle(cx, top + r, if (on) dp(context, 12f) else dp(context, 8f), paint)
    }
}

/** Columnas de la semana (para el inicio), con la de hoy llena y los nombres abajo. */
class WeekBars(c: Context, private val p: Pal, private val values: List<Long>, private val limit: Long?, private val labels: List<String>, private val hDp: Int = 64) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(c, 11f) * c.resources.configuration.fontScale; textAlign = Paint.Align.CENTER }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), px(context, hDp) + (text.textSize * 1.8f).toInt())
    override fun onDraw(canvas: Canvas) {
        val bottom = px(context, hDp).toFloat()
        val max = (values + listOfNotNull(limit)).max().toFloat()
        val slot = width / values.size.toFloat()
        val bw = minOf(dp(context, 18f), slot * 0.55f)
        for ((i, v) in values.withIndex()) {
            val over = limit != null && v >= limit
            val today = i == values.lastIndex
            paint.color = if (over) p.error else p.primary
            paint.alpha = if (today || over) 255 else 90
            val h = maxOf(bottom * v / max, dp(context, 3f))
            val cx = slot * i + slot / 2
            canvas.drawRoundRect(cx - bw / 2, bottom - h, cx + bw / 2, bottom, dp(context, 4f), dp(context, 4f), paint)
            text.color = if (today) p.text else p.muted
            text.typeface = if (today) MEDIUM else REGULAR
            canvas.drawText(labels[i], cx, height - text.textSize * 0.3f, text)
        }
        limit?.let {
            paint.color = p.error; paint.alpha = 200; paint.strokeWidth = dp(context, 1.5f)
            val y = bottom - bottom * it / max
            var x = 0f
            while (x < width) { canvas.drawLine(x, y, minOf(x + dp(context, 4f), width.toFloat()), y, paint); x += dp(context, 8f) }
        }
    }
}
