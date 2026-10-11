package com.alpha.spendtracker.assistant

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction

/** What the assistant gets back after asking Spendly to log an expense. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TrackExpenseResult(
    /** A short sentence to read back to the user, e.g. "Logged ₹250 for Lunch." */
    val message: String,
    /** True only if the expense was saved. False when Spendly needs the user to confirm or retry. */
    val saved: Boolean,
)

/**
 * Spendly's actions for voice assistants such as Gemini (Android 16+, experimental). The
 * assistant does the speech recognition and passes only the text; no screen or microphone is
 * needed, so it works from earbuds.
 */
class SpendlyAppFunctions(private val tracker: VoiceTracker) {

    /**
     * Logs one or more expenses, loans or borrowings the user just said, e.g. "spent 250 on lunch
     * at Swiggy", "tea 20 and auto 80", or "lent 5000 to Rahul". Call this whenever the user asks
     * to track, log, note or record money they spent, lent or borrowed.
     *
     * @param text The user's words, exactly as spoken, including the amount and what it was for.
     * @return A short confirmation to read aloud, and whether it was saved.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun trackExpense(appFunctionContext: AppFunctionContext, text: String): TrackExpenseResult =
        when (val outcome = tracker.track(text)) {
            is TrackOutcome.Logged -> TrackExpenseResult(outcome.spoken, saved = true)
            is TrackOutcome.NeedsReview -> TrackExpenseResult(outcome.spoken, saved = false)
            is TrackOutcome.Failed -> TrackExpenseResult(outcome.spoken, saved = false)
        }
}
