package com.brainrotless.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent

/** Si se abre la app oficial (ícono, recientes, widget, compartir), salta a Brainrotless. */
class AppGuard : AccessibilityService() {

    private val redirects = mapOf(
        "com.instagram.android" to "$PKG.Instagram",
        "com.google.android.youtube" to "$PKG.YouTube"
    )
    private var lastJump = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val alias = redirects[e.packageName?.toString()] ?: return
        if (Bypass.active(this)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastJump < 1500) return // el evento se dispara varias veces seguidas
        lastJump = now

        startActivity(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(this, alias))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onInterrupt() {}
}

/** Pausa temporal del bloqueo (ej: subir una historia con filtros desde la app oficial). */
object Bypass {
    private fun prefs(c: Context) = c.getSharedPreferences("bypass", Context.MODE_PRIVATE)
    private fun minutes(c: Context) = c.userPrefs().getString("bypass_minutes", "10")?.toIntOrNull() ?: 10

    // De noche no hay pausa: las apps oficiales dejarían ver todo
    fun active(c: Context) = !Sleep.active(c) && prefs(c).getLong("until", 0) > System.currentTimeMillis()
    fun start(c: Context) =
        prefs(c).edit().putLong("until", System.currentTimeMillis() + minutes(c) * 60_000L).apply()
    fun stop(c: Context) = prefs(c).edit().putLong("until", 0).apply()
    fun until(c: Context) = prefs(c).getLong("until", 0)
}

/** Botón en los ajustes rápidos: "Apps oficiales" on/off por unos minutos. */
class BypassTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        if (Sleep.active(this)) return refresh()
        if (Bypass.active(this)) Bypass.stop(this) else Bypass.start(this)
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val night = Sleep.current(this)
        t.label = getString(R.string.tile_label)
        t.state = when {
            night != null -> Tile.STATE_UNAVAILABLE
            Bypass.active(this) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= 29)
            t.subtitle = night?.let { "Bloqueado hasta las ${Sleep.fmt(this, it.end.toLocalTime())}" }
        t.updateTile()
    }
}
