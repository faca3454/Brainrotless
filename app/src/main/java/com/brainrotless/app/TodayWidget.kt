package com.brainrotless.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import java.util.Date

/**
 * Widget de la pantalla del teléfono: uso de hoy contra el límite, en qué estado está la app
 * (dormir, foco, límite) y accesos a IG Lite y YT Lite. Todos los textos se ponen desde acá,
 * así respetan el idioma elegido en la app (el diseño lo dibuja el launcher).
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        shown = null
        update(c)
    }

    companion object {
        private var shown: String? = null // lo último que se mostró: no repetir actualizaciones iguales

        /** Refresca todos los widgets puestos; barato si no cambió nada. */
        fun update(c: Context) {
            val mgr = AppWidgetManager.getInstance(c) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(c, TodayWidget::class.java))
            if (ids.isEmpty()) return

            val used = Usage.todayMs(c)
            val limit = Usage.limitMs(c)
            val amount = formatDuration(used) + (limit?.let { c.getString(R.string.usage_of, formatDuration(it)) } ?: "")
            val progress = limit?.let { (used * 1000 / it).toInt().coerceIn(0, 1000) } ?: 0
            val reset = Sleep.fmt(c, Usage.nextReset(c).toLocalTime())
            val status = when {
                Sleep.active(c) -> c.getString(R.string.widget_night, Lock.until(c, Lock.Reason.NIGHT))
                Focus.active(c) -> c.getString(R.string.widget_focus, Lock.until(c, Lock.Reason.FOCUS))
                Commitment.pauseActive(c) ->
                    c.getString(R.string.home_pause_until, DateFormat.getTimeFormat(c).format(Date(Commitment.pauseUntil(c))))
                Usage.overLimit(c) -> c.getString(R.string.home_over, reset)
                else -> c.getString(R.string.home_resets, reset)
            }
            val key = "$amount|$progress|$status|${Lang.current(c)}"
            if (key == shown) return
            shown = key

            val v = RemoteViews(c.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.w_title, c.getString(R.string.widget_title))
                setTextViewText(R.id.w_amount, amount)
                setTextViewText(R.id.w_status, status)
                setViewVisibility(R.id.w_progress, if (limit != null) View.VISIBLE else View.GONE)
                setProgressBar(R.id.w_progress, 1000, progress, false)
                setContentDescription(R.id.w_ig, c.getString(R.string.label_ig))
                setContentDescription(R.id.w_yt, c.getString(R.string.label_yt))
                setOnClickPendingIntent(R.id.w_root, open(c, Intent(c, HomeActivity::class.java), 10))
                setOnClickPendingIntent(R.id.w_ig, open(c, alias(c, "Instagram"), 11))
                setOnClickPendingIntent(R.id.w_yt, open(c, alias(c, "YouTube"), 12))
            }
            runCatching { mgr.updateAppWidget(ids, v) }
        }

        private fun alias(c: Context, name: String) =
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(c, "$PKG.$name"))

        private fun open(c: Context, i: Intent, code: Int) = PendingIntent.getActivity(
            c, code, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
