package com.alpha.spendtracker.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Which Groq model a call wants; the concrete id comes from [LlmConfig]. */
enum class LlmTier { FAST, SMART }

enum class LlmProvider { LOCAL, GROQ, GEMINI }

/** Whether a call may be answered by the on-device model, and with which prompt. */
sealed interface LocalUse {
    /** Cloud only. The default: a call site opts in once it can tell a bad on-device answer from a good one. */
    object Never : LocalUse

    /** Try the on-device model with the request's own prompt. */
    object Same : LocalUse

    /**
     * Try it with a smaller prompt. The on-device window is a fraction of the cloud's, so a call
     * whose full prompt cannot fit passes a trimmed one; the cloud still gets the full one.
     */
    class Compact(val system: String?, val user: String) : LocalUse
}

/** What shape the reply must have. */
sealed interface LlmJson {
    /** Free text. */
    object None : LlmJson

    /** Any syntactically valid JSON object (`response_format = json_object`). */
    object JsonObject : LlmJson

    /**
     * JSON constrained to [schema] (`response_format = json_schema`, strict). Not every model
     * accepts it, so [LlmClient] downgrades to [JsonObject] when Groq rejects the schema; callers
     * must still parse defensively.
     */
    class Schema(val name: String, val schema: Map<String, Any>) : LlmJson
}

data class LlmRequest(
    /** Short telemetry label: "parse", "classify", "history". Never user text. */
    val label: String,
    val tier: LlmTier,
    val user: String,
    val system: String? = null,
    val temperature: Double = 0.1,
    /**
     * "low" | "medium" | "high". It draws from the same budget as [maxTokens], so a tight
     * ceiling truncates the answer (see the notes at the call sites).
     */
    val reasoningEffort: String = "low",
    val maxTokens: Int,
    val json: LlmJson = LlmJson.None,
    val local: LocalUse = LocalUse.Never
)

data class LlmReply(
    val text: String,
    val provider: LlmProvider,
    /** The provider stopped because it hit [LlmRequest.maxTokens]; [text] may be cut off. */
    val truncated: Boolean,
    /** Total attempts across providers, 1 when the first call worked. */
    val attempts: Int
)

class LlmException(
    message: String,
    val kind: Kind,
    cause: Throwable? = null,
    /** How long the provider asked us to wait (`Retry-After`), when it said. */
    val retryAfterMs: Long? = null
) : Exception(message, cause) {
    enum class Kind {
        /** Neither provider has a key in Remote Config. */
        NO_KEY,

        /** HTTP 429 / quota. Transient, worth a retry. */
        RATE_LIMIT,

        /** 5xx / overloaded. Transient, worth a retry. */
        UNAVAILABLE,

        /** No connection, DNS failure, timeout. Transient, worth a retry. */
        NETWORK,

        /** 401/403 or an invalid key. A retry cannot help. */
        AUTH,

        /** 400/404/422: a bad model id (e.g. `model_decommissioned`) or malformed request. */
        BAD_REQUEST,

        /** The provider answered with nothing usable. */
        EMPTY,

        OTHER
    }

    val isTransient: Boolean get() = kind == Kind.RATE_LIMIT || kind == Kind.UNAVAILABLE || kind == Kind.NETWORK
}

/** Keys and model ids. Backed by Firebase Remote Config so a model sunset is a config push. */
interface LlmConfig {
    /** Pulls fresh values if they are stale. Never throws (other than cancellation). */
    suspend fun refresh()
    fun groqKey(): String
    fun geminiKey(): String
    fun groqModel(tier: LlmTier): String
    fun geminiModel(): String

    /**
     * Remote kill switch for the on-device model (a beta SDK that talks to a system service, so
     * "turn it off for everyone" should not need a release). Read from the cached values, not a
     * fetch, so an on-device answer never waits on the network.
     */
    fun localEnabled(): Boolean = true
}

/** The Gemini SDK behind an interface so [LlmClient] stays testable on the plain JVM. */
interface GeminiGateway {
    suspend fun generate(model: String, apiKey: String, system: String?, user: String, json: Boolean): String?

