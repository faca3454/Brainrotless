package com.brainrotless.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Columnas de uso por día, de un solo color: la elegida (al tocarla) va llena y las demás más
 * suaves; un día que pasó el límite, en color de aviso. Líneas de referencia para el límite y
 * para el promedio de antes de Lúcido. En modo [mini] es solo el dibujo (para el inicio).
 */
@SuppressLint("ViewConstructor") // se arma solo desde el código, nunca desde un XML
class DayBars(c: Context, private val mini: Boolean = false) : View(c) {
    var values: List<Long?> = emptyList()
    var labels: List<String> = emptyList()
    var limit: Long? = null
    var limitFrom = 0 // primera columna con límite (antes del compromiso no había)
    var before: Long? = null
    var limitLabel = ""
    var beforeLabel = ""
    var selected = -1
        set(value) { field = value; invalidate() }
    var onSelect: (Int) -> Unit = {}

    private val primary = Brand.color(c, R.color.brand_primary)
    private val warning = Brand.color(c, R.color.brand_warning)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { color = Brand.color(c, R.color.brand_line); strokeWidth = Brand.dp(c, 1f) }
    private val refLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Brand.dp(c, 1.5f)
        pathEffect = DashPathEffect(floatArrayOf(Brand.dp(c, 4f), Brand.dp(c, 4f)), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Brand.color(c, R.color.brand_text_muted)
        textSize = Brand.dp(c, 11f) * c.resources.configuration.fontScale
    }
    private val path = Path()
    private val box = RectF()
    private var grow = 1f

