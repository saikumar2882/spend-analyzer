package com.alpha.spendtracker.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") authorization: String,
        @Body request: GroqRequest
    ): Response<GroqResponse>
}

/**
 * Canonical Groq model ids, kept in one place so a decommissioning only has to be
 * fixed here.
 *
 * ⚠️ `llama-3.1-8b-instant` / `llama-3.3-70b-versatile` were **decommissioned on
 * 2026-08-16**; Groq's own migration target is the GPT-OSS pair, so that is what we
 * use. Both are reasoning models: pass [GroqRequest.include_reasoning] = false so the
 * chain-of-thought stays out of `message.content` (GPT-OSS does **not** accept
 * `reasoning_format`, only `include_reasoning` + `reasoning_effort`).
 */
object GroqModels {
    /** Cheap/fast worker — expense parsing, intent classification. */
    const val FAST = "openai/gpt-oss-20b"

    /** Larger model for history Q&A, where the answer quality matters. */
    const val SMART = "openai/gpt-oss-120b"
}

data class GroqRequest(
    val model: String = GroqModels.SMART,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.1,
    val response_format: GroqResponseFormat? = null,
    /** Groq's replacement for the deprecated `max_tokens`. */
    val max_completion_tokens: Int? = null,
    /** "low" | "medium" | "high" for the GPT-OSS models. */
    val reasoning_effort: String? = null,
    /** false keeps the reasoning trace out of the response entirely. */
    val include_reasoning: Boolean? = null
)

data class GroqMessage(
    val role: String,
    val content: String,
    /** Only ever populated on responses; omitted from requests (Moshi skips nulls). */
    val reasoning: String? = null
)

data class GroqResponseFormat(
    val type: String = "json_object"
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage,
    /** "stop" on a complete answer, "length" when max_completion_tokens cut it off. */
    val finish_reason: String? = null
)