    /**
     * Same call with the request's sampling [temperature]. The default hands over to the five
     * argument form, so an implementation (or test fake) written against that one still works;
     * [LlmClient] always calls this one so Gemini is as deterministic as the Groq call it replaces.
     */
    suspend fun generate(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean,
        temperature: Double
    ): String? = generate(model, apiKey, system, user, json)
}

/** What the on-device model produced. */
class LocalReply(val text: String, val truncated: Boolean)

/**
 * An on-device model (Gemini Nano through AICore) behind an interface so [LlmClient] stays testable
 * on the plain JVM. Implementations throw [LlmException] for what they understand
 * (`UNAVAILABLE`: not installed, busy or out of quota; `BAD_REQUEST`: the prompt does not fit);
 * anything else is classified by [LlmClient].
 */
interface LocalLlmGateway {
    /**
     * The most `system + user` text, in characters, the model's window can take. A larger call
     * skips the model without trying. It is a cheap pre-check; the implementation still counts
     * tokens exactly.
     */
    val maxInputChars: Int

    /** True when a model is installed and ready right now. Cached, cheap, never throws. */
    suspend fun isReady(): Boolean

    suspend fun generate(system: String?, user: String, temperature: Double, maxTokens: Int): LocalReply
}

/** No on-device model. The default, so a client built without one is cloud-only. */
object NoLocalLlm : LocalLlmGateway {
    override val maxInputChars = 0
    override suspend fun isReady() = false
    override suspend fun generate(system: String?, user: String, temperature: Double, maxTokens: Int): LocalReply =
        throw LlmException("No on-device model", LlmException.Kind.UNAVAILABLE)
}

/** Telemetry sink. [params] values are String / Int / Long / Boolean only. */
fun interface LlmEvents {
    fun log(name: String, params: Map<String, Any>)
}

/** Fallback model ids and the Remote Config key names. */
object LlmModels {
    const val GEMINI_DEFAULT = "gemini-3.5-flash"
    const val RC_GROQ_FAST = "groq_model_fast"
    const val RC_GROQ_SMART = "groq_model_smart"
    const val RC_GEMINI = "gemini_model"

    /** Phrased as "disabled" so an unset or not-yet-fetched value leaves the on-device model on. */
    const val RC_LOCAL_DISABLED = "local_llm_disabled"
}

/**
 * The one place that talks to an LLM: key and model lookup, retry with backoff, Groq→Gemini
 * failover and telemetry. Before this existed, four call sites each re-implemented the Remote
 * Config fetch and a fixed 2s retry, and Gemini only ran when the Groq key was *blank* — a Groq
 * outage or 429 degraded straight to the regex baseline even with a working Gemini key.
 *
 * Order: the on-device model first, for a call that opted in ([LlmRequest.local]) and when a model
 * is installed and the prompt fits — one try, no retries, before the Remote Config fetch so an
 * on-device answer costs no network, no key and no quota. Then Groq (if keyed) with up to
 * [MAX_ATTEMPTS] tries on transient errors, then Gemini (if keyed) the same way. A non-transient
 * Groq error (bad key, decommissioned model) skips its remaining retries and moves on at once, and
 * so does a 429 whose `Retry-After` is longer than a user will wait ([MAX_RETRY_AFTER_MS]); a
 * shorter one is waited out in place of the backoff.
 *
 * An on-device answer that was cut off at the token ceiling is not served while a cloud answer is
 * possible (a JSON object with no closing brace is unusable, and a reply that stops mid-line reads
 * as a bug); it is kept and returned only when nothing else answers.
 *
 * ⚠️ This class is deliberately free of `android.util.Log`: logging goes through [LlmEvents] so
 * the whole client runs in plain JVM unit tests.
 */
