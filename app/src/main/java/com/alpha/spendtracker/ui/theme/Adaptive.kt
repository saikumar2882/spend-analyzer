package com.alpha.spendtracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowWidthSizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    return when {
        widthDp < 600 -> WindowWidthSizeClass.COMPACT
        widthDp < 840 -> WindowWidthSizeClass.MEDIUM
        else -> WindowWidthSizeClass.EXPANDED
    }
}
