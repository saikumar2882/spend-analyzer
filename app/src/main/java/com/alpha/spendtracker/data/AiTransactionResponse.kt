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
