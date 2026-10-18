package com.brainrotless.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Avisa cuando hay un APK nuevo en GitHub Releases. */
object Updater {
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L

    private data class Release(val version: String, val apkUrl: String, val notes: String)

    fun maybeCheck(a: Activity, force: Boolean = false) {
        val current = BuildConfig.VERSION_NAME
        if (!force) {
            if (!a.userPrefs().getBoolean("check_updates", true)) return
            if (current.startsWith("dev")) return // builds de prueba no se actualizan solos
            val meta = a.getSharedPreferences("updater", Context.MODE_PRIVATE)
            if (System.currentTimeMillis() - meta.getLong("last_check", 0) < CHECK_EVERY_MS) return
        }

        thread(name = "update-check") {
            val rel = runCatching { fetchLatest() }.getOrNull()
            a.getSharedPreferences("updater", Context.MODE_PRIVATE).edit()
                .putLong("last_check", System.currentTimeMillis()).apply()

            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                when {
                    rel == null ->
                        if (force) toast(a, "No se pudo verificar. ¿Hay internet?")
                    isNewer(rel.version, current) -> showDialog(a, rel)
                    force -> toast(a, "Ya tenés la última versión ($current)")
                }
            }
        }
    }

    private fun showDialog(a: Activity, rel: Release) {
        AlertDialog.Builder(a)
            .setTitle("Brainrotless ${rel.version} disponible")
            .setMessage(
                (rel.notes.take(600).ifBlank { "Hay una versión nueva." }) +
                    "\n\nSe descarga el APK; abrilo para instalar encima (no perdés la sesión)."
            )
            .setPositiveButton("Descargar") { _, _ ->
                runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl))) }
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun fetchLatest(): Release? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apk = asset.optString("browser_download_url"); break
                }
            }
            if (tag.isBlank() || apk.isNullOrBlank()) return null
            return Release(tag, apk, json.optString("body"))
        } finally {
            conn.disconnect()
        }
    }

    /** Compara "1.2.10" vs "1.2.9" número por número. */
    fun isNewer(remote: String, local: String): Boolean {
        if (local.startsWith("dev")) return true
        val r = remote.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val d = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
}
