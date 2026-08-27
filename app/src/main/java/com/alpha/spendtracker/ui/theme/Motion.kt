/**
 * Motion tokens and the accessibility gate that every animated surface reads.
 */
package com.alpha.spendtracker.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has turned animations off system-wide ("remove animations" / developer
 * animator scale 0). Callers should collapse their animation duration to 0 rather than skip the
 * animation API, so the final value is still applied.
 *
 * Resolved once per composition: `Settings.Global.getFloat` is a ContentResolver query, and the
 * three chart/hero call sites used to run it on every recomposition — i.e. on every animation
 * frame, on the main thread.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

object MotionDuration {
    const val SHORT = 200
    const val MEDIUM = 400
    const val LONG = 600
    const val CHART_DRAW = 800
}

/** [duration], or 0 when the user has asked for reduced motion. */
fun motionDuration(duration: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else duration
