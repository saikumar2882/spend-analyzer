package com.alpha.spendtracker

import androidx.test.core.app.ApplicationProvider
import com.alpha.spendtracker.data.AiBatchParser
import com.alpha.spendtracker.data.AiCorrectionRepository
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.data.GeminiGateway
import com.alpha.spendtracker.data.GroqApiService
import com.alpha.spendtracker.data.GroqChoice
import com.alpha.spendtracker.data.GroqMessage
import com.alpha.spendtracker.data.GroqModels
import com.alpha.spendtracker.data.GroqRequest
import com.alpha.spendtracker.data.GroqResponse
import com.alpha.spendtracker.data.LlmClient
import com.alpha.spendtracker.data.LlmConfig
import com.alpha.spendtracker.data.LlmEvents
import com.alpha.spendtracker.data.LlmTier
import com.alpha.spendtracker.data.ScriptedLocal
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * The on-device model in [AiTransactionProcessor]: asked first, believed only when its answer
 * agrees with the sentence, and never the reason a parse is worse than it would have been.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiOnDeviceParseTest {

    private class FakeConfig : LlmConfig {
        override suspend fun refresh() {}
        override fun groqKey() = "key"
        override fun geminiKey() = ""
        override fun groqModel(tier: LlmTier) = if (tier == LlmTier.FAST) GroqModels.FAST else GroqModels.SMART
        override fun geminiModel() = "gemini-test"
    }

    private class FakeGroq(val respond: (GroqRequest) -> Response<GroqResponse>) : GroqApiService {
        val requests = mutableListOf<GroqRequest>()
        override suspend fun getCompletion(authorization: String, request: GroqRequest): Response<GroqResponse> {
            requests += request
            return respond(request)
        }
    }

    private fun groqReply(content: String) =
        Response.success(GroqResponse(listOf(GroqChoice(GroqMessage("assistant", content), "stop"))))

    /** 401: not transient, so the client gives up at once instead of backing off for real seconds. */
    private fun groqDown() =
        Response.error<GroqResponse>(401, """{"error":{"message":"nope"}}""".toResponseBody("application/json".toMediaType()))

    private class Rig(val groq: FakeGroq, val local: ScriptedLocal) {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        private val sink = LlmEvents { name, params -> events += name to params }
        private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefsRepo = AiPreferencesRepository(context)
        val processor = AiTransactionProcessor(
            LlmClient(groq, FakeConfig(), object : GeminiGateway {
                override suspend fun generate(model: String, apiKey: String, system: String?, user: String, json: Boolean): String? = null
            }, sink, local),
            sink, prefsRepo, AiCorrectionRepository(context)
        )
        val parseEvent get() = events.single { it.first == "ai_parse" }.second
        suspend fun usage() = prefsRepo.aiPreferencesFlow.first().dailyUsageCount
    }

    private fun rig(local: ScriptedLocal, cloud: (GroqRequest) -> Response<GroqResponse> = { groqReply("{}") }) =
        Rig(FakeGroq(cloud), local)

    private val prefs = AiPreferences(defaultApp = "Paytm", defaultPurpose = "Others")

    private fun entry(src: String, amount: Number?, purpose: String = "Food & Dining", notes: String = "Tea") =
        """{"src":"$src","amount":${amount ?: "null"},"appName":"","purpose":"$purpose","notes":"$notes","personName":"","date":"2026-10-30","needsAmount":${amount == null}}"""

    private fun batch(vararg entries: String) = """{"transactions":[${entries.joinToString(",")}]}"""

    // --- accepted ------------------------------------------------------------------------------

    @Test
    fun anAgreeingOnDeviceAnswerIsUsedAndNothingGoesToTheCloud() = runBlocking {
        val rig = rig(ScriptedLocal().then(batch(entry("tea 20", 20))))
        val before = rig.usage()

        val out = rig.processor.parse("tea 20", prefs).getOrThrow()

        assertEquals(listOf(20.0), out.map { it.amount })
        assertEquals("Food & Dining", out.single().purpose)
        assertFalse(out.single().usedFallback)
        assertTrue("the cloud was not asked", rig.groq.requests.isEmpty())
        assertEquals("the phone's own model costs no daily use", before, rig.usage())
        val event = rig.parseEvent
        assertEquals("llm", event["outcome"])
        assertEquals("LOCAL", event["provider"])
        assertEquals(false, event["local_rejected"])
    }

    @Test
    fun theOnDeviceModelGetsTheCompactPromptNotTheCloudOne() = runBlocking {
        val local = ScriptedLocal().then(batch(entry("tea 20", 20)))
        rig(local).processor.parse("tea 20", prefs)

        val asked = local.calls.single()
        val system = asked.system.orEmpty()
        assertTrue(system.contains("Today: "))
        assertFalse("the long cloud-only table must not be sent", system.contains("PLATFORM MAPPING"))
        assertTrue("it must still fit the on-device window", system.length + asked.user.length < 8_000)
        assertEquals("""USER INPUT: "tea 20"""", asked.user)
        // The window is small, and the answer is capped accordingly by the gateway; the request asks for the full budget.
        assertTrue(asked.maxTokens > 0)
    }

    @Test
    fun aSentenceWithNoAmountForTheRegexLeavesNothingToContradict() = runBlocking {
        // "two fifty" is words, so the offline baseline finds no amount and cannot veto the model.
        val rig = rig(ScriptedLocal().then(batch(entry("two fifty rupees chai", 250))))

        val out = rig.processor.parse("two fifty rupees chai", prefs).getOrThrow()

        assertEquals(250.0, out.single().amount!!, 0.0)
        assertTrue(rig.groq.requests.isEmpty())
    }

    @Test
    fun aMultiExpenseAnswerThatKeepsEveryExpenseIsUsed() = runBlocking {
        val rig = rig(ScriptedLocal().then(batch(entry("tea 20", 20), entry("auto 80", 80, "Commute", "Auto"))))

        val out = rig.processor.parse("tea 20, auto 80", prefs).getOrThrow()

        assertEquals(listOf(20.0, 80.0), out.map { it.amount })
        assertTrue(rig.groq.requests.isEmpty())
    }

    // --- rejected ------------------------------------------------------------------------------

    @Test
    fun anAmountTheSentenceDoesNotContainSendsTheCallToTheCloud() = runBlocking {
        val local = ScriptedLocal().then(batch(entry("tea 20", 25)))
        val rig = rig(local) { groqReply(batch(entry("tea 20", 20))) }
        val before = rig.usage()

        val out = rig.processor.parse("tea 20", prefs).getOrThrow()

        assertEquals("the cloud's answer, not the 25 the small model made up", 20.0, out.single().amount!!, 0.0)
        assertEquals(1, rig.groq.requests.size)
        assertEquals("a cloud answer is charged as before", before + 1, rig.usage())
        val event = rig.parseEvent
        assertEquals("GROQ", event["provider"])
        assertEquals(true, event["local_rejected"])
        assertEquals("llm", event["outcome"])
    }

    @Test
    fun theSecondOpinionIsTheCloudOnlyFormOfTheSameRequest() = runBlocking {
        val local = ScriptedLocal().then(batch(entry("tea 20", 25)))
        val rig = rig(local) { groqReply(batch(entry("tea 20", 20))) }

        rig.processor.parse("tea 20", prefs)

        assertEquals("the phone was asked once, not again for the second opinion", 1, local.calls.size)
        assertTrue(rig.groq.requests.single().messages.first().content.orEmpty().contains("PLATFORM MAPPING"))
    }

    @Test
    fun anUnreadableOnDeviceAnswerSendsTheCallToTheCloud() = runBlocking {
        val rig = rig(ScriptedLocal().then("Sure! Here is your expense: tea, twenty rupees.")) { groqReply(batch(entry("tea 20", 20))) }

        val out = rig.processor.parse("tea 20", prefs).getOrThrow()

        assertEquals(20.0, out.single().amount!!, 0.0)
        assertEquals(1, rig.groq.requests.size)
        assertEquals(true, rig.parseEvent["local_rejected"])
    }

    @Test
    fun aLostExpenseSendsTheCallToTheCloud() = runBlocking {
        // The regex found two amounts; the model returned one entry.
        val rig = rig(ScriptedLocal().then(batch(entry("tea 20", 20)))) {
            groqReply(batch(entry("tea 20", 20), entry("auto 80", 80, "Commute", "Auto")))
        }

        val out = rig.processor.parse("tea 20, auto 80", prefs).getOrThrow()

        assertEquals(listOf(20.0, 80.0), out.map { it.amount })
        assertEquals(true, rig.parseEvent["local_rejected"])
    }

    @Test
    fun aRejectedAnswerWithNoCloudFallsBackToTheBaselineNotTheRejectedAnswer() = runBlocking {
        val rig = rig(ScriptedLocal().then(batch(entry("tea 20", 25)))) { groqDown() }
        val before = rig.usage()

        val out = rig.processor.parse("tea 20", prefs).getOrThrow()

        assertEquals("the regex's 20, not the rejected 25", 20.0, out.single().amount!!, 0.0)
        assertTrue("flagged so the confirmation screen says to double-check it", out.single().usedFallback)
        assertEquals(before, rig.usage())
        val event = rig.parseEvent
        assertEquals("fallback_local_rejected", event["outcome"])
        assertEquals("AUTH", event["error_kind"])
        assertEquals(true, event["used_fallback"])
    }

    // --- not offered ---------------------------------------------------------------------------

    @Test
    fun teluguAndHindiScriptNeverGoToTheOnDeviceModel() = runBlocking {
        val local = ScriptedLocal()
        val rig = rig(local) { groqReply(batch(entry("టీ 20", 20))) }

        rig.processor.parse("టీ 20", prefs).getOrThrow()
        rig.processor.parse("चाय 20", prefs).getOrThrow()

        assertTrue(local.calls.isEmpty())
        assertEquals("both were answered by the cloud", 2, rig.groq.requests.size)
    }

    @Test
    fun aModelThatIsNotThereLeavesTheCloudPathExactlyAsItWas() = runBlocking {
        val local = ScriptedLocal(ready = false)
        val rig = rig(local) { groqReply(batch(entry("tea 20", 20))) }
        val before = rig.usage()

        val out = rig.processor.parse("tea 20", prefs).getOrThrow()

        assertEquals(20.0, out.single().amount!!, 0.0)
        assertTrue(local.calls.isEmpty())
        assertEquals(before + 1, rig.usage())
        val event = rig.parseEvent
        assertEquals("GROQ", event["provider"])
        assertEquals(false, event["local_rejected"])
    }

    // --- the pure rules ------------------------------------------------------------------------

    private fun parsed(vararg amounts: Double?) =
        amounts.map { AiBatchParser.Parsed(AiTransactionResponse(amount = it), "") }

    private fun interpreted(outcome: String, parsed: List<AiBatchParser.Parsed>) =
        AiTransactionProcessor.Interpreted(parsed, outcome, usedFallback = false)

    @Test
    fun onDeviceAnswerHoldsRules() {
        val holds = { i: AiTransactionProcessor.Interpreted, base: List<AiBatchParser.Parsed>, multi: Boolean ->
            AiTransactionProcessor.onDeviceAnswerHolds(i, base, multi)
        }
        val ok = AiTransactionProcessor.OUTCOME_LLM

        assertTrue(holds(interpreted(ok, parsed(20.0)), parsed(20.0), true))
        assertTrue("a different order of the same amounts", holds(interpreted(ok, parsed(80.0, 20.0)), parsed(20.0, 80.0), true))
        assertTrue("no amount found by the regex: nothing to contradict", holds(interpreted(ok, parsed(250.0)), parsed(null), true))
        assertTrue("a model amount of null is left for the baseline to fill", holds(interpreted(ok, parsed(null)), parsed(20.0), true))
        assertTrue("rounding noise", holds(interpreted(ok, parsed(20.001)), parsed(20.0), true))

        assertFalse("a made-up amount", holds(interpreted(ok, parsed(25.0)), parsed(20.0), true))
        assertFalse("one of two amounts made up", holds(interpreted(ok, parsed(20.0, 90.0)), parsed(20.0, 80.0), true))
        assertFalse("a lost expense", holds(interpreted(ok, parsed(20.0)), parsed(20.0, 80.0), true))
        assertTrue("single-expense mode keeps one entry by design", holds(interpreted(ok, parsed(20.0)), parsed(20.0, 80.0), false))
        assertFalse("unreadable", holds(interpreted(AiTransactionProcessor.OUTCOME_UNUSABLE_JSON, parsed(20.0)), parsed(20.0), true))
        assertFalse("cut off", holds(interpreted(AiTransactionProcessor.OUTCOME_TRUNCATED, parsed(20.0)), parsed(20.0), true))
    }

    @Test
    fun hasIndicScriptRules() {
        assertTrue(AiTransactionProcessor.hasIndicScript("టీ 20"))
        assertTrue(AiTransactionProcessor.hasIndicScript("चाय 20"))
        assertTrue("one word in a Latin sentence is enough", AiTransactionProcessor.hasIndicScript("lunch ₹200 భోజనం"))
        assertFalse(AiTransactionProcessor.hasIndicScript("tea 20"))
        assertFalse("romanised Telugu is Latin script", AiTransactionProcessor.hasIndicScript("500 dinner ki karchu chesa"))
        assertFalse("digits and symbols are not a script", AiTransactionProcessor.hasIndicScript("₹1,299 + 50"))
        assertFalse(AiTransactionProcessor.hasIndicScript(""))
    }

    @Test
    fun theCompactPromptSpeaksTheSameVocabularyAsTheCloudOne() {
        val compact = com.alpha.spendtracker.data.AiPrompt.compactSystemPrompt("INR", "Others", multi = true)
        // Every purpose it offers is canonical (the same guard AiPromptTest applies to the long prompt).
        CANONICAL_PURPOSES.forEach { assertTrue("missing $it", compact.contains("\"$it\"")) }
        assertTrue("the Others default is spelled canonically", compact.contains("Default purpose: \"Miscellaneous\""))
        assertTrue(compact.contains("\"transactions\""))
        assertTrue(compact.endsWith(com.alpha.spendtracker.data.PromptGuard.UNTRUSTED_INPUT_RULE))
        val single = com.alpha.spendtracker.data.AiPrompt.compactSystemPrompt("INR", "Others", multi = false)
        assertFalse("the single shape has no transactions array", single.contains("\"transactions\""))
        // Well under the cloud prompt, which is what makes it fit a 4000-token window.
        val full = com.alpha.spendtracker.data.AiPrompt.systemPrompt("INR", "Others", multi = true)
        assertTrue("compact ${compact.length} vs full ${full.length}", compact.length < full.length / 2)
        assertTrue(compact.length < 4_500)
    }
}
