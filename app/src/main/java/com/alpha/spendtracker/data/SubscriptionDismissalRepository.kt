package com.alpha.spendtracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subscriptionSuggestionStore by preferencesDataStore(name = "subscription_suggestions")

/** Remembers subscription suggestions the user dismissed, so they are never offered again. Device-local. */
@Singleton
class SubscriptionDismissalRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dismissedKey = stringSetPreferencesKey("dismissed_keys")

    val dismissedKeys: Flow<Set<String>> = context.subscriptionSuggestionStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[dismissedKey] ?: emptySet() }

    suspend fun dismiss(key: String) {
        context.subscriptionSuggestionStore.edit { prefs ->
            prefs[dismissedKey] = (prefs[dismissedKey] ?: emptySet()) + key
        }
    }
}
