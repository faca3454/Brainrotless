package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Intercepta las notificaciones de las apps oficiales, las borra
 * y publica una copia que al tocarla abre Lúcido en vez de la app oficial.
 */
class NotifRedirect : NotificationListenerService() {

    private val targets = mapOf(
        "com.instagram.android" to "https://www.instagram.com/direct/inbox/",
        "com.google.android.youtube" to "https://m.youtube.com/feed/subscriptions"
    )

    override fun onListenerConnected() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Mensajes y avisos").build()
        )
        // Redirigir también las que ya estaban en la bandeja
        runCatching { activeNotifications?.forEach(::onNotificationPosted) }
    }

    // Si se cierra el aviso de "Lúcido está apagado" durante un compromiso, vuelve a aparecer
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Protection.isGuardNotice(sbn)) Protection.updateGuardNotice(this)
    }

    @SuppressLint("MissingPermission") // sin permiso de notificaciones, notify() falla en silencio (runCatching)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val url = targets[sbn.packageName] ?: return
        val n = sbn.notification
        val f = n.flags

        // No tocar reproducción en segundo plano ni subidas en curso
        if (f and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        cancelNotification(sbn.key)
        if (f and Notification.FLAG_GROUP_SUMMARY != 0) return // el resumen de grupo no aporta nada

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)
        val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val open = Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val id = sbn.key.hashCode()
        val pi = PendingIntent.getActivity(
            this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = if (sbn.packageName.contains("instagram")) "IG Lite" else "YT Lite"
        val out = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setSubText(app)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(n.`when`)
            .setShowWhen(true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Falla en silencio si el usuario no dio permiso de notificaciones
        runCatching { NotificationManagerCompat.from(this).notify(id, out) }
    }

    companion object { const val CHANNEL = "redirect" }
}
