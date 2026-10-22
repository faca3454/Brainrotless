package com.brainrotless.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/**
 * Herramientas de los APK de prueba ("dev", "dev-<commit>"): las mediciones y diagnósticos del script
 * (assets/devtools.js) y la medición automática de IG Lite y YT Lite, al final de Ajustes. La versión
 * publicada se arma con src/nodevtools, donde nada de esto existe (ver app/build.gradle.kts).
 */
object DevTools {
    private const val EXTRA_BENCH = "bench"
    private const val PERF_REPORT = "Lúcido rendimiento" // así empieza el resumen de mediciones del script
    private var js: String? = null

    /** Va antes de block.user.js en cada página. */
    fun script(c: Context): String =
        js ?: c.assets.open("devtools.js").bufferedReader().use { it.readText() }.also { js = it }

    /** Puente del script de prueba (LucidoDev): copiar un diagnóstico o las mediciones, para pegarlos en el chat. */
    fun attach(v: WebView) = v.addJavascriptInterface(object {
        @JavascriptInterface fun report(text: String) = v.post {
            copy(v.context, text.take(10_000), if (text.startsWith(PERF_REPORT)) R.string.perf_copied else R.string.diag_copied)
        }
    }, "LucidoDev")

    fun copy(c: Context, text: String, msg: Int = R.string.perf_copied) {
        c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Lúcido", text))
        Toast.makeText(c, c.getString(msg), Toast.LENGTH_LONG).show()
    }

    /** Ajustes: "Medir IG Lite y YT Lite", al final. */
    fun addTo(f: PreferenceFragmentCompat) {
        val c = f.requireContext()
        val cat = PreferenceCategory(c).apply { title = c.getString(R.string.dev_cat) }
        f.preferenceScreen.addPreference(cat)
        cat.addPreference(Preference(c).apply {
            title = c.getString(R.string.dev_bench)
            summary = c.getString(R.string.dev_bench_summary)
            setOnPreferenceClickListener {
                c.startActivity(Intent(c, MainActivity::class.java).putExtra(EXTRA_BENCH, true))
                true
            }
        })
    }

    /** MainActivity: se abrió desde Ajustes para medir. */
    fun onIntent(a: MainActivity, i: Intent) {
        if (!i.getBooleanExtra(EXTRA_BENCH, false)) return
        i.removeExtra(EXTRA_BENCH)
        Bench(a).run()
    }
}

/**
 * Recorre IG Lite y YT Lite [ROUNDS] veces, como una persona: abrir, ir a otra página y volver, recargar
 * con el gesto, abrir un video. No abre chats (no marca mensajes como vistos). Muestra los tiempos y los
 * copia. Si salís de la app, se cancela.
 */
private class Bench(val a: MainActivity) {
    private class Left : Exception()
    private val rows = linkedMapOf<String, MutableList<String>>() // paso → un valor por vuelta

