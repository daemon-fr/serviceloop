package com.v16studio.serviceloop.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppearanceMode(val label: String, val meaning: String) {
    SYSTEM("System default", "Follow Android appearance."),
    LIGHT("Light", "Always use ServiceLoop light appearance."),
    DARK("Dark", "Always use ServiceLoop dark appearance."),
    ;

    companion object {
        fun fromStored(value: String?): AppearanceMode =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}

/** One process-wide, device-local source of truth for the user-facing appearance choice. */
class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MODE) _mode.value = readMode()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setMode(value: AppearanceMode) {
        _mode.value = value
        preferences.edit().putString(MODE, value.name).apply()
    }

    private fun readMode() = AppearanceMode.fromStored(preferences.getString(MODE, null))

    private companion object {
        const val PREFERENCES = "serviceloop_appearance"
        const val MODE = "mode"
    }
}
