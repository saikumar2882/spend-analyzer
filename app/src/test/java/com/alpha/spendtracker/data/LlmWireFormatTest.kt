package com.alpha.spendtracker.data

import com.alpha.spendtracker.di.AppModule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real request path: [LlmClient] → the Retrofit service and Moshi exactly as [AppModule]
 * builds them → an interceptor that stands in for the network and records the bytes. A body Groq
 * rejects with a 400 is indistinguishable from "this model has no schema mode" and would silently
 * trigger the downgrade, so the shape of the JSON is worth pinning down.
 */
class LlmWireFormatTest {

    private class Sent(val url: String, val method: String, val authorization: String?, val contentType: String?, val body: String)

    private val sent = mutableListOf<Sent>()
    private val responses = ArrayDeque<Triple<Int, String, Map<String, String>>>()

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            sent += Sent(request.url.toString(), request.method, request.header("Authorization"),
                request.body?.contentType()?.toString(), buffer.readUtf8())
            val (code, body, headers) = responses.removeFirstOrNull()
                ?: throw AssertionError("Unscripted HTTP call #${sent.size}")
            okhttp3.Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(code).message("stub")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build()

    // The very Moshi and Retrofit AppModule hands to Hilt.
    private val moshi: Moshi = AppModule.provideMoshi()
    private val service = AppModule.provideGroqApiService(okHttp, moshi)
    private val events = RecordingLlmEvents()
    private val config = StubLlmConfig(geminiKey = "")
    private val client = LlmClient(service, config, ScriptedGemini(), events)

    private fun respond(code: Int, body: String, vararg headers: Pair<String, String>) {
        responses += Triple(code, body, headers.toMap())
    }

