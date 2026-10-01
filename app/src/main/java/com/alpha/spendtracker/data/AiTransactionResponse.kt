package com.alpha.spendtracker.data

/**
 * Structured response from Gemini for AI transaction extraction.
 *
 * - [appPresetId] is the id of the matched AppPreset (e.g. "phone_pe"). Null
 *   if we couldn't match a known app — in that case [appName] still carries the
 *   raw string from the LLM and the UI defaults to the "Other Platform" preset.
 * - [purpose] is normalized to one of PURPOSE_PRESETS, defaulting to "Others".
 * - [notes] is the short description of what the user spent on (e.g. "Biryani").
 * - [personName] is the counterparty for Lending/Borrowing ("Rahul"); blank otherwise.
 */
data class AiTransactionResponse(
    val amount: Double? = null,
    val appName: String? = null,
    val appPresetId: String? = null,
    val purpose: String = "Others",
    val notes: String = "",
    val personName: String = "",
    val date: String = "today",
    /** Epoch millis. Null means "no date mentioned — use today at confirm time." */
    val timestamp: Long? = null,
    val needsAmount: Boolean = false,
    /** True when an on-device learned correction (not the AI) chose the app / purpose. */
    val learnedApp: Boolean = false,
    val learnedPurpose: Boolean = false
)

/**
 * Intent transport for already-parsed [AiTransactionResponse]s.
 *
 * The widget's overlay activity (and the launcher shortcuts that open it) parse the sentence
 * *before* handing off, so MainActivity receives finished results and only has to show the
 * confirmation sheet (behind the app lock, if one is set). One sentence can hold several
 * expenses, so the list travels as a single JSON extra. Kept next to the model so the writer and
 * reader can't drift.
 */
object AiResultIntent {
    private const val EXTRA_PRESENT = "AI_RESULT"
    private const val EXTRA_RESULTS = "AI_RESULTS_JSON"

    fun put(intent: android.content.Intent, results: List<AiTransactionResponse>): android.content.Intent =
        intent.apply {
            putExtra(EXTRA_PRESENT, true)
            putExtra(EXTRA_RESULTS, encode(results))
        }

    fun isPresent(intent: android.content.Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_PRESENT, false) == true

    /** The results carried by [intent]; empty if the extra is missing or unreadable. */
    fun read(intent: android.content.Intent): List<AiTransactionResponse> =
        decode(intent.getStringExtra(EXTRA_RESULTS))

    /** Clear the extras so rotation / recomposition can't re-open the confirmation sheet. */
    fun clear(intent: android.content.Intent) {
        listOf(EXTRA_PRESENT, EXTRA_RESULTS).forEach(intent::removeExtra)
    }

    internal fun encode(results: List<AiTransactionResponse>): String =
        org.json.JSONArray().apply {
            results.forEach { r ->
                put(org.json.JSONObject().apply {
                    r.amount?.let { put("amount", it) }
                    put("appName", r.appName ?: org.json.JSONObject.NULL)
                    put("appPresetId", r.appPresetId ?: org.json.JSONObject.NULL)
                    put("purpose", r.purpose)
                    put("notes", r.notes)
                    put("personName", r.personName)
                    put("date", r.date)
                    r.timestamp?.let { put("timestamp", it) }
                    put("needsAmount", r.needsAmount)
                    put("learnedApp", r.learnedApp)
                    put("learnedPurpose", r.learnedPurpose)
                })
            }
        }.toString()

    internal fun decode(json: String?): List<AiTransactionResponse> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { o ->
                AiTransactionResponse(
                    amount = if (o.has("amount")) o.getDouble("amount") else null,
                    appName = if (o.isNull("appName")) null else o.getString("appName"),
                    appPresetId = if (o.isNull("appPresetId")) null else o.getString("appPresetId"),
                    purpose = o.optString("purpose", "Others"),
                    notes = o.optString("notes", ""),
                    personName = o.optString("personName", ""),
                    date = o.optString("date", "today"),
                    timestamp = if (o.has("timestamp")) o.getLong("timestamp") else null,
                    needsAmount = o.optBoolean("needsAmount", false),
                    learnedApp = o.optBoolean("learnedApp", false),
                    learnedPurpose = o.optBoolean("learnedPurpose", false)
                )
            }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }
}
