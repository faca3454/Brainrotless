package com.brainrotless.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var web: WebView
    private lateinit var offline: View
    private lateinit var sleepScreen: View
    private lateinit var sleepText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val bridge = Bridge()
    private var scriptHandler: ScriptHandler? = null
    private var injectedScript = ""
    private var mainFrameError = false

    // Dominios que se quedan dentro de la app; el resto se abre afuera
    private val inAppHosts = listOf(
        "instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net",
        "youtube.com", "youtu.be", "google.com", "gstatic.com", "googlevideo.com", "ytimg.com"
    )
    private val linkHosts = listOf("instagram.com", "youtube.com", "youtu.be")
    private val scriptOrigins = setOf(
        "https://instagram.com", "https://*.instagram.com",
        "https://youtube.com", "https://*.youtube.com"
    )

    // --- Subida de archivos (historias, posts, fotos en DMs) ---
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
            fileCallback = null
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // --- Pantalla completa de video ---
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(if (dark) Color.BLACK else Color.WHITE) // sin flash blanco al cargar en modo oscuro
        refresh = SwipeRefreshLayout(this).apply {
            addView(web, ViewGroup.LayoutParams(MATCH, MATCH))
            // La página avisa (vía JS) si el gesto empezó arriba de todo
            setOnChildScrollUpCallback { _, _ -> fullscreenView != null || !bridge.atTop }
            setOnRefreshListener { web.reload() }
        }
        offline = buildNotice("Sin conexión", "Revisá el Wi-Fi o los datos", "Reintentar") { web.reload() }
        sleepScreen = buildNotice("Hora de dormir 🌙", "", "Ir a los mensajes de Instagram") { open(IG_INBOX) }
        sleepText = sleepScreen.findViewWithTag("subtitle")
        root = FrameLayout(this).apply {
            addView(refresh, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(offline, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(sleepScreen, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        setContentView(root)

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            userAgentString = userAgentString.replace("; wv", "") // ayuda con algunos logins
        }
        // Solo expone setAtTop(boolean): nada sensible si una página lo llama
        web.addJavascriptInterface(bridge, "NoScrollBridge")
        installScript()

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (isYouTube(u.toString()) && Sleep.active(this@MainActivity)) { showSleepScreen(); return true }
                shortsToWatch(u)?.let { v.loadUrl(it); return true }
                // intent:// o instagram:// que intentan abrir la app oficial
                if (u.scheme != "https" && u.scheme != "http") return true
                if (hostIn(u.host, inAppHosts)) return false
                // De noche los navegadores están bloqueados: avisar en vez de abrir uno
                Sleep.current(this@MainActivity)?.takeIf { Sleep.blocksBrowsers(this@MainActivity) }?.let {
                    val until = Sleep.fmt(this@MainActivity, it.end.toLocalTime())
                    Toast.makeText(this@MainActivity, "🌙 Los links externos se abren después de las $until", Toast.LENGTH_LONG).show()
                    return true
                }
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                return true
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                mainFrameError = false
                // Volver atrás a YouTube no pasa por shouldOverrideUrlLoading
                if (isYouTube(url) && Sleep.active(this@MainActivity)) { v.stopLoading(); showSleepScreen() }
            }

            override fun onPageFinished(v: WebView, url: String) {
                refresh.isRefreshing = false
                if (!mainFrameError) offline.visibility = View.GONE
                // Respaldo para WebViews viejas; el script tiene guardia contra doble inyección
                v.evaluateJavascript(injectedScript, null)
            }

            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (!req.isForMainFrame) return
                mainFrameError = true
                refresh.isRefreshing = false
                offline.visibility = View.VISIBLE
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null) // cancela uno pendiente
                fileCallback = callback
                val intent = params.createIntent().apply {
                    if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return runCatching { filePicker.launch(intent); true }.getOrElse {
                    fileCallback = null; false
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) { callback.onCustomViewHidden(); return }
                fullscreenView = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                refresh.visibility = View.GONE
                setBarsHidden(true)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    sleepScreen.visibility == View.VISIBLE -> finish()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null) open(urlFromIntent(intent) ?: launcherUrl(intent))
        else web.restoreState(savedInstanceState)

        maybeAskSetup()
        Rules.refreshAsync(this) { runOnUiThread { installScript() } } // se aplica en la próxima carga
        Updater.maybeCheck(this)
        Sleep.reschedule(this)
    }

    override fun onResume() {
        super.onResume()
        sleepTicker.run()
    }

    // ---------- Modo dormir ----------

    /** Revisa justo cuando empieza o termina la noche (y cada minuto, por las dudas). */
    private val sleepTicker = object : Runnable {
        override fun run() {
            applyLock()
            handler.removeCallbacks(this)
            handler.postDelayed(this, ((Sleep.msUntilChange(this@MainActivity) ?: 60_000) + 500).coerceIn(1_000, 60_000))
        }
    }

    private fun applyLock() {
        val night = Sleep.active(this)
        // Cambió el modo solo mensajes o algún ajuste: reinyectar y recargar para aplicarlo ya
        val scriptChanged = Rules.build(this) != injectedScript
        if (scriptChanged) installScript()
        when {
            night && isYouTube(web.url) -> showSleepScreen()
            !night && sleepScreen.visibility == View.VISIBLE -> open(YT_HOME) // se terminó la noche
            scriptChanged && sleepScreen.visibility != View.VISIBLE -> web.reload()
        }
    }

    /** Carga una URL, salvo YouTube en horario de dormir. */
    private fun open(url: String) {
        if (isYouTube(url) && Sleep.active(this)) { showSleepScreen(); return }
        sleepScreen.visibility = View.GONE
        web.loadUrl(url)
    }

    private fun showSleepScreen() {
        val night = Sleep.current(this) ?: return
        sleepText.text = "YouTube está bloqueado hasta las ${Sleep.fmt(this, night.end.toLocalTime())}"
        if (sleepScreen.visibility == View.VISIBLE) return
        sleepScreen.visibility = View.VISIBLE
        if (fullscreenView != null) exitFullscreen()
        web.loadUrl("about:blank") // corta el video y el audio
    }

    private fun isYouTube(url: String?) =
        url != null && hostIn(Uri.parse(url).host, listOf("youtube.com", "youtu.be"))

    // singleTask: links y notificaciones llegan acá si la app ya está abierta
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (fullscreenView != null) exitFullscreen()
        val url = urlFromIntent(intent)
        if (url != null) { open(url); return }
        // Tocaste el ícono de la otra plataforma: cambiar solo si hace falta
        val wantYT = isYouTubeAlias(intent)
        val onYT = sleepScreen.visibility == View.VISIBLE || isYouTube(web.url)
        if (wantYT != onYT) open(launcherUrl(intent))
    }

    // ---------- Script de bloqueo ----------

    private fun installScript() {
        injectedScript = Rules.build(this)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            scriptHandler?.remove()
            scriptHandler = WebViewCompat.addDocumentStartJavaScript(web, injectedScript, scriptOrigins)
        }
    }

    /** Puente JS → app para el pull-to-refresh. */
    class Bridge {
        @Volatile private var top = false
        val atTop get() = top
        @JavascriptInterface fun setAtTop(value: Boolean) { top = value }
    }

    // ---------- Intents ----------

    private fun urlFromIntent(i: Intent): String? {
        val uri = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Regex("https?://\\S+").find(it)?.value }
                ?.let(Uri::parse)
            else -> null
        } ?: return null
        if (!hostIn(uri.host, linkHosts)) return null
        return shortsToWatch(uri) ?: uri.toString()
    }

    private fun isYouTubeAlias(i: Intent) = i.component?.className?.endsWith("YouTube") == true

    private fun launcherUrl(i: Intent) = if (isYouTubeAlias(i)) YT_HOME else IG_INBOX

    private fun shortsToWatch(u: Uri): String? {
        if (u.host?.contains("youtube.com") != true) return null
        val m = Regex("^/shorts/([\\w-]{11})").find(u.path ?: "") ?: return null
        return "https://m.youtube.com/watch?v=${m.groupValues[1]}"
    }

    private fun hostIn(host: String?, list: List<String>) =
        host != null && list.any { host == it || host.endsWith(".$it") }

    // ---------- Pantallas de aviso ----------

    private val dark
        get() = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Pantalla completa con título, texto y un botón (sin conexión, hora de dormir). */
    private fun buildNotice(title: String, subtitle: String, button: String, onClick: () -> Unit): View {
        val fg = if (dark) Color.WHITE else Color.parseColor("#202020")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
            visibility = View.GONE
            isClickable = true // que no pase el toque a la WebView de atrás
            addView(TextView(context).apply {
                text = title
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = subtitle
                tag = "subtitle"
                setTextColor(fg)
                alpha = 0.7f
                setPadding(48, 12, 48, 36)
                gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = button
                setOnClickListener { onClick() }
            })
        }
    }

    // ---------- Configuración inicial ----------

    private fun maybeAskSetup() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val prefs = getSharedPreferences("setup", MODE_PRIVATE)
        val listenerOn = Setup.listenerOn(this)
        val guardOn = Setup.guardOn(this)
        if (prefs.getBoolean("done", false) && listenerOn && guardOn) return

        fun mark(on: Boolean) = if (on) "✓" else "○"
        val steps = listOfNotNull(
            "${mark(listenerOn)}  Notificaciones → activar Brainrotless",
            "○  Links → agregar dominios de Instagram/YouTube",
            "${mark(guardOn)}  Bloquear apps oficiales → Accesibilidad → Brainrotless",
            if (Setup.restrictedApplies()) "⚠  ¿Un permiso aparece gris? Tocá acá" else null
        ).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Configurar Brainrotless")
            .setItems(steps) { _, which ->
                when (which) {
                    0 -> Setup.openNotifications(this)
                    1 -> Setup.openLinks(this)
                    2 -> Setup.openAccessibility(this)
                    3 -> Setup.explainRestricted(this)
                }
            }
            .setNeutralButton("Ayuda") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(
                        "Si un permiso aparece gris o bloqueado, tocá '¿Un permiso aparece gris?' en la " +
                            "lista y seguí los pasos.\n\nPara que los links abran acá, en Instagram y " +
                            "YouTube oficiales desactivá 'Abrir vínculos compatibles'.\n\n" +
                            "Estos pasos también están en Ajustes (mantené presionado el ícono)."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Listo") { _, _ -> prefs.edit().putBoolean("done", true).apply() }
            .show()
    }

    // ---------- Pantalla completa ----------

    private fun exitFullscreen() {
        val v = fullscreenView ?: return
        root.removeView(v)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        refresh.visibility = View.VISIBLE
        setBarsHidden(false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun setBarsHidden(on: Boolean) {
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(sleepTicker)
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val IG_INBOX = "https://www.instagram.com/direct/inbox/"
        private const val YT_HOME = "https://m.youtube.com"
    }
}
