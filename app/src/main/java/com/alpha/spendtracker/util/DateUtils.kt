/**
 * Utility functions for formatting and manipulating dates and timestamps.
 */
package com.alpha.spendtracker.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import com.alpha.spendtracker.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

val activeAppLocale: Locale
    get() {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (!locales.isEmpty && locales[0] != null) locales[0]!! else Locale.getDefault()
    }

private val fullDateFormat: SimpleDateFormat
    get() = SimpleDateFormat("EEEE, d MMMM yyyy", activeAppLocale)

private val shortDateFormat: SimpleDateFormat
    get() = SimpleDateFormat("dd MMM, yyyy", activeAppLocale)

fun formatDate(millis: Long, context: Context? = null): String {
    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    return when {
        isSameDay(today, target) -> context?.getString(R.string.today) ?: "Today"
        isSameDay(yesterday(today), target) -> context?.getString(R.string.yesterday) ?: "Yesterday"
        else -> fullDateFormat.format(Date(millis))
    }
}

fun formatShortDate(millis: Long): String = shortDateFormat.format(Date(millis))

fun formatMonth(millis: Long): String {
    val formatter = SimpleDateFormat("MMMM yyyy", activeAppLocale)
    return formatter.format(Date(millis))
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return isSameDay(c1, c2)
}

fun yesterdayMillis(): Long = System.currentTimeMillis() - MILLIS_PER_DAY

private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

private fun yesterday(today: Calendar): Calendar =
    (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
