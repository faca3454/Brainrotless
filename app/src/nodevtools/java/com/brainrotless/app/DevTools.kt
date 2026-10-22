@file:Suppress("UNUSED_PARAMETER")

package com.brainrotless.app

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.preference.PreferenceFragmentCompat

/** La versión publicada no trae herramientas de prueba: esto no hace nada (las de verdad, en src/devtools). */
object DevTools {
    fun script(c: Context) = ""
    fun attach(v: WebView) {}
    fun addTo(f: PreferenceFragmentCompat) {}
    fun onIntent(a: MainActivity, i: Intent) {}
}
