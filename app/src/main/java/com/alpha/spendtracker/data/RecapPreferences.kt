package com.alpha.spendtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.recapDataStore: DataStore<Preferences> by preferencesDataStore(name = "recap_prefs")

/**
 * Remembers which weekly recap / monthly Wrapped was last announced, so a worker that runs twice
 * in the same window (retry, reschedule, app update) never double-notifies. Keys are prefixed
 * with the userId: Room is shared across accounts on a device, and so is this.
 */
class RecapPreferences(private val context: Context) {

    private object Keys {
        val LAST_WEEKLY = stringPreferencesKey("last_weekly_recap")
        val LAST_WRAPPED = stringPreferencesKey("last_monthly_wrapped")
    }

    suspend fun lastWeeklyRecap(): String =
        context.recapDataStore.data.first()[Keys.LAST_WEEKLY].orEmpty()

    suspend fun setLastWeeklyRecap(key: String) {
        context.recapDataStore.edit { it[Keys.LAST_WEEKLY] = key }
    }

    suspend fun lastMonthlyWrapped(): String =
        context.recapDataStore.data.first()[Keys.LAST_WRAPPED].orEmpty()

    suspend fun setLastMonthlyWrapped(key: String) {
        context.recapDataStore.edit { it[Keys.LAST_WRAPPED] = key }
    }
}
