package com.alpha.spendtracker

import com.alpha.spendtracker.ui.components.MarkdownBlock
import com.alpha.spendtracker.ui.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextParserTest {

    @Test
    fun parsesPersonGroupedDataAndInsightsCorrectly() {
        val input = """
            **People you currently owe money to**

            * **Roja** (Total: **₹‑100,000.00**)  
              - **2026-06-17**: **₹100,000.00** — Borrowing  

            * **akkka** (Total: **₹‑2,000.00**)  
              - **2026-05-20**: **₹2,000.00** — Borrowing (note: mummy ki ichha cash)  

            **Insight:** Your net loan position is **₹41,951.00** positive, meaning overall you have lent more than you have borrowed. Keep track of the two outstanding borrowings to maintain a clear net balance.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(input)

        // Expected blocks: Header, GroupCard (Roja), GroupCard (akkka), InsightCallout
        assertEquals(4, blocks.size)

        assertTrue(blocks[0] is MarkdownBlock.Header)
        val header = blocks[0] as MarkdownBlock.Header
        assertEquals("People you currently owe money to", header.title)

        assertTrue(blocks[1] is MarkdownBlock.GroupCard)
        val rojaGroup = blocks[1] as MarkdownBlock.GroupCard
        assertEquals("Roja", rojaGroup.title)
        assertEquals("₹100,000.00", rojaGroup.totalAmount)
        assertEquals(1, rojaGroup.items.size)
        assertEquals("2026-06-17", rojaGroup.items[0].date)
        assertEquals("₹100,000.00", rojaGroup.items[0].amount)

        assertTrue(blocks[2] is MarkdownBlock.GroupCard)
        val akkkaGroup = blocks[2] as MarkdownBlock.GroupCard
        assertEquals("akkka", akkkaGroup.title)
        assertEquals("₹2,000.00", akkkaGroup.totalAmount)

        assertTrue(blocks[3] is MarkdownBlock.InsightCallout)
        val insight = blocks[3] as MarkdownBlock.InsightCallout
        assertEquals("Insight", insight.title)
        assertTrue(insight.content.contains("net loan position"))
    }

    @Test
    fun parsesMarkdownTablesCorrectly() {
        val input = """
            ### Monthly Spending Table

            | Category | Amount | Share |
            |---|---|---|
            | Groceries | ₹12,500.00 | 40% |
            | Utilities | ₹5,000.00 | 16% |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(input)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Header)

        assertTrue(blocks[1] is MarkdownBlock.Table)
        val table = blocks[1] as MarkdownBlock.Table
        assertEquals(listOf("Category", "Amount", "Share"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Groceries", "₹12,500.00", "40%"), table.rows[0])
    }

    @Test
    fun parsesBulletListsWithPercentagesCorrectly() {
        val input = """
            * **Groceries & Food**: **₹15,000.00** (45%)
            * **Shopping**: **₹10,000.00** (30%)
        """.trimIndent()

        val blocks = parseMarkdownBlocks(input)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BulletList)
        val list = blocks[0] as MarkdownBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals(0.45f, list.items[0].percentage ?: 0f, 0.01f)
        assertEquals(0.30f, list.items[1].percentage ?: 0f, 0.01f)
    }
}
