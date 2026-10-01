package com.alpha.spendtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
        val WRAPPED_SEEN = stringPreferencesKey("wrapped_seen")
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

    /**
     * `userId:yyyy-MM` of the Wrapped the user has already looked at (or dismissed the in-app card
     * for). Separate from [lastMonthlyWrapped], which only means "the notification went out": the
     * card keeps showing until the user has actually seen the Wrapped, whichever way they got to it.
     * Only the in-app card's month is ever recorded here, so one value is enough.
     */
    val wrappedSeen: Flow<String> = context.recapDataStore.data.map { it[Keys.WRAPPED_SEEN].orEmpty() }

    suspend fun setWrappedSeen(key: String) {
        context.recapDataStore.edit { it[Keys.WRAPPED_SEEN] = key }
    }
}
