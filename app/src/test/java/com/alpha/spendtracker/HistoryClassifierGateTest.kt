package com.alpha.spendtracker

import com.alpha.spendtracker.data.AssistantSuggestions
import com.alpha.spendtracker.data.HistoryQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The model-side intent classifier is one more LLM call per question, skipped only when local
 * evidence is conclusive. "No" is always the safe answer (it costs one cheap call that fails
 * open), so these tests pin both directions: what must skip, and what must never.
 */
class HistoryClassifierGateTest {

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    private val now = at(2026, Calendar.OCTOBER, 1)

    private fun skips(q: String, vararg earlier: String): Boolean {
        val scope = HistoryQuery.resolveScope(q, earlier.toList(), now, tz, Calendar.MONDAY)
        return HistoryQuery.isConclusivelyFinancial(q, scope)
    }

    @Test
    fun everySuggestionChipSkipsTheClassifier() {
        AssistantSuggestions.ALL.forEach { assertTrue(it.question, skips(it.question)) }
    }

    @Test
    fun clearFinanceWordsSkipIt() {
        listOf(
            "how much did I spend on rent",
            "what was my biggest expense",
            "show my budget",
            "who owes me money",
            "list my subscriptions",
            "what are my top categories",
            "I lent some money to a friend, how do I see it"
        ).forEach { assertTrue(it, skips(it)) }
    }

    @Test
    fun weakWordsNeedAFirstPersonWord() {
        assertTrue(skips("what is my total"))
        assertTrue(skips("show me the average of my bills"))
        assertTrue(skips("what did I do last week"))
        // No "my / I / me": a sentence like this is as likely to be about anything else.
        assertFalse(skips("what is the total population of India"))
        assertFalse(skips("what is the average rainfall in Goa"))
    }

    @Test
    fun aPurposeOrAppWithAPeriodSkipsIt() {
        assertTrue(skips("swiggy last month"))
        assertTrue(skips("rent in august"))
        // A purpose word alone is not enough: "prime minister" would read as a subscription.
        assertFalse(skips("who is the prime minister"))
        // A period alone is not enough either.
        assertFalse(skips("who won the match last week"))
    }

    @Test
    fun anAmountAlwaysGoesToTheClassifier() {
        // The classifier's TRACKING verdict is the only thing that catches these.
        listOf(
            "put 500 on groceries",
            "spent 500 on dinner",
            "add ₹2,000 to rent",
            "paid rs 300 for tea",
            "add 2k to rent",
            "noted five hundred for groceries",
            "show spends above 2000"
        ).forEach { assertFalse(it, skips(it)) }
    }

    @Test
    fun numbersThatOnlyShapeTheQuestionAreNotAmounts() {
        assertFalse(HistoryQuery.hasAmount("Top 3 categories of last month"))
        assertFalse(HistoryQuery.hasAmount("Show my spending in the last 7 days"))
        assertFalse(HistoryQuery.hasAmount("how much did I spend in 2025"))
        assertFalse(HistoryQuery.hasAmount("what did I spend in mar 2025"))
        assertFalse(HistoryQuery.hasAmount("what did I spend on 15th september"))
        assertFalse(HistoryQuery.hasAmount("spending on 2026-09-10"))
        assertFalse(HistoryQuery.hasAmount("what did I spend in 30 days"))

        assertTrue(HistoryQuery.hasAmount("put 500 on groceries"))
        assertTrue(HistoryQuery.hasAmount("anything above 2000"))
        assertTrue(HistoryQuery.hasAmount("₹ 99"))
        assertTrue(HistoryQuery.hasAmount("2k on petrol"))
    }

    @Test
    fun unrelatedAndAmbiguousQuestionsStayWithTheClassifier() {
        listOf("tell me a joke", "what is the capital of France", "write a python script", "hello").forEach {
            assertFalse(it, skips(it))
        }
    }

    @Test
    fun hindiAndTeluguSpendWordsCount() {
        assertTrue(skips("इस महीने का खर्च"))
        assertTrue(skips("ఈ నెల ఖర్చు ఎంత"))
    }

    @Test
    fun aFollowUpWithItsOwnScopeOnAScopedConversationSkipsIt() {
        assertTrue(skips("and last month?", "what did I spend on food this week"))
        assertTrue(skips("what about swiggy?", "summarize my spending last month"))
        // Nothing of its own to go on: let the classifier look.
        assertFalse(skips("and who is the president?", "what did I spend on food this week"))
        // No earlier question to continue.
        assertFalse(skips("and last month?"))
    }
}
