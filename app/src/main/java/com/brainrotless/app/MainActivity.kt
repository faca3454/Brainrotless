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
import android.os.SystemClock
import android.text.format.DateFormat
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
    private lateinit var content: LinearLayout
    private lateinit var offline: View
    private val handler = Handler(Looper.getMainLooper())

    // Bloqueo de YouTube (noche o límite) y barra de "solo mensajes" arriba de Instagram
    private lateinit var lockScreen: View
    private lateinit var lockTitle: TextView
    private lateinit var lockText: TextView
    private lateinit var lockPause: Button
    private lateinit var lockBar: View
    private lateinit var lockBarText: TextView
    private lateinit var lockBarPause: Button
    private var usageMark = 0L // desde cuándo se cuenta el uso sin sumar
    private lateinit var guardScreen: View // accesibilidad apagada durante un compromiso

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
        lockScreen = buildNotice("", "", "Ir a los mensajes de Instagram", "") { open(IG_INBOX) }
        lockTitle = lockScreen.findViewWithTag("title")
        lockText = lockScreen.findViewWithTag("subtitle")
        lockPause = lockScreen.findViewWithTag("button2")
        lockPause.setOnClickListener { usePause() }
        guardScreen = buildNotice(
            "Brainrotless está apagado",
            "Tu compromiso sigue. Volvé a activar 'Bloquear apps oficiales' en Accesibilidad para seguir usando IG Lite y YT Lite.",
            "Activar"
        ) { Setup.openAccessibility(this) }
        lockBar = buildLockBar()
        lockBarText = lockBar.findViewWithTag("text")
        lockBarPause = lockBar.findViewWithTag("pause")
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(lockBar, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(refresh, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        root = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(offline, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(lockScreen, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(guardScreen, FrameLayout.LayoutParams(MATCH, MATCH))
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
                if (isYouTube(u.toString()) && Lock.yt(this@MainActivity) != null) { showLockScreen(); return true }
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
                if (isYouTube(url) && Lock.yt(this@MainActivity) != null) { v.stopLoading(); showLockScreen() }
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
                content.visibility = View.GONE
                setBarsHidden(true)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    lockScreen.visibility == View.VISIBLE || guardScreen.visibility == View.VISIBLE -> finish()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null) open(urlFromIntent(intent) ?: launcherUrl(intent))
        else web.restoreState(savedInstanceState)
        handlePauseIntent(intent)

        maybeAskSetup()
        Rules.refreshAsync(this) { runOnUiThread { installScript() } } // se aplica en la próxima carga
        Updater.maybeCheck(this)
        Sleep.reschedule(this)
    }

    override fun onResume() {
        super.onResume()
        usageMark = SystemClock.elapsedRealtime()
        lockTicker.run()
        Commitment.onEnd(this)?.let(::showSummary)
    }

    /** Terminó el compromiso: resumen y la pregunta de si empezar otro (no se renueva solo). */
    private fun showSummary(text: String) {
        Commitment.markSummarySeen(this)
        AlertDialog.Builder(this)
            .setMessage(text)
            .setPositiveButton("Empezar otro") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_START_COMMITMENT, true))
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    // ---------- Bloqueos: noche, límite diario y pausa de emergencia ----------

    /** Revisa justo cuando algo cambia (noche, límite, avisos, fin de la pausa) y cada minuto, por las dudas. */
    private val lockTicker = object : Runnable {
        override fun run() {
            applyLock()
            handler.removeCallbacks(this)
            handler.postDelayed(this, (Lock.msUntilChange(this@MainActivity) + 500).coerceIn(1_000, 60_000))
        }
    }

    private fun applyLock() {
        countUsage()
        // Accesibilidad apagada durante un compromiso: IG Lite y YT Lite no abren
        val guardOff = Commitment.active(this) && !Setup.guardOn(this)
        Protection.updateGuardNotice(this, guardOn = !guardOff)
        if (guardOff != (guardScreen.visibility == View.VISIBLE)) {
            guardScreen.visibility = if (guardOff) View.VISIBLE else View.GONE
            if (guardOff) web.loadUrl("about:blank") else open(launcherUrl(intent))
        }
        val yt = Lock.yt(this)
        // Cambió el modo solo mensajes o algún ajuste: reinyectar y recargar para aplicarlo ya
        val scriptChanged = Rules.build(this) != injectedScript
        if (scriptChanged) installScript()
        when {
            yt != null && isYouTube(web.url) -> showLockScreen()
            yt == null && lockScreen.visibility == View.VISIBLE -> open(YT_HOME) // se terminó el bloqueo
            scriptChanged && lockScreen.visibility != View.VISIBLE -> web.reload()
        }
        if (lockScreen.visibility == View.VISIBLE) showLockScreen() // refrescar texto y pausa
        refreshLockBar()
    }

    /** Todo el tiempo con Instagram o YouTube en pantalla suma al límite (salvo la pantalla de bloqueo). */
    private fun countUsage() {
        val now = SystemClock.elapsedRealtime()
        val blocked = lockScreen.visibility == View.VISIBLE || guardScreen.visibility == View.VISIBLE
        if (usageMark > 0 && !blocked) Usage.add(this, now - usageMark)
        usageMark = now
    }

    /** Carga una URL, salvo YouTube bloqueado. */
    private fun open(url: String) {
        if (isYouTube(url) && Lock.yt(this) != null) { showLockScreen(); return }
        lockScreen.visibility = View.GONE
        web.loadUrl(url)
    }

    private fun showLockScreen() {
        val r = Lock.yt(this) ?: return
        val until = Lock.until(this, r)
        lockTitle.text = if (r == Lock.Reason.NIGHT) "Hora de dormir 🌙" else "Límite de hoy ⏱"
        lockText.text = if (r == Lock.Reason.NIGHT) "YouTube está bloqueado hasta las $until"
            else "Usaste tu tiempo de hoy. YouTube vuelve a las $until"
        // De noche la pausa no abre YouTube: solo se ofrece por el límite
        val canPause = r == Lock.Reason.LIMIT && Commitment.canPause(this)
        lockPause.visibility = if (canPause) View.VISIBLE else View.GONE
        lockPause.text = "Pausa de emergencia, 15 min (quedan ${Commitment.pausesLeft(this)})"
        if (lockScreen.visibility == View.VISIBLE) return
        countUsage()
        lockScreen.visibility = View.VISIBLE
        if (fullscreenView != null) exitFullscreen()
        web.loadUrl("about:blank") // corta el video y el audio
    }

    /** Barra arriba de la página: "solo mensajes" con el botón de pausa, o la pausa en curso. */
    private fun refreshLockBar() {
        val ig = Lock.ig(this)
        val text = when {
            Commitment.pauseActive(this) -> "🆘 Pausa de emergencia hasta las " +
                DateFormat.getTimeFormat(this).format(java.util.Date(Commitment.pauseUntil(this))) + " (no suma al límite)"
            ig != null && !isYouTube(web.url) && lockScreen.visibility != View.VISIBLE -> Lock.igMessage(this, ig)
            else -> null
        }
        lockBar.visibility = if (text != null) View.VISIBLE else View.GONE
        lockBarText.text = text
        lockBarPause.visibility = if (ig != null && Commitment.canPause(this)) View.VISIBLE else View.GONE
        lockBarPause.text = "Pausa (${Commitment.pausesLeft(this)})"
    }

    private fun usePause() {
        if (!Commitment.startPause(this)) return
        val what = if (Sleep.active(this)) "Instagram completo" else "Instagram y YouTube"
        Toast.makeText(this, "🆘 Pausa de emergencia: $what por 15 minutos", Toast.LENGTH_LONG).show()
        applyLock()
    }

    private fun handlePauseIntent(i: Intent) {
        if (!i.getBooleanExtra(EXTRA_PAUSE, false)) return
        i.removeExtra(EXTRA_PAUSE)
        usePause()
    }

    private fun isYouTube(url: String?) =
        url != null && hostIn(Uri.parse(url).host, listOf("youtube.com", "youtu.be"))

    // singleTask: links y notificaciones llegan acá si la app ya está abierta
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (fullscreenView != null) exitFullscreen()
        handlePauseIntent(intent)
        val url = urlFromIntent(intent)
        if (url != null) { open(url); return }
        // Tocaste el ícono de la otra plataforma: cambiar solo si hace falta
        val wantYT = isYouTubeAlias(intent)
        val onYT = lockScreen.visibility == View.VISIBLE || isYouTube(web.url)
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

    /** Pantalla completa con título, texto y uno o dos botones (sin conexión, bloqueo de YouTube). */
    private fun buildNotice(
        title: String, subtitle: String, button: String, button2: String? = null, onClick: () -> Unit
    ): View {
        val fg = if (dark) Color.WHITE else Color.parseColor("#202020")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
            visibility = View.GONE
            isClickable = true // que no pase el toque a la WebView de atrás
            addView(TextView(context).apply {
                text = title
                tag = "title"
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
            if (button2 != null) addView(Button(context).apply {
                text = button2
                tag = "button2"
                visibility = View.GONE
            })
        }
    }

    private fun buildLockBar(): View {
        val fg = if (dark) Color.WHITE else Color.parseColor("#202020")
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 4, 16, 4)
            setBackgroundColor(Color.parseColor(if (dark) "#1E1E1E" else "#F0F0F0"))
            visibility = View.GONE
            addView(TextView(context).apply {
                tag = "text"
                setTextColor(fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                tag = "pause"
                isAllCaps = false
                setOnClickListener { usePause() }
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
        content.visibility = View.VISIBLE
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
        countUsage()
        usageMark = 0
        handler.removeCallbacks(lockTicker)
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
        const val EXTRA_PAUSE = "pause" // desde la notificación del límite: usar la pausa de emergencia
    }
}