    /** Las columnas crecen desde abajo. */
    fun animateIn() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator()
            addUpdateListener { grow = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (mini) Brand.px(context, 30) else Brand.px(context, 150) + labelBand()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun labelBand() = (text.textSize * 2).toInt()

    /** Ancho de las columnas: a la derecha queda un margen para los nombres de las líneas. */
    private fun plotWidth(): Float {
        if (mini) return width.toFloat()
        val names = listOf(formatDuration(StatsMath.gridStep(scale().toLong())), limitLabel, beforeLabel)
        return width - names.maxOf { text.measureText(it) } - Brand.dp(context, 8f)
    }

    private fun scale(): Float {
        // El mini gráfico muestra la forma de la semana: relativo al día de más uso
        if (mini) return (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(60_000L).toFloat()
        val maxValue = listOfNotNull(values.filterNotNull().maxOrNull(), limit, before).maxOrNull() ?: 0L
        return maxValue.coerceAtLeast(30 * 60_000L) * 1.08f
    }

    override fun onDraw(canvas: Canvas) {
        val n = values.size
        if (n == 0) return
        val top = if (mini) 0f else text.textSize * 0.8f
        val bottom = height - (if (mini) 0 else labelBand()).toFloat()
        val plot = bottom - top
        val scale = scale()
        val plotW = plotWidth()
        fun y(v: Long) = bottom - plot * (v / scale)

        val step = StatsMath.gridStep(scale.toLong())
        if (!mini) {
            // Base y una línea redonda de referencia (ej: 1 h), finas y discretas
            canvas.drawLine(0f, bottom, plotW, bottom, grid)
            canvas.drawLine(0f, y(step), plotW, y(step), grid)
        }

        val slot = plotW / n
        val barW = minOf(Brand.dp(context, if (mini) 5f else 24f), slot * 0.62f)
        val radius = minOf(Brand.dp(context, 4f), barW / 2)
        for ((i, v) in values.withIndex()) {
            if (v == null) continue // de antes de que Lúcido contara
            val over = i >= limitFrom && limit?.let { v >= it } == true
            val chosen = i == selected
            bar.color = if (over) warning else primary
            bar.alpha = if (chosen || over) 255 else 115
            val cx = slot * i + slot / 2
            val h = maxOf(plot * (v / scale) * grow, Brand.dp(context, 2f)) // un día en 0 se ve como una rayita
            box.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            path.reset()
            path.addRoundRect(box, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
            canvas.drawPath(path, bar)
            if (!mini && i < labels.size) {
                text.textAlign = Paint.Align.CENTER
                text.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                text.color = Brand.color(context, if (chosen) R.color.brand_text else R.color.brand_text_muted)
                canvas.drawText(labels[i], cx, height - text.textSize * 0.5f, text)
            }
        }
        text.typeface = Typeface.DEFAULT
        if (mini) return

        // Umbrales por encima de las columnas. Nombres a la derecha: el límite primero, después
        // "antes"; el de la grilla solo si no se pisa con otro.
        val taken = mutableListOf<Float>()
        fun name(label: String, at: Float, optional: Boolean = false) {
            var yy = at
            val gap = text.textSize * 1.15f
            if (taken.any { kotlin.math.abs(it - yy) < gap }) {
                if (optional) return
                yy = (taken.maxOrNull() ?: yy) + gap
            }
            taken += yy
            text.color = Brand.color(context, R.color.brand_text_muted)
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(label, plotW + Brand.dp(context, 8f), yy + text.textSize * 0.35f, text)
        }
        limit?.let { reference(canvas, y(it), R.color.brand_warning, slot * limitFrom, plotW); name(limitLabel, y(it)) }
        before?.let { reference(canvas, y(it), R.color.brand_text_muted, 0f, plotW); name(beforeLabel, y(it)) }
        name(formatDuration(step), y(step), optional = true)
    }

    /** Línea punteada de un umbral (límite o "antes"), por encima de las columnas. */
    private fun reference(canvas: Canvas, y: Float, color: Int, left: Float, right: Float) {
        refLine.color = Brand.color(context, color)
        path.reset()
        path.moveTo(left, y)
        path.lineTo(right, y)
        canvas.drawPath(path, refLine)
    }

    @SuppressLint("ClickableViewAccessibility") // tocar y deslizar elige un día; performClick avisa al soltar
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (mini || values.isEmpty()) return false
        val i = (e.x / (plotWidth() / values.size)).toInt().coerceIn(0, values.size - 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                if (values[i] != null && i != selected) { selected = i; onSelect(i) }
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

/**
 * Un punto por día de la racha: lleno si se cumplió la meta, solo el borde (color de aviso) si
 * no, y gris suave si ese día todavía no había meta o datos.
 */
class StreakDots(c: Context) : View(c) {
    var marks: List<Boolean?> = emptyList()
        set(value) { field = value; invalidate() }

    private val done = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.color(c, R.color.brand_primary) }
    private val missed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Brand.color(c, R.color.brand_warning)
        style = Paint.Style.STROKE
        strokeWidth = Brand.dp(c, 2f)
    }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.color(c, R.color.brand_surface_alt) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Brand.px(context, 14))

    override fun onDraw(canvas: Canvas) {
        if (marks.isEmpty()) return
        val slot = width.toFloat() / marks.size
        val r = minOf(slot * 0.32f, height / 2f - Brand.dp(context, 1f))
        for ((i, m) in marks.withIndex()) {
            val cx = slot * i + slot / 2
            when (m) {
                true -> canvas.drawCircle(cx, height / 2f, r, done)
                false -> canvas.drawCircle(cx, height / 2f, r - missed.strokeWidth / 2, missed)
                null -> canvas.drawCircle(cx, height / 2f, r * 0.6f, empty)
            }
        }
    }
}

/** Barra horizontal fina que se llena hasta [fraction] (antes y ahora, Instagram y YouTube). */
@SuppressLint("ViewConstructor") // se arma solo desde el código, nunca desde un XML
class FillBar(c: Context, private val strong: Boolean) : View(c) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.color(c, R.color.brand_surface_alt) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Brand.color(c, if (strong) R.color.brand_primary else R.color.brand_text_muted)
        if (!strong) alpha = 150 // lo que se compara queda en segundo plano
    }
    private val box = RectF()
    private var shown = 0f

    fun setFraction(f: Float) {
        ValueAnimator.ofFloat(0f, f.coerceIn(0f, 1f)).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Brand.px(context, 10))

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, r, r, track)
        if (shown <= 0f) return
        box.right = maxOf(width * shown, height.toFloat())
        canvas.drawRoundRect(box, r, r, fill)
    }
}

