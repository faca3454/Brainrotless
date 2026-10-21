package com.brainrotless.app.screens.proto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.brainrotless.app.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dirección C, "Reloj del día": el día entero en un dial de 24 h. La noche y las horas de foco
 * son arcos sobre el borde, el sol marca la hora actual y adentro va el uso de hoy. Es la pieza
 * que ninguna otra app tiene: muestra cuándo se cierra cada cosa, no solo cuánto usaste.
 */
object DirC {
    private val LIGHT = Pal(
        bg = 0xF7F4EE.argb(), surface = 0xFFFFFF.argb(), surface2 = 0xECE6DC.argb(), text = 0x15201E.argb(), muted = 0x55625F.argb(),
        primary = 0x13695F.argb(), onPrimary = 0xFFFFFF.argb(), container = 0xDCEEE8.argb(), onContainer = 0x0B3F39.argb(),
        accent = 0xE8962A.argb(), onAccent = 0x2B1A00.argb(), error = 0xB23A20.argb(), line = 0xE3DCD0.argb(), night = 0x34447F.argb(),
    )
    private val DARK = Pal(
        bg = 0x0B1416.argb(), surface = 0x132225.argb(), surface2 = 0x1D2E31.argb(), text = 0xEEE8DD.argb(), muted = 0xA0B0AC.argb(),
        primary = 0x5CC8B6.argb(), onPrimary = 0x04201C.argb(), container = 0x1C3B39.argb(), onContainer = 0xBFE9E0.argb(),
        accent = 0xF5B55C.argb(), onAccent = 0x2B1A00.argb(), error = 0xF28B6E.argb(), line = 0x223437.argb(), night = 0x7F8CD8.argb(),
    )
    fun pal(c: Context) = if (isNight(c)) DARK else LIGHT

