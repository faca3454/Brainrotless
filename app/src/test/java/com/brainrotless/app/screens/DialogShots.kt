package com.brainrotless.app.screens

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.brainrotless.app.Brand
import com.brainrotless.app.Dialogs
import com.brainrotless.app.PanelScreen
import com.brainrotless.app.R
import org.junit.Test
import java.util.Date

/** Un diálogo como se ve en la pantalla: su ventana, centrada, sobre el fondo oscurecido. */
fun dialog(c: Context, b: AlertDialog.Builder): View {
    val d = b.create()
    d.create()
    return FrameLayout(c).apply {
        setBackgroundColor(0x99000000.toInt())
        val m = Brand.px(c, 24)
        setPadding(m, Brand.px(c, 120), m, Brand.px(c, 120))
        addView(d.window!!.decorView.apply { (parent as? ViewGroup)?.removeView(this) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

private val SUMMARY = "Terminaste tu compromiso de 14 días.\n\nUsaste 41 min por día (antes, 1 h 35 min).\nCumpliste el límite 12 de 14 días."

/** Diálogos del inicio y de IG Lite / YT Lite (tema claro de la app). */
class DialogShots : Shots(theme = "HomeTheme") {
    private val looks = listOf(BASE, DARK, EN, WORST)

    @Test fun resumen() = shot("resumen", looks) { c -> dialog(c, Dialogs.summary(c, SUMMARY, R.string.not_now, {}, {})) }
    @Test fun sesion() = shot("sesion", looks) { c -> dialog(c, Dialogs.session(c, 20) {}) }
    @Test fun permisoGris() = shot("permiso_gris", looks) { c -> dialog(c, Dialogs.restricted(c) {}) }
    @Test fun idioma() = shot("idioma", listOf(BASE, DARK)) { c -> dialog(c, Dialogs.language(c) {}) }
    @Test fun actualizacion() = shot("actualizacion", listOf(BASE, DARK, WORST)) { c ->
        dialog(c, Dialogs.update(c, "1.4.0", "• Citas en los chats\n• Permisos a demanda") {})
    }
}

/** Diálogos del panel (tema AppCompat que sigue al sistema). */
class PanelDialogShots : Shots(theme = "PanelTheme") {
    private val looks = listOf(BASE, DARK, EN, WORST)

    @Test fun confirmar() = shot("confirmar_compromiso", looks) { c ->
        dialog(c, Dialogs.confirmCommitment(c, 14, Date(1_793_000_000_000L), PanelScreen.rules(c, true, true, 60)) {})
    }
    @Test fun comparar() = shot("comparar", looks) { c -> dialog(c, Dialogs.offerCompare(c) {}) }
    @Test fun resumen() = shot("resumen", listOf(BASE, DARK)) { c -> dialog(c, Dialogs.summary(c, SUMMARY, R.string.done, {}, null)) }
}
