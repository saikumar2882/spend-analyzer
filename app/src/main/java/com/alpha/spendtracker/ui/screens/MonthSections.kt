package com.alpha.spendtracker.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Which month sections the user has explicitly opened or closed on a list screen (History, Dues).
 *
 * ⚠️ This must be created *above* the screen, in `MainContainer`. `AnimatedContent` drops a
 * screen's state when you navigate away, so state held inside the screen forgot every choice the
 * moment you left, and a month you had closed came back open. A month with no entry here has not
 * been touched and falls back to the default (see [MonthSections]).
 *
 * Choices are stored as the explicit result (open/closed), not as a flip away from the default:
 * a flip would reopen a month you closed as soon as a new month became the newest one.
 */
@Stable
class MonthChoices {
    private val chosen = mutableStateMapOf<String, Boolean>()

    operator fun get(key: String): Boolean? = chosen[key]

    operator fun set(key: String, expanded: Boolean) {
        chosen[key] = expanded
    }

    companion object {
        // Each entry is "1<key>" or "0<key>": the first character is the open/closed flag.
        val Saver: Saver<MonthChoices, Any> = listSaver<MonthChoices, String>(
            save = { choices -> choices.chosen.map { (key, expanded) -> (if (expanded) "1" else "0") + key } },
            restore = { saved ->
                MonthChoices().also { choices ->
                    saved.forEach { choices.chosen[it.substring(1)] = it[0] == '1' }
                }
            }
        )
    }
}

@Composable
fun rememberMonthChoices(): MonthChoices = rememberSaveable(saver = MonthChoices.Saver) { MonthChoices() }

/**
 * Open/closed state of one screen's month headers.
 *
 * - A month the user has opened or closed stays that way ([MonthChoices]).
 * - Otherwise only the newest month ([newestKey]) is open, so the screen is never a bare list of
 *   headers.
 * - While [searching], every month is open and the stored choices are ignored, because hits hidden
 *   behind a tap would read as "no results". A tap during a search closes a month only for that
 *   search; it is not written to [MonthChoices].
 */
@Stable
class MonthSections internal constructor(
    private val choices: MonthChoices,
    private val newestKey: String?,
    private val searching: Boolean,
    private val closedInSearch: SnapshotStateList<String>
) {
    fun isExpanded(key: String): Boolean =
        if (searching) key !in closedInSearch else choices[key] ?: (key == newestKey)

    fun toggle(key: String) {
        if (searching) {
            if (!closedInSearch.remove(key)) closedInSearch.add(key)
        } else {
            choices[key] = !isExpanded(key)
        }
    }
}

@Composable
fun rememberMonthSections(choices: MonthChoices, newestKey: String?, searching: Boolean): MonthSections {
    // Starting or ending a search wipes the per-search closes.
    val closedInSearch = remember(searching) { mutableStateListOf<String>() }
    return MonthSections(choices, newestKey, searching, closedInSearch)
}
