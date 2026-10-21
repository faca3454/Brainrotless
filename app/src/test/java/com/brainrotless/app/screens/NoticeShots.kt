package com.brainrotless.app.screens

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.brainrotless.app.Brand
import com.brainrotless.app.Notices
import com.brainrotless.app.R
import org.junit.Test

/** Avisos de IG Lite y YT Lite (MainActivity), como los arma y completa la pantalla. */
class NoticeShots : Shots(theme = "AppTheme") {
    private fun screen(c: Context, title: String, text: String, button: String, pause: String? = null) =
        Notices.screen(c, title, text, button, if (pause != null) "" else null) {}.apply {
            visibility = View.VISIBLE
            pause?.let { findViewWithTag<TextView>("button2").apply { this.text = it; visibility = View.VISIBLE } }
        }

    @Test fun sinConexion() = shot("sin_conexion", listOf(BASE, DARK, EN, WORST)) { c ->
        screen(c, c.getString(R.string.offline_title), c.getString(R.string.offline_text), c.getString(R.string.retry))
    }

    private fun limit(c: Context) =
        screen(c, c.getString(R.string.lock_limit_title), c.getString(R.string.lock_limit_text, "4:00"), c.getString(R.string.go_to_messages),
            pause = c.getString(R.string.lock_pause_button, c.resources.getQuantityString(R.plurals.left_n, 2, 2)))

    @Test fun youtubeLimite() = shot("youtube_limite", ALL) { c -> limit(c) }

    /**
     * Acostado, en un teléfono chico, en inglés y con letra al 130 %: no entra, así que queda con
     * scroll en vez de aplastar (o hacer desaparecer) el botón de pausa. Con el alto real de la
     * pantalla: si no, la captura crece hasta mostrar todo.
     */
    @Test fun youtubeLimiteAcostado() = shot("youtube_limite", listOf(TURNED)) { c ->
        FrameLayout(c).apply {
            addView(limit(c), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                Brand.px(c, c.resources.configuration.screenHeightDp - 24)))
        }
    }

    @Test fun youtubeNoche() = shot("youtube_noche", listOf(BASE, DARK, EN)) { c ->
        screen(c, c.getString(R.string.lock_night_title), c.getString(R.string.lock_blocked_text, "7:00"), c.getString(R.string.go_to_messages))
    }

    @Test fun youtubeFoco() = shot("youtube_foco", listOf(BASE, DARK)) { c ->
        screen(c, c.getString(R.string.lock_focus_title), c.getString(R.string.lock_blocked_text, "13:00"), c.getString(R.string.go_to_messages))
    }

    @Test fun accesibilidadApagada() = shot("accesibilidad_apagada", listOf(BASE, DARK, EN, WORST)) { c ->
        screen(c, c.getString(R.string.guard_off_title), c.getString(R.string.guard_off_text), c.getString(R.string.turn_on))
    }

    /** La barra de "solo mensajes" arriba de Instagram (con la pausa), y la de la pausa en curso. */
    @Test fun barra() = shot("barra", ALL) { c ->
        fun bar(text: String, pause: Boolean) = Notices.lockBar(c) {}.apply {
            visibility = View.VISIBLE
            findViewWithTag<TextView>("text").text = text
            findViewWithTag<TextView>("pause").apply {
                visibility = if (pause) View.VISIBLE else View.GONE
                this.text = c.getString(R.string.pause_chip, 2)
            }
        }
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar(c.getString(R.string.ig_limit_msg, "4:00"), pause = true))
            addView(FrameLayout(c), LinearLayout.LayoutParams(1, Brand.px(c, 24)))
            addView(bar(c.getString(R.string.ig_night_msg, "7:00"), pause = false))
            addView(FrameLayout(c), LinearLayout.LayoutParams(1, Brand.px(c, 24)))
            addView(bar(c.getString(R.string.pause_bar, "18:15"), pause = false))
        }
    }
}