    /**
     * El dial: 24 h con la medianoche arriba. [night] y [focus] en horas decimales; [now] la hora;
     * [usage] la parte del límite usada (anillo de adentro), null sin anillo.
     */
    class DayDial(c: Context, private val p: Pal, private val night: Pair<Float, Float>, private val focus: Pair<Float, Float>?,
                  private val now: Float, private val usage: Float?, private val handles: Boolean = false) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = MEDIUM }
        private val box = RectF()
        private fun ang(h: Float) = -90f + h / 24f * 360f
        private fun sweep(a: Float, b: Float) = ((b - a + 24) % 24) / 24f * 360f

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val band = dp(context, 16f)
            val r = minOf(cx, cy) - dp(context, 22f)
            box.set(cx - r, cy - r, cx + r, cy + r)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = band
            paint.color = p.surface2; canvas.drawOval(box, paint)
            paint.color = p.night; canvas.drawArc(box, ang(night.first), sweep(night.first, night.second), false, paint)
            focus?.let { paint.color = p.primary; canvas.drawArc(box, ang(it.first), sweep(it.first, it.second), false, paint) }

            // Marcas de las horas y 0 · 6 · 12 · 18 afuera
            paint.strokeWidth = dp(context, 1.5f); paint.color = p.muted
            label.textSize = dp(context, 11f) * context.resources.configuration.fontScale; label.color = p.muted
            for (h in 0 until 24) {
                val a = Math.toRadians(ang(h.toFloat()).toDouble())
                val r1 = r + band / 2 + dp(context, 3f)
                val r2 = r1 + dp(context, if (h % 6 == 0) 6f else 3f)
                canvas.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), paint)
            }
            for (h in listOf(0, 6, 12, 18)) {
                val a = Math.toRadians(ang(h.toFloat()).toDouble())
                val rl = r - band / 2 - dp(context, 12f)
                canvas.drawText("$h", cx + (rl * cos(a)).toFloat(), cy + (rl * sin(a)).toFloat() + label.textSize / 3, label)
            }

            // Uso de hoy, un anillo fino adentro
            usage?.let {
                val ri = r - band / 2 - dp(context, 30f)
                box.set(cx - ri, cy - ri, cx + ri, cy + ri)
                paint.strokeWidth = dp(context, 6f); paint.strokeCap = Paint.Cap.ROUND
                paint.color = p.surface2; canvas.drawOval(box, paint)
                paint.color = if (it >= 1f) p.error else p.text; canvas.drawArc(box, -90f, 360f * it.coerceAtMost(1f), false, paint)
                paint.strokeCap = Paint.Cap.BUTT
            }

            // Ahora: el sol sobre el borde
            fun dot(h: Float, color: Int, rad: Float, ring: Int? = null) {
                val a = Math.toRadians(ang(h).toDouble())
                val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
                paint.style = Paint.Style.FILL
                ring?.let { paint.color = it; canvas.drawCircle(x, y, rad + dp(context, 3f), paint) }
                paint.color = color; canvas.drawCircle(x, y, rad, paint)
                paint.style = Paint.Style.STROKE
            }
            if (handles) { dot(night.first, p.night, dp(context, 13f), p.bg); dot(night.second, p.accent, dp(context, 13f), p.bg) }
            else dot(now, p.accent, dp(context, 9f), p.bg)
        }
    }

    private fun screen(c: Context, p: Pal) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(p.bg)
        setPadding(px(c, 16), px(c, 8), px(c, 16), px(c, 24))
    }

    private fun touch(c: Context, v: View) = LinearLayout(c).apply { gravity = Gravity.CENTER; addView(v); layoutParams = LinearLayout.LayoutParams(px(c, 48), px(c, 48)) }

    private fun legend(c: Context, color: Int, text: String, p: Pal) = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(View(c).apply { background = box(c, color, 999f) }, LinearLayout.LayoutParams(px(c, 10), px(c, 10)))
        addView(tv(c, text, 13f, p.muted).apply { setPadding(px(c, 6), 0, px(c, 14), 0) })
    }

    fun home(c: Context): View {
        val p = pal(c)
        val s = screen(c, p)
        s.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 64)
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(px(c, 32), px(c, 32)))
            add(tv(c, "Lúcido", 20f, p.text, MEDIUM).apply { setPadding(px(c, 10), 0, 0, 0) }, w = 0, weight = 1f)
            addView(touch(c, icon(c, R.drawable.ic_settings, p.muted)))
        })
        val dial = android.widget.FrameLayout(c).apply {
            addView(DayDial(c, p, 23.5f to 7f, 9f to 13f, 21f + 40 / 60f, 38 / 60f), android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(col(c,
                tv(c, "38 min", 34f, p.text, MEDIUM, tnum = true).apply { gravity = Gravity.CENTER },
                tv(c, "de 1 h hoy", 14f, p.muted).apply { gravity = Gravity.CENTER },
                gap(c, h = 6),
                tv(c, "21:40", 13f, p.onAccent, MEDIUM, tnum = true).apply {
                    gravity = Gravity.CENTER; background = box(c, p.accent, 999f); setPadding(px(c, 10), px(c, 3), px(c, 10), px(c, 3))
                },
            ).apply { gravity = Gravity.CENTER }, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        s.add(dial, h = px(c, 300), top = 4)
        s.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER
            addView(legend(c, p.night, "Noche 23:30–7:00", p))
            addView(legend(c, p.primary, "Foco 9–13", p))
            addView(legend(c, p.accent, "Ahora", p))
        }, top = 4)
        s.add(tv(c, "En 1 h 50 min empieza la noche: Instagram, solo mensajes; YouTube, cerrado.", 15f, p.text).apply {
            gravity = Gravity.CENTER; setPadding(px(c, 16), 0, px(c, 16), 0)
        }, top = 16)

        s.add(LinearLayout(c).apply {
            fun app(ic: Int, name: String, sub: String) = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = box(c, p.surface, 20f)
                setPadding(px(c, 14), px(c, 14), px(c, 14), px(c, 14))
                addView(ImageView(c).apply { setImageResource(ic) }, LinearLayout.LayoutParams(px(c, 36), px(c, 36)))
                add(col(c, tv(c, name, 16f, p.text, MEDIUM), tv(c, sub, 13f, p.muted)).apply { setPadding(px(c, 10), 0, 0, 0) }, w = 0, weight = 1f)
            }
            add(app(R.mipmap.ic_ig, "Instagram", "Reels hasta tu límite"), w = 0, weight = 1f)
            addView(gap(c, w = 12))
            add(app(R.mipmap.ic_yt, "YouTube", "Shorts hasta tu límite"), w = 0, weight = 1f)
        }, top = 20)

        s.add(col(c,
            row(c, p, badge(c, R.drawable.ic_moon, p.night, 0xFFFFFF.argb()), "Modo dormir", "Esta noche, de 23:30 a 7:00"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_focus, p.primary, p.onPrimary), "Modo foco", "Lunes a viernes, de 9:00 a 13:00"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_flag, p.accent, p.onAccent), "Compromiso", "12 días · 2 pausas · 4 días seguidos"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_settings, p.surface2, p.muted), "Instagram y YouTube", "Feed, videos y apps oficiales"),
        ).apply { background = box(c, p.surface, 24f); setPadding(0, px(c, 4), 0, px(c, 4)) }, top = 12)
        return s
    }

    fun sleep(c: Context): View {
        val p = pal(c)
        val s = screen(c, p)
        s.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 64)
            addView(touch(c, Glyph(c, "back", p.text)))
            add(tv(c, "Modo dormir", 22f, p.text, MEDIUM).apply { setPadding(px(c, 4), 0, 0, 0) }, w = 0, weight = 1f)
            addView(Switch3(c, p, true))
        })
        s.add(tv(c, "En tu horario, Instagram queda solo para mensajes y YouTube se bloquea.", 15f, p.muted).apply { setPadding(px(c, 4), 0, px(c, 4), 0) }, top = 4)
        s.add(DayDial(c, p, 23.5f to 7f, null, 21.6f, null, handles = true), h = px(c, 260), top = 12)
        s.add(LinearLayout(c).apply {
            fun t(l: String, v: String, color: Int) = col(c, LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(View(c).apply { background = box(c, color, 999f) }, LinearLayout.LayoutParams(px(c, 10), px(c, 10)))
                addView(tv(c, l, 14f, p.muted).apply { setPadding(px(c, 6), 0, 0, 0) })
            }, gap(c, h = 4), tv(c, v, 32f, p.text, MEDIUM, tnum = true)).apply {
                background = box(c, p.surface, 20f); setPadding(px(c, 16), px(c, 14), px(c, 16), px(c, 14))
            }
            add(t("Me acuesto", "23:30", p.night), w = 0, weight = 1f)
            addView(gap(c, w = 12))
            add(t("Me levanto", "7:00", p.accent), w = 0, weight = 1f)
        }, top = 8)
        s.add(col(c,
            row(c, p, gap(c), "Horario distinto por día", "Por ejemplo, más tarde los viernes", trail = Switch3(c, p, false)).apply { setPadding(px(c, 4), px(c, 10), px(c, 16), px(c, 10)) },
            divider(c, p.line, 20),
            row(c, p, gap(c), "Bloquear navegadores", "Chrome, Firefox", trail = Switch3(c, p, true)).apply { setPadding(px(c, 4), px(c, 10), px(c, 16), px(c, 10)) },
        ).apply { background = box(c, p.surface, 24f) }, top = 12)
        s.add(tv(c, "Aviso antes de empezar", 16f, p.text, MEDIUM).apply { setPadding(px(c, 4), 0, 0, 0) }, top = 24)
        s.add(com.brainrotless.app.FlowLayout(c).apply {
            listOf("Sin aviso", "5 min", "10 min", "15 min", "30 min").forEachIndexed { i, l -> addView(choice(c, p, l, i == 3)) }
        }, top = 12)
        return s
    }
}
