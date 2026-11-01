@file:OptIn(ExperimentalCoroutinesApi::class)

package com.alpha.spendtracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The on-device model's place in [LlmClient]: first, opt-in per call, never trusted to block the
 * cloud, and never the reason a call fails.
 */
class LlmClientLocalTest {

    private val groq = ScriptedGroq()
    private val gemini = ScriptedGemini()
    private val config = StubLlmConfig()
    private val events = RecordingLlmEvents()
    private val local = ScriptedLocal()
    private val client = LlmClient(groq, config, gemini, events, local)

    private fun Result<LlmReply>.error(): LlmException = exceptionOrNull() as LlmException

    @Test
    fun anOnDeviceAnswerNeedsNoKeyNoFetchAndNoCloudCall() = runTest {
        local.then("""{"amount":20}""")

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals("""{"amount":20}""", reply.text)
        assertEquals(LlmProvider.LOCAL, reply.provider)
        assertEquals(1, reply.attempts)
        assertFalse(reply.truncated)
        assertTrue(groq.calls.isEmpty())
        assertTrue(gemini.calls.isEmpty())
        // The Remote Config fetch is what an on-device answer must not wait on.
        assertEquals(0, config.refreshes)
        val event = events.calls.single()
        assertEquals("LOCAL", event["provider"])
        assertEquals(true, event["local_tried"])
        assertFalse(event.containsKey("local_error_kind"))
    }

    @Test
    fun aCallThatDidNotOptInNeverTouchesTheOnDeviceModel() = runTest {
        groq.then(groqReply("cloud"))

        val reply = client.complete(llmRequest()).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertTrue(local.calls.isEmpty())
        assertEquals("not even asked whether it is ready", 0, local.readyChecks)
        assertFalse(events.calls.single().containsKey("local_tried"))
    }

    @Test
    fun anOnDeviceModelThatIsNotInstalledIsSkippedSilently() = runTest {
        local.ready = false
        groq.then(groqReply("cloud"))

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertTrue(local.calls.isEmpty())
        // Nothing was tried, so nothing is reported as a local failure.
        assertFalse(events.calls.single().containsKey("local_tried"))
    }

    @Test
    fun theRemoteKillSwitchTurnsTheOnDeviceModelOff() = runTest {
        config.localEnabled = false
        groq.then(groqReply("cloud"))

        assertEquals(LlmProvider.GROQ, client.complete(llmRequest(local = LocalUse.Same)).getOrThrow().provider)
        assertTrue(local.calls.isEmpty())
        assertFalse(client.canUseLocal())
    }

