package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiCorrectionMemory
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.CorrectionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCorrectionMemoryTest {

    private val gpayGuess = AiTransactionResponse(
        amount = 60.0, appName = "Google Pay", appPresetId = "google_pay", purpose = "Others", notes = "Chai"
    )

    private fun teach(
        rules: List<CorrectionRule>, notes: String, app: String? = null, purpose: String? = null, now: Long = 1L
    ) = AiCorrectionMemory.learn(rules, notes, app, purpose, now)

    @Test
    fun keywordsAreLowercasedFilteredAndCapped() {
        assertEquals(listOf("chai"), AiCorrectionMemory.extractKeywords("Chai"))
        assertEquals(listOf("rahul"), AiCorrectionMemory.extractKeywords("Lent to Rahul"))
        assertEquals(listOf("masala", "chai"), AiCorrectionMemory.extractKeywords("Masala chai and samosa"))
        assertEquals(listOf("భోజనం"), AiCorrectionMemory.extractKeywords("భోజనం"))
        assertTrue(AiCorrectionMemory.extractKeywords("to a").isEmpty())
    }

    @Test
    fun appCorrectionAppliesAfterOneHit() {
        val rules = teach(emptyList(), "Chai", app = "Paytm")
        val out = AiCorrectionMemory.apply(gpayGuess, "chai 60", rules)
        assertEquals("Paytm", out.appName)
        assertEquals("paytm", out.appPresetId)
        assertTrue(out.learnedApp)
    }

    @Test
    fun purposeCorrectionNeedsTwoHits() {
        val once = teach(emptyList(), "Chai", purpose = "Groceries & Food")
        assertEquals("Others", AiCorrectionMemory.apply(gpayGuess, "chai 60", once).purpose)

        val twice = teach(once, "Chai", purpose = "Groceries & Food", now = 2L)
        val out = AiCorrectionMemory.apply(gpayGuess, "chai 60", twice)
        assertEquals("Groceries & Food", out.purpose)
        assertTrue(out.learnedPurpose)
    }

    @Test
    fun appHitsDoNotCountTowardsPurpose() {
        val rules = teach(teach(emptyList(), "Rahul", app = "PhonePe"), "Rahul", purpose = "Lending", now = 2L)
        assertEquals("Others", AiCorrectionMemory.apply(gpayGuess, "dinner with rahul 400", rules).purpose)
    }

    @Test
    fun changingTheCorrectionRestartsItsCount() {
        val rules = teach(teach(emptyList(), "Chai", purpose = "Others"), "Chai", purpose = "Others", now = 2L)
        val changed = teach(rules, "Chai", purpose = "Groceries & Food", now = 3L)
        assertEquals(1, changed.single().purposeHits)
        assertEquals("Groceries & Food", changed.single().purpose)
    }

    @Test
    fun appNamedInInputBeatsLearnedApp() {
        val rules = teach(emptyList(), "Chai", app = "Paytm")
        val swiggyGuess = gpayGuess.copy(appName = "Swiggy", appPresetId = "swiggy")
        val out = AiCorrectionMemory.apply(swiggyGuess, "chai 60 on swiggy", rules)
        assertEquals("Swiggy", out.appName)
        assertFalse(out.learnedApp)
    }

    @Test
    fun customAppIsRestoredAsOtherPlatform() {
        val rules = teach(emptyList(), "Chai", app = "Cred")
        val out = AiCorrectionMemory.apply(gpayGuess, "chai 60", rules)
        assertEquals("Cred", out.appName)
        assertEquals("other", out.appPresetId)
    }

    @Test
    fun longestKeywordWins() {
        var rules = teach(emptyList(), "Tea", app = "Paytm")
        rules = teach(rules, "Coffee", app = "PhonePe", now = 2L)
        assertEquals("PhonePe", AiCorrectionMemory.match(rules, "tea and coffee 90").app)
    }

    @Test
    fun learnedLendingFillsPerson() {
        var rules = teach(emptyList(), "Rahul", purpose = "Lending")
        rules = teach(rules, "Rahul", purpose = "Lending", now = 2L)
        val out = AiCorrectionMemory.apply(gpayGuess, "gave 500 to rahul", rules)
        assertEquals("Lending", out.purpose)
        assertEquals("Rahul", out.personName)
    }

    @Test
    fun capEvictsLowestHitsThenOldestButKeepsNewRule() {
        var rules = (1..AiCorrectionMemory.MAX_RULES).map {
            CorrectionRule("rule$it", app = "Paytm", appHits = if (it == 1) 1 else 5, updatedAt = it.toLong())
        }
        rules = teach(rules, "Brandnew", app = "PhonePe", now = 999L)
        assertEquals(AiCorrectionMemory.MAX_RULES, rules.size)
        assertTrue(rules.any { it.keyword == "brandnew" })
        assertTrue(rules.none { it.appHits == 1 && it.keyword != "brandnew" })
    }

    @Test
    fun blankNotesOrNoCorrectionLearnsNothing() {
        assertTrue(teach(emptyList(), "", app = "Paytm").isEmpty())
        assertTrue(teach(emptyList(), "Chai").isEmpty())
        assertNull(AiCorrectionMemory.match(emptyList(), "chai").app)
    }
}
