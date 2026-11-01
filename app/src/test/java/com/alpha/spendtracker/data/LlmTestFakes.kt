package com.alpha.spendtracker.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Hand-written fakes for the [LlmClient] tests. The whole client is plain JVM, so none of them
 * touch Android; time is the test scheduler's, which is why the backoff `delay`s cost nothing.
 */

internal fun groqReply(
    text: String?,
    finishReason: String? = "stop"
): Response<GroqResponse> =
    Response.success(GroqResponse(listOf(GroqChoice(GroqMessage("assistant", text), finishReason))))

/** An error response built the way OkHttp would hand it to Retrofit, headers included. */
internal fun groqFailure(
    code: Int,
    body: String = """{"error":{"message":"boom","type":"error"}}""",
    vararg headers: Pair<String, String>
): Response<GroqResponse> {
    val raw = okhttp3.Response.Builder()
        .request(Request.Builder().url("https://api.groq.com/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("error")
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()
    return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
}

/** Replays a script of responses (or throwables) and records every request it was sent. */
internal class ScriptedGroq : GroqApiService {
    class Call(val authorization: String, val request: GroqRequest)

    val calls = mutableListOf<Call>()
    private val script = ArrayDeque<suspend () -> Response<GroqResponse>>()

    fun then(response: Response<GroqResponse>) = apply { script += { response } }
    fun thenThrow(t: Throwable) = apply { script += { throw t } }
    fun thenDo(block: suspend () -> Response<GroqResponse>) = apply { script += block }

    override suspend fun getCompletion(authorization: String, request: GroqRequest): Response<GroqResponse> {
        calls += Call(authorization, request)
        val next = script.removeFirstOrNull()
            ?: throw AssertionError("Unscripted Groq call #${calls.size}; the client made more calls than expected")
        return next()
    }
}

internal class ScriptedGemini : GeminiGateway {
    class Call(
        val model: String,
        val apiKey: String,
        val system: String?,
        val user: String,
        val json: Boolean,
        val temperature: Double?
    )

    val calls = mutableListOf<Call>()
    private val script = ArrayDeque<suspend () -> String?>()

    fun then(text: String?) = apply { script += { text } }
    fun thenThrow(t: Throwable) = apply { script += { throw t } }
    fun thenDo(block: suspend () -> String?) = apply { script += block }

    /** The client must always use the temperature-carrying overload. */
    override suspend fun generate(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean
    ): String? = throw AssertionError("LlmClient called the overload without a temperature")

    override suspend fun generate(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean,
        temperature: Double
    ): String? {
        calls += Call(model, apiKey, system, user, json, temperature)
        val next = script.removeFirstOrNull()
            ?: throw AssertionError("Unscripted Gemini call #${calls.size}")
        return next()
    }
}

/** An on-device model that replays a script and records what it was asked. */
internal class ScriptedLocal(
    var ready: Boolean = true,
    override val maxInputChars: Int = 8_000
) : LocalLlmGateway {
    class Call(val system: String?, val user: String, val temperature: Double, val maxTokens: Int)

    val calls = mutableListOf<Call>()
    var readyChecks = 0
    private val script = ArrayDeque<suspend () -> LocalReply>()

    fun then(text: String, truncated: Boolean = false) = apply { script += { LocalReply(text, truncated) } }
    fun thenThrow(t: Throwable) = apply { script += { throw t } }

    override suspend fun isReady(): Boolean {
        readyChecks++
        return ready
    }

    override suspend fun generate(system: String?, user: String, temperature: Double, maxTokens: Int): LocalReply {
        calls += Call(system, user, temperature, maxTokens)
        val next = script.removeFirstOrNull()
            ?: throw AssertionError("Unscripted on-device call #${calls.size}")
        return next()
    }
}

internal class StubLlmConfig(
    var groqKey: String = "groq-key",
    var geminiKey: String = "gemini-key",
    var fastModel: String = "openai/gpt-oss-20b",
    var smartModel: String = "openai/gpt-oss-120b",
    var geminiModel: String = "gemini-test",
    var localEnabled: Boolean = true
) : LlmConfig {
    var refreshes = 0

    override suspend fun refresh() {
        refreshes++
    }

    override fun groqKey() = groqKey
    override fun geminiKey() = geminiKey
    override fun groqModel(tier: LlmTier) = if (tier == LlmTier.FAST) fastModel else smartModel
    override fun geminiModel() = geminiModel
    override fun localEnabled() = localEnabled
}

internal class RecordingLlmEvents : LlmEvents {
    val all = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun log(name: String, params: Map<String, Any>) {
        all += name to params
    }

    /** `llm_call` events, one per [LlmClient.complete]. */
    val calls: List<Map<String, Any>> get() = all.filter { it.first == "llm_call" }.map { it.second }
    val downgrades: List<Map<String, Any>> get() = all.filter { it.first == "llm_schema_downgrade" }.map { it.second }
}

internal fun llmRequest(
    tier: LlmTier = LlmTier.FAST,
    json: LlmJson = LlmJson.None,
    system: String? = "You are a parser.",
    user: String = "tea 20",
    temperature: Double = 0.1,
    reasoningEffort: String = "low",
    maxTokens: Int = 512,
    label: String = "parse",
    local: LocalUse = LocalUse.Never
) = LlmRequest(
    label = label,
    tier = tier,
    user = user,
    system = system,
    temperature = temperature,
    reasoningEffort = reasoningEffort,
    maxTokens = maxTokens,
    json = json,
    local = local
)

internal const val DECOMMISSIONED_BODY =
    """{"error":{"message":"The model `llama-3.1-8b-instant` has been decommissioned and is no longer supported. Please refer to https://console.groq.com/docs/deprecations for a recommendation on which model to use instead.","type":"invalid_request_error","code":"model_decommissioned"}}"""

internal const val SCHEMA_UNSUPPORTED_BODY =
    """{"error":{"message":"This model does not support response format `json_schema`. See https://console.groq.com/docs/structured-outputs#supported-models for the list of supported models.","type":"invalid_request_error"}}"""
