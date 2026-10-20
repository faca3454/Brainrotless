package com.brainrotless.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager

const val PKG = "com.brainrotless.app"
const val REPO = "faca3454/Brainrotless"

/** Preferencias de usuario (las que se editan en Ajustes). */
fun Context.userPrefs() = PreferenceManager.getDefaultSharedPreferences(this)

/** Estado de permisos y accesos directos a cada pantalla del sistema. */
object Setup {
    fun listenerOn(c: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun guardOn(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.startsWith("${c.packageName}/") && it.endsWith("AppGuard") }
    }

    fun openNotifications(c: Context) {
        // Directo al interruptor de Brainrotless (Android 11+); si no, la lista
        val cn = ComponentName(c, NotifRedirect::class.java).flattenToString()
        if (Build.VERSION.SDK_INT >= 30 && open(
                c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn)
            )
        ) return
        open(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun openAccessibility(c: Context) {
        // No hay acceso directo público al servicio; estos extras lo resaltan en la lista
        // (Android puro y varias marcas; las demás los ignoran)
        val key = ComponentName(c, AppGuard::class.java).flattenToString()
        open(
            c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", key)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        )
    }

    /** Android 13+ deja grises los permisos sensibles en apps instaladas fuera del Store. */
    fun restrictedApplies() = Build.VERSION.SDK_INT >= 33

    /** Explica cómo destrabar un permiso gris y lleva a la info de la app, donde está la opción. */
    fun explainRestricted(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("¿Un permiso aparece gris?")
            .setMessage(
                "Android lo bloquea en apps instaladas fuera del Play Store.\n\n" +
                    "1. Si todavía no lo hiciste, tocá el permiso gris una vez y aceptá el aviso " +
                    "\"Ajuste restringido\". Sin eso, la opción del paso 2 no aparece.\n\n" +
                    "2. Tocá Abrir. En la info de Brainrotless, tocá ⋮ (arriba a la derecha) → " +
                    "\"Permitir ajustes restringidos\".\n\n" +
                    "3. Volvé y activá el permiso."
            )
            .setPositiveButton("Abrir") { _, _ ->
                open(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun openLinks(c: Context) {
        val pkg = Uri.parse("package:${c.packageName}")
        val i = if (Build.VERSION.SDK_INT >= 31)
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
        else
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        open(c, i)
    }

    private fun open(c: Context, i: Intent) =
        runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
