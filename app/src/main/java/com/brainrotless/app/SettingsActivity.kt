package com.brainrotless.app

import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Date

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = R.id.settings_container }
        setContentView(container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private companion object {
            const val LOOSEN_MSG = "Durante el compromiso solo podés hacerlo más estricto"
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            Setup.migratePrefs(requireContext()) // antes de inflar, que guarda los valores por defecto
            setPreferencesFromResource(R.xml.preferences, rootKey)

            onClick("perm_notif") { Setup.openNotifications(requireContext()) }
            onClick("perm_links") { Setup.openLinks(requireContext()) }
            onClick("perm_guard") { Setup.openAccessibility(requireContext()) }
            onClick("perm_restricted") { Setup.explainRestricted(requireActivity()) }
            onClick("bypass_now") { toggleBypass() }
            onClick("bypass_tile") { addTile() }
            onClick("commit") { if (!Commitment.active(requireContext())) startCommitment() }
            onClick("debug_end") {
                Commitment.cancelForTesting(requireContext())
                Sleep.reschedule(requireContext())
                refreshCommitment()
            }
            onClick("pause_now") {
                if (Commitment.startPause(requireContext()))
                    Toast.makeText(requireContext(), "🆘 Pausa de emergencia: 15 minutos", Toast.LENGTH_LONG).show()
                refreshCommitment()
            }
            // Durante el compromiso: endurecer sí, aflojar no
            guard("sleep_on") { s, v -> s.copy(sleepOn = v as Boolean) }
            guard("sleep_browsers") { s, v -> s.copy(browsers = v as Boolean) }
            guard("daily_limit") { s, v -> s.copy(limitMinutes = (v as String).toInt().takeIf { it > 0 }) }
            guard("sleep_per_day") { s, v ->
                val c = requireContext()
                s.copy(nights = DayOfWeek.values().associateWith { Sleep.get(c, if (v as Boolean) it else null) })
            }
            onClick("sleep_all") { pickNight(null) }
            for (d in DayOfWeek.values()) onClick("sleep_day_${d.value}") { pickNight(d) }
            onClick("check_now") {
                Updater.maybeCheck(requireActivity(), force = true)
                Rules.refreshAsync(requireContext(), force = true) { }
            }
            findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
        }

        override fun onResume() {
            super.onResume()
            // Volviendo de los ajustes del sistema: refrescar el estado de cada permiso
            val c = requireContext()
            val listenerOn = Setup.listenerOn(c)
            val guardOn = Setup.guardOn(c)
            findPreference<Preference>("perm_notif")?.summary =
                status(listenerOn, "Las notificaciones abren esta app")
            findPreference<Preference>("perm_guard")?.summary =
                status(guardOn, "Abrir Instagram/YouTube oficiales te trae acá")
            findPreference<Preference>("perm_restricted")?.isVisible =
                Setup.restrictedApplies() && !(listenerOn && guardOn)
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefsListener)
            refreshSleep()
            refreshBypass()
            refreshCommitment()
        }

        override fun onPause() {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }

        // ---------- Pausa del bloqueo ----------

        private fun toggleBypass() {
            val c = requireContext()
            if (Bypass.active(c)) Bypass.stop(c) else Bypass.start(c)
            TileService.requestListeningState(c, ComponentName(c, BypassTile::class.java)) // que el botón se actualice
            refreshBypass()
        }

        private fun refreshBypass() {
            val c = requireContext()
            val night = Sleep.current(c)
            findPreference<Preference>("bypass_now")?.apply {
                isEnabled = night == null
                when {
                    night != null -> {
                        title = "Pausar ahora"
                        summary = "No funciona en horario de dormir (hasta las ${Sleep.fmt(c, night.end.toLocalTime())})"
                    }
                    Bypass.active(c) -> {
                        title = "Cortar la pausa"
                        summary = "Apps oficiales sin redirigir hasta las ${DateFormat.getTimeFormat(c).format(Date(Bypass.until(c)))}"
                    }
                    else -> {
                        title = "Pausar ahora"
                        summary = "Abrí Instagram o YouTube oficiales sin que te traiga acá"
                    }
                }
            }
            findPreference<Preference>("bypass_tile")?.isVisible = Build.VERSION.SDK_INT >= 33
        }

        /** Android 13+: ventana del sistema para agregar el botón "Apps oficiales" con un toque. */
        private fun addTile() {
            if (Build.VERSION.SDK_INT < 33) return
            val c = requireContext()
            c.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                ComponentName(c, BypassTile::class.java), getString(R.string.tile_label),
                Icon.createWithResource(c, R.drawable.ic_tile), c.mainExecutor
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Listo: el botón quedó en los ajustes rápidos"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "El botón ya está en los ajustes rápidos"
                    else -> null
                }
                msg?.let { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() }
            }
        }

        // ---------- Compromiso ----------

        private fun guard(key: String, change: (Strictness, Any) -> Strictness) {
            findPreference<Preference>(key)?.setOnPreferenceChangeListener { _, v ->
                val forbidden = Commitment.forbids(requireContext()) { change(it, v) }
                if (forbidden) Toast.makeText(requireContext(), LOOSEN_MSG, Toast.LENGTH_LONG).show()
                !forbidden
            }
        }

        private fun refreshCommitment() {
            val c = requireContext()
            val active = Commitment.active(c)
            val time = DateFormat.getTimeFormat(c)
            findPreference<Preference>("commit")?.apply {
                title = if (active) "Compromiso activo" else "Empezar compromiso"
                summary = if (active) {
                    val end = Date(Commitment.endMillis(c))
                    "Hasta el ${DateFormat.getDateFormat(c).format(end)} a las ${time.format(end)} · " +
                        "${Commitment.pausesLeft(c)} pausas de emergencia"
                } else "Mínimo ${Commitment.MIN_DAYS} días sin poder aflojar el modo dormir ni el límite diario"
            }
            findPreference<Preference>("usage_today")?.summary = buildString {
                append(minutes(Usage.todayMs(c)))
                Usage.limitMs(c)?.let { append(" de ${minutes(it)}") }
                append(" · se reinicia a las ${Sleep.fmt(c, Usage.nextReset(c).toLocalTime())}")
            }
            findPreference<Preference>("debug_end")?.isVisible = BuildConfig.DEBUG && active
            findPreference<Preference>("pause_now")?.apply {
                isVisible = active
                isEnabled = Commitment.canPause(c)
                summary = when {
                    Commitment.pauseActive(c) -> "En curso hasta las ${time.format(Date(Commitment.pauseUntil(c)))}"
                    Commitment.canPause(c) -> "15 minutos · quedan ${Commitment.pausesLeft(c)}"
                    else -> "Al llegar al límite o de noche (máximo 2 por día) · quedan ${Commitment.pausesLeft(c)}"
                }
            }
        }

        private fun minutes(ms: Long): String {
            val m = ms / 60_000
            return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
        }

        private fun startCommitment() {
            val c = requireContext()
            if (!Sleep.enabled(c) && Usage.limitMinutes(c) == null) {
                AlertDialog.Builder(c)
                    .setMessage("Primero activá el modo dormir o elegí un límite diario: eso es lo que el compromiso no te va a dejar aflojar.")
                    .setPositiveButton("OK", null).show()
                return
            }
            val picker = NumberPicker(c).apply {
                minValue = Commitment.MIN_DAYS
                maxValue = 365
                value = Commitment.MIN_DAYS
                wrapSelectorWheel = false
            }
            AlertDialog.Builder(c)
                .setTitle("¿Cuántos días?")
                .setMessage("Mínimo ${Commitment.MIN_DAYS}. Te da una pausa de emergencia cada 2 días.")
                .setView(picker)
                .setPositiveButton("Siguiente") { _, _ -> confirmCommitment(picker.value) }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        private fun confirmCommitment(days: Int) {
            val c = requireContext()
            val end = Date(System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L)
            val rules = buildList {
                if (Sleep.enabled(c)) {
                    add("• Apagar el modo dormir, acostarte más tarde ni despertarte más temprano")
                    if (c.userPrefs().getBoolean("sleep_browsers", true)) add("• Desbloquear los navegadores de noche")
                }
                Usage.limitMinutes(c)?.let { add("• Subir el límite diario (${minutes(it * 60_000L)} entre Instagram y YouTube)") }
            }
            val pauses = Commitment.pausesFor(days)
            val dialog = AlertDialog.Builder(c)
                .setTitle("Compromiso de $days días")
                .setMessage(
                    "Hasta el ${DateFormat.getDateFormat(c).format(end)} no vas a poder:\n\n" +
                        rules.joinToString("\n") +
                        "\n\nSí vas a poder hacerlo más estricto.\n\n" +
                        "Tenés $pauses pausas de emergencia de 15 minutos (máximo 2 por día), para cuando " +
                        "llegues al límite o de noche (de noche solo abren Instagram).\n\n" +
                        "Una vez que empieza, no se puede cancelar."
                )
                .setPositiveButton("Empezar") { _, _ ->
                    Commitment.start(c, days)
                    Sleep.reschedule(c)
                    refreshCommitment()
                    Toast.makeText(c, "Compromiso empezado: $days días 💪", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            // 5 segundos para leerlo antes de poder confirmar
            val start = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            start.isEnabled = false
            for (left in 5 downTo 0) start.postDelayed({
                start.text = if (left > 0) "Empezar ($left)" else "Empezar"
                start.isEnabled = left == 0
            }, (5 - left) * 1000L)
        }

        // ---------- Modo dormir ----------

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("sleep_") == true) refreshSleep()
            if (key?.startsWith("sleep_") == true || key == "daily_limit") refreshCommitment()
        }

        private fun refreshSleep() {
            val c = requireContext()
            val perDay = Sleep.perDay(c)
            findPreference<Preference>("sleep_all")?.apply {
                isVisible = !perDay
                summary = "Todos los días, de ${night(Sleep.get(c, null))}"
            }
            for (d in DayOfWeek.values()) findPreference<Preference>("sleep_day_${d.value}")?.apply {
                isVisible = perDay
                summary = night(Sleep.get(c, d))
            }
            findPreference<Preference>("sleep_browsers")?.summary = when {
                !Setup.guardOn(c) -> "⚠ Activá 'Bloquear apps oficiales' (arriba) para que funcione"
                else -> Browsers.names(c).takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?.let { "No abren en tu horario: $it" } ?: "No hay navegadores instalados"
            }
            Sleep.reschedule(c)
        }

        private fun night(t: Pair<LocalTime, LocalTime>) =
            "${Sleep.fmt(requireContext(), t.first)} a ${Sleep.fmt(requireContext(), t.second)}"

        /** Hora de dormir y después hora de despertar, del horario general (null) o de un día. */
        private fun pickNight(day: DayOfWeek?) {
            val c = requireContext()
            val (sleep, wake) = Sleep.get(c, day)
            pickTime("Hora de dormir", sleep) { newSleep ->
                pickTime("Hora de despertar", wake) { newWake ->
                    // El horario general rige todos los días; el de un día, solo ese
                    val days = day?.let { listOf(it) } ?: DayOfWeek.values().toList()
                    if (Commitment.forbids(c) { s -> s.copy(nights = s.nights + days.associateWith { newSleep to newWake }) })
                        Toast.makeText(c, LOOSEN_MSG, Toast.LENGTH_LONG).show()
                    else Sleep.set(c, day, newSleep, newWake)
                }
            }
        }

        private fun pickTime(title: String, t: LocalTime, done: (LocalTime) -> Unit) {
            TimePickerDialog(
                requireContext(), { _, h, m -> done(LocalTime.of(h, m)) },
                t.hour, t.minute, DateFormat.is24HourFormat(requireContext())
            ).apply { setTitle(title) }.show()
        }

        private fun status(on: Boolean, what: String) =
            if (on) "✓ Activado. $what" else "○ Desactivado. Tocá para activarlo"

        private fun onClick(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }
    }
}
