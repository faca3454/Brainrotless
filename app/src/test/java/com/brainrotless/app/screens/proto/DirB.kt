package com.brainrotless.app.screens.proto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.brainrotless.app.R

/**
 * Dirección B, "Señalética": como los carteles del subte. Una banda de color profundo arriba con
 * el número del día en grande, y cada modo como una "línea" con su disco de color. Filas, no
 * tarjetas; estados que se leen de lejos.
 */
object DirB {
    private val LIGHT = Pal(
        bg = 0xF6F3EC.argb(), surface = 0x0F3B3A.argb(), surface2 = 0xE9E4DA.argb(), text = 0x111A19.argb(), muted = 0x4E5B58.argb(),
        primary = 0x0F6E63.argb(), onPrimary = 0xFFFFFF.argb(), container = 0xFBF3E6.argb(), onContainer = 0xFBF3E6.argb(),
        accent = 0xF4A93D.argb(), onAccent = 0x2B1A00.argb(), error = 0xB3361C.argb(), line = 0xDAD4C8.argb(), night = 0x34449A.argb(),
    )
    private val DARK = Pal(
        bg = 0x0A1314.argb(), surface = 0x12403E.argb(), surface2 = 0x1A2627.argb(), text = 0xF1ECE3.argb(), muted = 0xA6B3B0.argb(),
        primary = 0x56C5B3.argb(), onPrimary = 0x04201C.argb(), container = 0xFBF3E6.argb(), onContainer = 0xFBF3E6.argb(),
        accent = 0xF5B55C.argb(), onAccent = 0x2B1A00.argb(), error = 0xF28B6E.argb(), line = 0x223234.argb(), night = 0x8C99E0.argb(),
    )
    private const val FOCUS = 0x0F8A7A
    fun pal(c: Context) = if (isNight(c)) DARK else LIGHT

