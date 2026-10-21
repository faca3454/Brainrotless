package com.brainrotless.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * Intercepta las notificaciones de las apps oficiales, las borra y publica una copia que al
 * tocarla abre Lúcido en vez de la app oficial. La copia lleva el ícono y el color de la app de
 * la que viene y la foto de quien te escribió. Los mensajes se arman como conversación: los de
 * la misma persona se juntan en una sola notificación, con su foto grande, como en las apps de chat.
 */
class NotifRedirect : NotificationListenerService() {

    private val targets = mapOf(
        "com.instagram.android" to "https://www.instagram.com/direct/inbox/",
        "com.google.android.youtube" to "https://m.youtube.com/feed/subscriptions"
    )

    override fun onListenerConnected() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.channel_redirect)).build()
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
        // Sin permiso para mostrar la copia, borrar la original perdería el mensaje: queda como está
        if (!Setup.canPost(this)) return

        cancelNotification(sbn.key)
        if (f and Notification.FLAG_GROUP_SUMMARY != 0) return // el resumen de grupo no aporta nada

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)
        val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val ig = sbn.packageName == "com.instagram.android"
        val label = getString(if (ig) R.string.label_ig else R.string.label_yt)
        val original = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val isMessage = original != null || n.category == Notification.CATEGORY_MESSAGE
        // Los mensajes de una misma conversación van todos a una sola notificación
        val key = if (isMessage) n.shortcutId ?: original?.conversationTitle?.toString() ?: title?.toString() ?: sbn.key
            else sbn.key
        val id = "${sbn.packageName}/$key".hashCode()

        // Un mensaje de Instagram abre ese chat (IG Lite lo busca por el nombre con que aparece en la lista)
        val chat = if (ig && isMessage) (original?.conversationTitle ?: title)?.toString() else null
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CHAT, chat)
        val pi = PendingIntent.getActivity(
            this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val avatar = largeIcon(n)?.let(::circle)

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (ig) R.drawable.ic_notif_ig else R.drawable.ic_notif_yt)
            .setColor(ContextCompat.getColor(this, if (ig) R.color.notif_ig else R.color.notif_yt))
            .setSubText(label)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(n.`when`)
            .setShowWhen(true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(n.category)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isMessage) {
            val style = original?.let(::withBitmaps) ?: appendMessage(id, title ?: label, text ?: "", n.`when`, avatar)
            b.setStyle(style)
            if (Build.VERSION.SDK_INT >= 30) {
                // Conversación: sale arriba, con la foto de quien escribe y el ícono de la app encima
                val who = style.messages.lastOrNull { it.person != null }?.person
                val face = if (style.isGroupConversation) avatar?.let(IconCompat::createWithBitmap) else who?.icon
                    ?: avatar?.let(IconCompat::createWithBitmap)
                val name = style.conversationTitle ?: who?.name ?: title ?: label
                conversation("${if (ig) "ig" else "yt"}_${key.hashCode()}", name, face, who, open, if (ig) "Instagram" else "YouTube")
                    ?.let(b::setShortcutId)
            } else avatar?.let { b.setLargeIcon(it) } // en versiones viejas la foto va al costado
            addActions(b, id, n)
        } else {
            avatar?.let { b.setLargeIcon(it) }
            val picture = picture(n) // ej: la miniatura de un video nuevo de YouTube
            b.setStyle(
                if (picture != null) NotificationCompat.BigPictureStyle().bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?).setSummaryText(text)
                else NotificationCompat.BigTextStyle().bigText(text)
            )
        }

        // Falla en silencio si el usuario no dio permiso de notificaciones. Si falla por una
        // imagen demasiado pesada, sale sin imágenes antes que perderse.
        val nm = NotificationManagerCompat.from(this)
        runCatching { nm.notify(id, b.build()) }.onFailure {
            runCatching {
                nm.notify(id, b.setStyle(NotificationCompat.BigTextStyle().bigText(text)).setLargeIcon(null as Bitmap?).build())
            }
        }
    }

    /**
     * Responder y marcar como leído sin abrir nada: si la notificación original trae esas
     * acciones, la nuestra también. Pasan por [NotifActions], que se las reenvía a la app oficial
     * y actualiza la conversación (la app oficial manda el mensaje sin abrirse).
     */
    private fun addActions(b: NotificationCompat.Builder, id: Int, n: Notification) {
        val actions = n.actions ?: return
        val reply = actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val read = if (Build.VERSION.SDK_INT >= 28)
            actions.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ && it.remoteInputs.isNullOrEmpty() }
            else null
        if (reply == null && read == null) return
        NotifActions.remember(id, NotifActions.Original(reply?.actionIntent, reply?.remoteInputs ?: emptyArray(), read?.actionIntent))

        fun pending(action: String, mutable: Boolean) = PendingIntent.getBroadcast(
            this, id, Intent(this, NotifActions::class.java).setAction(action).putExtra(NotifActions.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        )
        if (reply != null) b.addAction(
            // Mutable: Android le agrega lo que escribiste antes de mandarla
            NotificationCompat.Action.Builder(0, getString(R.string.notif_reply), pending(NotifActions.ACTION_REPLY, mutable = true))
                .addRemoteInput(androidx.core.app.RemoteInput.Builder(NotifActions.KEY_TEXT).setLabel(getString(R.string.notif_reply_hint)).build())
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(true)
                .setShowsUserInterface(false)
                .build()
        )
        if (read != null) b.addAction(
            NotificationCompat.Action.Builder(0, getString(R.string.notif_read), pending(NotifActions.ACTION_READ, mutable = false))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
        )
    }

    /** La foto de quien escribe: la imagen grande de la notificación original. */
    @Suppress("DEPRECATION") // EXTRA_LARGE_ICON viejo (Bitmap), para apps que todavía lo usan
    private fun largeIcon(n: Notification): Bitmap? =
        n.getLargeIcon()?.let(::load) ?: n.extras.get(Notification.EXTRA_LARGE_ICON) as? Bitmap

    /** La imagen grande expandida (miniatura de video), achicada para que pase sin problemas. */
    @Suppress("DEPRECATION")
    private fun picture(n: Notification): Bitmap? {
        val ex: Bundle = n.extras
        val bmp = ex.get(Notification.EXTRA_PICTURE) as? Bitmap
            ?: (if (Build.VERSION.SDK_INT >= 31) (ex.get(Notification.EXTRA_PICTURE_ICON) as? Icon)?.let(::load) else null)
            ?: return null
        val soft = software(bmp)
        if (soft.width <= MAX_PICTURE) return soft
        return Bitmap.createScaledBitmap(soft, MAX_PICTURE, soft.height * MAX_PICTURE / soft.width, true)
    }

    private fun load(icon: Icon): Bitmap? = runCatching { icon.loadDrawable(this)?.let(::toBitmap) }.getOrNull()

    private fun toBitmap(d: Drawable): Bitmap =
        d.toBitmap(d.intrinsicWidth.takeIf { it > 0 } ?: AVATAR, d.intrinsicHeight.takeIf { it > 0 } ?: AVATAR)

    /** Las imágenes "de hardware" no se pueden dibujar a mano: copia común. */
    private fun software(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    /** Recorta la foto en círculo (como las fotos de perfil), de hasta [AVATAR] px. */
    private fun circle(src: Bitmap): Bitmap {
        val img = software(src)
        val side = minOf(img.width, img.height)
        val size = minOf(side, AVATAR)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val from = Rect((img.width - side) / 2, (img.height - side) / 2, (img.width + side) / 2, (img.height + side) / 2)
        canvas.drawBitmap(img, from, Rect(0, 0, size, size), paint)
        return out
    }

    /**
     * La conversación tal como la armó la app oficial, con las fotos copiadas como imágenes
     * propias: las de la app oficial pueden ser archivos suyos que Lúcido no puede abrir.
     */
    private fun withBitmaps(s: NotificationCompat.MessagingStyle): NotificationCompat.MessagingStyle {
        fun fix(p: Person?): Person? = p?.toBuilder()
            ?.setIcon(p.icon?.let { i -> runCatching { i.loadDrawable(this) }.getOrNull() }?.let { IconCompat.createWithBitmap(circle(toBitmap(it))) })
            ?.build()
        // (con "also" y no "apply": MessagingStyle tiene su propio método apply)
        return NotificationCompat.MessagingStyle(fix(s.user) ?: s.user).also { copy ->
            copy.setConversationTitle(s.conversationTitle)
            copy.setGroupConversation(s.isGroupConversation)
            for (m in s.messages.takeLast(MAX_MESSAGES)) copy.addMessage(m.text ?: continue, m.timestamp, fix(m.person))
        }
    }

    /** Un mensaje suelto se suma a los anteriores de la misma persona que siguen en la bandeja. */
    private fun appendMessage(id: Int, from: CharSequence, text: CharSequence, time: Long, avatar: Bitmap?): NotificationCompat.MessagingStyle {
        val previous = getSystemService(NotificationManager::class.java)?.activeNotifications
            ?.firstOrNull { it.id == id }?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
        val style = previous ?: NotificationCompat.MessagingStyle(Person.Builder().setName(getString(R.string.notif_you)).build())
        val sender = Person.Builder()
            .setName(from)
            .setKey(from.toString())
            .setIcon(avatar?.let(IconCompat::createWithBitmap))
            .build()
        // La app oficial a veces vuelve a publicar el mismo mensaje (mismo texto y misma hora): no
        // repetirlo. Dos "jaja" seguidos de verdad tienen distinta hora y quedan los dos.
        val last = style.messages.lastOrNull()
        if (last?.text?.toString() != text.toString() || last?.timestamp != time) style.addMessage(text, time, sender)
        while (style.messages.size > MAX_MESSAGES) style.messages.removeAt(0)
        return style
    }

    /**
     * Atajo de conversación (Android 11+): sin él, Android no muestra la notificación como chat.
     * Va colgado del ícono de IG Lite / YT Lite: al mantenerlo presionado aparecen los chats
     * recientes. Si Android no acepta ese ícono, se publica sin él.
     */
    @RequiresApi(30)
    private fun conversation(id: String, name: CharSequence, face: IconCompat?, who: Person?, intent: Intent, alias: String): String? {
        fun shortcut(onIcon: Boolean) = ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(name)
            .setLongLived(true)
            .setIntent(intent)
            .setIcon(face ?: IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .apply {
                who?.let { setPerson(it) }
                if (onIcon) setActivity(ComponentName(this@NotifRedirect, "$PKG.$alias"))
            }
            .build()
        for (onIcon in listOf(true, false))
            if (runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, shortcut(onIcon)) }.getOrDefault(false)) return id
        return null
    }

    companion object {
        const val CHANNEL = "redirect"
        private const val AVATAR = 192 // px de la foto de perfil
        private const val MAX_PICTURE = 720 // px de ancho de la miniatura
        private const val MAX_MESSAGES = 6
    }
}
