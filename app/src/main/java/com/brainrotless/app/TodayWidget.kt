package com.brainrotless.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import java.util.Date

/** Widget "Hoy" (2×2): anillo con el uso, límite, estado y accesos a IG Lite y YT Lite. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(c, force = true)
}

/** Widget "Mini" (1×1): solo el anillo con los minutos de hoy. */
class MiniWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(c, force = true)
}

/** Widget "Barra" (4×1): uso, barrita, estado y accesos, en una fila. */
class BarWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(c, force = true)
}

/**
 * Arma y actualiza los tres widgets. Todos los textos se ponen desde acá (así respetan el idioma
 * elegido en la app) y el anillo se dibuja como una imagen chica. Barato si no cambió nada.
 */
object Widgets {
    private var shown: String? = null // lo último que se mostró: no repetir actualizaciones iguales
    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Lo que muestran los widgets, ya calculado. */
    class Data(
        val used: String, val limit: String?, val progress: Float?, val over: Boolean,
        val status: String, val ringNumber: String, val ringUnit: String,
    )

    private fun data(c: Context): Data {
        val usedMs = Usage.todayMs(c)
        val reset = Sleep.fmt(c, Usage.nextReset(c).toLocalTime())
        val over = Usage.overLimit(c)
        val status = when {
            Sleep.active(c) -> c.getString(R.string.widget_night, Lock.until(c, Lock.Reason.NIGHT))
            Focus.active(c) -> c.getString(R.string.widget_focus, Lock.until(c, Lock.Reason.FOCUS))
            Commitment.pauseActive(c) ->
                c.getString(R.string.home_pause_until, DateFormat.getTimeFormat(c).format(Date(Commitment.pauseUntil(c))))
            over -> c.getString(R.string.home_over, reset)
            else -> c.getString(R.string.home_resets, reset)
        }
        return data(c, usedMs, Usage.limitMs(c), over, status)
    }

    /** Los números y textos del widget a partir del uso de hoy, el límite y el estado. */
    fun data(c: Context, usedMs: Long, limitMs: Long?, over: Boolean, status: String): Data {
        val m = usedMs / 60_000
        val (number, unit) = if (m < 60) "$m" to c.getString(R.string.widget_unit_min)
            else "%d:%02d".format(m / 60, m % 60) to c.getString(R.string.widget_unit_h)
        return Data(
            used = formatDuration(usedMs),
            limit = limitMs?.let { formatDuration(it) },
            progress = limitMs?.let { (usedMs.toFloat() / it).coerceIn(0f, 1f) },
            over = over, status = status, ringNumber = number, ringUnit = unit,
        )
    }

    fun update(c: Context, force: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(c) ?: return
        fun ids(cls: Class<*>) = mgr.getAppWidgetIds(ComponentName(c, cls))
        val today = ids(TodayWidget::class.java)
        val mini = ids(MiniWidget::class.java)
        val bar = ids(BarWidget::class.java)
        if (today.isEmpty() && mini.isEmpty() && bar.isEmpty()) return

        val d = data(c)
        val night = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val key = "${d.used}|${d.limit}|${((d.progress ?: 0f) * 100).toInt()}|${d.status}|${Lang.current(c)}|$night"
        if (!force && key == shown) return
        shown = key

        val ring by lazy { ring(c, d) }
        runCatching {
            if (mini.isNotEmpty()) mgr.updateAppWidget(mini, mini(c, d, ring).apply {
                setOnClickPendingIntent(R.id.w_root, home(c))
            })
            if (bar.isNotEmpty()) mgr.updateAppWidget(bar, bar(c, d).apply { shortcuts(c) })
            if (today.isNotEmpty()) mgr.updateAppWidget(today, today(c, d, ring).apply { shortcuts(c) })
        }
    }

    /** Mini (1×1): el anillo. */
    fun mini(c: Context, d: Data, ring: Bitmap = ring(c, d)) = RemoteViews(c.packageName, R.layout.widget_mini).apply {
        setImageViewBitmap(R.id.w_ring, ring)
        setContentDescription(R.id.w_ring, "${d.used} · ${d.status}")
    }

