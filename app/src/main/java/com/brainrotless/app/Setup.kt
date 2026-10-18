package com.brainrotless.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

    fun openNotifications(c: Context) =
        open(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openAccessibility(c: Context) =
        open(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openLinks(c: Context) {
        val pkg = Uri.parse("package:${c.packageName}")
        val i = if (Build.VERSION.SDK_INT >= 31)
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
        else
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        open(c, i)
    }

    private fun open(c: Context, i: Intent) {
        runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
