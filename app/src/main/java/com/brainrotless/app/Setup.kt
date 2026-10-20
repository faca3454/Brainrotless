package com.brainrotless.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
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

    /** Ajustes que cambiaron de forma entre versiones. */
    fun migratePrefs(c: Context) {
        val p = c.userPrefs()
        if (!p.contains("ig_block_feed")) return
        p.edit()
            .putString("ig_feed", if (p.getBoolean("ig_block_feed", false)) "blocked" else "normal")
            .remove("ig_block_feed")
            .apply()
    }

    fun openNotifications(c: Context) {
        // Directo al interruptor de Lúcido (Android 11+); si no, la lista
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
            .setTitle(R.string.gray_title)
            .setMessage(R.string.gray_msg)
            .setPositiveButton(R.string.open) { _, _ ->
                open(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Si los links de Instagram/YouTube ya se abren en Lúcido. null en Android 11 o antes: no se puede saber. */
    fun linksOn(c: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 31) return null
        val state = c.getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(c.packageName) ?: return null
        return state.hostToStateMap.values.any { it != DomainVerificationUserState.DOMAIN_STATE_NONE }
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
