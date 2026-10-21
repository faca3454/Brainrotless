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

/**
 * Dirección A, "Amanecer nítido": la identidad de hoy, afinada. Misma paleta con el contraste
 * corregido, una escala de tipos, el plan agrupado en una sola superficie y el sol del logo en
 * la punta del anillo.
 */
object DirA {
    private val LIGHT = Pal(
        bg = 0xFBF7F0.argb(), surface = 0xFFFFFF.argb(), surface2 = 0xF1ECE3.argb(), text = 0x16211F.argb(), muted = 0x56635F.argb(),
        primary = 0x13695F.argb(), onPrimary = 0xFFFFFF.argb(), container = 0xDCEEE8.argb(), onContainer = 0x0B3F39.argb(),
        accent = 0xF2A33A.argb(), onAccent = 0x2B1A00.argb(), error = 0xB23A20.argb(), line = 0xE6DFD3.argb(), night = 0x3B4A7A.argb(),
    )
    private val DARK = Pal(
        bg = 0x0E1A1C.argb(), surface = 0x15262A.argb(), surface2 = 0x1C3135.argb(), text = 0xEEE8DD.argb(), muted = 0xA3B3AF.argb(),
        primary = 0x5CC8B6.argb(), onPrimary = 0x04201C.argb(), container = 0x1C3B39.argb(), onContainer = 0xBFE9E0.argb(),
        accent = 0xF5B55C.argb(), onAccent = 0x2B1A00.argb(), error = 0xF28B6E.argb(), line = 0x24393C.argb(), night = 0x8F9BD6.argb(),
    )
    fun pal(c: Context) = if (isNight(c)) DARK else LIGHT

