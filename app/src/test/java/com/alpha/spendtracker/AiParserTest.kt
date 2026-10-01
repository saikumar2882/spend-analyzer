package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