    fun run() = a.lifecycleScope.launch {
        Toast.makeText(a, R.string.dev_bench_running, Toast.LENGTH_LONG).show()
        val state = "IG solo mensajes: ${if (Lock.ig(a) != null) "sí" else "no"} · YT bloqueado: ${if (Lock.yt(a) != null) "sí" else "no"}"
        try {
            repeat(ROUNDS) {
                val ig = a.benchShow(youtube = false)
                load("IG abrir el inbox", ig) { ig.loadUrl(MainActivity.IG_INBOX) }
                step("IG ir a solicitudes", ig, "__lucidoGo('/direct/requests/')")
                step("IG atrás", ig, "history.back()")
                load("IG recargar con el gesto", ig) { a.benchReload() }
                if (Lock.yt(a) != null) return@repeat
                val yt = a.benchShow(youtube = true)
                load("YT abrir el inicio", yt) { yt.loadUrl(MainActivity.YT_HOME) }
                if (eval(yt, "!!document.querySelector('a[href^=\"/watch\"]')") != "true") {
                    add("YT abrir un video hasta que arranca", "sin videos en el inicio")
                    return@repeat
                }
                step("YT abrir un video hasta que arranca", yt, "document.querySelector('a[href^=\"/watch\"]').click()", video = true)
                eval(yt, "document.querySelectorAll('video').forEach(m => m.pause())")
                step("YT atrás", yt, "history.back()")
            }
        } catch (e: Left) {
            Toast.makeText(a, R.string.dev_bench_cancelled, Toast.LENGTH_LONG).show()
            return@launch
        }
        a.benchShow(youtube = false)
        val wv = WebViewCompat.getCurrentWebViewPackage(a)?.versionName ?: "?"
        val text = (listOf(
            "Lúcido medición automática · ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · " +
                "Android ${Build.VERSION.RELEASE} · WebView $wv",
            state,
            "ms desde que se pide, un valor por vuelta. + = seguía cambiando a los 10 s, — = no terminó en 20 s, " +
                "tl = tareas largas en el paso (cantidad/ms)",
        ) + rows.map { (k, v) -> "$k: ${v.joinToString(" · ")}" }).joinToString("\n")
        DevTools.copy(a, text)
        AlertDialog.Builder(a).setTitle(R.string.dev_bench_done).setMessage(text)
            .setPositiveButton(R.string.dev_copy_again) { _, _ -> DevTools.copy(a, text) }
            .setNegativeButton(R.string.done, null).show()
    }

    /** Carga entera: desde que se pide hasta el primer dibujo, el contenido (sale la foto) y la página quieta. */
    private suspend fun load(name: String, v: WebView, go: () -> Unit) {
        delay(SETTLE)
        val since = System.currentTimeMillis()
        go()
        val r = result(v, since) ?: return add(name, "—")
        fun ms(k: String) = r.optDouble(k).takeUnless { it.isNaN() }?.let { (it - since).roundToLong().toString() } ?: "?"
        add("$name, primer dibujo", ms("fcp"))
        add("$name, con contenido", ms("ready"))
        add("$name, completa", ms("end") + r.optString("more") + tasks(r))
    }

    /** Un paso dentro de la página ([js]): hasta que queda quieta o, con [video], hasta que arranca. */
    private suspend fun step(name: String, v: WebView, js: String, video: Boolean = false) {
        delay(SETTLE)
        val t0 = eval(v, "window.__lucidoDev ? __lucidoDev.start($video) : null").toDoubleOrNull() ?: return add(name, "—")
        eval(v, js)
        val r = result(v, 0) ?: return add(name, "—")
        val reloaded = if (r.getDouble("origin") > t0) " (recargó)" else "" // no navegó adentro: cargó de nuevo
        add(name, (r.getDouble("end") - t0).roundToLong().toString() + r.optString("more") + reloaded + tasks(r))
    }

    private fun tasks(r: JSONObject) = r.optJSONArray("long")?.let { " tl ${it.optInt(0)}/${it.optInt(1)}" } ?: ""

    private fun add(name: String, value: String) { rows.getOrPut(name) { mutableListOf() }.add(value) }

    /** Le pregunta a la página hasta que terminó el paso (20 s como mucho). */
    private suspend fun result(v: WebView, since: Long): JSONObject? {
        val until = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < until) {
            delay(200)
            if (!a.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) throw Left()
            val r = eval(v, "window.__lucidoDev ? __lucidoDev.result($since) : null")
            if (r.startsWith("{")) return JSONObject(r)
        }
        return null
    }

    private suspend fun eval(v: WebView, js: String): String = suspendCancellableCoroutine { k ->
        v.evaluateJavascript(js) { k.resume(it ?: "null") }
    }

    private companion object {
        const val ROUNDS = 3
        const val SETTLE = 1500L // entre pasos, para que la página termine lo que hace después de quedar quieta
    }
}
