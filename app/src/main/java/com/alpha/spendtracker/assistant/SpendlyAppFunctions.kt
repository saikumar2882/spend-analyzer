package com.alpha.spendtracker.assistant

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction

/** What the assistant gets back after asking Spendly to log an expense or loan. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TrackExpenseResult(
    /** A short sentence for Gemini to read back to the user, e.g. "Logged ₹1,000 lent to Sreenu in Spendly." */
    val message: String,
    /** True only if the expense was saved. False when Spendly needs the user to confirm or retry. */
    val saved: Boolean,
)

/**
 * Spendly's actions for voice assistants such as Gemini (Android 16+). The assistant does the
 * speech recognition and passes only the text; no screen or microphone is needed, so it works
 * even from earbuds or with the screen off.
 */
class SpendlyAppFunctions(private val tracker: VoiceTracker) {

    /**
     * Logs one or more expenses, loans, or borrowings when the user asks Spendly to track, log,
     * note, or record money spent, lent, or borrowed (e.g., "track 1000 lending to Sreenu in Spendly",
     * "spent 250 on lunch at Swiggy in Spendly", or "lent 5000 to Rahul in Spendly").
     *
     * Gemini provides a clear spoken confirmation to the user upon execution.
     *
     * @param text The user's words, exactly as spoken, including the amount, person, app, or purpose.
     * @return A short summary for Gemini to read aloud to the user as confirmation, and whether it was saved.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun trackExpense(appFunctionContext: AppFunctionContext, text: String): TrackExpenseResult =
        when (val outcome = tracker.track(text)) {
            is TrackOutcome.Logged -> TrackExpenseResult(outcome.spoken, saved = true)
            is TrackOutcome.NeedsReview -> TrackExpenseResult(outcome.spoken, saved = false)
            is TrackOutcome.Failed -> TrackExpenseResult(outcome.spoken, saved = false)
        }
}
