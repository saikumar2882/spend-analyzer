package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiParser
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiParserTest {

    private fun appId(text: String) = AiParser.findAppPreset(text)?.id

    @Test
    fun findsQuickCommerceApps() {
        assertEquals("zepto", appId("spent 300 on groceries in zepto"))
        assertEquals("swiggy", appId("200 biryani swiggy"))
        assertEquals("zomato", appId("ordered pizza on zomato for 450"))
    }

    @Test
    fun findsVoiceMisspellings() {
        assertEquals("zepto", appId("300 on vegetables in zapto"))
        assertEquals("swiggy", appId("250 lunch on swiggi"))
        assertEquals("zomato", appId("jomato dinner 600"))
    }

    @Test
    fun findsIndicScriptNames() {
        assertEquals("swiggy", appId("స్విగ్గీ లో 300 భోజనం"))
        assertEquals("zepto", appId("ज़ेप्टो पर 200 खर्च किए"))
    }

    @Test
    fun unmappedWordsNoLongerShortCircuit() {
        assertEquals("zepto", appId("transfer 500 on zepto"))
        assertEquals("swiggy", appId("paid two thousand on swiggy"))
    }

    @Test
    fun noAppWhenNoneMentioned() {
        assertNull(appId("spent 300 on lunch"))
        assertNull(appId("paid for the handbag"))
    }

    @Test
    fun extractsLendBorrowPerson() {
        val lent = AiParser.parseToBaseline("lent 500 to rahul for lunch", "Google Pay", "Others")
        assertEquals("Lending", lent.purpose)
        assertEquals("Rahul", lent.personName)

        val borrowed = AiParser.parseToBaseline("borrowed 2000 from my mom", "Google Pay", "Others")
        assertEquals("Borrowing", borrowed.purpose)
        assertEquals("Mom", borrowed.personName)
        assertEquals("", borrowed.notes)
    }

    @Test
    fun personOnlySetForLendBorrow() {
        assertEquals("", AiParser.parseToBaseline("300 on biryani", "Google Pay", "Others").personName)
    }

    // ---- amounts ---------------------------------------------------------------------------

    private fun amount(text: String) = AiParser.extractAmount(text)

    @Test
    fun groupedAmountsAreOneNumber() {
        assertEquals(1299.0, amount("spent 1,299 on shoes")!!, 0.001)
        assertEquals(150000.0, amount("paid 1,50,000 for the car")!!, 0.001)
        assertEquals(12345.5, amount("12,345.50 for rent")!!, 0.001)
        assertEquals(1234567.0, amount("sold it for 1,234,567")!!, 0.001)
        assertEquals(12500000.0, amount("flat booking 1,25,00,000")!!, 0.001)
    }

    @Test
    fun aGroupedAmountNeverShrinksToItsFirstGroup() {
        // A full stop right after a bare figure has always hidden it ("lunch 300." reads nothing);
        // a grouped one must do the same rather than fall back to "1" from "1,299.".
        assertNull(amount("lunch 300."))
        assertNull(amount("bought it for 1,299."))
        assertEquals(1299.0, amount("bought it for 1,299 today.")!!, 0.001)
    }

    @Test
    fun aCommaOnlyGroupsWhenAFullGroupFollows() {
        // "20,30" is two figures run together, not 2030; the first is the best guess.
        assertEquals(20.0, amount("tea 20,30 coffee")!!, 0.001)
        assertEquals(20.0, amount("tea 20, coffee 30")!!, 0.001)
    }

    @Test
    fun otherAmountFormsAreUnchanged() {
        assertEquals(300.0, amount("spent 300 on lunch")!!, 0.001)
        assertEquals(1500.0, amount("1.5k for dinner")!!, 0.001)
        assertEquals(1299.0, amount("₹1,299 for shoes")!!, 0.001)
        assertEquals(125000.0, amount("rs 1,25,000 for rent")!!, 0.001)
        assertEquals(1299.0, amount("shoes 1,299 rs")!!, 0.001)
        assertNull(amount("bought some shoes"))
    }

    @Test
    fun groupedAmountsSurviveTheMultiExpenseSplitter() {
        // The splitter must not cut inside "1,500" / "1,50,000", and each piece must read its own amount.
        val pieces = AiParser.splitExpenses("shoes 1,299, bag 2,500 and rent 1,50,000")
        assertEquals(listOf("shoes 1,299", "bag 2,500", "rent 1,50,000"), pieces)
        assertEquals(listOf(1299.0, 2500.0, 150000.0), pieces.map { amount(it) })
    }

    // ---- purpose keywords ------------------------------------------------------------------

    private fun purpose(text: String) = AiParser.inferPurpose(text)

    @Test
    fun aKeywordInsideALongerWordDoesNotFire() {
        // "kattanu" (Teluglish for "paid") holds "atta"; before, it tied with rent and won as Groceries.
        assertEquals("Housing", purpose("rent kattanu 5000"))
        assertNull(purpose("kattanu 5000"))
        assertNull(purpose("chocolate 50"))                     // "ola"
        assertNull(purpose("instead 200"))                      // "tea"
        assertEquals("Education", purpose("education 500"))     // "cat"
        assertNotEquals("Lending", purpose("lentils 100"))      // "lent"
        assertNotEquals("Lending", purpose("excellent service 500"))
        assertNotEquals("Borrowing", purpose("flowers 200"))    // "owe"
        assertNotEquals("Pets", purpose("petrol 500"))
        assertEquals("Commute", purpose("petrol 500"))
        assertNotEquals("Pets", purpose("catering 5000"))
        assertNotEquals("Commute", purpose("business lunch 400")) // "bus"
        assertNotEquals("Insurance & Taxes", purpose("taxi 200"))
        assertNotEquals("Personal Care", purpose("spaghetti 250")) // "spa"
    }

    @Test
    fun pluralsAndVerbEndingsStillMatch() {
        assertEquals("Food & Dining", purpose("2 pizzas 400"))
        assertEquals("Commute", purpose("two taxis 300"))
        assertEquals("Commute", purpose("buses 50"))
        assertEquals("Bills & Utilities", purpose("recharged phone 299"))
        assertEquals("Travel", purpose("traveling to goa 3000"))
        assertEquals("Gifts & Donations", purpose("donated 500 to temple"))
    }

    @Test
    fun keywordsBesideIndicScriptStillMatch() {
        // The boundary is a letter/mark lookaround, not \b, so Telugu/Hindi text around a Latin
        // keyword neither blocks nor fakes a match.
        assertEquals("Food & Dining", purpose("భోజనం కోసం 500 lunch"))
        assertEquals("Housing", purpose("500 రూపాయలు rent"))
        assertEquals("Commute", purpose("ऑटो के लिए auto 80"))
        assertEquals("Food & Dining", purpose("खाने पर 350 dinner"))
        assertNull(purpose("భోజనం కోసం 500 ఖర్చు చేశాను"))
    }

    @Test
    fun gymIsFitnessNotEntertainmentOrASubscription() {
        assertEquals("Fitness", purpose("gym 1500"))
        assertEquals("Fitness", purpose("gym membership 2000"))
        assertEquals("Fitness", purpose("yoga class 800"))
        assertEquals("Subscriptions", purpose("netflix membership 649"))
        assertEquals("Entertainment", purpose("movie 400"))
    }

    @Test
    fun savingsIsItsOwnPurposeNotInvestments() {
        assertEquals("Savings", purpose("savings 3000"))
        assertEquals("Investments", purpose("mutual fund sip 5000"))
    }

    @Test
    fun ambiguousNewKeywordsLoseToTheMoreSpecificPurpose() {
        assertEquals("Subscriptions", purpose("spotify premium 119"))
        assertEquals("Pets", purpose("dog food 600"))
        assertEquals("Commute", purpose("auto to office 80"))
        assertEquals("Food & Dining", purpose("lunch at office 200"))
        assertEquals("Insurance & Taxes", purpose("health insurance 12000"))
    }

    // One sentence per canonical purpose: the offline baseline must be able to choose every one.
    private val sentenceFor = mapOf(
        "Food & Dining" to "spent 250 on lunch",
        "Groceries" to "300 on vegetables",
        "Housing" to "rent 12000",
        "Commute" to "uber 180",
        "Lending" to "lent 500 to rahul",
        "Borrowing" to "borrowed 2000 from mom",
        "Shopping" to "bought a shirt for 1299",
        "Bills & Utilities" to "electricity bill 1500",
        "EMIs & Cards" to "emi 5000",
        "Investments" to "sip 5000",
        "Savings" to "savings 3000",
        "Subscriptions" to "netflix 649",
        "Healthcare" to "doctor consultation 500",
        "Personal Care" to "haircut 300",
        "Education" to "school fees 25000",
        "Entertainment" to "movie 400",
        "Travel" to "flight to delhi 5400",
        "Insurance & Taxes" to "insurance premium 12000",
        "Gifts & Donations" to "bought a gift 800",
        "Fitness" to "gym 1500",
        "Pets" to "dog food 600",
        "Work Expenses" to "office supplies 900",
        "Miscellaneous" to "misc 100"
    )

    @Test
    fun everyCanonicalPurposeIsReachableOffline() {
        // A purpose added to CANONICAL_PURPOSES without a keyword row (and a sentence here) fails this.
        assertEquals(CANONICAL_PURPOSES.toSet(), sentenceFor.keys)
        CANONICAL_PURPOSES.forEach { p ->
            assertEquals("'${sentenceFor.getValue(p)}'", p, purpose(sentenceFor.getValue(p)))
        }
    }

    @Test
    fun keywordTableOnlyNamesCanonicalPurposes() {
        // A keyword row under a legacy twin ("Food & Drinks") would write the very bucket the AI Track avoids.
        assertTrue(AiParser.PURPOSE_KEYWORDS.keys.all { it in CANONICAL_PURPOSES })
        assertTrue(AiParser.PURPOSE_KEYWORDS.values.all { it.isNotEmpty() })
    }
}