    private fun parse(json: String): Map<String, Any?> =
        @Suppress("UNCHECKED_CAST")
        (moshi.adapter(Any::class.java).fromJson(json) as Map<String, Any?>)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String) = this[key] as Map<String, Any?>

    private val okResponse = """{"choices":[{"index":0,"message":{"role":"assistant","content":"{}"},"finish_reason":"stop"}]}"""

    // Lists, booleans, nested maps, enums, a nullable-number type array, integers and doubles,
    // plus text that needs escaping (quotes, a newline) and a non-ASCII rupee sign.
    private val schemaMap: Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("transactions"),
        "properties" to mapOf(
            "transactions" to mapOf(
                "type" to "array",
                "minItems" to 0,
                "maxItems" to 10,
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("amount", "purpose"),
                    "properties" to mapOf(
                        "amount" to mapOf(
                            "type" to listOf("number", "null"),
                            "description" to "Amount in ₹, e.g. \"1,500\"\nor null"
                        ),
                        "purpose" to mapOf("type" to "string", "enum" to listOf("Food", "Rent", "Lending")),
                        "confidence" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 1.0),
                        "recurring" to mapOf("type" to "boolean", "default" to false)
                    )
                )
            )
        )
    )

    // The same schema written out by hand, so the test is not just Moshi agreeing with itself.
    private val schemaJson = """
        {"type":"object","additionalProperties":false,"required":["transactions"],
         "properties":{"transactions":{"type":"array","minItems":0,"maxItems":10,
          "items":{"type":"object","additionalProperties":false,"required":["amount","purpose"],
           "properties":{
            "amount":{"type":["number","null"],"description":"Amount in ₹, e.g. \"1,500\"\nor null"},
            "purpose":{"type":"string","enum":["Food","Rent","Lending"]},
            "confidence":{"type":"number","minimum":0.0,"maximum":1.0},
            "recurring":{"type":"boolean","default":false}}}}}}
    """.trimIndent()

    @Test
    fun aSchemaRequestIsGroqsStrictJsonSchemaEnvelope() = runTest {
        respond(200, okResponse)

        client.complete(
            llmRequest(
                json = LlmJson.Schema("expense_batch", schemaMap),
                system = "Parse it.", user = "tea 20 and auto 80", temperature = 0.1, maxTokens = 900
            )
        ).getOrThrow()

        val request = sent.single()
        assertEquals("https://api.groq.com/openai/v1/chat/completions", request.url)
        assertEquals("POST", request.method)
        assertEquals("Bearer groq-key", request.authorization)
        assertTrue(request.contentType, request.contentType!!.startsWith("application/json"))
        assertFalse("nulls must be omitted, not sent as null: ${request.body}", request.body.contains(":null"))

        val body = parse(request.body)
        assertEquals(
            setOf("model", "messages", "temperature", "response_format", "max_completion_tokens", "reasoning_effort", "include_reasoning"),
            body.keys
        )
        assertEquals("openai/gpt-oss-20b", body["model"])
        assertEquals(0.1, body["temperature"])
        assertEquals(900.0, body["max_completion_tokens"])
        assertEquals("low", body["reasoning_effort"])
        assertEquals(false, body["include_reasoning"])

        @Suppress("UNCHECKED_CAST")
        val messages = body["messages"] as List<Map<String, Any?>>
        assertEquals(listOf("system", "user"), messages.map { it["role"] })
        assertEquals(listOf("Parse it.", "tea 20 and auto 80"), messages.map { it["content"] })
        assertTrue("request messages must not carry a reasoning field", messages.none { it.containsKey("reasoning") })

        val format = body.obj("response_format")
        assertEquals(setOf("type", "json_schema"), format.keys)
        assertEquals("json_schema", format["type"])
        val jsonSchema = format.obj("json_schema")
        assertEquals(setOf("name", "schema", "strict"), jsonSchema.keys)
        assertEquals("expense_batch", jsonSchema["name"])
        assertEquals(true, jsonSchema["strict"])
        assertEquals(parse(schemaJson), jsonSchema["schema"])
    }

    @Test
    fun aSchemaSurvivesTheDowngradeResendAsPlainJsonObject() = runTest {
        respond(400, SCHEMA_UNSUPPORTED_BODY)
        respond(200, okResponse)

        client.complete(llmRequest(json = LlmJson.Schema("expense_batch", schemaMap))).getOrThrow()

        assertEquals(2, sent.size)
        val resent = parse(sent[1].body)
        assertEquals(mapOf("type" to "json_object"), resent["response_format"])
        assertFalse(sent[1].body.contains("json_schema"))
    }

    @Test
    fun aPlainRequestHasNoResponseFormatAtAll() = runTest {
        respond(200, okResponse)

        client.complete(llmRequest(json = LlmJson.None)).getOrThrow()

        val body = parse(sent.single().body)
        assertFalse(body.containsKey("response_format"))
        assertFalse(sent.single().body.contains("response_format"))
    }

    @Test
    fun aJsonObjectRequestSendsOnlyTheType() = runTest {
        respond(200, okResponse)

        client.complete(llmRequest(json = LlmJson.JsonObject)).getOrThrow()

        assertEquals(mapOf("type" to "json_object"), parse(sent.single().body)["response_format"])
    }

    @Test
    fun modelsWithoutReasoningSupportGetNoReasoningKeys() = runTest {
        config.fastModel = "meta-llama/llama-4-scout"
        respond(200, okResponse)

        client.complete(llmRequest()).getOrThrow()

        val body = parse(sent.single().body)
        assertFalse(body.containsKey("reasoning_effort"))
        assertFalse(body.containsKey("include_reasoning"))
    }

    // --- responses ----------------------------------------------------------------------------

    @Test
    fun parsesARepresentativeGroqResponse() {
        val json = """
            {
              "id": "chatcmpl-1f2e",
              "object": "chat.completion",
              "created": 1789000000,
              "model": "openai/gpt-oss-20b",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": "{\"transactions\":[{\"amount\":20}]}",
                  "reasoning": "The user spent 20."
                },
                "logprobs": null,
                "finish_reason": "stop"
              }],
              "usage": {"queue_time": 0.05, "prompt_tokens": 120, "completion_tokens": 40, "total_tokens": 160},
              "usage_breakdown": null,
              "system_fingerprint": "fp_x",
              "x_groq": {"id": "req_01"},
              "service_tier": "on_demand"
            }
        """.trimIndent()

        val parsed = moshi.adapter(GroqResponse::class.java).fromJson(json)!!

        val choice = parsed.choices.single()
        assertEquals("""{"transactions":[{"amount":20}]}""", choice.message.content)
        assertEquals("assistant", choice.message.role)
        assertEquals("The user spent 20.", choice.message.reasoning)
        assertEquals("stop", choice.finish_reason)
    }

    @Test
    fun theReasoningTraceNeverLeaksIntoTheReplyText() = runTest {
        respond(200, """{"choices":[{"message":{"role":"assistant","content":"the answer","reasoning":"secret thoughts"},"finish_reason":"stop"}]}""")

        assertEquals("the answer", client.complete(llmRequest()).getOrThrow().text)
    }

    @Test
    fun aMissingFinishReasonParsesAsNotTruncated() = runTest {
        respond(200, """{"choices":[{"message":{"role":"assistant","content":"x"}}]}""")

        val reply = client.complete(llmRequest()).getOrThrow()

        assertFalse(reply.truncated)
    }

    @Test
    fun lengthFinishReasonSurvivesTheWire() = runTest {
        respond(200, """{"choices":[{"message":{"role":"assistant","content":"{\"a\":"},"finish_reason":"length"}]}""")

        assertTrue(client.complete(llmRequest()).getOrThrow().truncated)
    }

    @Test
    fun nullContentIsAnEmptyReplyNotAParseCrash() = runTest {
        // A reasoning model that spends its whole budget thinking may answer content:null.
        respond(200, """{"choices":[{"message":{"role":"assistant","content":null},"finish_reason":"length"}]}""")

        val error = client.complete(llmRequest()).exceptionOrNull() as LlmException

        assertEquals(LlmException.Kind.EMPTY, error.kind)
        assertNull(error.cause)
    }

    @Test
    fun retryAfterAndTheErrorBodyReachTheClientThroughRetrofit() = runTest {
        respond(429, """{"error":{"message":"Rate limit reached for model","code":"rate_limit_exceeded"}}""", "Retry-After" to "1800")

        val error = client.complete(llmRequest()).exceptionOrNull() as LlmException

        assertEquals(LlmException.Kind.RATE_LIMIT, error.kind)
        assertEquals(1_800_000L, error.retryAfterMs)
        assertTrue(error.message, "Rate limit reached" in error.message.orEmpty())
        assertEquals(1, sent.size)
    }

    @Test
    fun aDecommissionedModelBodyReachesTheErrorMessage() = runTest {
        respond(400, DECOMMISSIONED_BODY)

        val error = client.complete(llmRequest()).exceptionOrNull() as LlmException

        assertEquals(LlmException.Kind.BAD_REQUEST, error.kind)
        assertNotNull(error.message)
        assertTrue(error.message, "decommissioned" in error.message.orEmpty())
    }
}
