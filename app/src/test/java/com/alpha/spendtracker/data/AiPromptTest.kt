package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The prompt and the strict schema are generated from one vocabulary; these tests are what keep it that way. */
class AiPromptTest {

    private val legacyTwins = PURPOSE_PRESETS - CANONICAL_PURPOSES.toSet()
    private val appNames = APP_PRESETS.map { it.displayName }

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 31, 12, 0, 0) }.timeInMillis

    private fun prompt(multi: Boolean, defaultPurpose: String = "Groceries") =
        AiPrompt.systemPrompt("₹", defaultPurpose, multi, now)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.props(): Map<String, Map<String, Any>> =
        this["properties"] as Map<String, Map<String, Any>>

    @Suppress("UNCHECKED_CAST")
    private fun transactionItem(schema: Map<String, Any>): Map<String, Any> =
        (schema.props().getValue("transactions")["items"]) as Map<String, Any>

    // ---- the schema ----------------------------------------------------------------------

    @Test
    fun everyObjectRequiresEveryPropertyAndForbidsExtras() {
        // Groq strict mode: "All schema properties must be marked as required" and "All objects must
        // set additionalProperties: false".
        fun check(obj: Map<String, Any>) {
            assertEquals("object", obj["type"])
            assertEquals(false, obj["additionalProperties"])
            assertEquals(obj.props().keys, (obj["required"] as List<*>).toSet())
            assertEquals("required has duplicates", obj.props().size, (obj["required"] as List<*>).size)
        }
        val multi = AiPrompt.schema(multi = true)
        check(multi)
        check(transactionItem(multi))
        check(AiPrompt.schema(multi = false))
    }

    @Test
    fun theSingleShapeIsTheMultiEntryWithoutSrc() {
        val item = transactionItem(AiPrompt.schema(multi = true))
        val single = AiPrompt.schema(multi = false)
        assertEquals(setOf("transactions"), AiPrompt.schema(multi = true).props().keys)
        assertEquals(setOf("src", "amount", "appName", "purpose", "notes", "personName", "date", "needsAmount"), item.props().keys)
        assertEquals(item.props().keys - "src", single.props().keys)
    }

    @Test
    fun enumValuesAreDrawnFromTheCanonicalLists() {
        listOf(true, false).forEach { multi ->
            val schema = AiPrompt.schema(multi)
            val fields = if (multi) transactionItem(schema).props() else schema.props()

            @Suppress("UNCHECKED_CAST")
            val purposes = fields.getValue("purpose")["enum"] as List<String>
            @Suppress("UNCHECKED_CAST")
            val apps = fields.getValue("appName")["enum"] as List<String>

            assertEquals(CANONICAL_PURPOSES, purposes)
            assertTrue(purposes.all { it in PURPOSE_PRESETS })
            assertTrue("a purpose outside the legacy-free list", purposes.none { it in legacyTwins })
            assertTrue("every app in the enum is a real preset (or blank)", apps.all { it == "" || it in appNames })
            assertTrue("\"\" must be allowed: it means 'no app named'", "" in apps)
            assertFalse("the placeholder is not something a user says", "Other Platform" in apps)
            assertEquals("every other preset is offered", appNames.size - 1, apps.size - 1)
        }
    }

    @Test
    fun amountIsTheOnlyNullableField() {
        val fields = transactionItem(AiPrompt.schema(multi = true)).props()
        assertEquals(listOf("number", "null"), fields.getValue("amount")["type"])
        assertEquals("boolean", fields.getValue("needsAmount")["type"])
        (fields - "amount").forEach { (name, def) -> assertTrue("$name should not be nullable", def["type"] is String) }
    }

    @Test
    fun theSchemaUsesOnlyKeywordsGroqDocumentsForStrictMode() {
        // Conservative on purpose: an unlisted keyword is either rejected (400 -> downgrade) or, worse,
        // accepted and not enforced. `enum` and type arrays are the only constraints used.
        val allowed = setOf("type", "properties", "required", "additionalProperties", "items", "enum")
        fun checkNode(node: Map<String, Any>) {
            assertTrue("keywords ${node.keys - allowed} are not on the strict-mode allow-list", node.keys.all { it in allowed })
            @Suppress("UNCHECKED_CAST")
            (node["properties"] as Map<String, Map<String, Any>>?)?.values?.forEach(::checkNode)
            @Suppress("UNCHECKED_CAST")
            (node["items"] as Map<String, Any>?)?.let(::checkNode)
        }
        checkNode(AiPrompt.schema(multi = true))
        checkNode(AiPrompt.schema(multi = false))
    }

    @Test
    fun theSchemaSerializesTheWayGroqExpects() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val schema = AiPrompt.jsonSchema(multi = true)
        val body = moshi.adapter(GroqRequest::class.java).toJson(
            GroqRequest(
                messages = listOf(GroqMessage("user", "x")),
                response_format = GroqResponseFormat(type = "json_schema", json_schema = GroqJsonSchema(schema.name, schema.schema))
            )
        )
        assertTrue(body, body.contains("\"type\":\"json_schema\""))
        assertTrue(body, body.contains("\"strict\":true"))
        assertTrue(body, body.contains("\"name\":\"expenses\""))
        assertTrue(body, body.contains("\"additionalProperties\":false"))
        assertTrue(body, body.contains("\"type\":[\"number\",\"null\"]"))
        assertTrue(body, body.contains("\"Food & Dining\""))
        assertFalse(body, body.contains("Food & Drinks"))
    }

    // ---- the prompt ----------------------------------------------------------------------

    @Test
    fun theHintTableCoversExactlyTheCanonicalPurposesInOrder() {
        assertEquals(CANONICAL_PURPOSES, AiPrompt.PURPOSE_HINTS.map { it.purpose })
        assertTrue(AiPrompt.PURPOSE_HINTS.all { it.cues.isNotEmpty() })
    }

    /** Every purpose string the prompt text asks for, shows or defaults to. */
    private fun purposesIn(prompt: String): List<String> {
        val inExamples = Regex(""""purpose": "([^"]*)"""").findAll(prompt).map { it.groupValues[1] }
        val inList = Regex("""EXACT string from \[([^\]]*)]""").find(prompt)!!.groupValues[1].split(", ")
        val inMapping = prompt.lineSequence().dropWhile { !it.startsWith("PURPOSE MAPPING") }.drop(1)
            .takeWhile { !it.startsWith("Default (nothing") }.map { it.substringBefore("\": ").removePrefix("\"") }
        val inDefault = Regex("""Default \(nothing clearly matches\)[^"]*"([^"]*)"""").find(prompt)!!.groupValues[1]
        val inUserDefaults = Regex("""- Purpose: (.*)""").find(prompt)!!.groupValues[1]
        val inPersonRule = Regex("""purpose "([^"]*)" or "([^"]*)"""").find(prompt)!!.groupValues.drop(1)
        return (inExamples + inList + inMapping + inDefault + inUserDefaults + inPersonRule).toList()
    }

    @Test
    fun everyPurposeNamedInThePromptIsCanonical() {
        listOf(true, false).forEach { multi ->
            val found = purposesIn(prompt(multi))
            assertTrue("found only $found", found.size > 30)
            found.forEach { assertTrue("'$it' in the ${if (multi) "multi" else "single"} prompt is not canonical", it in CANONICAL_PURPOSES) }
        }
    }

    @Test
    fun theMappingAndTheListNameEveryCanonicalPurposeAndNoTwin() {
        val text = prompt(multi = true)
        CANONICAL_PURPOSES.forEach { assertTrue("$it missing from the prompt", text.contains("\"$it\"")) }
        legacyTwins.forEach { twin ->
            // Whole-word, case-sensitive: "Investment" must not match inside "Investments", and the
            // lowercase cue words ("fuel", "credit card bill") are not purpose names.
            val asName = Regex("""(?<![A-Za-z&] )(?<![A-Za-z])${Regex.escape(twin)}(?![A-Za-z])""")
            assertFalse("legacy twin '$twin' appears in the prompt", asName.containsMatchIn(text))
        }
    }

    @Test
    fun aLegacyOrUnsetDefaultPurposeIsShownAsItsCanonicalBucket() {
        assertTrue(prompt(true, "Food & Drinks").contains("- Purpose: Food & Dining"))
        assertTrue(prompt(true, "Others").contains("- Purpose: Miscellaneous"))
        assertTrue(prompt(false, "Rent & Utilities").contains("use the user's default purpose above: \"Housing\""))
        assertTrue(prompt(true, "Groceries").contains("- Purpose: Groceries"))
        assertTrue(prompt(true, "").contains("- Purpose: Miscellaneous"))
    }

    @Test
    fun theMultiAndSingleShapesPromptForTheirOwnOutput() {
        val multi = prompt(multi = true)
        val single = prompt(multi = false)
        assertTrue(multi.contains("{\"transactions\": [{\"src\""))
        assertTrue(multi.contains("MULTIPLE EXPENSES"))
        assertFalse(single.contains("transactions"))
        assertFalse(single.contains("\"src\""))
        assertTrue(single.contains("largest monetary number found"))
    }

    @Test
    fun appNamesAndTodayAreInThePrompt() {
        val text = prompt(multi = true)
        APP_PRESETS.filter { it.id != "other" }.forEach { assertTrue(it.displayName, text.contains(it.displayName)) }
        assertTrue(text.contains("Today: 2026-10-31"))
        assertTrue("the yesterday example uses yesterday", text.contains("\"date\": \"2026-10-30\""))
        // The multi-line mapping was spliced in after trimIndent, so nothing may be left indented.
        assertFalse(text.lines().any { it.startsWith(" ") })
        assertFalse(text.contains("@@"))
    }
}
