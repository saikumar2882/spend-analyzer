package com.alpha.spendtracker.data

import com.alpha.spendtracker.data.ReceiptOcrText.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrTextTest {

    private val gpayReceipt = listOf(
        Line("Payment successful", 40),
        Line("11 Oct 2026, 10:32 AM", 30),
        Line("₹1,250", 120),
        Line("Paid to Swiggy", 36),
        Line("UPI transaction ID: 628812345678", 28),
    )

    @Test fun tallestNumberLineIsTheAmountAndComesFirst() {
        val text = ReceiptOcrText.fromLines(gpayReceipt)
        assertTrue(text, text.startsWith("₹1,250 "))
        assertEquals(1250.0, AiParser.extractAmount(text)!!, 0.0)
    }

    @Test fun misreadRupeeGlyphIsReplaced() {
        val text = ReceiptOcrText.fromLines(
            gpayReceipt.map { if (it.height == 120) Line("T250.50", 120) else it }
        )
        assertTrue(text, text.startsWith("₹250.50 "))
    }

    @Test fun amountLineIsNotRepeatedInTheRest() {
        val text = ReceiptOcrText.fromLines(gpayReceipt)
        assertEquals(1, "1,250".toRegex().findAll(text).count())
    }

    @Test fun noStandoutNumberKeepsReadingOrder() {
        val lines = listOf(Line("Paid to Zepto", 30), Line("250", 30), Line("11 Oct", 30))
        assertEquals("Paid to Zepto 250 11 Oct", ReceiptOcrText.fromLines(lines))
    }

    @Test fun tallDateIsNotMistakenForAmount() {
        val lines = listOf(Line("Paid to Zepto", 30), Line("11 Oct 2026", 90), Line("Rs 250", 30))
        assertTrue(!ReceiptOcrText.fromLines(lines).startsWith("₹"))
    }

    @Test fun emptyOcrGivesEmptyText() {
        assertEquals("", ReceiptOcrText.fromLines(emptyList()))
        assertEquals("", ReceiptOcrText.fromLines(listOf(Line("  ", 20))))
    }
}
