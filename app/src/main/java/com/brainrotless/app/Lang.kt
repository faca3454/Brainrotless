package com.brainrotless.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Idioma de la app: español o inglés. Se elige la primera vez que se abre y se cambia en Ajustes.
 * Hasta elegir, sigue al teléfono (español si está en español; si no, inglés).
 */
object Lang {
    private const val KEY = "language"

    private fun chosen(c: Context) = c.userPrefs().getString(KEY, null)
    fun hasChosen(c: Context) = chosen(c) != null
    fun current(c: Context) = chosen(c) ?: if (Locale.getDefault().language == "es") "es" else "en"
    private fun locale(c: Context) = Locale(current(c))

    /** Contexto con el idioma elegido (para las Activities, en attachBaseContext). */
    fun wrap(base: Context): Context {
        val loc = locale(base)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration).apply { setLocale(loc) }
        return base.createConfigurationContext(cfg)
    }

    /**
     * Aplica el idioma a unos Resources ya creados: los de la app (que usan servicios,
     * notificaciones y el botón de ajustes rápidos) o los de una Activity después de crear una
     * WebView, que en algunas versiones de Android los devuelve al idioma del sistema.
     */
    @Suppress("DEPRECATION")
    fun applyTo(c: Context, res: Resources = c.applicationContext.resources) {
        val loc = locale(c)
        Locale.setDefault(loc)
        val cfg = Configuration(res.configuration).apply { setLocale(loc) }
        res.updateConfiguration(cfg, res.displayMetrics)
    }

    fun set(a: Activity, lang: String) {
        a.userPrefs().edit().putString(KEY, lang).apply()
        applyTo(a)
        a.recreate()
    }

    /** La primera vez: elegir idioma (se muestra en los dos, para que se entienda igual). */
    fun askIfNeeded(a: Activity) {
        if (chosen(a) != null) return
        AlertDialog.Builder(a)
            .setTitle("Idioma · Language")
            .setItems(arrayOf("Español", "English")) { _, which -> set(a, if (which == 0) "es" else "en") }
            .setCancelable(false)
            .show()
    }
}

/** Aplica el idioma elegido apenas arranca el proceso (también si lo despierta una alarma o un servicio). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.applyTo(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Lang.applyTo(this) // el sistema repone su idioma al girar o cambiar de tema
        Widgets.update(this, force = true) // claro/oscuro: redibujar el anillo con los colores nuevos
    }
}
