package com.brainrotless.app

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

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
            setPreferencesFromResource(R.xml.preferences, rootKey)

            onClick("perm_notif") { Setup.openNotifications(requireContext()) }
            onClick("perm_links") { Setup.openLinks(requireContext()) }
            onClick("perm_guard") { Setup.openAccessibility(requireContext()) }
            onClick("perm_restricted") { Setup.explainRestricted(requireActivity()) }
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
        }

        private fun status(on: Boolean, what: String) =
            if (on) "✓ Activado. $what" else "○ Desactivado. Tocá para activarlo"

        private fun onClick(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }
    }
}