    /** Barra gruesa de la banda: cuánto del límite se usó, con la marca del límite al final. */
    class Track(c: Context, private val f: Float, private val fill: Int, private val bg: Int) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), px(context, 12))
        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            paint.color = bg; canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
            paint.color = fill; canvas.drawRoundRect(0f, 0f, width * f, height.toFloat(), r, r, paint)
        }
    }

    private fun disc(c: Context, res: Int, color: Int) = badge(c, res, color, 0xFFFFFF.argb(), 44, 11)

    private fun band(c: Context, p: Pal) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(p.surface)
        setPadding(px(c, 20), px(c, 8), px(c, 20), px(c, 24))
    }

    private fun cream(alpha: Int) = (alpha shl 24) or 0xFBF3E6

    fun home(c: Context): View {
        val p = pal(c)
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        val b = band(c, p)
        b.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 56)
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(px(c, 32), px(c, 32)))
            add(tv(c, "Lúcido", 18f, cream(255), MEDIUM).apply { setPadding(px(c, 10), 0, 0, 0) }, w = 0, weight = 1f)
            addView(LinearLayout(c).apply { gravity = Gravity.CENTER; addView(icon(c, R.drawable.ic_settings, cream(220))) },
                LinearLayout.LayoutParams(px(c, 48), px(c, 48)))
        })
        b.add(tv(c, "Hoy en Instagram y YouTube", 15f, cream(200)), top = 12)
        b.add(LinearLayout(c).apply {
            gravity = Gravity.BOTTOM
            addView(tv(c, "38", 80f, cream(255), CONDENSED, tnum = true))
            addView(tv(c, " min", 30f, cream(255), CONDENSED).apply { setPadding(0, 0, 0, px(c, 10)) })
            add(gap(c), w = 0, weight = 1f)
            addView(tv(c, "de 1 h", 22f, cream(200), CONDENSED).apply { setPadding(0, 0, 0, px(c, 10)) })
        }, top = 2)
        b.add(Track(c, 38 / 60f, p.accent, cream(40)), top = 8)
        b.add(tv(c, "Se reinicia a las 4:00 · 4 días seguidos dentro del límite", 14f, cream(200)), top = 10)
        b.add(LinearLayout(c).apply {
            fun app(ic: Int, name: String, sub: String) = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = box(c, cream(24), 16f)
                setPadding(px(c, 12), px(c, 12), px(c, 12), px(c, 12))
                addView(ImageView(c).apply { setImageResource(ic) }, LinearLayout.LayoutParams(px(c, 36), px(c, 36)))
                add(col(c, tv(c, name, 16f, cream(255), MEDIUM), tv(c, sub, 12.5f, cream(190))).apply { setPadding(px(c, 10), 0, 0, 0) },
                    w = 0, weight = 1f)
            }
            add(app(R.mipmap.ic_ig, "Instagram", "Reels hasta tu límite"), w = 0, weight = 1f)
            addView(gap(c, w = 10))
            add(app(R.mipmap.ic_yt, "YouTube", "Shorts hasta tu límite"), w = 0, weight = 1f)
        }, top = 20)
        root.add(b)

        // Cada modo, una línea con su color
        fun line(res: Int, color: Int, title: String, sub: String) =
            row(c, p, disc(c, res, color), title, sub, titleSp = 19f, face = CONDENSED).apply { setPadding(px(c, 20), px(c, 12), px(c, 16), px(c, 12)) }
        root.add(line(R.drawable.ic_moon, p.night, "Modo dormir", "Esta noche, de 23:30 a 7:00"), top = 8)
        root.add(divider(c, p.line, 84))
        root.add(line(R.drawable.ic_focus, FOCUS.argb(), "Modo foco", "Jueves, de 9:00 a 13:00"))
        root.add(divider(c, p.line, 84))
        root.add(line(R.drawable.ic_flag, 0xC9811A.argb(), "Compromiso", "Faltan 12 días · 2 pausas de emergencia"))
        root.add(divider(c, p.line, 84))
        root.add(line(R.drawable.ic_settings, 0x5E6B68.argb(), "Instagram y YouTube", "Feed, videos y apps oficiales"))
        return root
    }

    fun commitment(c: Context): View {
        val p = pal(c)
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        val b = band(c, p)
        b.add(LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 56)
            addView(LinearLayout(c).apply { gravity = Gravity.CENTER; addView(Glyph(c, "back", cream(255))) },
                LinearLayout.LayoutParams(px(c, 48), px(c, 48)))
            addView(tv(c, "Compromiso", 18f, cream(255), MEDIUM))
        })
        b.add(LinearLayout(c).apply {
            gravity = Gravity.BOTTOM
            addView(tv(c, "12", 96f, cream(255), CONDENSED, tnum = true))
            addView(tv(c, " días", 32f, cream(255), CONDENSED).apply { setPadding(0, 0, 0, px(c, 14)) })
        }, top = 4)
        b.add(LinearLayout(c).apply {
            for (i in 0 until 14) add(View(c).apply { background = box(c, if (i < 2) cream(60) else p.accent, 2f) },
                w = 0, h = px(c, 14), weight = 1f).also { if (i < 13) addView(gap(c, w = 4)) }
        }, top = 4)
        b.add(tv(c, "Hasta el 2 de noviembre, 21:40. No se puede aflojar.", 14f, cream(200)), top = 12)
        root.add(b)

        fun stat(title: String, sub: String, value: String, trail: View? = null) = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(c, 72)
            setPadding(px(c, 20), px(c, 12), px(c, 20), px(c, 12))
            add(col(c, tv(c, title, 19f, p.text, CONDENSED), tv(c, sub, 14f, p.muted)), w = 0, weight = 1f)
            addView(tv(c, value, 28f, p.text, CONDENSED, tnum = true))
            trail?.let { addView(gap(c, w = 12)); addView(it) }
        }
        root.add(stat("Hoy", "de 1 h", "38 min"), top = 8)
        root.add(divider(c, p.line, 20))
        root.add(stat("Pausas de emergencia", "Máximo 2 por día", "2"))
        root.add(button(c, "Usar pausa de emergencia", p.text, p.bg).apply {
            (this.layoutParams as? LinearLayout.LayoutParams)
        }.let { btn -> LinearLayout(c).apply { setPadding(px(c, 20), 0, px(c, 20), 0); add(btn) } })
        root.add(tv(c, "Límite diario", 19f, p.text, CONDENSED).apply { setPadding(px(c, 20), 0, 0, 0) }, top = 28)
        root.add(tv(c, "Solo podés bajarlo mientras dure.", 14f, p.muted).apply { setPadding(px(c, 20), 0, 0, 0) }, top = 2)
        root.add(com.brainrotless.app.FlowLayout(c).apply {
            setPadding(px(c, 20), 0, px(c, 20), 0)
            listOf("30 min", "45 min", "1 h", "1 h 30 min", "2 h", "Sin límite").forEachIndexed { i, l ->
                addView(choice(c, p, l, i == 2).apply { if (i > 2) alpha = 0.38f })
            }
        }, top = 12)
        root.add(divider(c, p.line, 20), top = 24)
        root.add(row(c, p, disc(c, R.drawable.ic_flag, 0xC9811A.argb()), "Comparar con tu uso de antes", "Activado: al terminar vas a ver cuánto bajó",
            trail = gap(c), titleSp = 19f, face = CONDENSED).apply { setPadding(px(c, 20), px(c, 12), px(c, 16), px(c, 24)) })
        return root
    }
}
