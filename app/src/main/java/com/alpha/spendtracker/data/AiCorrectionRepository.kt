package com.alpha.spendtracker.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Own file so backup_rules / data_extraction_rules can exclude it: corrections never leave the device.
private val Context.correctionStore by preferencesDataStore(name = "ai_corrections")

@Singleton
class AiCorrectionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val rulesKey = stringPreferencesKey("rules")

    val rules: Flow<List<CorrectionRule>> = context.correctionStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it[rulesKey]) }

    suspend fun learn(notes: String, app: String?, purpose: String?) {
        context.correctionStore.edit { prefs ->
            val updated = AiCorrectionMemory.learn(
                decode(prefs[rulesKey]), notes, app, purpose, System.currentTimeMillis()
            )
            prefs[rulesKey] = encode(updated)
        }
    }

    private fun encode(rules: List<CorrectionRule>): String = JSONArray().apply {
        rules.forEach { r ->
            put(JSONObject().apply {
                put("k", r.keyword)
                r.app?.let { put("a", it) }
                put("ah", r.appHits)
                r.purpose?.let { put("p", it) }
                put("ph", r.purposeHits)
                put("u", r.updatedAt)
            })
        }
    }.toString()

    private fun decode(raw: String?): List<CorrectionRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CorrectionRule(
                    keyword = o.getString("k"),
                    app = o.optString("a").ifBlank { null },
                    appHits = o.optInt("ah"),
                    purpose = o.optString("p").ifBlank { null },
                    purposeHits = o.optInt("ph"),
                    updatedAt = o.optLong("u")
                )
            }
        } catch (e: Exception) {
            Log.w("AiCorrectionRepository", "Discarding unreadable correction rules", e)
            emptyList()
        }
    }
}
