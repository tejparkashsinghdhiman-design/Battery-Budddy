package com.example.batterybuddy

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A SharedPreferences value as Compose state. It reads live (so the Quick Settings tile or the service can change
 * it too) and the returned setter writes straight back. Usage: val (limit, setLimit) = rememberPref("limit", 80)
 */
@Composable
fun <T : Any> rememberPref(key: String, def: T): Pair<T, (T) -> Unit> {
    val prefs = Buddy.prefs(LocalContext.current)

    @Suppress("UNCHECKED_CAST")
    fun read(): T = when (def) {
        is Int -> prefs.getInt(key, def)
        is Long -> prefs.getLong(key, def)
        is Boolean -> prefs.getBoolean(key, def)
        is String -> prefs.getString(key, def) ?: def
        else -> def
    } as T

    val state = remember(key) { mutableStateOf(read()) }
    DisposableEffect(key) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == key) state.value = read() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return state.value to { v: T ->
        val e = prefs.edit()
        when (v) {
            is Int -> e.putInt(key, v)
            is Long -> e.putLong(key, v)
            is Boolean -> e.putBoolean(key, v)
            is String -> e.putString(key, v)
            else -> e
        }
        e.apply()
    }
}