/**
 * El día en un dial de 24 h, con la medianoche arriba: la noche ([night], índigo) y las horas de
 * foco ([focus], verde azulado) como arcos sobre el borde, el sol ámbar en la hora actual
 * ([now]) y, adentro, un anillo fino con lo usado del límite ([usage], null sin límite). Las
 * horas van en decimales (23.5 = 23:30). Con [handles], las puntas de la noche llevan una perilla
 * (la luna al acostarse, el sol al levantarse) y no se marca la hora actual.
 */
@SuppressLint("ViewConstructor") // se arma solo desde el código, nunca desde un XML
class DayDial(c: Context) : View(c) {
    var night: Pair<Float, Float>? = null
    var focus: Pair<Float, Float>? = null
    var now = 12f
    var usage: Float? = null
    var over = false
    var handles = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = Brand.dp(c, 11f) * c.resources.configuration.fontScale
        color = Brand.color(c, R.color.brand_text_muted)
    }
    private val box = RectF()
    private val track = Brand.color(c, R.color.brand_surface_alt)
    private val nightColor = Brand.color(c, R.color.brand_night)
    private val focusColor = Brand.color(c, R.color.brand_primary)
    private val sun = Brand.color(c, R.color.brand_accent)
    private val text = Brand.color(c, R.color.brand_text)
    private val warning = Brand.color(c, R.color.brand_warning)
    private val ground = Brand.color(c, R.color.brand_bg)
    private var grow = 1f

    /** El anillo de adentro se llena desde cero. */
    fun animateIn() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { grow = it.animatedValue as Float; invalidate() }
        }.start()
    }

    private fun angle(h: Float) = -90f + h / 24f * 360f
    private fun sweep(from: Float, to: Float) = ((to - from + 24f) % 24f).let { if (it == 0f) 24f else it } / 24f * 360f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val band = Brand.dp(context, 14f)
        val r = minOf(cx, cy) - Brand.dp(context, 18f)
        box.set(cx - r, cy - r, cx + r, cy + r)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = band
        paint.color = track
        canvas.drawOval(box, paint)
        night?.let { (a, b) -> paint.color = nightColor; canvas.drawArc(box, angle(a), sweep(a, b), false, paint) }
        focus?.let { (a, b) -> paint.color = focusColor; canvas.drawArc(box, angle(a), sweep(a, b), false, paint) }

        // Marcas de cada hora afuera; 0 · 6 · 12 · 18 adentro
        paint.strokeWidth = Brand.dp(context, 1.5f)
        paint.color = label.color
        for (h in 0 until 24) {
            val a = Math.toRadians(angle(h.toFloat()).toDouble())
            val r1 = r + band / 2 + Brand.dp(context, 3f)
            val r2 = r1 + Brand.dp(context, if (h % 6 == 0) 6f else 3f)
            canvas.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), paint)
        }
        for (h in listOf(0, 6, 12, 18)) {
            val a = Math.toRadians(angle(h.toFloat()).toDouble())
            val rl = r - band / 2 - label.textSize
            canvas.drawText("$h", cx + (rl * cos(a)).toFloat(), cy + (rl * sin(a)).toFloat() + label.textSize / 3, label)
        }

        usage?.let { u ->
            val ri = r - band / 2 - label.textSize * 2.2f
            box.set(cx - ri, cy - ri, cx + ri, cy + ri)
            paint.strokeWidth = Brand.dp(context, 6f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = track
            canvas.drawOval(box, paint)
            paint.color = if (over) warning else text
            if (u > 0f) canvas.drawArc(box, -90f, 360f * u.coerceAtMost(1f) * grow, false, paint)
        }

        fun dot(h: Float, color: Int, radius: Float) {
            val a = Math.toRadians(angle(h).toDouble())
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = ground // un aro del color del fondo lo separa del arco
            canvas.drawCircle(x, y, radius + Brand.dp(context, 3f), paint)
            paint.color = color
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.STROKE
        }
        val n = night
        if (handles && n != null) {
            dot(n.first, nightColor, Brand.dp(context, 12f))
            dot(n.second, sun, Brand.dp(context, 12f))
        } else dot(now, sun, Brand.dp(context, 8f))
    }
}
