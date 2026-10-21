package com.brainrotless.app.screens

import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.brainrotless.app.Brand
import com.brainrotless.app.R
import org.junit.Rule
import java.util.TimeZone

/**
 * Base de las capturas de Paparazzi: cada pantalla se dibuja con datos de prueba en las
 * variantes que importan (claro/oscuro, español/inglés, teléfono chico, letra grande).
 * `./gradlew recordPaparazziDebug` las genera y `verifyPaparazziDebug` las compara.
 */
abstract class Shots(theme: String = "HomeTheme") {
    /** Una variante: cómo se ve el teléfono. */
    data class Look(val name: String, val dark: Boolean = false, val en: Boolean = false, val small: Boolean = false, val font: Float = 1f,
                    val landscape: Boolean = false)

    companion object {
        val BASE = Look("claro")
        val DARK = Look("oscuro", dark = true)
        val EN = Look("en", en = true)
        val SMALL = Look("chico", small = true)
        val BIG_FONT = Look("letra130", font = 1.3f)
        val WORST = Look("chico_letra130_en", en = true, small = true, font = 1.3f)
        val ALL = listOf(BASE, DARK, EN, SMALL, BIG_FONT, WORST)
        val TURNED = Look("acostado_chico_letra130_en", en = true, small = true, font = 1.3f, landscape = true)

        // A 2x (no a la densidad real): las imágenes se leen bien y pesan menos en el repo
        private val PHONE = DeviceConfig.PIXEL_5.copy(screenWidth = 786, screenHeight = 1702, xdpi = 320, ydpi = 320, density = Density.XHIGH)
        private val SMALL_PHONE = DeviceConfig.NEXUS_5.copy(screenWidth = 720, screenHeight = 1280, xdpi = 320, ydpi = 320, density = Density.XHIGH)
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = PHONE,
        theme = theme,
        // Toda la pantalla, aunque sea más larga que el teléfono
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        useDeviceResolution = true,
    )

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
    }

    fun shot(name: String, looks: List<Look>, draw: (Context) -> View) {
        for (look in looks) {
            val phone = if (look.small) SMALL_PHONE else PHONE
            // Acostado: ancho y alto se dan vuelta
            val device = if (!look.landscape) phone else phone.copy(
                screenWidth = phone.screenHeight, screenHeight = phone.screenWidth, orientation = ScreenOrientation.LANDSCAPE)
            paparazzi.unsafeUpdateConfig(deviceConfig = device.copy(
                nightMode = if (look.dark) NightMode.NIGHT else NightMode.NOTNIGHT,
                locale = if (look.en) "en" else "es",
                fontScale = look.font,
            ))
            val c = IconFix(paparazzi.context)
            paparazzi.snapshot(FrameLayout(c).apply {
                setBackgroundColor(Brand.color(c, R.color.brand_bg))
                addView(draw(c).also(::runPosted))
                endAnimations()
            }, "${name}_${look.name}")
        }
    }
}

/**
 * Paparazzi dibuja un solo cuadro y no llega a correr lo que las vistas dejaron con post {} (ahí
 * arrancan los anillos y las barras): se corre a mano, en orden, antes de la captura.
 */
private fun runPosted(v: View) {
    val queue = View::class.java.getDeclaredField("mRunQueue").apply { isAccessible = true }.get(v)
    if (queue != null) {
        fun field(name: String) = queue.javaClass.getDeclaredField(name).apply { isAccessible = true }
        val count = field("mCount").getInt(queue)
        val actions = field("mActions").get(queue) as Array<*>?
        val action = Class.forName("android.view.HandlerActionQueue\$HandlerAction").getDeclaredField("action").apply { isAccessible = true }
        for (i in 0 until count) (action.get(actions!![i]) as Runnable).run()
        field("mCount").setInt(queue, 0)
    }
    if (v is ViewGroup) for (i in 0 until v.childCount) runPosted(v.getChildAt(i))
}

/** Termina las animaciones que arrancaron (anillos, barras), como si ya hubieran pasado. */
private fun endAnimations() {
    val handler = Class.forName("android.animation.AnimationHandler")
    val instance = handler.getMethod("getInstance").invoke(null)
    val running = handler.getDeclaredField("mAnimationCallbacks").apply { isAccessible = true }.get(instance) as List<*>
    running.filterIsInstance<ValueAnimator>().forEach { it.end() }
}

/**
 * layoutlib no sabe dibujar íconos adaptativos (le falta la máscara del sistema): en las pruebas,
 * los de Instagram y YouTube se arman con su fondo y su frente, sin recortar.
 */
private class IconFix(base: Context) : ContextWrapper(base) {
    private val icons = mapOf(
        R.mipmap.ic_ig to (R.drawable.ic_ig_bg to R.drawable.ic_ig_fg),
        R.mipmap.ic_yt to (R.drawable.ic_yt_bg to R.drawable.ic_yt_fg),
    )
    private val res = object : Resources(base.assets, base.resources.displayMetrics, base.resources.configuration) {
        override fun getDrawable(id: Int, theme: Theme?): Drawable = icons[id]?.let { (bg, fg) ->
            LayerDrawable(arrayOf(base.resources.getDrawable(bg, theme), base.resources.getDrawable(fg, theme)))
        } ?: base.resources.getDrawable(id, theme)
    }
    override fun getResources(): Resources = res
}