    /** Anillo con el sol del logo en la punta. */
    class SunRing(c: Context, private val p: Pal, private val f: Float) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val box = RectF()
        override fun onDraw(canvas: Canvas) {
            val w = dp(context, 8f); paint.strokeWidth = w
            box.set(w, w, width - w, height - w)
            paint.color = p.surface2; canvas.drawOval(box, paint)
            paint.color = p.primary; canvas.drawArc(box, -90f, 360f * f, false, paint)
            val a = Math.toRadians(-90.0 + 360 * f)
            val r = box.width() / 2
            paint.style = Paint.Style.FILL; paint.color = p.accent
            canvas.drawCircle(box.centerX() + (r * Math.cos(a)).toFloat(), box.centerY() + (r * Math.sin(a)).toFloat(), dp(context, 7f), paint)
            paint.style = Paint.Style.STROKE
        }
    }

    private fun screen(c: Context, p: Pal) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(p.bg)
        setPadding(px(c, 16), px(c, 8), px(c, 16), px(c, 24))
    }

    private fun card(c: Context, p: Pal, v: View, padDp: Int = 20) = v.apply {
        background = box(c, p.surface, 24f)
        setPadding(px(c, padDp), px(c, padDp), px(c, padDp), px(c, padDp))
    }

    private fun touch(c: Context, v: View) = LinearLayout(c).apply {
        gravity = Gravity.CENTER
        addView(v)
        layoutParams = LinearLayout.LayoutParams(px(c, 48), px(c, 48))
    }

    fun home(c: Context): View {
        val p = pal(c)
        val s = screen(c, p)
        s.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 64)
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(px(c, 36), px(c, 36)))
            add(tv(c, "Lúcido", 22f, p.text, MEDIUM).apply { setPadding(px(c, 12), 0, 0, 0) }, w = 0, weight = 1f)
            addView(touch(c, icon(c, R.drawable.ic_settings, p.muted)))
        })

        // Hoy: el número manda; el anillo y la semana lo acompañan
        val today = col(c,
            LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(SunRing(c, p, 38 / 60f), LinearLayout.LayoutParams(px(c, 84), px(c, 84)))
                add(col(c,
                    tv(c, "Hoy en Instagram y YouTube", 14f, p.muted),
                    gap(c, h = 4),
                    LinearLayout(c).apply {
                        gravity = Gravity.BOTTOM
                        addView(tv(c, "38 min", 36f, p.text, MEDIUM, tnum = true))
                        addView(tv(c, "  de 1 h", 16f, p.muted).apply { setPadding(0, 0, 0, px(c, 5)) })
                    },
                    gap(c, h = 4),
                    tv(c, "Se reinicia a las 4:00", 14f, p.muted),
                ).apply { setPadding(px(c, 20), 0, 0, 0) }, w = 0, weight = 1f)
            },
            gap(c, h = 20),
            WeekBars(c, p, listOf(52L, 38, 71, 44, 29, 61, 38), 60L, listOf("J", "V", "S", "D", "L", "M", "M")),
            gap(c, h = 16),
            LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(tv(c, "4 días seguidos", 13f, p.onAccent, MEDIUM).apply {
                    background = box(c, p.accent, 999f); setPadding(px(c, 12), px(c, 6), px(c, 12), px(c, 6))
                })
                add(gap(c), w = 0, weight = 1f)
                addView(tv(c, "Estadísticas", 14f, p.primary, MEDIUM))
                addView(Glyph(c, "go", p.primary, 20))
            },
        )
        s.add(card(c, p, today), top = 4)

        s.add(LinearLayout(c).apply {
            fun app(ic: Int, name: String, sub: String) = card(c, p, col(c,
                ImageView(c).apply { setImageResource(ic) }.also { it.layoutParams = LinearLayout.LayoutParams(px(c, 40), px(c, 40)) },
                gap(c, h = 12), tv(c, name, 16f, p.text, MEDIUM), gap(c, h = 2), tv(c, sub, 13f, p.muted)), 16)
            add(app(R.mipmap.ic_ig, "Instagram", "Reels hasta tu límite"), w = 0, weight = 1f)
            addView(gap(c, w = 12))
            add(app(R.mipmap.ic_yt, "YouTube", "Shorts hasta tu límite"), w = 0, weight = 1f)
        }, top = 12)

        // El plan: una sola superficie con filas, no una tarjeta por cosa
        val plan = col(c,
            row(c, p, badge(c, R.drawable.ic_moon, p.container, p.primary), "Modo dormir", "Esta noche, de 23:30 a 7:00"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_focus, p.container, p.primary), "Modo foco", "Jueves, de 9:00 a 13:00"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_flag, p.container, p.primary), "Compromiso", "12 días · 2 pausas de emergencia"),
            divider(c, p.line, 72),
            row(c, p, badge(c, R.drawable.ic_settings, p.container, p.primary), "Instagram y YouTube", "Feed, videos y apps oficiales"),
        ).apply { background = box(c, p.surface, 24f); setPadding(0, px(c, 4), 0, px(c, 4)) }
        s.add(plan, top = 12)
        return s
    }

    fun commitment(c: Context): View {
        val p = pal(c)
        val s = screen(c, p)
        s.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 64)
            addView(touch(c, Glyph(c, "back", p.text)))
            addView(tv(c, "Compromiso", 22f, p.text, MEDIUM).apply { setPadding(px(c, 4), 0, 0, 0) })
        })
        // Días: segmentos, uno por día, los que pasaron llenos
        val segments = LinearLayout(c).apply {
            for (i in 0 until 14) add(View(c).apply { background = box(c, if (i < 2) p.primary else p.surface2, 3f) },
                w = 0, h = px(c, 8), weight = 1f).also { if (i < 13) addView(gap(c, w = 3)) }
        }
        s.add(card(c, p, col(c,
            LinearLayout(c).apply {
                gravity = Gravity.BOTTOM
                addView(tv(c, "12", 56f, p.text, MEDIUM, tnum = true))
                addView(tv(c, "  días restantes", 18f, p.muted).apply { setPadding(0, 0, 0, px(c, 9)) })
            },
            gap(c, h = 16), segments, gap(c, h = 12),
            tv(c, "Termina el 2 de noviembre a las 21:40. No se puede aflojar.", 14f, p.muted),
        )), top = 4)

        s.add(LinearLayout(c).apply {
            fun tile(t: String, v: String, sub: String) = col(c, tv(c, t, 13f, p.onContainer), gap(c, h = 4),
                tv(c, v, 24f, p.text, MEDIUM, tnum = true), gap(c, h = 2), tv(c, sub, 13f, p.muted)).apply {
                background = box(c, p.container, 20f); setPadding(px(c, 16), px(c, 14), px(c, 16), px(c, 14))
            }
            add(tile("Hoy", "38 min", "de 1 h"), w = 0, weight = 1f)
            addView(gap(c, w = 12))
            add(tile("Pausas de emergencia", "2", "Máximo 2 por día"), w = 0, weight = 1f)
        }, top = 12)
        s.add(button(c, "Usar pausa de emergencia", p.surface, p.primary, p.line), top = 12)

        s.add(tv(c, "Límite diario", 16f, p.text, MEDIUM).apply { setPadding(px(c, 4), 0, 0, 0) }, top = 24)
        s.add(tv(c, "Solo podés bajarlo mientras dure.", 14f, p.muted).apply { setPadding(px(c, 4), 0, 0, 0) }, top = 4)
        s.add(com.brainrotless.app.FlowLayout(c).apply {
            listOf("30 min", "45 min", "1 h", "1 h 30 min", "2 h", "Sin límite").forEachIndexed { i, l ->
                addView(choice(c, p, l, i == 2).apply { if (i > 2) alpha = 0.38f })
            }
        }, top = 12)

        s.add(card(c, p, row(c, p, badge(c, R.drawable.ic_flag, p.container, p.primary), "Comparar con tu uso de antes",
            "Activado: al terminar vas a ver cuánto bajó", trail = gap(c)).apply { setPadding(0, 0, 0, 0) }, 8), top = 24)
        return s
    }
}
