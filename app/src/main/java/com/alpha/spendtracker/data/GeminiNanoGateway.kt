package com.alpha.spendtracker.data

import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiNano"

/**
 * [LocalLlmGateway] over ML Kit's GenAI Prompt API, i.e. Gemini Nano running inside Android's AICore
 * service. There is no model file to ship or manage, the model never loads into this process (a
 * failure is an exception here, not a killed app), and a prompt never leaves the phone.
 *
 * ⚠️ What it is and is not:
 * - Only some devices have it (recent Pixel and flagship Samsung/Xiaomi/OnePlus…), and not with an
 *   unlocked bootloader. Everywhere else [isReady] is false and the cloud path is unchanged.
 * - The window is **under 4000 tokens, output included**, so a call passes a small prompt
 *   ([LocalUse.Compact]) and [generate] counts tokens exactly before sending.
 * - It is never asked to download: `DOWNLOADABLE` counts as not ready. A silent download of a
 *   model of that size over mobile data is not something to do unasked.
 * - No JSON mode: the prompt asks for JSON and the callers parse defensively, as they do for Gemini.
 * - AICore enforces a per-app inference quota and answers `BUSY` past it; any failure here sends the
 *   call to the cloud and [LlmClient] stops trying the model for a minute.
 */
@Singleton
class GeminiNanoGateway @Inject constructor() : LocalLlmGateway {

    // English runs near 4 characters a token; 3 leaves room for digit-heavy text, which tokenises
    // worse. Under half the 4000-token window, so the answer has room. Cheap pre-check only:
    // generate() counts tokens exactly.
    override val maxInputChars = 8_000

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /** One inference at a time: AICore queues them anyway, and a second would only hit its quota sooner. */
    private val inference = Mutex()
    private val statusLock = Mutex()

    @Volatile
    private var ready = false

    @Volatile
    private var checkedAt = 0L

    override suspend fun isReady(): Boolean {
        // The SDK needs API 26 (the app's minSdk is 24); it must not even be touched below that.
        if (Build.VERSION.SDK_INT < MIN_SDK) return false
        if (fresh()) return ready
        return statusLock.withLock {
            if (fresh()) return@withLock ready
            ready = try {
                val status = withTimeoutOrNull(STATUS_TIMEOUT_MS) { model.checkStatus() }
                Log.d(TAG, "Gemini Nano status=$status (available=${FeatureStatus.AVAILABLE})")
                status == FeatureStatus.AVAILABLE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Nano status check failed: ${e.message}")
                false
            }
            checkedAt = System.currentTimeMillis()
            ready
        }
    }

    private fun fresh(): Boolean =
        checkedAt != 0L && System.currentTimeMillis() - checkedAt < if (ready) READY_TTL_MS else NOT_READY_TTL_MS

    override suspend fun generate(system: String?, user: String, temperature: Double, maxTokens: Int): LocalReply =
        inference.withLock {
            if (!isReady()) throw LlmException("On-device model is not available", LlmException.Kind.UNAVAILABLE)
            try {
                run(system, user, temperature, maxTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmException) {
                throw e
            } catch (e: Exception) {
                // One class for every SDK/AICore failure. LlmClient's text-based guessing would read
                // "feature not found" as a bad request and skip the cool-down that stops every call
                // paying a failed round trip.
                throw LlmException("On-device model failed: ${e.message.orEmpty().take(200)}", LlmException.Kind.UNAVAILABLE, e)
            }
        }

    private suspend fun run(system: String?, user: String, temperature: Double, maxTokens: Int): LocalReply {
        val m = model
        // The system channel when this model has one, so the user's text cannot read as rules;
        // otherwise the rules lead the text, which is the best a plain prompt can do.
        val useSystem = system != null && m.isSystemPromptAvailable()
        val prompt = if (system == null || useSystem) user else "$system\n\n$user"
        val output = maxTokens.coerceAtMost(MAX_OUTPUT_TOKENS)

        val builder = if (useSystem) {
            GenerateContentRequest.Builder(SystemInstruction(system!!), TextPart(prompt))
        } else {
            GenerateContentRequest.Builder(TextPart(prompt))
        }
        val request = builder.apply {
            this.temperature = temperature.toFloat()
            this.maxOutputTokens = output
            this.candidateCount = 1
        }.build()

        val used = m.countTokens(request).totalTokens
        val limit = m.getTokenLimit()
        if (used + output > limit) {
            // About this prompt, not the model: LlmClient will not cool the model down for it.
            throw LlmException(
                "Prompt of $used tokens plus $output for the answer does not fit the on-device window of $limit",
                LlmException.Kind.BAD_REQUEST
            )
        }

        val response = withTimeoutOrNull(GENERATE_TIMEOUT_MS) { m.generateContent(request) }
            ?: throw LlmException("On-device model timed out", LlmException.Kind.UNAVAILABLE)
        val candidate = response.candidates.firstOrNull()
        return LocalReply(
            text = candidate?.text.orEmpty().trim(),
            truncated = candidate?.finishReason == Candidate.FinishReason.MAX_TOKENS
        )
    }

    private companion object {
        const val MIN_SDK = 26

        /** Longest answer asked for; the API advises against long output. */
        const val MAX_OUTPUT_TOKENS = 768

        /** A ready model stays ready for a while; one that is not (DOWNLOADABLE, unsupported) is rechecked rarely. */
        const val READY_TTL_MS = 60_000L
        const val NOT_READY_TTL_MS = 5 * 60_000L
        const val STATUS_TIMEOUT_MS = 3_000L

        /** A cold AICore start can take a few seconds; past this the cloud is quicker than waiting. */
        const val GENERATE_TIMEOUT_MS = 25_000L
    }
}
