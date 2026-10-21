package com.brainrotless.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
import androidx.lifecycle.Lifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var refresh: SwipeRefreshLayout
    // Una WebView por plataforma: cambiar de Instagram a YouTube no recarga nada.
    // La de YouTube se crea la primera vez que se usa.
    private lateinit var igWeb: WebView
    private var ytWebOrNull: WebView? = null
    private val ytWeb: WebView
        get() = ytWebOrNull ?: buildWebView().also {
            it.visibility = View.GONE
            webs.addView(it, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            ytWebOrNull = it
            addScript(it)
        }
    private fun webViews() = listOfNotNull(igWeb, ytWebOrNull)
    private lateinit var web: WebView // la que está en pantalla
    private lateinit var webs: FrameLayout
    private var lostUrl: String? = null // página que el sistema cerró con la app en segundo plano
    private var backToYouTube: Boolean? = null // llegaste por un link desde la otra plataforma: atrás vuelve ahí
    private var pendingChat: String? = null // chat a abrir cuando termine de cargar Instagram (notificación)
    private lateinit var content: LinearLayout
    private lateinit var offline: View
    private lateinit var retryButton: View
    private lateinit var progress: View // línea fina arriba de la página mientras carga entera
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
    private var lang = "" // idioma con el que se armó la pantalla
    private lateinit var guardScreen: View // accesibilidad apagada durante un compromiso

    private val bridge = Bridge(
        onReport = { text -> runOnUiThread { copyDiagnostic(text) } },
        // Deslizar para responder pasó el punto: la vibración del sistema (respeta "respuesta táctil")
        onHaptic = { runOnUiThread { web.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK) } },
    )
    private val scriptHandlers = mutableMapOf<WebView, ScriptHandler>()
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

    // --- Micrófono, para grabar audios en los chats (Instagram lo pide al mantener el botón) ---
    private var micRequest: PermissionRequest? = null
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            val r = micRequest ?: return@registerForActivityResult
            micRequest = null
            if (ok) r.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else r.deny()
        }

    // --- Pantalla completa de video ---
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La primera vez desde el ícono de IG Lite o YT Lite: arrancar por la guía (idioma,
        // qué hace, plan). Los links compartidos abren igual.
        if (OnboardingActivity.needed(this) && savedInstanceState == null && urlFromIntent(intent) == null) {
            // En su propia tarea: si no, quedaría debajo de IG Lite y el botón atrás volvería a ella
            startActivity(OnboardingActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        lang = Lang.current(this)

        igWeb = buildWebView()
        web = igWeb
        setFront(igWeb, true)
        webs = FrameLayout(this).apply { addView(igWeb, FrameLayout.LayoutParams(MATCH, MATCH)) }
        progress = View(this).apply {
            setBackgroundColor(Brand.color(context, R.color.brand_primary))
            pivotX = 0f
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        webs.addView(progress, FrameLayout.LayoutParams(MATCH, Brand.px(this, 3), Gravity.TOP))
        refresh = SwipeRefreshLayout(this).apply {
            addView(webs, ViewGroup.LayoutParams(MATCH, MATCH))
            // La página avisa (vía JS) si el gesto empezó arriba de todo
            setOnChildScrollUpCallback { _, _ -> fullscreenView != null || !bridge.atTop }
            setColorSchemeColors(Brand.color(context, R.color.brand_primary))
            setProgressBackgroundColorSchemeColor(Brand.color(context, R.color.brand_surface))
            setOnRefreshListener { web.reload() }
        }
        offline = Notices.screen(this, getString(R.string.offline_title), getString(R.string.offline_text), getString(R.string.retry)) { retry() }
        retryButton = offline.findViewWithTag("button")
        lockScreen = Notices.screen(this, "", "", getString(R.string.go_to_messages), "") { goTo(youtube = false) }
        lockTitle = lockScreen.findViewWithTag("title")
        lockText = lockScreen.findViewWithTag("subtitle")
        lockPause = lockScreen.findViewWithTag("button2")
        lockPause.setOnClickListener { usePause() }
        guardScreen = Notices.screen(
            this,
            getString(R.string.guard_off_title), getString(R.string.guard_off_text), getString(R.string.turn_on)
        ) { Setup.openAccessibility(this) }
        lockBar = Notices.lockBar(this) { usePause() }
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
        // Cada vez que aparece o se va un aviso, o se cambia de plataforma, hay un layout: repintar las barras
        root.viewTreeObserver.addOnGlobalLayoutListener { paintBars() }
        installScript()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val blocked = lockScreen.visibility == View.VISIBLE || guardScreen.visibility == View.VISIBLE
                val origin = backToYouTube
                when {
                    fullscreenView != null -> exitFullscreen()
                    !blocked && web.canGoBack() -> web.goBack()
                    origin != null && guardScreen.visibility != View.VISIBLE -> { backToYouTube = null; goTo(origin) }
                    // En la primera página la app pasa al fondo, como cualquier app: al volver sigue donde
                    // estaba, sin cargar todo de nuevo (finish() cerraba las dos páginas)
                    else -> moveTaskToBack(true)
                }
            }
        })

        if (savedInstanceState == null) open(urlFromIntent(intent) ?: launcherUrl(intent), intent.getStringExtra(EXTRA_CHAT))
        else {
            savedInstanceState.getBundle("ig")?.let { igWeb.restoreState(it) }
            savedInstanceState.getBundle("yt")?.let { ytWeb.restoreState(it) }
            switchTo(if (savedInstanceState.getBoolean("onYouTube")) ytWeb else igWeb)
        }
        handlePauseIntent(intent)

        Lang.askIfNeeded(this) // sin guía (se abrió con un link compartido): el idioma, nada más
        Rules.refreshAsync(this) { runOnUiThread { installScript() } } // se aplica en la próxima carga
        Updater.maybeCheck(this)
        Sleep.reschedule(this)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun buildWebView(): WebView = WebView(this).apply {
        setBackgroundColor(if (dark) Color.BLACK else Color.WHITE) // sin flash blanco al cargar en modo oscuro
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            userAgentString = userAgentString.replace("; wv", "") // ayuda con algunos logins
        }
        // Solo expone setAtTop(boolean), haptic() y, en los APK de prueba, report(texto): nada sensible si una página lo llama
        addJavascriptInterface(bridge, "NoScrollBridge")
        webViewClient = client
        webChromeClient = chromeClient
    }.also {
        // Crear una WebView puede devolver los textos al idioma del sistema: volver al elegido
        Lang.applyTo(this, resources)
        Lang.applyTo(this)
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            val url = u.toString()
            if (isYouTube(url) && Lock.yt(this@MainActivity) != null) {
                if (v === igWeb) backToYouTube = false
                switchTo(ytWeb); showLockScreen(); return true
            }
            if (!Lock.reelsAllowed(this@MainActivity)) shortsToWatch(u)?.let { open(it); return true }
            // intent:// o instagram:// que intentan abrir la app oficial
            if (u.scheme != "https" && u.scheme != "http") return true
            // Un link de YouTube desde Instagram (o al revés) se abre en la WebView de esa plataforma
            val target = when {
                isYouTube(url) -> ytWeb
                hostIn(u.host, listOf("instagram.com")) -> igWeb
                else -> null
            }
            if (target != null && target !== v) { backToYouTube = v !== igWeb; open(url); return true }
            if (hostIn(u.host, inAppHosts)) return false
            // De noche los navegadores están bloqueados: avisar en vez de abrir uno
            Sleep.current(this@MainActivity)?.takeIf { Sleep.blocksBrowsers(this@MainActivity) }?.let {
                val until = Sleep.fmt(this@MainActivity, it.end.toLocalTime())
                Toast.makeText(this@MainActivity, getString(R.string.external_links_night, until), Toast.LENGTH_LONG).show()
                return true
            }
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
            return true
        }

        override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            if (v === web) mainFrameError = false
            // Volver atrás a YouTube no pasa por shouldOverrideUrlLoading
            if (isYouTube(url) && Lock.yt(this@MainActivity) != null) {
                v.stopLoading()
                if (v === web) showLockScreen()
            }
        }

        override fun onPageFinished(v: WebView, url: String) {
            if (v === web) {
                refresh.isRefreshing = false
                if (!mainFrameError) offline.visibility = View.GONE
                setRetrying(false)
            }
            // Respaldo para WebViews viejas; el script tiene guardia contra doble inyección
            v.evaluateJavascript(injectedScript, null)
            if (v === igWeb) pendingChat?.let { chat ->
                pendingChat = null
                v.evaluateJavascript("window.__lucidoGo && __lucidoGo(null, ${JSONObject.quote(chat)})", null)
            }
        }

        // El sistema cerró la página (falta de memoria) o se cayó: WebView nueva en su lugar, en vez de
        // cerrar la app. Las dos comparten el proceso de la página, así que llega una vez por cada una.
        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            val url = v.url?.takeIf { it != "about:blank" }
            if (v === web && fullscreenView != null) exitFullscreen()
            val fresh = buildWebView().apply { visibility = v.visibility }
            webs.addView(fresh, webs.indexOfChild(v), FrameLayout.LayoutParams(MATCH, MATCH))
            webs.removeView(v)
            scriptHandlers.remove(v)
            v.destroy()
            if (v === igWeb) igWeb = fresh else ytWebOrNull = fresh
            addScript(fresh)
            setFront(fresh, v === web)
            if (v === web) {
                web = fresh
                // En segundo plano se recarga al volver: si no, la página vuelve a ocupar la memoria que faltaba
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) url?.let(fresh::loadUrl) else lostUrl = url
            }
            return true
        }

        override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
            if (!req.isForMainFrame || v !== web) return
            mainFrameError = true
            refresh.isRefreshing = false
            offline.visibility = View.VISIBLE
            setRetrying(false)
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view === web) showProgress(newProgress)
        }

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

        // Solo el micrófono y solo para Instagram y YouTube; lo demás (cámara, etc.), no. El permiso de
        // Android se pide la primera vez que hace falta, no antes
        override fun onPermissionRequest(request: PermissionRequest) {
            val mic = PermissionRequest.RESOURCE_AUDIO_CAPTURE
            if (mic !in request.resources || !hostIn(request.origin.host, linkHosts)) return request.deny()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                return request.grant(arrayOf(mic))
            micRequest?.deny()
            micRequest = request
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (micRequest === request) micRequest = null
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

    // ---------- Instagram y YouTube, cada uno en su WebView ----------

    private val onYouTube get() = web !== igWeb
    private fun hasPage(v: WebView) = v.url.let { it != null && it != "about:blank" }

    /** Muestra la WebView de esa plataforma con un fundido corto; la otra queda viva, en pausa. */
    private fun switchTo(v: WebView) {
        if (v === web && v.visibility == View.VISIBLE) return
        if (usageMark > 0) countUsage() // lo de hasta ahora, a la app que se estaba viendo
        val old = web
        web = v
        offline.visibility = View.GONE
        v.onResume()
        setFront(v, true)
        v.alpha = 0f
        v.visibility = View.VISIBLE
        v.bringToFront()
        progress.bringToFront()
        progress.animate().cancel()
        progress.alpha = 0f // la que se va puede haber estado cargando
        v.animate().alpha(1f).setDuration(180).setInterpolator(Brand.EASE_OUT).withEndAction { if (web !== old) old.visibility = View.GONE }.start()
        if (old !== v) {
            pauseMedia(old)
            old.onPause()
            setFront(old, false)
        }
        refreshLockBar()
    }

    /** La de pantalla: su proceso con prioridad alta y dibujo por adelantado (menos huecos al scrollear). */
    private fun setFront(v: WebView, front: Boolean) {
        v.settings.offscreenPreRaster = front
        v.setRendererPriorityPolicy(if (front) WebView.RENDERER_PRIORITY_IMPORTANT else WebView.RENDERER_PRIORITY_WAIVED, false)
    }

    private fun pauseMedia(v: WebView) =
        v.evaluateJavascript("document.querySelectorAll('video,audio').forEach(m => m.pause())", null)

    /** Ícono o botón de una plataforma: volver a donde estabas, o a su inicio si todavía no cargó. */
    private fun goTo(youtube: Boolean) {
        val v = if (youtube) ytWeb else igWeb
        if (hasPage(v) && !(youtube && Lock.yt(this) != null)) {
            lockScreen.visibility = View.GONE
            switchTo(v)
        } else open(if (youtube) YT_HOME else IG_INBOX)
    }

    override fun onResume() {
        super.onResume()
        getSystemService(ConnectivityManager::class.java)?.let { cm ->
            network.online = cm.activeNetwork?.let(cm::getNetworkCapabilities)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            runCatching { cm.registerDefaultNetworkCallback(network) }
        }
        if (Lang.current(this) != lang) return recreate() // cambiaron el idioma en Ajustes
        usageMark = SystemClock.elapsedRealtime()
        lostUrl?.let { lostUrl = null; web.loadUrl(it) }
        Session.resume(this)
        lockTicker.run()
        Commitment.onEnd(this)?.let(::showSummary)
    }

    /** Terminó el compromiso: resumen y la pregunta de si empezar otro (no se renueva solo). */
    private fun showSummary(text: String) {
        Commitment.markSummarySeen(this)
        Dialogs.summary(this, text, R.string.not_now,
            onAnother = { PanelActivity.open(this, PanelActivity.COMMIT) },
            onCharts = { PanelActivity.open(this, PanelActivity.STATS) }).show()
    }

    // ---------- Bloqueos: noche, límite diario y pausa de emergencia ----------

    /** Revisa justo cuando algo cambia (noche, límite, avisos, fin de la pausa) y cada minuto, por las dudas. */
    private val lockTicker = object : Runnable {
        override fun run() {
            applyLock()
            maybeWarnSession()
            handler.removeCallbacks(this)
            val next = listOfNotNull(Lock.msUntilChange(this@MainActivity), Session.msUntilWarn(this@MainActivity)?.takeIf { it > 0 }).min()
            handler.postDelayed(this, (next + 500).coerceIn(1_000, 60_000))
        }
    }

    private fun applyLock() {
        countUsage()
        // Accesibilidad apagada durante un compromiso: IG Lite y YT Lite no abren
        val guardOff = Commitment.active(this) && !Setup.guardOn(this)
        Protection.updateGuardNotice(this, guardOn = !guardOff)
        if (guardOff != (guardScreen.visibility == View.VISIBLE)) {
            guardScreen.visibility = if (guardOff) View.VISIBLE else View.GONE
            if (guardOff) webViews().forEach { it.loadUrl("about:blank") } else goTo(onYouTube)
        }
        val yt = Lock.yt(this)
        // Cambió el modo solo mensajes o algún ajuste: reinyectar y recargar para aplicarlo ya
        val scriptChanged = Rules.build(this) != injectedScript
        if (scriptChanged) installScript()
        // YouTube bloqueado: pantalla de bloqueo si está a la vista; si está de fondo, cortarlo igual
        if (yt != null && ytWebOrNull?.let(::hasPage) == true) {
            if (onYouTube) showLockScreen() else ytWeb.loadUrl("about:blank")
        }
        when {
            yt == null && lockScreen.visibility == View.VISIBLE -> open(YT_HOME) // se terminó el bloqueo
            scriptChanged && lockScreen.visibility != View.VISIBLE -> webViews().filter(::hasPage).forEach { it.reload() }
        }
        if (lockScreen.visibility == View.VISIBLE) showLockScreen() // refrescar texto y pausa
        refreshLockBar()
        Widgets.update(this)
    }

    // ---------- Aviso de sesión larga ----------

    private var sessionDialog: AlertDialog? = null

    private fun maybeWarnSession() {
        val blocked = lockScreen.visibility == View.VISIBLE || guardScreen.visibility == View.VISIBLE
        if (!Session.due(this) || blocked || sessionDialog?.isShowing == true) return
        Session.snooze(this) // si sale y vuelve enseguida, el próximo aviso es en 5 minutos
        sessionDialog = Dialogs.session(this, Session.minutes(this)) {
            webViews().forEach(::pauseMedia)
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.show()
    }

    /** Todo el tiempo con Instagram o YouTube en pantalla suma al límite (salvo la pantalla de bloqueo). */
    private fun countUsage() {
        val now = SystemClock.elapsedRealtime()
        val blocked = lockScreen.visibility == View.VISIBLE || guardScreen.visibility == View.VISIBLE
        if (usageMark > 0 && !blocked) Usage.add(this, now - usageMark, if (onYouTube) Usage.App.YT else Usage.App.IG, toast = false)
        usageMark = now
    }

    /** Carga una URL en la WebView de su plataforma, salvo YouTube bloqueado. Con [chat], abre ese chat de Instagram. */
    private fun open(url: String, chat: String? = null) {
        val v = if (isYouTube(url)) ytWeb else igWeb
        switchTo(v)
        if (v !== igWeb && Lock.yt(this) != null) { showLockScreen(); return }
        lockScreen.visibility = View.GONE
        val load = { pendingChat = chat; v.loadUrl(url) }
        if (v !== igWeb || !hasPage(v)) return load()
        // Instagram ya cargado (ej: una notificación): el script navega dentro del sitio, sin recargar ni
        // parpadeo, y abre el chat. Si no se encarga (otra ruta, un script viejo), se carga como siempre.
        val path = JSONObject.quote(Uri.parse(url).path ?: "/")
        v.evaluateJavascript("window.__lucidoGo ? __lucidoGo($path, ${chat?.let(JSONObject::quote)}) : false") {
            if (it != "true") load()
        }
    }

    private fun showLockScreen() {
        val r = Lock.yt(this) ?: return
        val until = Lock.until(this, r)
        lockTitle.text = getString(when (r) {
            Lock.Reason.NIGHT -> R.string.lock_night_title
            Lock.Reason.FOCUS -> R.string.lock_focus_title
            Lock.Reason.LIMIT -> R.string.lock_limit_title
        })
        lockText.text = getString(if (r == Lock.Reason.LIMIT) R.string.lock_limit_text else R.string.lock_blocked_text, until)
        // De noche la pausa no abre YouTube: solo se ofrece por el límite
        val canPause = r == Lock.Reason.LIMIT && Commitment.canPause(this)
        lockPause.visibility = if (canPause) View.VISIBLE else View.GONE
        lockPause.text = getString(R.string.lock_pause_button, left(Commitment.pausesLeft(this)))
        if (lockScreen.visibility == View.VISIBLE) return
        countUsage()
        lockScreen.visibility = View.VISIBLE
        if (fullscreenView != null) exitFullscreen()
        ytWebOrNull?.loadUrl("about:blank") // corta el video y el audio
    }

    /**
     * Barra arriba de la página: "solo mensajes" con el botón de pausa, la pausa en curso o, antes de
     * que Instagram quede solo para mensajes, cuándo empieza la noche y los últimos minutos del límite.
     */
    private fun refreshLockBar() {
        val ig = Lock.ig(this)
        val shown = lockScreen.visibility != View.VISIBLE
        val soon = Sleep.soon(this)
        val left = Usage.remainingMs(this)?.takeIf { it in 1..10 * 60_000L }
        val text = when {
            Commitment.pauseActive(this) ->
                getString(R.string.pause_bar, DateFormat.getTimeFormat(this).format(java.util.Date(Commitment.pauseUntil(this))))
            ig != null -> Lock.igMessage(this, ig).takeIf { !onYouTube && shown }
            !shown -> null
            soon != null -> getString(R.string.sleep_warn_title, Sleep.fmt(this, soon.toLocalTime()))
            left != null -> ((left + 59_999) / 60_000).toInt().let { resources.getQuantityString(R.plurals.minutes_left, it, it) }
            else -> null
        }
        lockBar.visibility = if (text != null) View.VISIBLE else View.GONE
        lockBarText.text = text
        lockBarPause.visibility = if (ig != null && Commitment.canPause(this)) View.VISIBLE else View.GONE
        lockBarPause.text = getString(R.string.pause_chip, Commitment.pausesLeft(this))
    }

    private fun left(n: Int) = resources.getQuantityString(R.plurals.left_n, n, n)

    private fun usePause() {
        if (!Commitment.startPause(this)) return
        val msg = if (Sleep.active(this)) R.string.pause_started_ig else R.string.pause_started_all
        Toast.makeText(this, getString(msg), Toast.LENGTH_LONG).show()
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
        backToYouTube = null // llegaste desde afuera: atrás vuelve a esa app
        if (fullscreenView != null) exitFullscreen()
        handlePauseIntent(intent)
        val url = urlFromIntent(intent)
        if (url != null) { open(url, intent.getStringExtra(EXTRA_CHAT)); return }
        // Tocaste un ícono: volver a donde estabas en esa plataforma, sin recargar
        goTo(isYouTubeAlias(intent))
    }

    // ---------- Script de bloqueo ----------

    private fun installScript() {
        injectedScript = Rules.build(this)
        webViews().forEach(::addScript)
    }

    private fun addScript(v: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        scriptHandlers[v]?.remove()
        scriptHandlers[v] = WebViewCompat.addDocumentStartJavaScript(v, injectedScript, scriptOrigins)
    }

    /** Puente JS → app: pull-to-refresh, la vibración de deslizar para responder y, solo en los APK de prueba, diagnósticos del script. */
    class Bridge(private val onReport: (String) -> Unit, private val onHaptic: () -> Unit) {
        @Volatile private var top = false
        val atTop get() = top
        @Volatile private var lastHaptic = 0L
        @JavascriptInterface fun setAtTop(value: Boolean) { top = value }
        // Una vibración corta, como mucho una cada 300 ms (si una página la llamara de más, solo vibra)
        @JavascriptInterface fun haptic() {
            val now = SystemClock.uptimeMillis()
            if (now - lastHaptic < 300) return
            lastHaptic = now
            onHaptic()
        }
        @JavascriptInterface fun report(text: String) { if (testBuild) onReport(text.take(10_000)) }
    }

    /** Diagnóstico o mediciones del script (APK de prueba): al portapapeles, para pegarlo en el chat. */
    private fun copyDiagnostic(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Lúcido", text))
        val msg = if (text.startsWith(PERF_REPORT)) R.string.perf_copied else R.string.diag_copied
        Toast.makeText(this, getString(msg), Toast.LENGTH_LONG).show()
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
        // Shorts al reproductor clásico, salvo que se puedan ver dentro del límite
        return (if (Lock.reelsAllowed(this)) null else shortsToWatch(uri)) ?: uri.toString()
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

    // ---------- Carga y conexión ----------

    /** Línea de carga: avanza con la página y se desvanece al terminar. Con el gesto de recargar ya se ve el círculo. */
    private fun showProgress(p: Int) {
        val loading = p < 100 && !refresh.isRefreshing
        if (loading && progress.alpha == 0f) progress.scaleX = 0f
        progress.animate().scaleX(p / 100f).alpha(if (loading) 1f else 0f)
            .setDuration(200).setInterpolator(Brand.EASE_OUT).start()
    }

    /** "Reintentar": mientras vuelve a cargar, el botón queda apagado, así se ve que algo pasa. */
    private fun retry() {
        if (!retryButton.isEnabled) return
        setRetrying(true)
        web.reload()
    }

    private fun setRetrying(on: Boolean) {
        retryButton.isEnabled = !on
        retryButton.alpha = if (on) 0.5f else 1f
    }

    // Sin conexión: cuando vuelve una red que anda, se recarga sola, sin tocar "Reintentar". Solo al
    // pasar de no tener a tener: si Instagram no responde con la red andando, no recarga en bucle.
    private val network = object : ConnectivityManager.NetworkCallback() {
        @Volatile var online = true
        override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
            val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (ok && !online) runOnUiThread { if (offline.visibility == View.VISIBLE) retry() }
            online = ok
        }
        override fun onLost(n: Network) { online = false }
    }

    // ---------- Barras del sistema ----------

    private var barsColor = 0

    /** Barras de estado y de navegación del color de lo que se ve: la página, o el aviso que la tapa. */
    private fun paintBars() {
        val notice = listOf(offline, lockScreen, guardScreen).any { it.visibility == View.VISIBLE }
        val color = when {
            notice -> Brand.color(this, R.color.brand_bg)
            !dark -> Color.WHITE
            onYouTube -> YT_DARK
            else -> Color.BLACK
        }
        if (color == barsColor) return
        barsColor = color
        window.statusBarColor = color
        // Android 8.0 no tiene íconos oscuros para la barra de navegación: en claro queda negra
        if (dark || Build.VERSION.SDK_INT >= 27) window.navigationBarColor = color
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
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
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
        countUsage()
        usageMark = 0
        Session.pause(this)
        handler.removeCallbacks(lockTicker)
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("ig", Bundle().also { igWeb.saveState(it) })
        ytWebOrNull?.let { v -> outState.putBundle("yt", Bundle().also { v.saveState(it) }) }
        outState.putBoolean("onYouTube", onYouTube)
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val IG_INBOX = "https://www.instagram.com/direct/inbox/"
        private const val YT_HOME = "https://m.youtube.com"
        private const val YT_DARK = 0xFF0F0F0F.toInt() // fondo de YouTube en oscuro (su theme-color)
        const val EXTRA_PAUSE = "pause" // desde la notificación del límite: usar la pausa de emergencia
        const val EXTRA_CHAT = "chat" // desde la notificación de un mensaje: el nombre del chat, para abrirlo
        private const val PERF_REPORT = "Lúcido rendimiento" // así empieza el resumen de mediciones del script
    }
}
