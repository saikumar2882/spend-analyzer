package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiParser
import com.alpha.spendtracker.data.SharedPaymentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPaymentTextTest {

    private val gpayReceipt = """
        I paid ₹450 to Zepto using Google Pay
        UPI transaction ID: 627381926354
        To: zepto.payu@hdfcbank
        From: XXXXXX4521
        https://pay.google.com/receipt/abc123
    """.trimIndent()

    @Test
    fun stripsIdsUpiIdsAccountsAndLinks() {
        val cleaned = SharedPaymentText.clean(gpayReceipt)
        assertTrue(cleaned.startsWith("I paid ₹450 to Zepto using Google Pay"))
        assertFalse(cleaned.contains("627381926354"))
        assertFalse(cleaned.contains("@"))
        assertFalse(cleaned.contains("4521"))
        assertFalse(cleaned.contains("http"))
    }

    @Test
    fun amountSurvivesCleaning() {
        assertEquals(450.0, AiParser.extractAmount(SharedPaymentText.clean(gpayReceipt))!!, 0.001)
        assertEquals(
            125000.0,
            AiParser.extractAmount(SharedPaymentText.clean("Paid ₹1,25,000 for rent. Ref No. 9876543210"))!!,
            0.001
        )
    }

    @Test
    fun addsSourceAppOnlyWhenTextDoesNotNameOne() {
        assertEquals(
            "Payment of ₹200 successful via PhonePe",
            SharedPaymentText.toParserInput("Payment of ₹200 successful", "com.phonepe.app")
        )
        val named = SharedPaymentText.toParserInput("Paid ₹200 on Swiggy", "com.phonepe.app")
        assertFalse(named.contains("via"))
        assertEquals("Paid ₹200", SharedPaymentText.toParserInput("Paid ₹200", "com.whatsapp"))
        assertEquals("Paid ₹200", SharedPaymentText.toParserInput("Paid ₹200", null))
    }

    @Test
    fun phoneNumbersAreRemoved() {
        val cleaned = SharedPaymentText.clean("Sent ₹300 to Rahul +91 98765 43210")
        assertFalse(cleaned.contains("98765"))
        assertEquals(300.0, AiParser.extractAmount(cleaned)!!, 0.001)
    }
}
