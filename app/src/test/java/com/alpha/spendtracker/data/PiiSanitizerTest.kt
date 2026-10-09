package com.alpha.spendtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiSanitizerTest {

    @Test
    fun testAnonymizeCardNumbers() {
        val input = "paid 1234567890123456 for credit card bill"
        val result = PiiSanitizer.anonymize(input)

        assertEquals("paid [CARD_1] for credit card bill", result.anonymizedText)
        assertEquals("1234567890123456", result.tokens["[CARD_1]"])

        val restored = PiiSanitizer.deanonymize(result.anonymizedText, result.tokens)
        assertEquals(input, restored)
    }

    @Test
    fun testAnonymizeCardNumbersWithHyphensAndSpaces() {
        val inputDashes = "account 1234-5678-9012-3456"
        val resultDashes = PiiSanitizer.anonymize(inputDashes)
        assertEquals("account [CARD_1]", resultDashes.anonymizedText)
        assertEquals("1234-5678-9012-3456", resultDashes.tokens["[CARD_1]"])

        val restored = PiiSanitizer.deanonymize(resultDashes.anonymizedText, resultDashes.tokens)
        assertEquals(inputDashes, restored)
    }

    @Test
    fun testAnonymizeUpiIds() {
        val input1 = "paid john@okicici 500"
        val result1 = PiiSanitizer.anonymize(input1)

        assertEquals("paid [UPI_1] 500", result1.anonymizedText)
        assertEquals("john@okicici", result1.tokens["[UPI_1]"])

        val input2 = "sent 250 to 9876543210@ybl"
        val result2 = PiiSanitizer.anonymize(input2)

        assertTrue(result2.anonymizedText.contains("[UPI_1]"))
        assertEquals("9876543210@ybl", result2.tokens["[UPI_1]"])
    }

    @Test
    fun testAnonymizePhoneNumbers() {
        val input1 = "call +91 9876543210 for details"
        val result1 = PiiSanitizer.anonymize(input1)

        assertEquals("call [PHONE_1] for details", result1.anonymizedText)
        assertEquals("+91 9876543210", result1.tokens["[PHONE_1]"])

        val input2 = "sent money to 9876543210 on phonepe"
        val result2 = PiiSanitizer.anonymize(input2)

        assertTrue(result2.anonymizedText.contains("[PHONE_1]"))
        assertEquals("9876543210", result2.tokens["[PHONE_1]"])
    }

    @Test
    fun testAnonymizePersonalNamesInLendingAndBorrowing() {
        val lentInput = "lent 500 to Rahul for lunch"
        val lentResult = PiiSanitizer.anonymize(lentInput)

        assertEquals("lent 500 to [PERSON_1] for lunch", lentResult.anonymizedText)
        assertEquals("Rahul", lentResult.tokens["[PERSON_1]"])

        val borrowedInput = "borrowed 2000 from Priya for rent"
        val borrowedResult = PiiSanitizer.anonymize(borrowedInput)

        assertEquals("borrowed 2000 from [PERSON_1] for rent", borrowedResult.anonymizedText)
        assertEquals("Priya", borrowedResult.tokens["[PERSON_1]"])
    }

    @Test
    fun testMultiplePiiTypesInSingleInputAndRoundtrip() {
        val input = "lent 500 to Rahul via john@okicici using card 1234567890123456 phone +91 9876543210"
        val result = PiiSanitizer.anonymize(input)

        assertFalse(result.anonymizedText.contains("Rahul"))
        assertFalse(result.anonymizedText.contains("john@okicici"))
        assertFalse(result.anonymizedText.contains("1234567890123456"))
        assertFalse(result.anonymizedText.contains("9876543210"))

        assertTrue(result.tokens.containsKey("[PERSON_1]"))
        assertTrue(result.tokens.containsKey("[UPI_1]"))
        assertTrue(result.tokens.containsKey("[CARD_1]"))
        assertTrue(result.tokens.containsKey("[PHONE_1]"))

        val restored = PiiSanitizer.deanonymize(result.anonymizedText, result.tokens)
        assertEquals(input, restored)
    }

    @Test
    fun testDeanonymizeWithLlmFormattingVariations() {
        val tokens = mapOf("[PERSON_1]" to "Rahul", "[UPI_1]" to "john@okicici")

        // Standard token
        assertEquals("lent 500 to Rahul", PiiSanitizer.deanonymize("lent 500 to [PERSON_1]", tokens))

        // LLM output variations like space or lower case
        assertEquals("lent 500 to Rahul", PiiSanitizer.deanonymize("lent 500 to [PERSON 1]", tokens))
        assertEquals("lent 500 to Rahul", PiiSanitizer.deanonymize("lent 500 to [person_1]", tokens))
    }

    @Test
    fun testUserProvidedExamples() {
        // Example 1: Given 10,000 to Srinivas
        val ex1 = "given 10000 to Srinivas"
        val res1 = PiiSanitizer.anonymize(ex1)
        assertEquals("given 10000 to [PERSON_1]", res1.anonymizedText)
        assertEquals("Srinivas", res1.tokens["[PERSON_1]"])
        assertEquals(ex1, PiiSanitizer.deanonymize(res1.anonymizedText, res1.tokens))

        // Example 2: Spend 5000 on Swiggy for buying clothes
        val ex2 = "spend 5000 on Swiggy for buying clothes"
        val res2 = PiiSanitizer.anonymize(ex2)
        assertEquals("spend 5000 on Swiggy for buying clothes", res2.anonymizedText)
        assertTrue(res2.tokens.isEmpty()) // Swiggy & clothes are non-PII transaction metadata

        // Example 3: Lent 10000 to Srinivas via 9876543210@ybl account 123456789012
        val ex3 = "lent 10000 to Srinivas via 9876543210@ybl account 123456789012"
        val res3 = PiiSanitizer.anonymize(ex3)
        assertEquals("lent 10000 to [PERSON_1] via [UPI_1] account [CARD_1]", res3.anonymizedText)
        assertEquals("Srinivas", res3.tokens["[PERSON_1]"])
        assertEquals("9876543210@ybl", res3.tokens["[UPI_1]"])
        assertEquals("123456789012", res3.tokens["[CARD_1]"])
        assertEquals(ex3, PiiSanitizer.deanonymize(res3.anonymizedText, res3.tokens))
    }

    @Test
    fun testUnstructuredAndMessyQueries() {
        // Query 1: Mixed Teluglish voice transcript with app misspelling
        val q1 = "swiggi lo biryani ki 350 karchu chesa gpay nundi yesterday night"
        val res1 = PiiSanitizer.anonymize(q1)
        assertEquals(q1, res1.anonymizedText) // No PII present, passes intact to Groq system prompt for Telugu/Teluglish translation

        // Query 2: Garbled multi-expense voice transcript with names and amounts
        val q2 = "gpay 200 tea and auto 80 to zepto groceries 450 paid to Ramesh 1500 lent"
        val res2 = PiiSanitizer.anonymize(q2)
        println("RES2 ANONYMIZED: ${res2.anonymizedText}")
        println("RES2 TOKENS: ${res2.tokens}")
        assertTrue(res2.anonymizedText.contains("[PERSON_1]"))
        assertFalse(res2.anonymizedText.contains("Ramesh"))
        assertEquals("Ramesh", res2.tokens["[PERSON_1]"])
        assertEquals(q2, PiiSanitizer.deanonymize(res2.anonymizedText, res2.tokens))

        // Query 3: Complex sentence with card/account #, UPI ID, landlord name, and purpose
        val q3 = "transfered 12500 for house rent to landlord Ramesh account 987654321098 via phone pay upi ramesh@sbi"
        val res3 = PiiSanitizer.anonymize(q3)
        println("RES3 ANONYMIZED: ${res3.anonymizedText}")
        println("RES3 TOKENS: ${res3.tokens}")
        assertTrue(res3.anonymizedText.contains("[PERSON_1]"))
        assertTrue(res3.anonymizedText.contains("[CARD_1]"))
        assertTrue(res3.anonymizedText.contains("[UPI_1]"))
        assertEquals("Ramesh", res3.tokens["[PERSON_1]"])
        assertEquals("987654321098", res3.tokens["[CARD_1]"])
        assertEquals("ramesh@sbi", res3.tokens["[UPI_1]"])
        assertEquals(q3, PiiSanitizer.deanonymize(res3.anonymizedText, res3.tokens))

        // Query 4: Slang & informal speech
        val q4 = "gave 2k to my bro Vikrant for petrol n snacks on gpay"
        val res4 = PiiSanitizer.anonymize(q4)
        println("RES4 ANONYMIZED: ${res4.anonymizedText}")
        println("RES4 TOKENS: ${res4.tokens}")
        assertEquals("gave 2k to my bro [PERSON_1] for petrol n snacks on gpay", res4.anonymizedText)
        assertEquals("Vikrant", res4.tokens["[PERSON_1]"])
        assertEquals(q4, PiiSanitizer.deanonymize(res4.anonymizedText, res4.tokens))
    }

    @Test
    fun testVerblessAndImplicitInputs() {
        // Example 1 & 2: Simple verb-less inputs ("500 on chai", "500 on tea")
        val input1 = "500 on chai"
        val res1 = PiiSanitizer.anonymize(input1)
        assertEquals("500 on chai", res1.anonymizedText)
        assertTrue(res1.tokens.isEmpty()) // No PII present

        val input2 = "500 on tea"
        val res2 = PiiSanitizer.anonymize(input2)
        assertEquals("500 on tea", res2.anonymizedText)
        assertTrue(res2.tokens.isEmpty()) // No PII present

        // Example 3: Implicit / verb-less phrase with person name ("handed on Google Pay for Rahul")
        val input3 = "handed on Google Pay for Rahul"
        val res3 = PiiSanitizer.anonymize(input3)
        assertEquals("handed on Google Pay for [PERSON_1]", res3.anonymizedText)
        assertEquals("Rahul", res3.tokens["[PERSON_1]"])
        assertEquals(input3, PiiSanitizer.deanonymize(res3.anonymizedText, res3.tokens))

        // Example 4: Ultra-short app & amount ("300 Zepto")
        val input4 = "300 Zepto"
        val res4 = PiiSanitizer.anonymize(input4)
        assertEquals("300 Zepto", res4.anonymizedText)
        assertTrue(res4.tokens.isEmpty())
    }

    @Test
    fun testNoPiiInputUnchanged() {
        val input = "spent 300 on swiggy for biryani"
        val result = PiiSanitizer.anonymize(input)

        assertEquals(input, result.anonymizedText)
        assertTrue(result.tokens.isEmpty())
    }
}
