package com.brainrotless.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock

/**
 * Apps cuyo propósito es navegar: las que se declaran navegador o abren cualquier link web.
 * Se detectan solas, así que entran también los que se instalen después.
 */
object Browsers {
    // Abren links pero no son navegadores
    private val notBrowsers = setOf(PKG, "com.google.android.googlequicksearchbox")

    private var cache: Set<String> = emptySet()
    private var cachedAt = -1L

    fun packages(c: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (cachedAt >= 0 && now - cachedAt < 60_000) return cache
        val pm = c.packageManager
        val declared = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        // Un dominio inventado: solo lo abre quien abre cualquier link, no las apps de un sitio
        val anyLink = Intent(Intent.ACTION_VIEW, Uri.parse("https://brainrotless.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        cache = listOf(declared, anyLink)
            .flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_ALL) }
            .map { it.activityInfo.packageName }
            .filterNot { it in notBrowsers }
            .toSet()
        cachedAt = now
        return cache
    }

    /** Nombres para mostrar en Ajustes. */
    fun names(c: Context): List<String> {
        val pm = c.packageManager
        return packages(c).mapNotNull { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }.sorted()
    }
}
