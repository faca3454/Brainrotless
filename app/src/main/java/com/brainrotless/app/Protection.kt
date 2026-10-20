package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.max

/** Uso de Instagram y YouTube oficiales antes de instalar Lúcido (para la estadística final). */
object UsageAccess {
    private val OFFICIAL = listOf("com.instagram.android", "com.google.android.youtube")
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun open(c: Context) {
        val pkg = Uri.parse("package:${c.packageName}")
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(direct) }.onFailure {
            runCatching { c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /**
     * Promedio diario en las 4 semanas antes de instalar Lúcido. Se calcula una vez y se
     * guarda, porque Android va borrando los datos viejos. null si no hay permiso o datos.
     */
    fun baseline(c: Context): Long? {
        val p = c.getSharedPreferences("usage_baseline", Context.MODE_PRIVATE)
        if (p.contains("ms_per_day")) return p.getLong("ms_per_day", 0)
        if (!granted(c)) return null
        val install = c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime
        val from = install - 28 * DAY_MS
        val stats = c.getSystemService(UsageStatsManager::class.java)
            ?.queryAndAggregateUsageStats(from, install) ?: return null
        val found = OFFICIAL.mapNotNull { stats[it] }
        val ms = found.sumOf { it.totalTimeInForeground }
        if (ms <= 0) return null
        val since = max(from, found.minOf { it.firstTimeStamp })
        val days = ((install - since) / DAY_MS).coerceIn(1, 28)
        return (ms / days).also { p.edit().putLong("ms_per_day", it).apply() }
    }
}

/**
 * Difícil de sacar durante un compromiso: protección contra desinstalación (administrador de
 * dispositivo, con espera para quitarlo) y aviso fijo si se apaga la accesibilidad.
 */
object Protection {
    private const val CHANNEL = "guard"
    private const val NOTIF_ID = 2003

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, Admin::class.java)

    fun adminActive(c: Context) = dpm(c)?.isAdminActive(admin(c)) == true

    fun requestAdmin(a: Activity) {
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Mientras dure tu compromiso, para desinstalar Lúcido vas a tener que quitar este " +
                    "permiso primero, con 2 minutos de espera. Al terminar el compromiso se quita solo."
            )
        runCatching { a.startActivity(i) }
    }

    fun removeAdmin(c: Context) {
        if (adminActive(c)) runCatching { dpm(c)?.removeActiveAdmin(admin(c)) }
    }

    /** Accesibilidad apagada durante un compromiso: notificación fija hasta que se vuelva a activar. */
    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    fun updateGuardNotice(c: Context, guardOn: Boolean = Setup.guardOn(c)) {
        val nm = NotificationManagerCompat.from(c)
        if (guardOn || !Commitment.active(c)) { nm.cancel(NOTIF_ID); return }
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Protección del compromiso").build()
        )
        val pi = PendingIntent.getActivity(
            c, 2, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Tu compromiso sigue: volvé a activar 'Bloquear apps oficiales' en Accesibilidad. " +
            "Mientras tanto, IG Lite y YT Lite no abren."
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("⚠ Lúcido está apagado")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    fun isGuardNotice(sbn: StatusBarNotification) = sbn.packageName == PKG && sbn.id == NOTIF_ID
}

/** Administrador de dispositivo: sin él activo no se puede desinstalar la app. No usa ninguna política. */
class Admin : DeviceAdminReceiver() {
    override fun onDisableRequested(c: Context, i: Intent): CharSequence {
        if (!Commitment.active(c)) return "Lúcido ya no va a estar protegido contra la desinstalación."
        runCatching { c.startActivity(Intent(c, WaitActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return "Vas a romper tu compromiso: quedan ${Commitment.daysLeft(c)} días."
    }
}

/** Espera de 2 minutos antes de poder quitar la protección del compromiso, para cortar el impulso. */
class WaitActivity : ComponentActivity() {
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun centered(v: TextView) = v.apply { gravity = Gravity.CENTER; setPadding(0, Brand.px(context, 8), 0, Brand.px(context, 8)) }
        val countdown = centered(Brand.text(this, "", 44f, R.color.brand_primary, bold = true))
        // Vuelve a la ventana del sistema para confirmar
        val proceed = Brand.secondaryButton(this, "Continuar") { finish() }.apply { isEnabled = false; alpha = 0.4f }
        val wrap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = Brand.px(context, 32)
            setPadding(pad, pad, pad, pad)
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_logo) },
                LinearLayout.LayoutParams(Brand.px(context, 56), Brand.px(context, 56)).apply { bottomMargin = Brand.px(context, 20) })
            addView(centered(Brand.text(context, "Pará un momento ✋", 24f, bold = true)))
            addView(centered(Brand.text(context, "Estás por quitar la protección de tu compromiso (quedan " +
                "${Commitment.daysLeft(this@WaitActivity)} días). Si igual querés hacerlo, esperá:", 16f, R.color.brand_text_muted)))
            addView(countdown)
            addView(Brand.primaryButton(context, "Mantener mi compromiso") {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }, wrap)
            addView(proceed, LinearLayout.LayoutParams(wrap).apply { topMargin = Brand.px(context, 10) })
        })

        val back = object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} }
        onBackPressedDispatcher.addCallback(this, back) // durante la espera, atrás no hace nada
        timer = object : CountDownTimer(WAIT_MS, 1000) {
            override fun onTick(left: Long) {
                val s = (left + 999) / 1000
                countdown.text = "%d:%02d".format(s / 60, s % 60)
            }
            override fun onFinish() {
                countdown.text = "0:00"
                proceed.isEnabled = true
                proceed.alpha = 1f
                back.isEnabled = false
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private companion object { const val WAIT_MS = 2 * 60 * 1000L }
}
