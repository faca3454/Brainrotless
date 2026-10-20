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
import android.widget.Toast
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
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            Setup.migratePrefs(requireContext()) // antes de inflar, que guarda los valores por defecto
            setPreferencesFromResource(R.xml.preferences, rootKey)

            onClick("perm_notif") { Setup.openNotifications(requireContext()) }
            onClick("perm_links") { Setup.openLinks(requireContext()) }
            onClick("perm_guard") { Setup.openAccessibility(requireContext()) }
            onClick("perm_restricted") { Setup.explainRestricted(requireActivity()) }
            onClick("bypass_now") { toggleBypass() }
            onClick("bypass_tile") { addTile() }
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

        // ---------- Modo dormir ----------

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("sleep_") == true) refreshSleep()
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
            pickTime("Hora de dormir", sleep) { s ->
                pickTime("Hora de despertar", wake) { w -> Sleep.set(c, day, s, w) }
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