    @Test
    fun aPromptThatCannotFitSkipsTheOnDeviceModelWithoutTryingIt() = runTest {
        val small = ScriptedLocal(maxInputChars = 20)
        val c = LlmClient(groq, config, gemini, events, small)
        groq.then(groqReply("cloud"))

        val reply = c.complete(llmRequest(system = "s".repeat(15), user = "u".repeat(10), local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertTrue(small.calls.isEmpty())
    }

    @Test
    fun theCompactFormGoesToThePhoneAndTheFullPromptToTheCloud() = runTest {
        local.thenThrow(LlmException("busy", LlmException.Kind.UNAVAILABLE))
        groq.then(groqReply("cloud"))

        client.complete(
            llmRequest(system = "FULL SYSTEM", user = "FULL USER", local = LocalUse.Compact("small system", "small user"))
        ).getOrThrow()

        val asked = local.calls.single()
        assertEquals("small system", asked.system)
        assertEquals("small user", asked.user)
        val sent = groq.calls.single().request.messages.map { it.role to it.content }
        assertEquals(listOf("system" to "FULL SYSTEM", "user" to "FULL USER"), sent)
    }

    @Test
    fun theRequestsSamplingAndBudgetReachTheOnDeviceModel() = runTest {
        local.then("ok")

        client.complete(llmRequest(temperature = 0.2, maxTokens = 321, local = LocalUse.Same)).getOrThrow()

        val asked = local.calls.single()
        assertEquals(0.2, asked.temperature, 0.0)
        assertEquals(321, asked.maxTokens)
    }

    @Test
    fun aFailureFallsThroughToTheCloudAndIsReported() = runTest {
        local.thenThrow(LlmException("AICore busy", LlmException.Kind.UNAVAILABLE))
        groq.then(groqReply("cloud"))

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertEquals("one on-device try plus one Groq call", 2, reply.attempts)
        val event = events.calls.single()
        assertEquals(true, event["local_tried"])
        assertEquals("UNAVAILABLE", event["local_error_kind"])
        assertEquals("a cloud answer after a local failure is not a Groq→Gemini failover", false, event["failover"])
    }

    @Test
    fun aFailedOnDeviceModelIsLeftAloneForAWhile() = runTest {
        local.thenThrow(LlmException("quota", LlmException.Kind.UNAVAILABLE))
        groq.then(groqReply("one")).then(groqReply("two"))

        client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()
        assertFalse(client.canUseLocal())
        client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals("the second call did not pay another failed round trip", 1, local.calls.size)
    }

    @Test
    fun aPromptThatDidNotFitDoesNotSwitchTheModelOff() = runTest {
        local.thenThrow(LlmException("does not fit", LlmException.Kind.BAD_REQUEST)).then("fine")
        groq.then(groqReply("cloud"))

        assertEquals(LlmProvider.GROQ, client.complete(llmRequest(local = LocalUse.Same)).getOrThrow().provider)
        assertTrue("BAD_REQUEST is about that prompt, not the model", client.canUseLocal())
        assertEquals(LlmProvider.LOCAL, client.complete(llmRequest(local = LocalUse.Same)).getOrThrow().provider)
    }

    @Test
    fun aRawExceptionIsTheModelBeingUnavailableNotABadRequest() = runTest {
        // "not found" would read as a BAD_REQUEST to the text classifier used for cloud errors.
        local.thenThrow(IllegalStateException("AICore: feature not found"))
        groq.then(groqReply("cloud"))

        client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals("UNAVAILABLE", events.calls.single()["local_error_kind"])
        assertFalse("and it cools the model down", client.canUseLocal())
    }

    @Test
    fun aBlankAnswerCountsAsAFailure() = runTest {
        local.then("   ")
        groq.then(groqReply("cloud"))

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertEquals("EMPTY", events.calls.single()["local_error_kind"])
    }

    @Test
    fun aCutOffOnDeviceAnswerYieldsToTheCloud() = runTest {
        local.then("""{"transactions":[{"amount":""", truncated = true)
        groq.then(groqReply("""{"complete":true}"""))

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.GROQ, reply.provider)
        assertEquals("""{"complete":true}""", reply.text)
    }

    @Test
    fun aCutOffOnDeviceAnswerIsBetterThanNothing() = runTest {
        local.then("The biggest was", truncated = true)
        groq.then(groqFailure(500)).then(groqFailure(500)).then(groqFailure(500))
        gemini.thenThrow(RuntimeException("503 unavailable")).thenThrow(RuntimeException("503 unavailable")).thenThrow(RuntimeException("503 unavailable"))

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.LOCAL, reply.provider)
        assertEquals("The biggest was", reply.text)
        assertTrue("the caller must be able to tell it is cut off", reply.truncated)
    }

    @Test
    fun aCutOffOnDeviceAnswerIsKeptWhenThereAreNoCloudKeys() = runTest {
        config.groqKey = ""
        config.geminiKey = ""
        local.then("The biggest was", truncated = true)

        val reply = client.complete(llmRequest(local = LocalUse.Same)).getOrThrow()

        assertEquals(LlmProvider.LOCAL, reply.provider)
        assertTrue(reply.truncated)
    }

    @Test
    fun aFailedOnDeviceModelWithNoCloudKeysIsStillANoKeyError() = runTest {
        config.groqKey = ""
        config.geminiKey = ""
        local.thenThrow(LlmException("busy", LlmException.Kind.UNAVAILABLE))

        val result = client.complete(llmRequest(local = LocalUse.Same))

        assertEquals(LlmException.Kind.NO_KEY, result.error().kind)
        assertEquals("UNAVAILABLE", events.calls.single()["local_error_kind"])
    }

    @Test
    fun cancellationOutOfTheOnDeviceModelStopsTheCall() = runTest {
        local.thenThrow(CancellationException("cancelled"))

        try {
            client.complete(llmRequest(local = LocalUse.Same))
            fail("a cancelled call must not fall through to the cloud")
        } catch (e: CancellationException) {
            // expected
        }
        assertTrue("the cloud must not be called for a cancelled request", groq.calls.isEmpty())
        assertNull(events.calls.firstOrNull())
    }

    @Test
    fun theDefaultClientHasNoOnDeviceModel() = runTest {
        val cloudOnly = LlmClient(groq, config, gemini, events)
        groq.then(groqReply("cloud"))

        assertFalse(cloudOnly.canUseLocal())
        assertEquals(LlmProvider.GROQ, cloudOnly.complete(llmRequest(local = LocalUse.Same)).getOrThrow().provider)
    }
}
