package com.alpha.spendtracker.assistant

import com.alpha.spendtracker.assistant.VoiceTrackPlanner.Plan
import com.alpha.spendtracker.assistant.VoiceTrackPlanner.Reason
import com.alpha.spendtracker.data.AiTransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTrackPlannerTest {

    private fun plan(vararg r: AiTransactionResponse): Plan {
        var n = 0
        return VoiceTrackPlanner.plan(r.toList(), "Google Pay", "Others", now = 1_000L) { "id${n++}" }
    }

    private val lunch = AiTransactionResponse(amount = 250.0, appName = "Swiggy", purpose = "Food & Drinks", notes = "Lunch")

    @Test fun confidentSingleExpenseIsSavedWithItsFields() {
        val p = plan(lunch) as Plan.Save
        val d = p.drafts.single()
        assertEquals(250.0, d.amount, 0.0)
        assertEquals("Food & Drinks", d.purpose)
        assertEquals("Swiggy", d.appName)
        assertEquals("Lunch", d.notes)
        assertEquals("id0", d.uuid)
    }

    @Test fun severalExpensesAreAllSavedWithDistinctIds() {
        val p = plan(lunch, lunch.copy(amount = 20.0, notes = "Tea")) as Plan.Save
        assertEquals(listOf("id0", "id1"), p.drafts.map { it.uuid })
    }

    @Test fun noResultsOrNoAmountGoesToReview() {
        assertEquals(Plan.Review(Reason.NO_AMOUNT), plan())
        assertEquals(Plan.Review(Reason.NO_AMOUNT), plan(lunch.copy(amount = null)))
        assertEquals(Plan.Review(Reason.NO_AMOUNT), plan(lunch.copy(amount = 0.0)))
        assertEquals(Plan.Review(Reason.NO_AMOUNT), plan(lunch.copy(needsAmount = true)))
    }

    @Test fun oneBadExpenseInABatchHoldsBackTheWholeBatch() {
        assertTrue(plan(lunch, lunch.copy(amount = null)) is Plan.Review)
    }

    @Test fun loanWithoutAPersonGoesToReview() {
        val lent = AiTransactionResponse(amount = 5000.0, purpose = "Lending")
        assertEquals(Plan.Review(Reason.NEEDS_PERSON), plan(lent))
        assertEquals(Plan.Review(Reason.NEEDS_PERSON), plan(lent.copy(purpose = "Borrowing", personName = " ")))
    }

    @Test fun loanWithAPersonIsSavedInTheDuesNotesFormat() {
        val d = (plan(AiTransactionResponse(amount = 5000.0, purpose = "Lending", personName = "Rahul")) as Plan.Save)
            .drafts.single()
        assertEquals("Lending", d.purpose)
        assertTrue(d.notes, d.notes.startsWith("Rahul"))
    }

    @Test fun offlineHeuristicGuessIsNeverSavedUnseen() {
        assertEquals(Plan.Review(Reason.UNSURE), plan(lunch.copy(usedFallback = true)))
    }
}
