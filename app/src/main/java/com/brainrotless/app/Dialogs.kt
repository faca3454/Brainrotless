package com.brainrotless.app

import android.app.AlertDialog
import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/**
 * Los diálogos propios de la app, armados en un solo lugar (las pantallas los muestran con
 * show()). Así se pueden dibujar con datos de prueba (Paparazzi).
 */
object Dialogs {
    /** Terminó el compromiso: el resumen y si empezar otro. [onCharts] null: sin el botón de gráficos. */
    fun summary(c: Context, text: String, done: Int, onAnother: () -> Unit, onCharts: (() -> Unit)?): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setMessage(text)
            .setPositiveButton(R.string.start_another) { _, _ -> onAnother() }
            .apply { onCharts?.let { f -> setNeutralButton(R.string.summary_charts) { _, _ -> f() } } }
            .setNegativeButton(done, null)

    /** Confirmar un compromiso nuevo (la pantalla agrega la cuenta regresiva del botón). */
    fun confirmCommitment(c: Context, days: Int, end: Date, rules: List<String>, onStart: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.commit_confirm_title, days))
            .setMessage(c.getString(R.string.commit_confirm_msg, DateFormat.getDateFormat(c).format(end),
                rules.joinToString("\n"), Commitment.pausesFor(days)))
            .setPositiveButton(R.string.start) { _, _ -> onStart() }
            .setNegativeButton(R.string.cancel, null)

    /** Recién empezado el compromiso: ofrecer el permiso para la estadística final. */
    fun offerCompare(c: Context, onOpen: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(R.string.offer_title)
            .setMessage(R.string.offer_msg)
            .setPositiveButton(R.string.offer_compare) { _, _ -> onOpen() }
            .setNegativeButton(R.string.not_now, null)

    /** Sesión larga en IG Lite o YT Lite. */
    fun session(c: Context, minutes: Int, onExit: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.session_title, minutes))
            .setMessage(R.string.session_text)
            .setPositiveButton(R.string.session_exit) { _, _ -> onExit() }
            .setNegativeButton(R.string.session_more, null)

    /** Antes de gastar una pausa de emergencia (hay pocas): cuánto dura y cuántas quedan. */
    fun pauseConfirm(c: Context, left: Int, onUse: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(R.string.pause_confirm_title)
            .setMessage(c.resources.getQuantityString(R.plurals.pause_confirm_text, left, left))
            .setPositiveButton(R.string.pause_confirm_use) { _, _ -> onUse() }
            .setNegativeButton(R.string.cancel, null)

    /** Cómo destrabar un permiso gris (Android 13+). */
    fun restricted(c: Context, onOpen: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(R.string.gray_title)
            .setMessage(R.string.gray_msg)
            .setPositiveButton(R.string.open) { _, _ -> onOpen() }
            .setNegativeButton(R.string.cancel, null)

    /** La primera vez sin guía: el idioma (en los dos, para que se entienda igual). */
    fun language(c: Context, onPick: (String) -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle("Idioma · Language")
            .setItems(arrayOf("Español", "English")) { _, which -> onPick(if (which == 0) "es" else "en") }
            .setCancelable(false)

    /** Hay una versión nueva. */
    fun update(c: Context, version: String, notes: String, onDownload: () -> Unit): AlertDialog.Builder =
        AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.update_title, version))
            .setMessage(notes.take(600).ifBlank { c.getString(R.string.update_new) } + c.getString(R.string.update_install))
            .setPositiveButton(R.string.update_download) { _, _ -> onDownload() }
            .setNegativeButton(R.string.update_later, null)
}
