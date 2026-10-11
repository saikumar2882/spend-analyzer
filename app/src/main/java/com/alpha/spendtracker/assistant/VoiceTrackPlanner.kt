package com.alpha.spendtracker.assistant

import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.components.toDraft
import com.alpha.spendtracker.ui.components.toNewSpend
import com.alpha.spendtracker.ui.viewmodel.SpendDraft
import java.util.UUID

/**
 * Decides whether a spoken expense can be saved with no one looking at a screen.
 *
 * Voice transcripts and the parser both make mistakes, and nobody confirms before the save, so
 * anything doubtful is sent to review (a notification that opens the normal confirmation sheet)
 * instead of being written to the ledger. Pure, so the rules are unit-tested.
 */
object VoiceTrackPlanner {

    enum class Reason { NO_AMOUNT, NEEDS_PERSON, UNSURE }

    sealed interface Plan {
        data class Save(val drafts: List<SpendDraft>) : Plan
        data class Review(val reason: Reason) : Plan
    }

    fun plan(
        results: List<AiTransactionResponse>,
        defaultApp: String,
        defaultPurpose: String,
        now: Long = System.currentTimeMillis(),
        newUuid: () -> String = { UUID.randomUUID().toString() }
    ): Plan {
        if (results.isEmpty() || results.any { (it.amount ?: 0.0) <= 0.0 || it.needsAmount }) {
            return Plan.Review(Reason.NO_AMOUNT)
        }
        // "Sent 5000 to that guy": a loan with no name is useless in Dues, and the name is what
        // the user would be asked for on the confirmation sheet.
        if (results.any { it.isDues() && it.personName.isBlank() }) {
            return Plan.Review(Reason.NEEDS_PERSON)
        }
        // The cloud model failed and this is only the offline heuristic guess.
        if (results.any { it.usedFallback }) {
            return Plan.Review(Reason.UNSURE)
        }
        return Plan.Save(results.map { it.toNewSpend(defaultApp, defaultPurpose, now).toDraft(newUuid()) })
    }

    private fun AiTransactionResponse.isDues() =
        purpose.equals("Lending", ignoreCase = true) || purpose.equals("Borrowing", ignoreCase = true)
}
