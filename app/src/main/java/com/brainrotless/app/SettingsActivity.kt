package com.brainrotless.app

import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
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
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Date

class SettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))

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

    companion object {
        const val EXTRA_START_COMMITMENT = "start_commitment" // desde el resumen: "Empezar otro"
        const val EXTRA_FOCUS = "focus" // desde el inicio: llevar a esa sección
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
            onClick("commit") { if (!Commitment.active(requireContext())) startCommitment() }
            onClick("debug_end") {
                Commitment.cancelForTesting(requireContext())
                Sleep.reschedule(requireContext())
                refreshCommitment()
                Commitment.onEnd(requireContext())?.let { showSummary(it, markSeen = true) }
            }
            onClick("protect") { if (!Protection.adminActive(requireContext())) Protection.requestAdmin(requireActivity()) }
            onClick("usage_access") { UsageAccess.open(requireContext()) }
            onClick("last_summary") { Commitment.lastSummary(requireContext())?.let { showSummary(it, markSeen = false) } }
            onClick("pause_now") {
                if (Commitment.startPause(requireContext()))
                    Toast.makeText(requireContext(), getString(R.string.pause_15), Toast.LENGTH_LONG).show()
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
            onClick("focus_time") { pickFocus() }
            for (d in DayOfWeek.values()) onClick("sleep_day_${d.value}") { pickNight(d) }
            onClick("check_now") {
                Updater.maybeCheck(requireActivity(), force = true)
                Rules.refreshAsync(requireContext(), force = true) { }
            }
            findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
            findPreference<ListPreference>("language")?.apply {
                value = Lang.current(requireContext())
                setOnPreferenceChangeListener { _, v -> Lang.set(requireActivity(), v as String); true }
            }
        }

        override fun onResume() {
            super.onResume()
            // Volviendo de los ajustes del sistema: refrescar el estado de cada permiso
            val c = requireContext()
            val listenerOn = Setup.listenerOn(c)
            val guardOn = Setup.guardOn(c)
            findPreference<Preference>("perm_notif")?.summary =
                status(listenerOn, getString(R.string.perm_notif_on))
            findPreference<Preference>("perm_guard")?.summary =
                status(guardOn, getString(R.string.perm_guard_on))
            findPreference<Preference>("perm_restricted")?.isVisible =
                Setup.restrictedApplies() && !(listenerOn && guardOn)
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefsListener)
            refreshSleep()
            refreshFocus()
            refreshBypass()
            refreshCommitment()
            val i = requireActivity().intent
            if (i.getBooleanExtra(EXTRA_START_COMMITMENT, false)) {
                i.removeExtra(EXTRA_START_COMMITMENT)
                if (!Commitment.active(requireContext())) startCommitment()
            }
            i.getStringExtra(EXTRA_FOCUS)?.let { key ->
                i.removeExtra(EXTRA_FOCUS)
                scrollToPreference(key)
            }
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
                        title = getString(R.string.bypass_now)
                        summary = getString(R.string.bypass_night, Sleep.fmt(c, night.end.toLocalTime()))
                    }
                    Bypass.active(c) -> {
                        title = getString(R.string.bypass_stop)
                        summary = getString(R.string.bypass_active, DateFormat.getTimeFormat(c).format(Date(Bypass.until(c))))
                    }
                    else -> {
                        title = getString(R.string.bypass_now)
                        summary = getString(R.string.bypass_idle)
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
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> c.getString(R.string.tile_added)
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> c.getString(R.string.tile_already)
                    else -> null
                }
                msg?.let { Toast.makeText(c, it, Toast.LENGTH_SHORT).show() }
            }
        }

        // ---------- Compromiso ----------

        private fun guard(key: String, change: (Strictness, Any) -> Strictness) {
            findPreference<Preference>(key)?.setOnPreferenceChangeListener { _, v ->
                val forbidden = Commitment.forbids(requireContext()) { change(it, v) }
                if (forbidden) Toast.makeText(requireContext(), getString(R.string.loosen_forbidden), Toast.LENGTH_LONG).show()
                !forbidden
            }
        }

        private fun refreshCommitment() {
            val c = requireContext()
            val active = Commitment.active(c)
            val time = DateFormat.getTimeFormat(c)
            findPreference<Preference>("commit")?.apply {
                title = getString(if (active) R.string.commit_active else R.string.commit_start)
                summary = if (active) {
                    val end = Date(Commitment.endMillis(c))
                    val pauses = Commitment.pausesLeft(c)
                    getString(R.string.commit_active_summary, DateFormat.getDateFormat(c).format(end), time.format(end),
                        resources.getQuantityString(R.plurals.pauses_n, pauses, pauses))
                } else getString(R.string.commit_idle_summary, Commitment.MIN_DAYS)
            }
            findPreference<Preference>("usage_today")?.summary = buildString {
                append(formatDuration(Usage.todayMs(c)))
                Usage.limitMs(c)?.let { append(getString(R.string.usage_of, formatDuration(it))) }
                append(getString(R.string.usage_resets, Sleep.fmt(c, Usage.nextReset(c).toLocalTime())))
            }
            findPreference<Preference>("debug_end")?.isVisible = BuildConfig.DEBUG && active
            findPreference<Preference>("protect")?.apply {
                isVisible = active
                summary = getString(if (Protection.adminActive(c)) R.string.protect_on else R.string.protect_off)
            }
            findPreference<Preference>("usage_access")?.summary = if (UsageAccess.granted(c)) {
                UsageAccess.baseline(c) // calcularlo ya, antes de que Android borre los datos viejos
                getString(R.string.usage_access_on)
            } else getString(R.string.usage_access_off)
            findPreference<Preference>("last_summary")?.apply {
                val last = Commitment.lastSummary(c)
                isVisible = !active && last != null
                summary = last?.lines()?.firstOrNull()
            }
            findPreference<Preference>("pause_now")?.apply {
                isVisible = active
                isEnabled = Commitment.canPause(c)
                summary = when {
                    Commitment.pauseActive(c) -> getString(R.string.pause_running, time.format(Date(Commitment.pauseUntil(c))))
                    Commitment.canPause(c) -> getString(R.string.pause_available, left(Commitment.pausesLeft(c)))
                    else -> getString(R.string.pause_when, left(Commitment.pausesLeft(c)))
                }
            }
        }

        private fun left(n: Int) = resources.getQuantityString(R.plurals.left_n, n, n)

        private fun showSummary(text: String, markSeen: Boolean) {
            if (markSeen) Commitment.markSummarySeen(requireContext())
            AlertDialog.Builder(requireContext())
                .setMessage(text)
                .setPositiveButton(R.string.start_another) { _, _ -> startCommitment() }
                .setNegativeButton(R.string.done, null)
                .show()
        }

        private fun startCommitment() {
            val c = requireContext()
            if (!Sleep.enabled(c) && Usage.limitMinutes(c) == null) {
                AlertDialog.Builder(c)
                    .setMessage(R.string.commit_needs)
                    .setPositiveButton(R.string.ok, null).show()
                return
            }
            val picker = NumberPicker(c).apply {
                minValue = Commitment.MIN_DAYS
                maxValue = 365
                value = Commitment.MIN_DAYS
                wrapSelectorWheel = false
            }
            AlertDialog.Builder(c)
                .setTitle(R.string.commit_days_title)
                .setMessage(getString(R.string.commit_days_msg, Commitment.MIN_DAYS))
                .setView(picker)
                .setPositiveButton(R.string.next) { _, _ -> confirmCommitment(picker.value) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun confirmCommitment(days: Int) {
            val c = requireContext()
            val end = Date(System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L)
            val rules = buildList {
                if (Sleep.enabled(c)) {
                    add(getString(R.string.rule_sleep))
                    if (c.userPrefs().getBoolean("sleep_browsers", true)) add(getString(R.string.rule_browsers))
                }
                Usage.limitMinutes(c)?.let { add(getString(R.string.rule_limit, formatDuration(it * 60_000L))) }
            }
            val pauses = Commitment.pausesFor(days)
            val dialog = AlertDialog.Builder(c)
                .setTitle(getString(R.string.commit_confirm_title, days))
                .setMessage(getString(R.string.commit_confirm_msg, DateFormat.getDateFormat(c).format(end), rules.joinToString("\n"), pauses))
                .setPositiveButton(R.string.start) { _, _ ->
                    Commitment.start(c, days)
                    Sleep.reschedule(c)
                    refreshCommitment()
                    Toast.makeText(c, getString(R.string.commit_started, days), Toast.LENGTH_LONG).show()
                    offerProtection()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            // 5 segundos para leerlo antes de poder confirmar
            val start = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            start.isEnabled = false
            for (left in 5 downTo 0) start.postDelayed({
                start.text = if (left > 0) getString(R.string.start_countdown, left) else getString(R.string.start)
                start.isEnabled = left == 0
            }, (5 - left) * 1000L)
        }

        /** Recién empezado el compromiso: ofrecer la protección y el permiso para la estadística final. */
        private fun offerProtection() {
            val c = requireContext()
            val steps = buildList {
                if (!Protection.adminActive(c)) add("protect")
                if (!UsageAccess.granted(c)) add("usage")
            }
            if (steps.isEmpty()) return
            AlertDialog.Builder(c)
                .setTitle(R.string.offer_title)
                .setMessage(R.string.offer_msg)
                .setPositiveButton(if ("protect" in steps) R.string.offer_protect else R.string.offer_compare) { _, _ ->
                    if ("protect" in steps) Protection.requestAdmin(requireActivity()) else UsageAccess.open(c)
                }
                .setNegativeButton(R.string.not_now, null)
                .show()
        }

        // ---------- Modo foco ----------

        private fun refreshFocus() {
            val c = requireContext()
            val (start, end) = Focus.times(c)
            findPreference<Preference>("focus_time")?.summary =
                getString(R.string.night_range, Sleep.fmt(c, start), Sleep.fmt(c, end))
            val names = resources.getStringArray(R.array.weekday_entries)
            findPreference<Preference>("focus_days")?.summary =
                Focus.days(c).sorted().joinToString(", ") { names[it.value - 1] }.ifEmpty { getString(R.string.focus_no_days) }
        }

        private fun pickFocus() {
            val c = requireContext()
            val (start, end) = Focus.times(c)
            pickTime(getString(R.string.pick_focus_start), start) { s ->
                pickTime(getString(R.string.pick_focus_end), end) { e -> Focus.setTimes(c, s, e) }
            }
        }

        // ---------- Modo dormir ----------

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("sleep_") == true) refreshSleep()
            if (key?.startsWith("sleep_") == true || key == "daily_limit") refreshCommitment()
            if (key?.startsWith("focus_") == true) refreshFocus()
            Widgets.update(requireContext())
        }

        private fun refreshSleep() {
            val c = requireContext()
            val perDay = Sleep.perDay(c)
            findPreference<Preference>("sleep_all")?.apply {
                isVisible = !perDay
                summary = getString(R.string.sleep_all_summary, night(Sleep.get(c, null)))
            }
            for (d in DayOfWeek.values()) findPreference<Preference>("sleep_day_${d.value}")?.apply {
                isVisible = perDay
                summary = night(Sleep.get(c, d))
            }
            findPreference<Preference>("sleep_browsers")?.summary = when {
                !Setup.guardOn(c) -> getString(R.string.browsers_need_guard)
                else -> Browsers.names(c).takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?.let { getString(R.string.browsers_list, it) } ?: getString(R.string.browsers_none)
            }
            Sleep.reschedule(c)
        }

        private fun night(t: Pair<LocalTime, LocalTime>) =
            getString(R.string.night_range, Sleep.fmt(requireContext(), t.first), Sleep.fmt(requireContext(), t.second))

        /** Hora de dormir y después hora de despertar, del horario general (null) o de un día. */
        private fun pickNight(day: DayOfWeek?) {
            val c = requireContext()
            val (sleep, wake) = Sleep.get(c, day)
            pickTime(getString(R.string.pick_sleep), sleep) { newSleep ->
                pickTime(getString(R.string.pick_wake), wake) { newWake ->
                    // El horario general rige todos los días; el de un día, solo ese
                    val days = day?.let { listOf(it) } ?: DayOfWeek.values().toList()
                    if (Commitment.forbids(c) { s -> s.copy(nights = s.nights + days.associateWith { newSleep to newWake }) })
                        Toast.makeText(c, getString(R.string.loosen_forbidden), Toast.LENGTH_LONG).show()
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
            if (on) getString(R.string.status_on, what) else getString(R.string.status_off)

        private fun onClick(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }
    }
}
