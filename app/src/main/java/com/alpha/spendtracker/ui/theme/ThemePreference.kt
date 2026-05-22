/**
 * Utilities for managing and persisting the user's theme preference (Light, Dark, System).
 */
package com.alpha.spendtracker.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class ThemePreference { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME = "spend_tracker_settings"
private const val KEY_THEME = "theme_preference"

@Composable
fun rememberThemePreference(): MutableState<ThemePreference> {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val state = remember {
        val saved = prefs.getString(KEY_THEME, null)
        val parsed = saved?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.SYSTEM
        mutableStateOf(parsed)
    }
    LaunchedEffect(state.value) {
        prefs.edit().putString(KEY_THEME, state.value.name).apply()
    }
    return state
}

@Composable
fun ThemePreference.isDark(): Boolean = when (this) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

fun ThemePreference.next(): ThemePreference = when (this) {
    ThemePreference.SYSTEM -> ThemePreference.LIGHT
    ThemePreference.LIGHT -> ThemePreference.DARK
    ThemePreference.DARK -> ThemePreference.SYSTEM
}
