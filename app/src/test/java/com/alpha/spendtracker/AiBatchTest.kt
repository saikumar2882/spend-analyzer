package com.alpha.spendtracker

import android.content.Intent
import com.alpha.spendtracker.data.AiBatchParser
import com.alpha.spendtracker.data.AiParser
import com.alpha.spendtracker.data.AiResultIntent
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.isSavable
import com.alpha.spendtracker.ui.components.splitLendBorrowNotes
import com.alpha.spendtracker.ui.components.toNewSpend
import com.alpha.spendtracker.ui.components.toResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One sentence, several expenses: splitting it offline, reading the model's multi-entry JSON,
 * carrying the list to MainActivity, and turning it into the rows the review list shows.
 * Robolectric because `org.json` and `Intent` are Android classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiBatchTest {

    // ---- splitting one sentence ----------------------------------------------------------

    @Test
    fun commasAndAndSplitIntoSeparateExpenses() {
        assertEquals(
            listOf("tea 20", "auto 80", "lunch 150"),
            AiParser.splitExpenses("tea 20, auto 80 and lunch 150")
        )
        assertEquals(listOf("tea 20", "coffee 30"), AiParser.splitExpenses("tea 20; coffee 30"))
        assertEquals(listOf("tea 20", "coffee 30"), AiParser.splitExpenses("tea 20 & coffee 30"))
    }

    @Test
    fun aSentenceWithOneAmountIsNeverSplit() {
        assertEquals(listOf("lent 500 to rahul and priya"), AiParser.splitExpenses("lent 500 to rahul and priya"))
        assertEquals(listOf("bread and butter for 100"), AiParser.splitExpenses("bread and butter for 100"))
        assertEquals(listOf("500 for lunch and chai"), AiParser.splitExpenses("500 for lunch and chai"))
    }

    @Test
    fun aCommaInsideANumberIsNotABreak() {
        assertEquals(listOf("paid 1,500 for rent"), AiParser.splitExpenses("paid 1,500 for rent"))
        assertEquals(listOf("rent 1,500", "wifi 700"), AiParser.splitExpenses("rent 1,500, wifi 700"))
    }

    @Test
    fun aPieceWithoutAnAmountJoinsItsNeighbour() {
        // The trailing words belong to the coffee; the leading word to the first expense.
        assertEquals(listOf("tea 20", "coffee 30 with sugar"), AiParser.splitExpenses("tea 20, coffee 30 with sugar"))
        assertEquals(listOf("yesterday tea 20", "coffee 30"), AiParser.splitExpenses("yesterday, tea 20, coffee 30"))
    }

    // ---- offline baselines ---------------------------------------------------------------

    @Test
    fun baselinesSplitAndShareASingleNamedApp() {
        val phonePe = APP_PRESETS.first { it.displayName == "PhonePe" }
        val out = AiBatchParser.baselines("tea 20 on phonepe, auto 80", "Google Pay", "Others", multi = true)
        assertEquals(2, out.size)
        assertEquals(listOf(20.0, 80.0), out.map { it.response.amount })
        // "auto 80" names no app of its own, and only one app is named in the sentence.
        assertEquals(listOf(phonePe.id, phonePe.id), out.map { it.response.appPresetId })
        assertEquals(listOf("tea 20 on phonepe", "auto 80"), out.map { it.source })
    }

    @Test
    fun baselinesKeepEachExpensesOwnAppWhenSeveralAreNamed() {
        val out = AiBatchParser.baselines("tea 20 on phonepe, coffee 30 on paytm", "Google Pay", "Others", multi = true)
        assertEquals(listOf("PhonePe", "Paytm"), out.map { it.response.appName })
    }

    @Test
    fun baselinesStaySingleWhenMultiIsOff() {
        val out = AiBatchParser.baselines("tea 20, auto 80", "Google Pay", "Others", multi = false)
        assertEquals(1, out.size)
    }

    // ---- reading the model's reply -------------------------------------------------------

    private fun parse(json: String, text: String, defaultApp: String = "Google Pay", multi: Boolean = true) =
        AiBatchParser.parse(json, text, defaultApp, "Others", multi)

    @Test
    fun multiReplyBecomesOneLogPerEntryWithItsOwnApp() {
        val text = "tea 20 via phonepe, auto 80, lunch 150 on swiggy"
        val json = """
            {"transactions": [
              {"src": "tea 20 via phonepe", "amount": 20, "appName": "Google Pay", "purpose": "Groceries & Food", "notes": "Tea", "personName": "", "date": "2026-10-01", "needsAmount": false},
              {"src": "auto 80", "amount": 80, "appName": "", "purpose": "Travel & Commute", "notes": "Auto", "personName": "", "date": "2026-10-01", "needsAmount": false},
              {"src": "lunch 150 on swiggy", "amount": 150, "appName": "Swiggy", "purpose": "Groceries & Food", "notes": "Lunch", "personName": "", "date": "2026-10-01", "needsAmount": false}
            ]}
        """.trimIndent()
        val out = parse(json, text, defaultApp = "Paytm")!!
        assertEquals(listOf(20.0, 80.0, 150.0), out.map { it.response.amount })
        // The model said Google Pay for the tea, but the user's own words in that entry say PhonePe.
        // The auto names nothing, so it falls to the default; the lunch keeps Swiggy.
        assertEquals(listOf("PhonePe", "Paytm", "Swiggy"), out.map { it.response.appName })
        assertEquals(listOf("Groceries & Food", "Travel & Commute", "Groceries & Food"), out.map { it.response.purpose })
        assertEquals(listOf("Tea", "Auto", "Lunch"), out.map { it.response.notes })
        assertEquals("auto 80", out[1].source)
        out.forEach { assertNotNull(it.response.timestamp) }
    }

    @Test
    fun theModelsReflexGooglePayIsOnlyBelievedWhenTheUserSaidIt() {
        val text = "tea 20, auto 80"
        val json = """{"transactions": [
            {"src": "tea 20", "amount": 20, "appName": "Google Pay", "purpose": "Groceries & Food", "notes": "Tea", "date": "2026-10-01"},
            {"src": "auto 80", "amount": 80, "appName": "Google Pay", "purpose": "Travel & Commute", "notes": "Auto", "date": "2026-10-01"}
        ]}"""
        assertEquals(listOf("PhonePe", "PhonePe"), parse(json, text, defaultApp = "PhonePe")!!.map { it.response.appName })
        // Said anywhere in the sentence, it is the user's own word and is trusted for each entry.
        assertEquals(
            listOf("Google Pay", "Google Pay"),
            parse(json, "tea 20, auto 80 all on gpay", defaultApp = "PhonePe")!!.map { it.response.appName }
        )
    }

    @Test
    fun theOlderFlatSingleObjectStillWorks() {
        val json = """{"amount": 250, "appName": "Zepto", "purpose": "Groceries & Food", "notes": "Milk", "personName": "", "date": "2026-10-01", "needsAmount": false}"""
        val out = parse(json, "250 milk from zepto")!!
        assertEquals(1, out.size)
        assertEquals(250.0, out.single().response.amount)
        assertEquals("Zepto", out.single().response.appName)
        // A one-expense sentence uses the whole sentence as its source, as it always did.
        assertEquals("250 milk from zepto", out.single().source)
    }

    @Test
    fun aSingleEntryInTheMultiShapeIsOrdinary() {
        val json = """{"transactions": [{"src": "x", "amount": 90, "appName": "", "purpose": "Travel & Commute", "notes": "Cab", "date": "2026-10-01"}]}"""
        val out = parse(json, "cab 90")!!
        assertEquals(1, out.size)
        assertEquals("cab 90", out.single().source)
    }

    @Test
    fun aSharedReceiptKeepsOnlyTheFirstEntry() {
        val json = """{"transactions": [
            {"src": "a", "amount": 500, "appName": "", "purpose": "Others", "notes": "Shop", "date": "2026-10-01"},
            {"src": "b", "amount": 1200, "appName": "", "purpose": "Others", "notes": "Balance", "date": "2026-10-01"}
        ]}"""
        val out = parse(json, "paid 500 to shop. balance 1200", multi = false)!!
        assertEquals(listOf(500.0), out.map { it.response.amount })
    }

    @Test
    fun entriesWithoutAnAmountAreDroppedFromABatch() {
        val json = """{"transactions": [
            {"src": "tea 20", "amount": 20, "purpose": "Groceries & Food", "notes": "Tea", "date": "2026-10-01"},
            {"src": "something", "amount": null, "purpose": "Others", "notes": "", "date": "2026-10-01"}
        ]}"""
        assertEquals(listOf(20.0), parse(json, "tea 20 and something")!!.map { it.response.amount })

        val allEmpty = """{"transactions": [
            {"src": "a", "amount": null, "purpose": "Others"},
            {"src": "b", "amount": null, "purpose": "Others"}
        ]}"""
        assertNull(parse(allEmpty, "a and b"))
    }

    @Test
    fun aSingleEntryWithoutAnAmountIsKeptSoTheUserCanFillItIn() {
        val out = parse("""{"amount": null, "purpose": "Others", "notes": "Gift", "needsAmount": true}""", "gift")!!
        assertEquals(1, out.size)
        assertTrue(out.single().response.needsAmount)
        assertNull(out.single().response.amount)
    }

    @Test
    fun aBatchIsCappedAndGarbageFallsBack() {
        val many = (1..12).joinToString(",") { """{"src": "e$it $it", "amount": $it, "purpose": "Others", "notes": "N$it", "date": "2026-10-01"}""" }
        assertEquals(AiBatchParser.MAX_TRANSACTIONS, parse("""{"transactions": [$many]}""", "x")!!.size)
        assertNull(parse("not json at all", "x"))
        assertNull(parse("""{"transactions": []}""", "x"))
    }

    @Test
    fun jsonWrappedInProseIsStillRead() {
        val out = parse("""Sure! {"amount": 40, "purpose": "Travel & Commute", "notes": "Auto"} done""", "auto 40")!!
        assertEquals(40.0, out.single().response.amount)
    }

    // ---- hand-off to MainActivity --------------------------------------------------------

    @Test
    fun resultsSurviveTheIntentRoundTrip() {
        val results = listOf(
            AiTransactionResponse(
                amount = 20.0, appName = "PhonePe", appPresetId = "phone_pe", purpose = "Groceries & Food",
                notes = "Tea", date = "2026-10-01", timestamp = 1_790_000_000_000L, learnedApp = true
            ),
            AiTransactionResponse(
                amount = null, appName = null, appPresetId = null, purpose = "Lending",
                notes = "Lunch", personName = "Rahul", needsAmount = true, learnedPurpose = true
            )
        )
        val intent = AiResultIntent.put(Intent(), results)
        assertTrue(AiResultIntent.isPresent(intent))
        assertEquals(results, AiResultIntent.read(intent))

        AiResultIntent.clear(intent)
        assertFalse(AiResultIntent.isPresent(intent))
        assertTrue(AiResultIntent.read(intent).isEmpty())
    }

    @Test
    fun anUnreadableExtraYieldsNoResultsRatherThanACrash() {
        val intent = Intent().putExtra("AI_RESULT", true).putExtra("AI_RESULTS_JSON", "{broken")
        assertTrue(AiResultIntent.read(intent).isEmpty())
    }

    // ---- the review list -----------------------------------------------------------------

    @Test
    fun aResultBecomesTheSameLogTheReviewFormWouldShow() {
        val phonePe = APP_PRESETS.first { it.displayName == "PhonePe" }
        val r = AiTransactionResponse(
            amount = 80.0, appName = "PhonePe", appPresetId = phonePe.id, purpose = "Travel & Commute",
            notes = " Auto ", timestamp = 1_790_000_000_000L
        )
        val spend = r.toNewSpend(defaultApp = "Google Pay", defaultPurpose = "Others", now = 5L)
        assertEquals(phonePe, spend.preset)
        assertEquals("Travel & Commute", spend.purpose)
        assertEquals("Auto", spend.notes)
        assertEquals(1_790_000_000_000L, spend.timestamp)
        assertTrue(spend.isSavable())

        // No stated date → "now"; unknown purpose → the user's default; unknown app → the default app.
        val bare = AiTransactionResponse(amount = 10.0, appName = "??", purpose = "nonsense")
            .toNewSpend(defaultApp = "PhonePe", defaultPurpose = "Others", now = 5L)
        assertEquals(5L, bare.timestamp)
        assertEquals("Others", bare.purpose)
        assertEquals("PhonePe", bare.preset.displayName)
    }

    @Test
    fun aLogWithoutAnAmountCannotBeSaved() {
        val noAmount = AiTransactionResponse(amount = null, purpose = "Others").toNewSpend("Google Pay", "Others")
        assertFalse(noAmount.isSavable())
        val zero = AiTransactionResponse(amount = 0.0, purpose = "Others").toNewSpend("Google Pay", "Others")
        assertFalse(zero.isSavable())
    }

    @Test
    fun anEditedLogGoesBackIntoTheListUnchanged() {
        val original = AiTransactionResponse(
            amount = 500.0, appName = "Google Pay", appPresetId = "google_pay", purpose = "Lending",
            notes = "Lunch", personName = "Rahul", timestamp = 1_790_000_000_000L
        )
        val spend = original.toNewSpend("Google Pay", "Others")
        assertEquals("Rahul - Lunch", spend.notes)

        val back = spend.toResponse()
        assertEquals("Rahul", back.personName)
        assertEquals("Lunch", back.notes)
        assertEquals(spend, back.toNewSpend("Google Pay", "Others"))
    }

    @Test
    fun lendBorrowNotesSplitOnTheFirstDash() {
        assertEquals("Rahul" to "Trip - Goa", splitLendBorrowNotes("Rahul - Trip - Goa"))
        assertEquals("Rahul" to "", splitLendBorrowNotes("Rahul"))
        assertEquals("" to "", splitLendBorrowNotes("  "))
    }
}
