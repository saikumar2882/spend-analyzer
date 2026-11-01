package com.alpha.spendtracker

import com.alpha.spendtracker.data.AiBatchParser
import com.alpha.spendtracker.data.AiPrompt
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What [AiBatchParser] does with replies shaped by the strict schema in [AiPrompt] (every key
 * present, "" for "not said", null only for the amount), and with the purposes inside them.
 * Robolectric because `org.json` is an Android class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiBatchSchemaReplyTest {

    private fun parse(json: String, text: String, defaultApp: String = "Paytm", defaultPurpose: String = "Others", multi: Boolean = true) =
        AiBatchParser.parse(json, text, defaultApp, defaultPurpose, multi)

    @Test
    fun aFullySchemaShapedMultiReplyParses() {
        val text = "tea 20, auto 80 and rent 12000 on cash"
        val json = """{"transactions":[
            {"src":"tea 20","amount":20,"appName":"","purpose":"Food & Dining","notes":"Tea","personName":"","date":"2026-10-30","needsAmount":false},
            {"src":"auto 80","amount":80,"appName":"","purpose":"Commute","notes":"Auto","personName":"","date":"2026-10-30","needsAmount":false},
            {"src":"rent 12000 on cash","amount":12000,"appName":"Cash","purpose":"Housing","notes":"","personName":"","date":"2026-10-30","needsAmount":false}
        ]}"""
        val out = parse(json, text)!!
        assertEquals(listOf(20.0, 80.0, 12000.0), out.map { it.response.amount })
        assertEquals(listOf("Food & Dining", "Commute", "Housing"), out.map { it.response.purpose })
        // "" means no app was named: the first two take the user's default, the third names Cash itself.
        assertEquals(listOf("Paytm", "Paytm", "Cash"), out.map { it.response.appName })
        assertTrue(out.none { it.response.needsAmount })
    }

    @Test
    fun aSchemaShapedSingleReplyWithNullAmountKeepsTheEntryForTheUserToFill() {
        val json = """{"amount":null,"appName":"","purpose":"Gifts & Donations","notes":"Gift","personName":"","date":"2026-10-30","needsAmount":true}"""
        val out = parse(json, "bought a gift", multi = false)!!.single()
        assertNull(out.response.amount)
        assertTrue(out.response.needsAmount)
        assertEquals("Gift", out.response.notes)
        assertEquals("Gifts & Donations", out.response.purpose)
    }

    @Test
    fun everyCanonicalPurposeInAReplyComesThroughUnchanged() {
        CANONICAL_PURPOSES.forEach { purpose ->
            val json = """{"amount":10,"appName":"","purpose":"$purpose","notes":"","personName":"","date":"2026-10-30","needsAmount":false}"""
            assertEquals(purpose, parse(json, "10 thing", multi = false)!!.single().response.purpose)
        }
    }

    @Test
    fun legacyTwinsInAReplyAreCanonicalisedSoTheDashboardDoesNotSplit() {
        fun purposeFor(raw: String) = parse(
            """{"amount":10,"appName":"","purpose":"$raw","notes":"","personName":"","date":"2026-10-30","needsAmount":false}""",
            "10 thing", multi = false
        )!!.single().response.purpose
        assertEquals("Food & Dining", purposeFor("Food & Drinks"))
        assertEquals("Commute", purposeFor("Travel & Commute"))
        assertEquals("Housing", purposeFor("Rent & Utilities"))
        assertEquals("Healthcare", purposeFor("healthcare & medical"))
    }

    @Test
    fun anOffListPurposeIsReplacedByTheBaselinesReadOfTheUsersWords() {
        val json = """{"amount":200,"appName":"","purpose":"Beverages and Meals","notes":"Biryani","personName":"","date":"2026-10-30","needsAmount":false}"""
        // The user said "biryani": the keyword baseline knows that is food, the model's invention does not count.
        assertEquals("Food & Dining", parse(json, "paid 200 for biryani", multi = false)!!.single().response.purpose)
        // Nothing to infer from: the user's default, canonicalised ("Others" is the app-wide default).
        assertEquals("Miscellaneous", parse(json, "paid 200 to someone", multi = false)!!.single().response.purpose)
        assertEquals("Groceries", parse(json, "paid 200 to someone", defaultPurpose = "Groceries", multi = false)!!.single().response.purpose)
    }

    @Test
    fun baselinesWriteTheCanonicalBucketForALegacyOrUnsetDefault() {
        fun purpose(default: String) = AiBatchParser.baselines("paid 500 to someone", "Paytm", default, multi = true).single().response.purpose
        assertEquals("Miscellaneous", purpose("Others"))
        assertEquals("Food & Dining", purpose("Food & Drinks"))
        assertEquals("Commute", purpose("Fuel"))
        assertEquals("Education", purpose("Education"))
        // A default no alias recognises is the user's own choice and stays as it is.
        assertEquals("My Custom Bucket", purpose("My Custom Bucket"))
        // Keyword-inferred purposes are untouched.
        assertEquals("Lending", AiBatchParser.baselines("lent 500 to rahul", "Paytm", "Others", multi = true).single().response.purpose)
    }

    @Test
    fun theSchemaIsHonouredByTheParserEndToEnd() {
        // The schema names exactly the keys merge() reads: nothing the schema allows is ignored,
        // nothing the parser reads is missing from it.
        val keys = AiPrompt.schema(multi = false)["properties"] as Map<*, *>
        assertEquals(setOf("amount", "appName", "purpose", "notes", "personName", "date", "needsAmount"), keys.keys)
        assertFalse(keys.containsKey("src"))
    }
}
