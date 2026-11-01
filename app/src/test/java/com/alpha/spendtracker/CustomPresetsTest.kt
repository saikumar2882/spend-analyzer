package com.alpha.spendtracker

import androidx.test.core.app.ApplicationProvider
import com.alpha.spendtracker.data.AiBatchParser
import com.alpha.spendtracker.data.AiCorrectionRepository
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AiPrompt
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.CustomPresets
import com.alpha.spendtracker.data.GeminiGateway
import com.alpha.spendtracker.data.GroqApiService
import com.alpha.spendtracker.data.GroqChoice
import com.alpha.spendtracker.data.GroqMessage
import com.alpha.spendtracker.data.GroqModels
import com.alpha.spendtracker.data.GroqRequest
import com.alpha.spendtracker.data.GroqResponse
import com.alpha.spendtracker.data.HistoryQuery
import com.alpha.spendtracker.data.LlmClient
import com.alpha.spendtracker.data.LlmConfig
import com.alpha.spendtracker.data.LlmEvents
import com.alpha.spendtracker.data.LlmTier
import com.alpha.spendtracker.ui.components.getOrderedAppPresets
import com.alpha.spendtracker.ui.components.getOrderedCategoryGridItems
import com.alpha.spendtracker.ui.components.toNewSpend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * The apps and categories a user adds: what is accepted, how it is stored, and that the AI Track
 * prompt, the offline parser, the model-reply merge, the confirmation conversion and the history
 * assistant all know them. Robolectric for `org.json` and DataStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomPresetsTest {

    private val custom = CustomPresets(apps = listOf("Jio Mart"), categories = listOf("Pet Grooming"))

    // ---- what is accepted ----------------------------------------------------------------

    @Test
    fun cleanNameStripsWhatCouldBreakAPromptAndCapsTheLength() {
        assertEquals("Jio Mart x", CustomPresets.cleanName("  Jio,  \"Mart\"\n[x] "))
        assertEquals(CustomPresets.MAX_NAME_LENGTH, CustomPresets.cleanName("a".repeat(80)).length)
        assertEquals("", CustomPresets.cleanName(" ,, \"\" "))
    }

    @Test
    fun aNewAppIsAddedOnceAndKnownNamesAreIgnored() {
        val added = CustomPresets.EMPTY.withApp("Jio Mart")
        assertEquals(listOf("Jio Mart"), added.apps)
        // The same app spelled differently is the same app.
        assertEquals(added, added.withApp("jiomart"))
        // Built-in names and aliases mean the built-in; "Other" is the type-a-name entry itself.
        for (known in listOf("Google Pay", "gpay", "swiggy", "Other Platform", "other", "   ")) {
            assertEquals("'$known' should not be added", CustomPresets.EMPTY, CustomPresets.EMPTY.withApp(known))
        }
        // Containing a built-in's name is not being that built-in.
        assertEquals(listOf("Swiggy Genie"), CustomPresets.EMPTY.withApp("Swiggy Genie").apps)
    }

    @Test
    fun aNewCategoryIsAddedOnceAndKnownNamesAreIgnored() {
        val added = CustomPresets.EMPTY.withCategory("Pet Grooming")
        assertEquals(listOf("Pet Grooming"), added.categories)
        assertEquals(added, added.withCategory("pet grooming"))
        // Built-ins, legacy twins, aliases and the grid's own search words all mean an existing category.
        for (known in listOf("Food & Dining", "food", "Food & Drinks", "Others", "Lending", "Fuel", "Books", "")) {
            assertEquals("'$known' should not be added", CustomPresets.EMPTY, CustomPresets.EMPTY.withCategory(known))
        }
        assertEquals("Food & Dining", CustomPresets.EMPTY.resolveCategory("food"))
        assertEquals("Education", CustomPresets.EMPTY.resolveCategory("Books"))
    }

    @Test
    fun theListsAreCapped() {
        var list = CustomPresets.EMPTY
        repeat(CustomPresets.MAX_PER_KIND + 5) { i ->
            list = list.withApp("Shop $i x").withCategory("Bucket $i x")
        }
        assertEquals(CustomPresets.MAX_PER_KIND, list.apps.size)
        assertEquals(CustomPresets.MAX_PER_KIND, list.categories.size)
    }

    @Test
    fun removingMatchesTheSameWayAddingDoes() {
        assertEquals(CustomPresets.EMPTY, custom.withoutApp("jiomart").withoutCategory("PET GROOMING"))
    }

    @Test
    fun storageRoundTripsAndRecleansWhatItReads() {
        val names = listOf("Jio Mart", "Pet Grooming")
        assertEquals(names, CustomPresets.decode(CustomPresets.encode(names)))
        assertEquals(listOf("Jio Mart", "x"), CustomPresets.decode("Jio Mart\n\njiomart\n\"x\""))
        assertEquals(emptyList<String>(), CustomPresets.decode(null))
    }

    // ---- the pickers ---------------------------------------------------------------------

    @Test
    fun customAppsSitBeforeOtherPlatformAndFollowTheUsersOrder() {
        val names = custom.allAppPresets().map { it.displayName }
        assertEquals("Other Platform", names.last())
        assertEquals("Jio Mart", names[names.size - 2])
        val jio = custom.appPresets.single()
        assertTrue(jio.id.startsWith("custom_"))
        assertEquals("Other", jio.category)
        // Pinned to the top through the existing order list.
        assertEquals(jio.id, getOrderedAppPresets(listOf(jio.id), custom).first().id)
    }

    @Test
    fun customCategoriesSitBeforeMiscellaneous() {
        val names = getOrderedCategoryGridItems(emptyList(), custom).map { it.canonicalName }
        assertEquals("Miscellaneous", names.last())
        assertEquals("Pet Grooming", names[names.size - 2])
    }

    // ---- the AI Track prompt and schema --------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun enumOf(schema: Map<String, Any>, field: String): List<String> =
        ((schema["properties"] as Map<String, Map<String, Any>>).getValue(field)["enum"] as List<String>)

    @Test
    fun thePromptAndSchemaListTheUsersOwnAppsAndCategories() {
        val prompt = AiPrompt.systemPrompt("₹", "Groceries", multi = false, custom = custom)
        assertTrue(prompt.contains("Jio Mart"))
        assertTrue(prompt.contains("Pet Grooming"))
        val schema = AiPrompt.schema(multi = false, custom = custom)
        assertTrue("Jio Mart" in enumOf(schema, "appName"))
        assertTrue("Pet Grooming" in enumOf(schema, "purpose"))

        val plain = AiPrompt.systemPrompt("₹", "Groceries", multi = false)
        assertFalse(plain.contains("Jio Mart"))
        assertFalse("Pet Grooming" in enumOf(AiPrompt.schema(multi = false), "purpose"))
    }

    // ---- the offline parser and the model-reply merge ------------------------------------

    @Test
    fun theOfflineParserFindsTheUsersOwnAppAndCategoryInTheSentence() {
        val withCustom = AiBatchParser.baselines("jio mart 450", "Paytm", "Others", multi = false, custom = custom).single().response
        assertEquals("Jio Mart", withCustom.appName)
        val without = AiBatchParser.baselines("jio mart 450", "Paytm", "Others", multi = false).single().response
        assertEquals("Paytm", without.appName)

        val category = AiBatchParser.baselines("spent 300 on pet grooming", "Paytm", "Others", multi = false, custom = custom).single().response
        assertEquals("Pet Grooming", category.purpose)
    }

    @Test
    fun aModelAnswerNamingACustomAppOrCategoryIsKept() {
        val reply = """{"amount":450,"appName":"Jio Mart","purpose":"Pet Grooming","notes":"Shampoo","personName":"","date":"2026-10-30","needsAmount":false}"""
        val kept = AiBatchParser.parse(reply, "450 shampoo", "Paytm", "Others", multi = false, custom = custom)!!.single().response
        assertEquals("Jio Mart", kept.appName)
        assertEquals("Pet Grooming", kept.purpose)

        // Without the user's list the same answer is an unknown app and an unknown purpose.
        val dropped = AiBatchParser.parse(reply, "450 shampoo", "Paytm", "Others", multi = false)!!.single().response
        assertEquals("Paytm", dropped.appName)
        assertNotEquals("Pet Grooming", dropped.purpose)
    }

    // ---- confirmation → saved ------------------------------------------------------------

    @Test
    fun savingKeepsTheCustomAppAndCategory() {
        val response = AiTransactionResponse(
            amount = 450.0, appName = "Jio Mart", appPresetId = custom.appPresets.single().id, purpose = "Pet Grooming"
        )
        val spend = response.toNewSpend("Paytm", "Others", now = 0L, custom = custom)
        assertEquals("Jio Mart", spend.preset.displayName)
        assertEquals("", spend.customAppName)
        assertEquals("Pet Grooming", spend.purpose)

        // Without the list, neither is known: the app falls back to the user's default and the
        // category to the default purpose. This is why every caller passes the list.
        val unlisted = response.toNewSpend("Paytm", "Others", now = 0L)
        assertEquals("paytm", unlisted.preset.id)
        assertEquals("Miscellaneous", unlisted.purpose)
    }

    // ---- the history assistant -----------------------------------------------------------

    @Test
    fun theHistoryAssistantScopesToTheUsersOwnNames() {
        val q = "how much did I spend on pet grooming last month"
        assertEquals(listOf("Pet Grooming"), HistoryQuery.parseScope(q, custom = custom).purposes)
        // Without the list the built-in words inside it ("pet", "grooming") widen it to two whole buckets.
        assertEquals(setOf("Pets", "Personal Care"), HistoryQuery.parseScope(q).purposes.toSet())

        assertEquals(listOf("Jio Mart"), HistoryQuery.parseScope("spend on jio mart this month", custom = custom).apps)
        val cafe = CustomPresets(apps = listOf("Zepto Cafe"))
        assertEquals(listOf("Zepto Cafe"), HistoryQuery.parseScope("spend on zepto cafe", custom = cafe).apps)
    }

    // ---- end to end through the processor ------------------------------------------------

    private class FakeConfig : LlmConfig {
        override suspend fun refresh() {}
        override fun groqKey() = "key"
        override fun geminiKey() = ""
        override fun groqModel(tier: LlmTier) = if (tier == LlmTier.FAST) GroqModels.FAST else GroqModels.SMART
        override fun geminiModel() = "gemini-test"
    }

    private class FakeGroq(val content: String) : GroqApiService {
        val requests = mutableListOf<GroqRequest>()
        override suspend fun getCompletion(authorization: String, request: GroqRequest): Response<GroqResponse> {
            requests += request
            return Response.success(GroqResponse(listOf(GroqChoice(GroqMessage("assistant", content), "stop"))))
        }
    }

    private fun processor(groq: FakeGroq): AiTransactionProcessor {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sink = LlmEvents { _, _ -> }
        return AiTransactionProcessor(
            LlmClient(groq, FakeConfig(), object : GeminiGateway {
                override suspend fun generate(model: String, apiKey: String, system: String?, user: String, json: Boolean): String? = null
            }, sink),
            sink, AiPreferencesRepository(context), AiCorrectionRepository(context)
        )
    }

    private val unnamed = """{"amount":450,"appName":"","purpose":"Miscellaneous","notes":"","personName":"","date":"2026-10-30","needsAmount":false}"""

    @Test
    fun theProcessorTellsTheModelAboutTheUsersListUnlessPrivacyIsOn() = runBlocking {
        val open = FakeGroq(unnamed)
        processor(open).parse("450 at jio mart", AiPreferences(defaultApp = "Paytm", customPresets = custom), allowMultiple = false)
        val openPrompt = open.requests.single().messages.first().content.orEmpty()
        assertTrue(openPrompt.contains("Jio Mart") && openPrompt.contains("Pet Grooming"))

        // Privacy on: the names are the user's free text, so they stay on the phone...
        val private = FakeGroq(unnamed)
        val out = processor(private).parse(
            "450 at jio mart",
            AiPreferences(defaultApp = "Paytm", isE2eEncryptionEnabled = true, customPresets = custom),
            allowMultiple = false
        ).getOrThrow()
        val privatePrompt = private.requests.single().messages.first().content.orEmpty()
        assertFalse(privatePrompt.contains("Jio Mart") || privatePrompt.contains("Pet Grooming"))
        // ...and are still recognised in the sentence on the device.
        assertEquals("Jio Mart", out.single().appName)
    }
}
