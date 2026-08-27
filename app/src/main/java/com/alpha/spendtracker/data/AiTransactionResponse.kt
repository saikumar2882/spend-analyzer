package com.alpha.spendtracker.data

/**
 * Structured response from Gemini for AI transaction extraction.
 *
 * - [appPresetId] is the id of the matched AppPreset (e.g. "phone_pe"). Null
 *   if we couldn't match a known app — in that case [appName] still carries the
 *   raw string from the LLM and the UI defaults to the "Other Platform" preset.
 * - [purpose] is normalized to one of PURPOSE_PRESETS, defaulting to "Others".
 * - [notes] is the short description of what the user spent on (e.g. "Biryani").
 */
data class AiTransactionResponse(
    val amount: Double? = null,
    val appName: String? = null,
    val appPresetId: String? = null,
    val purpose: String = "Others",
    val notes: String = "",
    val date: String = "today",
    /** Epoch millis. Null means "no date mentioned — use today at confirm time." */
    val timestamp: Long? = null,
    val needsAmount: Boolean = false
)

/**
 * Intent transport for an already-parsed [AiTransactionResponse].
 *
 * The widget's overlay activity parses the sentence *before* handing off, so MainActivity
 * receives a finished result and only has to show the confirmation sheet (behind the app
 * lock, if one is set). Kept next to the model so the writer and reader can't drift.
 */
object AiResultIntent {
    private const val EXTRA_PRESENT = "AI_RESULT"
    private const val EXTRA_AMOUNT = "AI_RESULT_AMOUNT"
    private const val EXTRA_APP_NAME = "AI_RESULT_APP_NAME"
    private const val EXTRA_APP_PRESET_ID = "AI_RESULT_APP_PRESET_ID"
    private const val EXTRA_PURPOSE = "AI_RESULT_PURPOSE"
    private const val EXTRA_NOTES = "AI_RESULT_NOTES"
    private const val EXTRA_DATE = "AI_RESULT_DATE"
    private const val EXTRA_TIMESTAMP = "AI_RESULT_TIMESTAMP"
    private const val EXTRA_NEEDS_AMOUNT = "AI_RESULT_NEEDS_AMOUNT"

    fun put(intent: android.content.Intent, result: AiTransactionResponse): android.content.Intent =
        intent.apply {
            putExtra(EXTRA_PRESENT, true)
            result.amount?.let { putExtra(EXTRA_AMOUNT, it) }
            putExtra(EXTRA_APP_NAME, result.appName)
            putExtra(EXTRA_APP_PRESET_ID, result.appPresetId)
            putExtra(EXTRA_PURPOSE, result.purpose)
            putExtra(EXTRA_NOTES, result.notes)
            putExtra(EXTRA_DATE, result.date)
            result.timestamp?.let { putExtra(EXTRA_TIMESTAMP, it) }
            putExtra(EXTRA_NEEDS_AMOUNT, result.needsAmount)
        }

    fun isPresent(intent: android.content.Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_PRESENT, false) == true

    fun read(intent: android.content.Intent): AiTransactionResponse = AiTransactionResponse(
        amount = if (intent.hasExtra(EXTRA_AMOUNT)) intent.getDoubleExtra(EXTRA_AMOUNT, 0.0) else null,
        appName = intent.getStringExtra(EXTRA_APP_NAME),
        appPresetId = intent.getStringExtra(EXTRA_APP_PRESET_ID),
        purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: "Others",
        notes = intent.getStringExtra(EXTRA_NOTES) ?: "",
        date = intent.getStringExtra(EXTRA_DATE) ?: "today",
        timestamp = if (intent.hasExtra(EXTRA_TIMESTAMP)) intent.getLongExtra(EXTRA_TIMESTAMP, 0L) else null,
        needsAmount = intent.getBooleanExtra(EXTRA_NEEDS_AMOUNT, false)
    )

    /** Clear the extras so rotation / recomposition can't re-open the confirmation sheet. */
    fun clear(intent: android.content.Intent) {
        listOf(
            EXTRA_PRESENT, EXTRA_AMOUNT, EXTRA_APP_NAME, EXTRA_APP_PRESET_ID,
            EXTRA_PURPOSE, EXTRA_NOTES, EXTRA_DATE, EXTRA_TIMESTAMP, EXTRA_NEEDS_AMOUNT
        ).forEach(intent::removeExtra)
    }
}
