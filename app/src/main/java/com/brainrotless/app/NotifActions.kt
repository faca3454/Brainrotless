package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import android.app.RemoteInput as PlatformRemoteInput

/**
 * Botones "Responder" y "Marcar como leído" de las notificaciones que copia [NotifRedirect]:
 * le pasa la acción a la app oficial (que manda el mensaje sin abrirse) y actualiza la nuestra.
 * Las acciones originales se guardan en memoria; si Android cerró Lúcido mientras tanto, se
 * avisa que hay que responder desde IG Lite.
 */
class NotifActions : BroadcastReceiver() {
    /** Acciones de la notificación original de la app oficial. */
    class Original(val reply: PendingIntent?, val inputs: Array<PlatformRemoteInput>, val read: PendingIntent?)

    companion object {
        const val ACTION_REPLY = "com.brainrotless.app.REPLY"
        const val ACTION_READ = "com.brainrotless.app.READ"
        const val EXTRA_ID = "id"
        const val KEY_TEXT = "text"

        private val originals = object : LinkedHashMap<Int, Original>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Original>?) = size > 40
        }

        fun remember(id: Int, o: Original) = synchronized(originals) { originals[id] = o }
        private fun recall(id: Int) = synchronized(originals) { originals[id] }
    }

    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra(EXTRA_ID, 0)
        val original = recall(id)
        when (i.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(i)?.getCharSequence(KEY_TEXT)?.takeIf { it.isNotBlank() } ?: return
                val sent = original?.reply != null && forward(c, original, text)
                if (!sent) Toast.makeText(c, R.string.notif_reply_failed, Toast.LENGTH_LONG).show()
                update(c, id, if (sent) text else null)
            }
            ACTION_READ -> {
                original?.read?.let { pi -> runCatching { pi.send() } }
                NotificationManagerCompat.from(c).cancel(id)
            }
        }
    }

    /** Le pasa el texto a la acción de responder de la app oficial, con sus propias claves. */
    private fun forward(c: Context, o: Original, text: CharSequence): Boolean = runCatching {
        val results = Bundle().apply { o.inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fill = Intent()
        PlatformRemoteInput.addResultsToIntent(o.inputs, fill, results)
        if (Build.VERSION.SDK_INT >= 28) PlatformRemoteInput.setResultsSource(fill, PlatformRemoteInput.SOURCE_FREE_FORM_INPUT)
        o.reply!!.send(c, 0, fill)
        true
    }.getOrDefault(false)

    /**
     * Vuelve a publicar la notificación (así Android deja de mostrar "enviando") y, si se mandó,
     * suma la respuesta a la conversación como mensaje tuyo.
     */
    @SuppressLint("MissingPermission") // sin permiso, notify() falla en silencio
    private fun update(c: Context, id: Int, reply: CharSequence?) {
        val current = c.getSystemService(NotificationManager::class.java)?.activeNotifications
            ?.firstOrNull { it.id == id }?.notification ?: return
        val b = NotificationCompat.Builder(c, current).setOnlyAlertOnce(true)
        if (reply != null) NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(current)?.let { style ->
            style.addMessage(reply, System.currentTimeMillis(), null as androidx.core.app.Person?) // sin persona: sos vos
            b.setStyle(style)
        }
        runCatching { NotificationManagerCompat.from(c).notify(id, b.build()) }
    }
}
