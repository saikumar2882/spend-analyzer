@file:OptIn(ExperimentalCoroutinesApi::class)

package com.alpha.spendtracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class LlmClientTest {

    private val groq = ScriptedGroq()
    private val gemini = ScriptedGemini()
    private val config = StubLlmConfig()
    private val events = RecordingLlmEvents()
    private val client = LlmClient(groq, config, gemini, events)

    /** Virtual milliseconds [block] took, i.e. the backoff it waited out. */
    private suspend fun TestScope.elapsedDuring(block: suspend () -> Unit): Long {
        val start = testScheduler.currentTime
        block()
        return testScheduler.currentTime - start
    }

    private fun Result<LlmReply>.error(): LlmException = exceptionOrNull() as LlmException

    // --- success path -------------------------------------------------------------------------

    @Test
    fun successSendsSystemAndUserMessagesWithoutResponseFormat() = runTest {
        groq.then(groqReply("""{"ok":true}"""))

        val result = client.complete(llmRequest(user = "tea 20", system = "Parse it.", maxTokens = 777, temperature = 0.3))

        val reply = result.getOrThrow()
        assertEquals("""{"ok":true}""", reply.text)
        assertEquals(LlmProvider.GROQ, reply.provider)
        assertEquals(1, reply.attempts)
        assertFalse(reply.truncated)

        assertEquals(1, groq.calls.size)
        val call = groq.calls.single()
        assertEquals("Bearer groq-key", call.authorization)
        val body = call.request
        assertEquals("openai/gpt-oss-20b", body.model)
        assertEquals(listOf("system" to "Parse it.", "user" to "tea 20"), body.messages.map { it.role to it.content })
        assertEquals(0.3, body.temperature, 0.0)
        assertEquals(777, body.max_completion_tokens)
        assertNull("LlmJson.None must not send a response_format", body.response_format)
        assertEquals("low", body.reasoning_effort)
        assertEquals(false, body.include_reasoning)
        assertTrue("Gemini must stay untouched when Groq answers", gemini.calls.isEmpty())
        assertEquals(1, config.refreshes)
    }

    @Test
    fun noSystemPromptSendsOnlyTheUserMessage() = runTest {
        groq.then(groqReply("hi"))

        client.complete(llmRequest(system = null, user = "hello")).getOrThrow()

        assertEquals(listOf("user" to "hello"), groq.calls.single().request.messages.map { it.role to it.content })
    }

    @Test
    fun tierPicksTheModelIdFromConfig() = runTest {
        config.smartModel = "some/pushed-model"
        groq.then(groqReply("a")).then(groqReply("b"))

        client.complete(llmRequest(tier = LlmTier.SMART)).getOrThrow()
        client.complete(llmRequest(tier = LlmTier.FAST)).getOrThrow()

        assertEquals(listOf("some/pushed-model", "openai/gpt-oss-20b"), groq.calls.map { it.request.model })
    }

    @Test
    fun jsonObjectModeSendsPlainJsonObjectFormat() = runTest {
        groq.then(groqReply("{}"))

        client.complete(llmRequest(json = LlmJson.JsonObject)).getOrThrow()

        val format = groq.calls.single().request.response_format!!
        assertEquals("json_object", format.type)
        assertNull(format.json_schema)
    }

    // --- retries ------------------------------------------------------------------------------

    @Test
    fun transient429ThenSuccessRetriesAfterAboutOneSecond() = runTest {
        groq.then(groqFailure(429)).then(groqReply("done"))

        var reply: LlmReply? = null
        val elapsed = elapsedDuring { reply = client.complete(llmRequest()).getOrThrow() }

        assertEquals("done", reply!!.text)
        assertEquals(2, reply!!.attempts)
        assertEquals(2, groq.calls.size)
        assertTrue("backoff was $elapsed ms", elapsed in 1_000..1_250)
        assertEquals(2, events.calls.single()["attempts"])
        assertEquals("ok", events.calls.single()["outcome"])
    }

    @Test
    fun a429ThenA503ThenSuccessBacksOffOneThenTwoSeconds() = runTest {
        groq.then(groqFailure(429)).then(groqFailure(503)).then(groqReply("done"))

        var reply: LlmReply? = null
        val elapsed = elapsedDuring { reply = client.complete(llmRequest()).getOrThrow() }

        assertEquals(3, reply!!.attempts)
        // 1s + 2s, each with up to 250ms of jitter.
        assertTrue("backoff was $elapsed ms", elapsed in 3_000..3_500)
    }

    @Test
    fun retryAfterHeaderLongerThanBackoffIsHonored() = runTest {
        groq.then(groqFailure(429, headers = arrayOf("Retry-After" to "3"))).then(groqReply("done"))

        val elapsed = elapsedDuring { client.complete(llmRequest()).getOrThrow() }

        assertEquals("waited exactly what the server asked", 3_000L, elapsed)
    }

    @Test
    fun retryAfterBeyondTheCapSkipsRetriesAndFailsOverAtOnce() = runTest {
        groq.then(groqFailure(429, headers = arrayOf("retry-after" to "1800")))
        gemini.then("from gemini")

        var reply: LlmReply? = null
        val elapsed = elapsedDuring { reply = client.complete(llmRequest()).getOrThrow() }

        assertEquals(1, groq.calls.size)
        assertEquals(0L, elapsed)
        assertEquals(LlmProvider.GEMINI, reply!!.provider)
        assertEquals(2, reply!!.attempts)
        assertEquals(true, events.calls.single()["failover"])
    }

    @Test
    fun retryAfterBeyondTheCapWithoutFallbackSurfacesTheHint() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(429, headers = arrayOf("retry-after" to "1800")))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.RATE_LIMIT, error.kind)
        assertEquals(1_800_000L, error.retryAfterMs)
        assertEquals(1, groq.calls.size)
    }

    @Test
    fun zeroOrUnparseableRetryAfterFallsBackToTheNormalBackoff() = runTest {
        groq.then(groqFailure(429, headers = arrayOf("retry-after" to "0")))
            .then(groqFailure(429, headers = arrayOf("retry-after" to "Wed, 21 Oct 2026 07:28:00 GMT")))
            .then(groqReply("done"))

        val elapsed = elapsedDuring { client.complete(llmRequest()).getOrThrow() }

        assertTrue("backoff was $elapsed ms", elapsed in 3_000..3_500)
    }

    @Test
    fun any5xxIsTransientAndRetried() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(520)).then(groqFailure(500)).then(groqFailure(502))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.UNAVAILABLE, error.kind)
        assertEquals(3, groq.calls.size)
    }

    @Test
    fun ioExceptionIsNetworkAndRetried() = runTest {
        groq.thenThrow(UnknownHostException("api.groq.com")).thenThrow(IOException("reset")).then(groqReply("done"))

        val reply = client.complete(llmRequest()).getOrThrow()

        assertEquals(3, reply.attempts)
        assertEquals(LlmProvider.GROQ, reply.provider)
    }

    @Test
    fun exhaustedNetworkErrorsReportNetwork() = runTest {
        config.geminiKey = ""
        groq.thenThrow(IOException("a")).thenThrow(IOException("b")).thenThrow(IOException("c"))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.NETWORK, error.kind)
        assertEquals(3, events.calls.single()["attempts"])
    }

    // --- failover -----------------------------------------------------------------------------

    @Test
    fun exhaustedGroqFailsOverToGemini() = runTest {
        groq.then(groqFailure(503)).then(groqFailure(503)).then(groqFailure(503))
        gemini.then("""{"from":"gemini"}""")

        var reply: LlmReply? = null
        val elapsed = elapsedDuring {
            reply = client.complete(llmRequest(system = "sys", user = "usr", json = LlmJson.JsonObject, temperature = 0.2)).getOrThrow()
        }

        assertEquals("""{"from":"gemini"}""", reply!!.text)
        assertEquals(LlmProvider.GEMINI, reply!!.provider)
        assertEquals("3 Groq tries + 1 Gemini", 4, reply!!.attempts)
        assertEquals(3, groq.calls.size)
        assertTrue("backoff was $elapsed ms", elapsed in 3_000..3_500)

        val g = gemini.calls.single()
        assertEquals("gemini-key", g.apiKey)
        assertEquals("gemini-test", g.model)
        assertEquals("sys", g.system)
        assertEquals("usr", g.user)
        assertTrue("JSON mode carries over to Gemini", g.json)
        assertEquals("Gemini is sampled as cold as the Groq call it replaces", 0.2, g.temperature!!, 0.0)

        val event = events.calls.single()
        assertEquals("GEMINI", event["provider"])
        assertEquals(true, event["failover"])
        assertEquals(4, event["attempts"])
        assertEquals("ok", event["outcome"])
        assertEquals("NONE", event["error_kind"])
    }

    @Test
    fun blankGroqKeyGoesStraightToGeminiAndIsNotAFailover() = runTest {
        config.groqKey = ""
        gemini.then("hello")

        val reply = client.complete(llmRequest()).getOrThrow()

        assertTrue(groq.calls.isEmpty())
        assertEquals(LlmProvider.GEMINI, reply.provider)
        assertEquals(1, reply.attempts)
        assertEquals(false, events.calls.single()["failover"])
    }

    @Test
    fun bothKeysBlankIsNoKeyAndNothingIsCalled() = runTest {
        config.groqKey = ""
        config.geminiKey = "  "

        var result: Result<LlmReply>? = null
        val elapsed = elapsedDuring { result = client.complete(llmRequest()) }

        val error = result!!.error()
        assertEquals(LlmException.Kind.NO_KEY, error.kind)
        assertTrue(groq.calls.isEmpty())
        assertTrue(gemini.calls.isEmpty())
        assertEquals(0L, elapsed)
        val event = events.calls.single()
        assertEquals("NO_KEY", event["error_kind"])
        assertEquals("NONE", event["provider"])
        assertEquals(0, event["attempts"])
        assertEquals("error", event["outcome"])
    }

    @Test
    fun decommissionedModelIsNotRetriedAndFallsToGemini() = runTest {
        groq.then(groqFailure(400, DECOMMISSIONED_BODY))
        gemini.then("saved by gemini")

        var reply: LlmReply? = null
        val elapsed = elapsedDuring { reply = client.complete(llmRequest()).getOrThrow() }

        assertEquals(1, groq.calls.size)
        assertEquals(0L, elapsed)
        assertEquals(LlmProvider.GEMINI, reply!!.provider)
        assertEquals(2, reply!!.attempts)
    }

    @Test
    fun decommissionedModelWithoutGeminiCarriesTheResponseBody() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(400, DECOMMISSIONED_BODY))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.BAD_REQUEST, error.kind)
        assertTrue(error.message, "decommissioned" in error.message.orEmpty())
        assertTrue(error.message, error.message.orEmpty().startsWith("Groq API error: 400"))
        assertEquals(1, groq.calls.size)
        assertFalse(error.isTransient)
    }

    @Test
    fun unauthorizedIsAuthAndNotRetried() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(401, """{"error":{"message":"Invalid API Key","code":"invalid_api_key"}}"""))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.AUTH, error.kind)
        assertEquals(1, groq.calls.size)
    }

    @Test
    fun otherClientErrorsAreNotRetried() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(413, """{"error":{"message":"Request too large"}}"""))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.OTHER, error.kind)
        assertEquals(1, groq.calls.size)
    }

    @Test
    fun whenBothFailTheGroqErrorWinsAndTheGeminiKindIsStillReported() = runTest {
        groq.then(groqFailure(400, DECOMMISSIONED_BODY))
        gemini.thenThrow(RuntimeException("The model is overloaded."))
            .thenThrow(RuntimeException("The model is overloaded."))
            .thenThrow(RuntimeException("The model is overloaded."))

        val error = client.complete(llmRequest()).error()

        assertEquals(LlmException.Kind.BAD_REQUEST, error.kind)
        val event = events.calls.single()
        assertEquals("1 Groq try + 3 Gemini tries", 4, event["attempts"])
        assertEquals(true, event["failover"])
        assertEquals("BAD_REQUEST", event["error_kind"])
        assertEquals("UNAVAILABLE", event["fallback_error_kind"])
        assertEquals("NONE", event["provider"])
    }

    @Test
    fun groqFailingWithNoGeminiKeyIsNotCountedAsAFailover() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(503)).then(groqFailure(503)).then(groqFailure(503))

        client.complete(llmRequest()).error()

        val event = events.calls.single()
        assertEquals(false, event["failover"])
        assertEquals(3, event["attempts"])
        assertFalse("no secondary error without a second provider", event.containsKey("fallback_error_kind"))
    }

    // --- empty / truncated --------------------------------------------------------------------

    @Test
    fun lengthFinishReasonMarksTheReplyTruncated() = runTest {
        groq.then(groqReply("""{"a":1""", finishReason = "length"))

        val reply = client.complete(llmRequest()).getOrThrow()

        assertTrue(reply.truncated)
        assertEquals(true, events.calls.single()["truncated"])
    }

    @Test
    fun blankContentIsEmptyNotRetriedAndFallsToGemini() = runTest {
        groq.then(groqReply("   "))
        gemini.then("real answer")

        val reply = client.complete(llmRequest()).getOrThrow()

        assertEquals(1, groq.calls.size)
        assertEquals(LlmProvider.GEMINI, reply.provider)
    }

    @Test
    fun nullContentAndNoChoicesAreBothEmpty() = runTest {
        config.geminiKey = ""
        groq.then(groqReply(null, finishReason = "length"))
            .then(retrofit2.Response.success(GroqResponse(emptyList())))

        assertEquals(LlmException.Kind.EMPTY, client.complete(llmRequest()).error().kind)
        assertEquals(LlmException.Kind.EMPTY, client.complete(llmRequest()).error().kind)
    }

    @Test
    fun geminiEmptyAnswerIsAnError() = runTest {
        config.groqKey = ""
        gemini.then(null)

        assertEquals(LlmException.Kind.EMPTY, client.complete(llmRequest()).error().kind)
    }

    // --- schema mode --------------------------------------------------------------------------

    private val schema = LlmJson.Schema("expense", mapOf("type" to "object"))

    @Test
    fun schemaRejectionDowngradesToJsonObjectOnceAndIsRemembered() = runTest {
        groq.then(groqFailure(400, SCHEMA_UNSUPPORTED_BODY))
            .then(groqReply("""{"n":1}"""))
            .then(groqReply("""{"n":2}"""))

        val first = client.complete(llmRequest(json = schema)).getOrThrow()

        assertEquals("""{"n":1}""", first.text)
        assertEquals("the retry inside one attempt is not a second attempt", 1, first.attempts)
        assertEquals(2, groq.calls.size)
        assertEquals("json_schema", groq.calls[0].request.response_format!!.type)
        assertEquals("expense", groq.calls[0].request.response_format!!.json_schema!!.name)
        assertEquals("json_object", groq.calls[1].request.response_format!!.type)
        assertNull(groq.calls[1].request.response_format!!.json_schema)
        assertEquals(1, events.downgrades.size)
        assertEquals("openai/gpt-oss-20b", events.downgrades.single()["model"])
        assertEquals(true, events.downgrades.single()["remembered"])

        // Same model again: straight to json_object, no second schema attempt.
        client.complete(llmRequest(json = schema)).getOrThrow()
        assertEquals(3, groq.calls.size)
        assertEquals("json_object", groq.calls[2].request.response_format!!.type)
        assertEquals(1, events.downgrades.size)
    }

    @Test
    fun theSchemaMemoryIsPerModel() = runTest {
        groq.then(groqFailure(400, SCHEMA_UNSUPPORTED_BODY)).then(groqReply("a")).then(groqReply("b"))

        client.complete(llmRequest(tier = LlmTier.FAST, json = schema)).getOrThrow()
        client.complete(llmRequest(tier = LlmTier.SMART, json = schema)).getOrThrow()

        assertEquals("openai/gpt-oss-120b", groq.calls[2].request.model)
        assertEquals("json_schema", groq.calls[2].request.response_format!!.type)
    }

    @Test
    fun a400ThatIsNotAboutSchemasIsNotDowngraded() = runTest {
        config.geminiKey = ""
        groq.then(groqFailure(400, DECOMMISSIONED_BODY))

        val error = client.complete(llmRequest(json = schema)).error()

        assertEquals(LlmException.Kind.BAD_REQUEST, error.kind)
        assertEquals(1, groq.calls.size)
        assertTrue(events.downgrades.isEmpty())
    }

    @Test
    fun theWordSchemaInsideFailedGenerationDoesNotTriggerADowngrade() = runTest {
        // failed_generation echoes the model's output, i.e. the user's own words.
        val body = """{"error":{"message":"Please reduce the length of the messages or completion.","type":"invalid_request_error","code":"context_length_exceeded","failed_generation":"my schema and response_format notes"}}"""
        config.geminiKey = ""
        groq.then(groqFailure(400, body))

        val error = client.complete(llmRequest(json = schema)).error()

        assertEquals(LlmException.Kind.BAD_REQUEST, error.kind)
        assertEquals(1, groq.calls.size)
        assertTrue(events.downgrades.isEmpty())
    }

    @Test
    fun aFailedGenerationRetriesAsJsonObjectOnceWithoutBlacklistingTheModel() = runTest {
        val body = """{"error":{"message":"Failed to generate JSON. Please adjust your prompt. See 'failed_generation' for more details.","type":"invalid_request_error","code":"json_validate_failed","failed_generation":"{\"note\":\"x\""}}"""
        groq.then(groqFailure(400, body)).then(groqReply("""{"n":1}""")).then(groqReply("""{"n":2}"""))

        client.complete(llmRequest(json = schema)).getOrThrow()
        assertEquals("json_object", groq.calls[1].request.response_format!!.type)
        assertEquals(false, events.downgrades.single()["remembered"])

        // One bad generation must not switch strict mode off for the rest of the process.
        client.complete(llmRequest(json = schema)).getOrThrow()
        assertEquals("json_schema", groq.calls[2].request.response_format!!.type)
    }

    @Test
    fun aNonJsonErrorBodyIsStillRecognisedByItsText() = runTest {
        groq.then(groqFailure(400, "Bad Request: response_format json_schema is unsupported")).then(groqReply("ok"))

        client.complete(llmRequest(json = schema)).getOrThrow()

        assertEquals(2, groq.calls.size)
    }

    @Test
    fun aTransientFailureOfTheDowngradedCallDoesNotRetrySchemaMode() = runTest {
        groq.then(groqFailure(400, SCHEMA_UNSUPPORTED_BODY))
            .then(groqFailure(429))
            .then(groqReply("finally"))

        val reply = client.complete(llmRequest(json = schema)).getOrThrow()

        assertEquals("finally", reply.text)
        assertEquals(listOf("json_schema", "json_object", "json_object"), groq.calls.map { it.request.response_format!!.type })
    }

    @Test
    fun schemaModeReachesGeminiAsPlainJsonMimeType() = runTest {
        config.groqKey = ""
        gemini.then("{}")

        client.complete(llmRequest(json = schema)).getOrThrow()

        assertTrue(gemini.calls.single().json)
    }

    // --- model-specific parameters ------------------------------------------------------------

    @Test
    fun reasoningParametersAreOnlySentToGptOssModels() = runTest {
        config.fastModel = "meta-llama/llama-4-scout"
        config.smartModel = "openai/gpt-oss-120b"
        groq.then(groqReply("a")).then(groqReply("b"))

        client.complete(llmRequest(tier = LlmTier.FAST, reasoningEffort = "high")).getOrThrow()
        client.complete(llmRequest(tier = LlmTier.SMART, reasoningEffort = "high")).getOrThrow()

        val other = groq.calls[0].request
        assertNull(other.reasoning_effort)
        assertNull(other.include_reasoning)
        val gptOss = groq.calls[1].request
        assertEquals("high", gptOss.reasoning_effort)
        assertEquals(false, gptOss.include_reasoning)
    }

    // --- Gemini error text --------------------------------------------------------------------

    @Test
    fun geminiExceptionsAreClassifiedByTheirText() = runTest {
        val cases = listOf(
            Triple(RuntimeException("[429] Resource has been exhausted (e.g. check quota)."), LlmException.Kind.RATE_LIMIT, 3),
            Triple(RuntimeException("Quota exceeded for metric 'generate_content_requests_per_minute'"), LlmException.Kind.RATE_LIMIT, 3),
            Triple(RuntimeException("503 We are experiencing high demand for this model."), LlmException.Kind.UNAVAILABLE, 3),
            Triple(RuntimeException("The model is overloaded. Please try again later."), LlmException.Kind.UNAVAILABLE, 3),
            Triple(RuntimeException("API key not valid. Please pass a valid API key."), LlmException.Kind.AUTH, 1),
            Triple(RuntimeException("models/gemini-9 is not found for API version v1beta"), LlmException.Kind.BAD_REQUEST, 1),
            Triple(
                RuntimeException("Something unexpected happened.", UnknownHostException("generativelanguage.googleapis.com")),
                LlmException.Kind.NETWORK, 3
            ),
            Triple(IOException("Connection reset"), LlmException.Kind.NETWORK, 3),
            Triple(RuntimeException("The request failed to complete in the allotted time."), LlmException.Kind.NETWORK, 3),
            Triple(RuntimeException("Prompt was blocked: SAFETY"), LlmException.Kind.OTHER, 1),
            // 429 inside a longer number is not a status code.
            Triple(RuntimeException("trace id 14290 failed"), LlmException.Kind.OTHER, 1)
        )
        val problems = mutableListOf<String>()
        for ((failure, kind, attempts) in cases) {
            val gem = ScriptedGemini().apply { repeat(3) { thenThrow(failure) } }
            val c = LlmClient(ScriptedGroq(), StubLlmConfig(groqKey = ""), gem, RecordingLlmEvents())

            val error = c.complete(llmRequest()).error()

            if (error.kind != kind) problems += "'${failure.message}' -> ${error.kind}, expected $kind"
            if (gem.calls.size != attempts) problems += "'${failure.message}' made ${gem.calls.size} calls, expected $attempts"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun geminiErrorMessageNamesTheProvider() = runTest {
        config.groqKey = ""
        gemini.thenThrow(RuntimeException("API key not valid"))

        assertEquals("Gemini error: API key not valid", client.complete(llmRequest()).error().message)
    }

    @Test
    fun aGatewayWrittenAgainstTheFiveArgumentFormStillWorks() = runTest {
        var seen: List<Any?>? = null
        val legacy = object : GeminiGateway {
            override suspend fun generate(model: String, apiKey: String, system: String?, user: String, json: Boolean): String? {
                seen = listOf(model, apiKey, system, user, json)
                return "legacy ok"
            }
        }
        val c = LlmClient(ScriptedGroq(), StubLlmConfig(groqKey = ""), legacy, RecordingLlmEvents())

        val reply = c.complete(llmRequest(system = "s", user = "u", json = LlmJson.JsonObject)).getOrThrow()

        assertEquals("legacy ok", reply.text)
        assertEquals(listOf("gemini-test", "gemini-key", "s", "u", true), seen)
    }

    // --- robustness ---------------------------------------------------------------------------

    @Test
    fun keysFromRemoteConfigAreTrimmedBeforeTheyReachTheAuthorizationHeader() = runTest {
        config.groqKey = "  gsk_abc\n"
        groq.then(groqReply("ok"))

        client.complete(llmRequest()).getOrThrow()

        assertEquals("Bearer gsk_abc", groq.calls.single().authorization)
    }

    @Test
    fun aWhitespaceOnlyKeyCountsAsMissing() = runTest {
        config.groqKey = "\n"
        gemini.then("from gemini")

        val reply = client.complete(llmRequest()).getOrThrow()

        assertTrue(groq.calls.isEmpty())
        assertEquals(LlmProvider.GEMINI, reply.provider)
    }

    @Test
    fun aBrokenTelemetrySinkNeverFailsTheCall() = runTest {
        val broken = LlmClient(groq, config, gemini, LlmEvents { _, _ -> throw IllegalStateException("analytics down") })
        groq.then(groqFailure(400, SCHEMA_UNSUPPORTED_BODY)).then(groqReply("still answered"))

        val reply = broken.complete(llmRequest(json = schema)).getOrThrow()

        assertEquals("still answered", reply.text)
    }

    // --- cancellation -------------------------------------------------------------------------

    @Test
    fun cancellationFromGroqPropagatesUnretriedAndUnreported() = runTest {
        groq.thenThrow(CancellationException("cancelled"))

        val outcome = runCatching { client.complete(llmRequest()) }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, groq.calls.size)
        assertTrue(gemini.calls.isEmpty())
        assertTrue("a cancelled call is not a failed call", events.calls.isEmpty())
    }

    @Test
    fun cancellationFromGeminiPropagatesUnretried() = runTest {
        config.groqKey = ""
        gemini.thenThrow(CancellationException("cancelled"))

        val outcome = runCatching { client.complete(llmRequest()) }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, gemini.calls.size)
        assertTrue(events.calls.isEmpty())
    }

    @Test
    fun aCancelledCallWrappedByTheSdkIsNotScoredAsAProviderFailure() = runTest {
        // The Gemini SDK catches Throwable and rethrows its own RuntimeException, so the caller's
        // cancellation reaches the client looking like an ordinary failure.
        config.groqKey = ""
        gemini.thenDo {
            // currentCoroutineContext(), not coroutineContext: the test scope has a member of that
            // name and cancelling *its* job would cancel the whole test.
            currentCoroutineContext().job.cancel()
            throw RuntimeException("Something unexpected happened.")
        }

        val deferred = async { client.complete(llmRequest()) }
        val outcome = runCatching { deferred.await() }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(1, gemini.calls.size)
        assertTrue("no llm_call event for a cancelled call", events.calls.isEmpty())
    }

    // --- telemetry ----------------------------------------------------------------------------

    @Test
    fun llmCallEventCarriesTheDocumentedParams() = runTest {
        groq.then(groqFailure(429)).then(groqReply("x", finishReason = "length"))

        client.complete(llmRequest(label = "history")).getOrThrow()

        val event = events.calls.single()
        assertEquals(
            setOf("label", "provider", "outcome", "error_kind", "attempts", "latency_ms", "truncated", "failover"),
            event.keys
        )
        assertEquals("history", event["label"])
        assertEquals("GROQ", event["provider"])
        assertEquals("ok", event["outcome"])
        assertEquals("NONE", event["error_kind"])
        assertEquals(2, event["attempts"])
        assertEquals(true, event["truncated"])
        assertEquals(false, event["failover"])
        val latency = event["latency_ms"]
        assertTrue("latency_ms must be a Long, was ${latency?.javaClass}", latency is Long && latency >= 0)
    }

    @Test
    fun telemetryValuesAreOnlyTheTypesAnalyticsCanTake() = runTest {
        groq.then(groqFailure(400, DECOMMISSIONED_BODY))
        gemini.thenThrow(RuntimeException("The model is overloaded.")).thenThrow(RuntimeException("The model is overloaded.")).thenThrow(RuntimeException("The model is overloaded."))
        client.complete(llmRequest()).error()

        for ((name, params) in events.all) {
            for ((key, value) in params) {
                assertTrue("$name.$key has ${value.javaClass}", value is String || value is Int || value is Long || value is Boolean)
                // Firebase Analytics: names are 1..40 chars of [A-Za-z0-9_] starting with a letter.
                assertTrue("$name.$key is not a valid Analytics name", Regex("[A-Za-z][A-Za-z0-9_]{0,39}").matches(key))
                if (value is String) assertTrue("$name.$key value too long for Analytics", value.length <= 100)
            }
            assertTrue(Regex("[A-Za-z][A-Za-z0-9_]{0,39}").matches(name))
        }
    }

    @Test
    fun labelIsTheOnlyRequestFieldThatReachesTelemetry() = runTest {
        groq.then(groqReply("answer to a private question"))

        client.complete(llmRequest(user = "PRIVATE-USER-TEXT", system = "PRIVATE-SYSTEM-TEXT")).getOrThrow()

        val flattened = events.all.joinToString { it.toString() }
        assertFalse(flattened.contains("PRIVATE"))
        assertFalse(flattened.contains("private question"))
    }
}