@Singleton
class LlmClient @Inject constructor(
    private val groq: GroqApiService,
    private val config: LlmConfig,
    private val gemini: GeminiGateway,
    private val events: LlmEvents,
    private val local: LocalLlmGateway = NoLocalLlm
) {

    companion object {
        const val MAX_ATTEMPTS = 3

        /**
         * After the on-device model fails (busy, out of AICore's per-app quota, failed to bind) it
         * is skipped for this long, so each call does not pay a failed round trip before the cloud.
         */
        private const val LOCAL_COOLDOWN_MS = 60_000L
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_JITTER_MS = 250L

        /**
         * Longest `Retry-After` we will sit out. Anything longer (a per-day token cap says
         * "come back in 30 minutes") will not clear inside a user's tap, so the remaining retries
         * are skipped and the next provider gets the call straight away.
         */
        private const val MAX_RETRY_AFTER_MS = 5_000L

        /**
         * What a 400 must mention to count as "this model/schema is not supported" (matched on
         * Groq's `error.message`, never on `failed_generation`; see [schemaProblem]).
         */
        private val SCHEMA_REJECTION_HINTS =
            listOf("json_schema", "json schema", "response_format", "response format", "structured output")

        /** Moshi's built-in map reader: no Kotlin reflection needed to peek into an error body. */
        private val errorBodyAdapter by lazy {
            Moshi.Builder().build().adapter<Map<String, Any?>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )
        }
    }

    /** Models that rejected `json_schema`; later calls skip straight to `json_object`. */
    private val schemaUnsupported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Wall-clock millis before which the on-device model is not tried again (see [LOCAL_COOLDOWN_MS]). */
    @Volatile
    private var localCooldownUntil = 0L

    /**
     * Whether an on-device model could take a call right now. Call sites use it to decide whether
     * building a smaller prompt for [LocalUse.Compact] is worth the work.
     */
    suspend fun canUseLocal(): Boolean {
        if (!config.localEnabled() || System.currentTimeMillis() < localCooldownUntil) return false
        return try {
            local.isReady()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun complete(request: LlmRequest): Result<LlmReply> {
        val startedAt = System.nanoTime()

        // On-device first. Before config.refresh(): an answer from the phone needs no key, and
        // must not wait on (or be blocked by) the network fetch.
        val onDevice = callLocal(request)
        onDevice?.reply?.let { return success(request, it, 1, startedAt, fellBack = false, local = onDevice) }

        config.refresh()

        // Trimmed: a key pasted into the Remote Config console with a trailing newline makes OkHttp
        // throw on the Authorization header, which looked like a provider failure.
        val groqKey = config.groqKey().trim()
        val geminiKey = config.geminiKey().trim()
        if (groqKey.isBlank() && geminiKey.isBlank()) {
            onDevice?.partial?.let { return success(request, it, 1, startedAt, fellBack = false, local = onDevice) }
            val e = LlmException("AI API key is missing in Remote Config", LlmException.Kind.NO_KEY)
            report(request, null, e, attempts = onDevice?.let { 1 } ?: 0, startedAt = startedAt, fellBack = false, local = onDevice)
            return Result.failure(e)
        }

        var attempts = if (onDevice != null) 1 else 0
        var lastError: LlmException? = null
        var groqFailed = false
        var geminiError: LlmException? = null

        if (groqKey.isNotBlank()) {
            val outcome = withRetries(request, LlmProvider.GROQ) { callGroq(request, groqKey) }
            attempts += outcome.attempts
            outcome.reply?.let { return success(request, it, attempts, startedAt, fellBack = false, local = onDevice) }
            lastError = outcome.error
            groqFailed = true
        }

        if (geminiKey.isNotBlank()) {
            val outcome = withRetries(request, LlmProvider.GEMINI) { callGemini(request, geminiKey) }
            attempts += outcome.attempts
            outcome.reply?.let { return success(request, it, attempts, startedAt, fellBack = groqFailed, local = onDevice) }
            geminiError = outcome.error
            // Keep Groq's error when it is the more informative one (e.g. a decommissioned model).
            lastError = lastError ?: outcome.error
        }

        // The cloud had its chance; a cut-off on-device answer is better than none.
        onDevice?.partial?.let { return success(request, it, attempts, startedAt, fellBack = false, local = onDevice) }

        val error = lastError ?: LlmException("No AI provider answered", LlmException.Kind.OTHER)
        // "Failover" means Gemini really was tried after Groq failed; a keyless Gemini is not one.
        report(
            request, null, error, attempts, startedAt,
            fellBack = groqFailed && geminiKey.isNotBlank(),
            // `error` is Groq's when both failed, so a Gemini-side config failure would vanish.
            secondaryError = if (groqFailed) geminiError else null,
            local = onDevice
        )
        return Result.failure(error)
    }

    /** What the on-device attempt came to: an [reply], or an [error] (with the cut-off text as [partial]). */
    private class LocalOutcome(val reply: LlmReply?, val error: LlmException?, val partial: LlmReply?)

    /**
     * The on-device attempt, or null when it was not eligible (the call did not opt in, the kill
     * switch is off, the model is cooling down or not installed, or the prompt cannot fit) — in
     * which case nothing was tried and nothing is reported.
     */
    private suspend fun callLocal(request: LlmRequest): LocalOutcome? {
        val (system, user) = when (val use = request.local) {
            LocalUse.Never -> return null
            LocalUse.Same -> request.system to request.user
            is LocalUse.Compact -> use.system to use.user
        }
        if ((system?.length ?: 0) + user.length > local.maxInputChars) return null
        if (!canUseLocal()) return null
        try {
            val out = local.generate(system, user, request.temperature, request.maxTokens)
            if (out.text.isBlank()) throw LlmException("On-device model returned an empty answer", LlmException.Kind.EMPTY)
            val reply = LlmReply(out.text, LlmProvider.LOCAL, truncated = out.truncated, attempts = 1)
            if (!out.truncated) return LocalOutcome(reply, null, null)
            return LocalOutcome(null, LlmException("On-device answer was cut off", LlmException.Kind.EMPTY), partial = reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            // Only an LlmException the gateway threw on purpose says what went wrong. Anything else
            // is the model being unavailable: the text classifier would read "feature not found" as
            // a bad request and skip the cool-down below.
            val classified = e as? LlmException
                ?: LlmException("On-device model failed: ${e.message.orEmpty().take(200)}", LlmException.Kind.UNAVAILABLE, e)
            // BAD_REQUEST is about this prompt (it did not fit once tokens were counted exactly),
            // not about the model, so it must not switch the model off for the next call.
            if (classified.kind != LlmException.Kind.BAD_REQUEST) {
                localCooldownUntil = System.currentTimeMillis() + LOCAL_COOLDOWN_MS
            }
            return LocalOutcome(null, classified, null)
        }
    }

    private class Outcome(val reply: LlmReply?, val error: LlmException?, val attempts: Int)

    private suspend fun withRetries(
        request: LlmRequest,
        provider: LlmProvider,
        call: suspend () -> LlmReply
    ): Outcome {
        var last: LlmException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return Outcome(call().copy(attempts = attempt), null, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The Gemini SDK wraps every Throwable it catches, including the
                // CancellationException of a cancelled caller, in its own RuntimeException. Without
                // this a cancelled call would be scored as a provider failure and fall through to
                // the next provider instead of stopping.
                currentCoroutineContext().ensureActive()
                val classified = classify(e, provider)
                last = classified
                if (!classified.isTransient || attempt == MAX_ATTEMPTS) {
                    return Outcome(null, classified, attempt)
                }
                val hinted = classified.retryAfterMs
                if (hinted != null && hinted > MAX_RETRY_AFTER_MS) {
                    return Outcome(null, classified, attempt)
                }
                // Exponential backoff with jitter: 1s, 2s (+ up to 250ms). 429s are per-minute
                // limits, so a short wait often clears them; when the server names a wait, never
                // come back sooner than that.
                val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1)) + Random.nextLong(MAX_JITTER_MS + 1)
                delay(maxOf(backoff, hinted ?: 0L))
            }
        }
        return Outcome(null, last, MAX_ATTEMPTS)
    }

    private suspend fun callGroq(request: LlmRequest, key: String): LlmReply {
        val model = config.groqModel(request.tier)
        var json = request.json
        if (json is LlmJson.Schema && model in schemaUnsupported) json = LlmJson.JsonObject

        var response = groq.getCompletion("Bearer $key", body(request, model, json))

        // A model that cannot do strict schemas answers 400. Retry the same request as plain JSON
        // mode instead of failing the user's call; the caller parses defensively either way.
        if (!response.isSuccessful && json is LlmJson.Schema && response.code() == 400) {
            val errorBody = response.errorBody()?.string().orEmpty()
            val problem = schemaProblem(errorBody)
            if (problem == SchemaProblem.NONE) throw groqError(400, errorBody, null)
            // Only "this model can't do schemas" is remembered. A one-off failed generation says
            // nothing about the model, and remembering it would switch strict mode off for every
            // later call in this process.
            val remember = problem == SchemaProblem.UNSUPPORTED
            if (remember) schemaUnsupported += model
            logEvent(
                "llm_schema_downgrade",
                mapOf("model" to model.take(60), "remembered" to remember)
            )
            response = groq.getCompletion("Bearer $key", body(request, model, LlmJson.JsonObject))
        }

        if (!response.isSuccessful) {
            // The body is the only place a decommissioned model announces itself.
            throw groqError(response.code(), response.errorBody()?.string().orEmpty(), retryAfterMs(response))
        }
        val choice = response.body()?.choices?.firstOrNull()
        val text = choice?.message?.content.orEmpty()
        if (text.isBlank()) throw LlmException("Groq returned an empty answer", LlmException.Kind.EMPTY)
        return LlmReply(text, LlmProvider.GROQ, truncated = choice?.finish_reason == "length", attempts = 1)
    }

    private suspend fun callGemini(request: LlmRequest, key: String): LlmReply {
        val text = gemini.generate(
            model = config.geminiModel(),
            apiKey = key,
            system = request.system,
            user = request.user,
            json = request.json !is LlmJson.None,
            temperature = request.temperature
        ).orEmpty()
        if (text.isBlank()) throw LlmException("Gemini returned an empty answer", LlmException.Kind.EMPTY)
        return LlmReply(text, LlmProvider.GEMINI, truncated = false, attempts = 1)
    }

    private fun body(request: LlmRequest, model: String, json: LlmJson): GroqRequest {
        // `reasoning_effort` / `include_reasoning` are GPT-OSS parameters; another model id set
        // through Remote Config may reject them.
        val reasoning = "gpt-oss" in model
        return GroqRequest(
            model = model,
            messages = listOfNotNull(
                request.system?.let { GroqMessage("system", it) },
                GroqMessage("user", request.user)
            ),
            temperature = request.temperature,
            response_format = when (json) {
                LlmJson.None -> null
                LlmJson.JsonObject -> GroqResponseFormat()
                is LlmJson.Schema -> GroqResponseFormat(
                    type = "json_schema",
                    json_schema = GroqJsonSchema(json.name, json.schema)
                )
            },
            max_completion_tokens = request.maxTokens,
            reasoning_effort = if (reasoning) request.reasoningEffort else null,
            include_reasoning = if (reasoning) false else null
        )
    }

    private enum class SchemaProblem {
        /** Not about schemas at all (bad key, decommissioned model, oversized prompt...). */
        NONE,

        /** The model or the schema itself is not accepted in `json_schema` mode. */
        UNSUPPORTED,

        /** Valid request, but this one generation did not come out as JSON. */
        FAILED_GENERATION
    }

    /**
     * Reads a Groq 400 body. ⚠️ Matches on `error.code` / `error.message` / `error.param` only:
     * a `json_validate_failed` body also carries `failed_generation`, the model's raw output,
     * which echoes the user's own words, so a note containing "schema" used to be enough to turn
     * strict mode off.
     */
    private fun schemaProblem(body: String): SchemaProblem {
        val error = try {
            errorBodyAdapter.fromJson(body)?.get("error") as? Map<*, *>
        } catch (e: Exception) {
            null // not JSON (a proxy's HTML page, say): fall back to the raw text below
        }
        val code = (error?.get("code") as? String).orEmpty()
        if (code == "json_validate_failed") return SchemaProblem.FAILED_GENERATION
        val text = if (error != null) {
            listOf("message", "param", "code").joinToString(" ") { error[it] as? String ?: "" }
        } else {
            body
        }.lowercase()
        return if (SCHEMA_REJECTION_HINTS.any { it in text }) SchemaProblem.UNSUPPORTED else SchemaProblem.NONE
    }

    /** `Retry-After` in seconds (Groq's form); an HTTP-date or garbage is treated as absent. */
    private fun retryAfterMs(response: Response<*>): Long? {
        val seconds = response.headers()["retry-after"]?.trim()?.toDoubleOrNull() ?: return null
        if (!seconds.isFinite() || seconds < 0) return null
        return (seconds * 1_000).toLong()
    }

    private fun groqError(code: Int, body: String, retryAfterMs: Long?): LlmException {
        val kind = when {
            code == 429 -> LlmException.Kind.RATE_LIMIT
            // Any 5xx: Groq sits behind Cloudflare, whose 52x family is just as transient as 503.
            code == 408 || code in 500..599 -> LlmException.Kind.UNAVAILABLE
            code == 401 || code == 403 -> LlmException.Kind.AUTH
            code == 400 || code == 404 || code == 422 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.OTHER
        }
        // Same wording the callers' string matching has always seen ("Groq API error: 429 ...").
        return LlmException("Groq API error: $code ${body.take(300)}", kind, retryAfterMs = retryAfterMs)
    }

    /** True when [msg] contains the HTTP status [code] as a number of its own, not inside "4290". */
    private fun mentionsStatus(msg: String, vararg codes: Int): Boolean =
        codes.any { Regex("(?<!\\d)$it(?!\\d)").containsMatchIn(msg) }

    /** Maps whatever a provider call threw (Gemini SDK exceptions are untyped) to a kind. */
    private fun classify(e: Exception, provider: LlmProvider): LlmException {
        if (e is LlmException) return e
        val msg = e.message.orEmpty()
        val lower = msg.lowercase()
        // The Gemini SDK reports a dropped connection as UnknownException("Something unexpected
        // happened.") with the IOException only as the cause, so the whole chain has to be looked at.
        val ioFailure = generateSequence<Throwable>(e) { it.cause }.take(6).any { it is IOException }
        val kind = when {
            ioFailure || "timed out" in lower || "timeout" in lower || "allotted time" in lower ->
                LlmException.Kind.NETWORK
            mentionsStatus(msg, 429) || "quota" in lower || "rate limit" in lower ||
                "resource has been exhausted" in lower -> LlmException.Kind.RATE_LIMIT
            mentionsStatus(msg, 500, 503, 504) || "high demand" in lower || "unavailable" in lower ||
                "overloaded" in lower || "internal error" in lower || "deadline" in lower ->
                LlmException.Kind.UNAVAILABLE
            "api key" in lower || mentionsStatus(msg, 401, 403) || "permission denied" in lower ||
                "unauthenticated" in lower -> LlmException.Kind.AUTH
            // A model id pushed through Remote Config that Gemini does not know ("models/x is not
            // found for API version v1beta"), the same class of failure as Groq's decommissioning.
            mentionsStatus(msg, 404) || "not found" in lower || "invalid argument" in lower ->
                LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.OTHER
        }
        return LlmException("${provider.name.lowercase().replaceFirstChar { it.uppercase() }} error: $msg", kind, e)
    }

    private fun success(
        request: LlmRequest,
        reply: LlmReply,
        attempts: Int,
        startedAt: Long,
        fellBack: Boolean,
        local: LocalOutcome? = null
    ): Result<LlmReply> {
        val total = reply.copy(attempts = attempts)
        report(request, total, null, attempts, startedAt, fellBack, local = local)
        return Result.success(total)
    }

    private fun report(
        request: LlmRequest,
        reply: LlmReply?,
        error: LlmException?,
        attempts: Int,
        startedAt: Long,
        fellBack: Boolean,
        secondaryError: LlmException? = null,
        local: LocalOutcome? = null
    ) {
        val params = mutableMapOf<String, Any>(
            "label" to request.label,
            "provider" to (reply?.provider?.name ?: "NONE"),
            "outcome" to if (reply != null) "ok" else "error",
            "error_kind" to (error?.kind?.name ?: "NONE"),
            "attempts" to attempts,
            // Monotonic: wall-clock time jumps when the network sets the phone's clock.
            "latency_ms" to (System.nanoTime() - startedAt) / 1_000_000,
            "truncated" to (reply?.truncated ?: false),
            "failover" to fellBack
        )
        secondaryError?.let { params["fallback_error_kind"] = it.kind.name }
        // Present only when the on-device model was actually tried, so its failure rate (and how
        // often the cloud had to step in) is visible without a second event.
        if (local != null) {
            params["local_tried"] = true
            local.error?.let { params["local_error_kind"] = it.kind.name }
        }
        logEvent("llm_call", params)
    }

    /** Telemetry must never cost the user an answer the provider already gave. */
    private fun logEvent(name: String, params: Map<String, Any>) {
        try {
            events.log(name, params)
        } catch (e: Exception) {
            // Dropped on purpose; there is nowhere safer to report that the reporter is broken.
        }
    }
}