    /** Barra (4×1): uso, barrita y estado. */
    fun bar(c: Context, d: Data) = RemoteViews(c.packageName, R.layout.widget_bar).apply {
        setTextViewText(R.id.w_amount, amountWithLimit(c, d))
        setTextViewText(R.id.w_status, d.status)
        setViewVisibility(R.id.w_progress, if (d.progress != null) View.VISIBLE else View.GONE)
        setProgressBar(R.id.w_progress, 1000, ((d.progress ?: 0f) * 1000).toInt(), false)
        setContentDescription(R.id.w_ig, c.getString(R.string.label_ig))
        setContentDescription(R.id.w_yt, c.getString(R.string.label_yt))
    }

    /** Hoy (2×2): anillo, límite y estado. */
    fun today(c: Context, d: Data, ring: Bitmap = ring(c, d)) = RemoteViews(c.packageName, R.layout.widget_today).apply {
        setImageViewBitmap(R.id.w_ring, ring)
        setTextViewText(R.id.w_title, c.getString(R.string.widget_title))
        setTextViewText(R.id.w_limit, d.limit?.let { c.getString(R.string.widget_of_limit, it) } ?: c.getString(R.string.widget_no_limit))
        setTextViewText(R.id.w_status, d.status)
        setContentDescription(R.id.w_ig, c.getString(R.string.label_ig))
        setContentDescription(R.id.w_yt, c.getString(R.string.label_yt))
    }

    /** "23 min de 1 h", con el límite más chico y apagado. */
    private fun amountWithLimit(c: Context, d: Data): CharSequence {
        val limit = d.limit?.let { c.getString(R.string.usage_of, it) } ?: return d.used
        return SpannableString(d.used + limit).apply {
            val from = d.used.length
            setSpan(RelativeSizeSpan(0.75f), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Brand.color(c, R.color.brand_text_muted)), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun RemoteViews.shortcuts(c: Context) {
        setOnClickPendingIntent(R.id.w_root, home(c))
        setOnClickPendingIntent(R.id.w_ig, open(c, alias(c, "Instagram"), 11))
        setOnClickPendingIntent(R.id.w_yt, open(c, alias(c, "YouTube"), 12))
    }

    /** El anillo: progreso contra el límite (si hay) y los minutos en el centro. */
    private fun ring(c: Context, d: Data): Bitmap {
        val size = Brand.px(c, 64).coerceIn(96, 256)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = size * 0.10f
        val box = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = Brand.color(c, R.color.brand_surface_alt)
        }
        canvas.drawOval(box, line)
        d.progress?.takeIf { it > 0f }?.let {
            line.color = Brand.color(c, if (d.over) R.color.brand_warning else R.color.brand_primary)
            line.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(box, -90f, 360f * it, false, line)
            // El sol del logo en la punta, como el del dial (si no se pasó del límite)
            if (!d.over) {
                val a = Math.toRadians(-90.0 + 360.0 * it)
                val r = box.width() / 2
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.color(c, R.color.brand_accent) }
                canvas.drawCircle(box.centerX() + (r * Math.cos(a)).toFloat(), box.centerY() + (r * Math.sin(a)).toFloat(), stroke * 0.75f, dot)
            }
        }
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Brand.color(c, R.color.brand_text)
            textAlign = Paint.Align.CENTER
            typeface = MEDIUM
            textSize = size * (if (d.ringNumber.length > 3) 0.24f else 0.30f)
        }
        val unit = Paint(number).apply {
            color = Brand.color(c, R.color.brand_text_muted)
            typeface = Typeface.DEFAULT
            textSize = size * 0.14f
        }
        val base = size / 2f + number.textSize * 0.25f
        canvas.drawText(d.ringNumber, size / 2f, base, number)
        canvas.drawText(d.ringUnit, size / 2f, base + unit.textSize * 1.2f, unit)
        return bmp
    }

    private fun home(c: Context) = open(c, Intent(c, HomeActivity::class.java), 10)

    private fun alias(c: Context, name: String) =
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(c, "$PKG.$name"))

    private fun open(c: Context, i: Intent, code: Int) = PendingIntent.getActivity(
        c, code, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
