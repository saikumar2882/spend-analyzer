package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiBatchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantTrackingTest {

    @Test
    fun testTrackingInputBaselineParsing() {
        // Test "track ₹10,000 for rent"
        val input1 = "track ₹10,000 for rent"
        val parsed1 = AiBatchParser.baselines(input1, "Google Pay", "Others", multi = true)
        assertEquals(1, parsed1.size)
        assertEquals(10000.0, parsed1[0].response.amount ?: 0.0, 0.01)
        assertEquals("Rent & Utilities", parsed1[0].response.purpose)

        // Test "spent 500 on coffee"
        val input2 = "spent 500 on coffee"
        val parsed2 = AiBatchParser.baselines(input2, "Google Pay", "Others", multi = true)
        assertEquals(1, parsed2.size)
        assertEquals(500.0, parsed2[0].response.amount ?: 0.0, 0.01)
        assertEquals("Food & Drinks", parsed2[0].response.purpose)

        // Test "paid 1500 for electricity bill using phonepe"
        val input3 = "paid 1500 for electricity bill using phonepe"
        val parsed3 = AiBatchParser.baselines(input3, "Google Pay", "Others", multi = true)
        assertEquals(1, parsed3.size)
        assertEquals(1500.0, parsed3[0].response.amount ?: 0.0, 0.01)
        assertEquals("phone_pe", parsed3[0].response.appPresetId)
    }

    @Test
    fun testMultiExpenseTrackingInput() {
        val input = "coffee 50 and lunch 200 track it"
        val parsed = AiBatchParser.baselines(input, "Google Pay", "Others", multi = true)
        assertTrue(parsed.size >= 2)
        assertEquals(50.0, parsed[0].response.amount ?: 0.0, 0.01)
        assertEquals(200.0, parsed[1].response.amount ?: 0.0, 0.01)
    }
}
