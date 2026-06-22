package com.alpha.spendtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_preferences")

data class AiPreferences(
    val defaultCurrency: String = "₹",
    val defaultApp: String = "Google Pay",
    val defaultPurpose: String = "Others",
    val dailyUsageCount: Int = 0,
    val lastUsageDate: Long = 0L,
    val isConfigured: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val dismissedUpdateVersion: String = ""
)

class AiPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val CURRENCY = stringPreferencesKey("default_currency")
        val APP = stringPreferencesKey("default_app")
        val PURPOSE = stringPreferencesKey("default_purpose")
        val USAGE_COUNT = intPreferencesKey("daily_usage_count")
        val LAST_USAGE_DATE = longPreferencesKey("last_usage_date")
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
    }

    val aiPreferencesFlow: Flow<AiPreferences> = context.dataStore.data
        .map { preferences ->
            val lastDate = preferences[PreferencesKeys.LAST_USAGE_DATE] ?: 0L
            val currentCount = preferences[PreferencesKeys.USAGE_COUNT] ?: 0
            
            // Reset count if it's a new day
            val isNewDay = !isSameDay(lastDate, System.currentTimeMillis())
            val finalCount = if (isNewDay) 0 else currentCount

            AiPreferences(
                defaultCurrency = preferences[PreferencesKeys.CURRENCY] ?: "₹",
                defaultApp = preferences[PreferencesKeys.APP] ?: "Google Pay",
                defaultPurpose = preferences[PreferencesKeys.PURPOSE] ?: "Others",
                dailyUsageCount = finalCount,
                lastUsageDate = lastDate,
                isConfigured = preferences[PreferencesKeys.IS_CONFIGURED] ?: false,
                isBiometricEnabled = preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] ?: false,
                dismissedUpdateVersion = preferences[PreferencesKeys.DISMISSED_UPDATE_VERSION] ?: ""
            )
        }

    suspend fun updateSettings(currency: String, app: String, purpose: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY] = currency
            preferences[PreferencesKeys.APP] = app
            preferences[PreferencesKeys.PURPOSE] = purpose
            preferences[PreferencesKeys.IS_CONFIGURED] = true
        }
    }

    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] = enabled
        }
    }

    /** Remembers the version the user already dismissed/downloaded so we never nag about it again. */
    suspend fun setDismissedUpdateVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISMISSED_UPDATE_VERSION] = version
        }
    }

    suspend fun incrementUsage() {
        context.dataStore.edit { preferences ->
            val lastDate = preferences[PreferencesKeys.LAST_USAGE_DATE] ?: 0L
            val currentCount = preferences[PreferencesKeys.USAGE_COUNT] ?: 0
            
            val currentTime = System.currentTimeMillis()
            if (isSameDay(lastDate, currentTime)) {
                preferences[PreferencesKeys.USAGE_COUNT] = currentCount + 1
            } else {
                preferences[PreferencesKeys.USAGE_COUNT] = 1
                preferences[PreferencesKeys.LAST_USAGE_DATE] = currentTime
            }
        }
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        if (time1 == 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
