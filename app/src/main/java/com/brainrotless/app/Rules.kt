package com.brainrotless.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Reglas de bloqueo (block.user.js).
 *
 * La app trae una copia incluida y, si está activado en Ajustes, descarga la versión
 * de la rama main del repo. La descargada solo se usa si su @version es igual o
 * mayor que la incluida, así un APK nuevo nunca queda pisado por reglas viejas.
 */
object Rules {
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/$REPO/main/app/src/main/assets/block.user.js"
    private const val CACHE_FILE = "rules.js"
    private const val MAX_BYTES = 256 * 1024
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    fun version(js: String): Int =
        Regex("@version\\s+(\\d+)").find(js)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun bundled(c: Context): String =
        c.assets.open("block.user.js").bufferedReader().use { it.readText() }

    /** Script de reglas que corresponde usar ahora. */
    fun current(c: Context): String {
        val base = bundled(c)
        if (!c.userPrefs().getBoolean("remote_rules", true)) return base
        val cached = File(c.filesDir, CACHE_FILE)
        if (cached.exists()) {
            val remote = runCatching { cached.readText() }.getOrNull()
            if (remote != null && version(remote) >= version(base)) return remote
        }
        return base
    }

    /** Script final que se inyecta: configuración de Ajustes + reglas. */
    fun build(c: Context): String {
        Setup.migratePrefs(c)
        val p = c.userPrefs()
        val reels = Lock.reelsAllowed(c)
        val cfg = JSONObject()
            .put("igFeed", p.getString("ig_feed", "normal"))
            .put("igBlockExplore", p.getBoolean("ig_block_explore", true))
            .put("igTapToPlay", p.getBoolean("ig_tap_to_play", false))
            .put("igReels", reels)
            .put("ytShorts", reels)
            .put("smooth", p.getBoolean("smooth_transitions", true))
            .put("swipeReply", p.getBoolean("swipe_reply", true))
        // Hora de dormir o límite diario: Instagram solo para mensajes
        Lock.ig(c)?.let { cfg.put("restricted", true).put("restrictedMsg", Lock.igMessage(c, it)) }
        return "window.__noscrollCfg=$cfg;\n" + current(c)
    }

    /** Busca reglas nuevas en segundo plano; llama a onUpdated (en otro hilo) si cambiaron. */
    fun refreshAsync(c: Context, force: Boolean = false, onUpdated: () -> Unit) {
        val app = c.applicationContext
        if (!app.userPrefs().getBoolean("remote_rules", true)) return
        val meta = app.getSharedPreferences("rules_meta", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - meta.getLong("last_check", 0) < CHECK_EVERY_MS) return

        thread(name = "rules-update") {
            val body = runCatching { download() }.getOrNull() ?: return@thread
            meta.edit().putLong("last_check", now).apply()

            // Validación mínima: que sea nuestro script y no más viejo que el incluido
            val valid = body.contains("==UserScript==") &&
                body.contains("__noscroll") &&
                version(body) >= version(bundled(app))
            if (!valid || body == current(app)) return@thread

            val tmp = File(app.filesDir, "$CACHE_FILE.tmp")
            tmp.writeText(body)
            if (tmp.renameTo(File(app.filesDir, CACHE_FILE))) onUpdated()
        }
    }

    private fun download(): String? {
        val conn = (URL(REMOTE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            useCaches = false
        }
        try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
