package com.alpha.spendtracker.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlmClient"

/**
 * [LlmConfig] over Firebase Remote Config: `groq_api_key` / `gemini_api_key` plus the model ids
 * (`groq_model_fast`, `groq_model_smart`, `gemini_model`), so a provider decommissioning a model
 * is fixed by publishing a Remote Config value instead of shipping a release. A blank value
 * falls back to the compiled-in default.
 */
@Singleton
class RemoteLlmConfig @Inject constructor() : LlmConfig {

    private val remoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(remoteConfigSettings {
                // Short on purpose: picks up a rotated key or a new model id within a minute.
                minimumFetchIntervalInSeconds = REFETCH_INTERVAL_SECONDS
            })
            setDefaultsAsync(
                mapOf(
                    "groq_api_key" to "",
                    "gemini_api_key" to "",
                    LlmModels.RC_GROQ_FAST to GroqModels.FAST,
                    LlmModels.RC_GROQ_SMART to GroqModels.SMART,
                    LlmModels.RC_GEMINI to LlmModels.GEMINI_DEFAULT,
                    LlmModels.RC_LOCAL_DISABLED to false
                )
            )
        }
    }

    private val gate = FetchGate(intervalMs = REFETCH_INTERVAL_SECONDS * 1_000, retryMs = FAILED_FETCH_RETRY_MS)

    override suspend fun refresh() {
        // Every AI call used to `fetchAndActivate().await()` — a network round trip with no
        // timeout in front of the request. The gate fetches at most once per interval and bounds
        // the wait, so a dead connection cannot stall a call that would have used the cached key.
        // With nothing cached (first launch) the call that follows has no key and is certain to
        // fail, so that one is worth a longer wait.
        val timeoutMs = if (groqKey().isBlank() && geminiKey().isBlank()) FIRST_FETCH_TIMEOUT_MS else FETCH_TIMEOUT_MS
        gate.run {
            try {
                withTimeoutOrNull(timeoutMs) { remoteConfig.fetchAndActivate().await() } != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Remote Config fetch failed, using cached values: ${e.message}")
                false
            }
        }
    }

    override fun groqKey(): String = remoteConfig.getString("groq_api_key")

    override fun geminiKey(): String = remoteConfig.getString("gemini_api_key")

    override fun groqModel(tier: LlmTier): String = when (tier) {
        LlmTier.FAST -> remoteConfig.getString(LlmModels.RC_GROQ_FAST).ifBlank { GroqModels.FAST }
        LlmTier.SMART -> remoteConfig.getString(LlmModels.RC_GROQ_SMART).ifBlank { GroqModels.SMART }
    }

    override fun geminiModel(): String =
        remoteConfig.getString(LlmModels.RC_GEMINI).ifBlank { LlmModels.GEMINI_DEFAULT }

    override fun localEnabled(): Boolean = !remoteConfig.getBoolean(LlmModels.RC_LOCAL_DISABLED)

    private companion object {
        const val REFETCH_INTERVAL_SECONDS = 60L
        const val FETCH_TIMEOUT_MS = 4_000L
        const val FIRST_FETCH_TIMEOUT_MS = 10_000L

        /** After a failed or timed-out fetch; short enough to recover, long enough not to hammer. */
        const val FAILED_FETCH_RETRY_MS = 15_000L
    }
}

/**
 * Decides when [RemoteLlmConfig] may go to the network. Split out so the policy runs in plain JVM
 * tests (Remote Config itself cannot).
 *
 * - One fetch at a time: two AI calls landing together (Track plus the widget, say) used to each
 *   start their own; the second now waits on the lock, then sees the result and returns.
 * - A success is good for [intervalMs]; a failure or timeout only for [retryMs]. Without the
 *   second rule every call made while offline (or throttled by the backend) re-ran the fetch and
 *   waited out its timeout again.
 */
internal class FetchGate(
    private val intervalMs: Long,
    private val retryMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()

    @Volatile
    private var nextFetchAt = 0L

    /** [fetch] returns true when it completed, false on a timeout or failure. */
    suspend fun run(fetch: suspend () -> Boolean) {
        if (clock() < nextFetchAt) return
        lock.withLock {
            if (clock() < nextFetchAt) return
            // A cancelled fetch throws out of here before the line below, so the next caller
            // tries again instead of inheriting a "fetched" mark that never happened.
            val ok = fetch()
            nextFetchAt = clock() + if (ok) intervalMs else retryMs
        }
    }
}

/** [GeminiGateway] over the `google/generative-ai` SDK. */
@Singleton
class SdkGeminiGateway @Inject constructor() : GeminiGateway {

    override suspend fun generate(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean
    ): String? = call(model, apiKey, system, user, json, temperature = null)

    override suspend fun generate(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean,
        temperature: Double
    ): String? = call(model, apiKey, system, user, json, temperature)

    private suspend fun call(
        model: String,
        apiKey: String,
        system: String?,
        user: String,
        json: Boolean,
        temperature: Double?
    ): String? {
        val sampling = temperature?.toFloat()
        val generative = GenerativeModel(
            modelName = model,
            apiKey = apiKey,
            generationConfig = generationConfig {
                if (json) responseMimeType = "application/json"
                if (sampling != null) this.temperature = sampling
            },
            // The SDK's default is Long.MAX_VALUE ms, i.e. no overall deadline.
            requestOptions = RequestOptions(timeout = REQUEST_TIMEOUT_MS),
            // The instruction channel, not a first text part of the user turn: it keeps the
            // user's text from being read as part of the rules.
            systemInstruction = system?.let { content { text(it) } }
        )
        return generative.generateContent(content { text(user) }).text
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 30_000L
    }
}

/**
 * [LlmEvents] over Firebase Analytics, so the failure modes that used to be silent (a
 * decommissioned model, a truncated reply, the regex baseline standing in for the LLM) show up in
 * the console. Config-class failures (a bad model id or key) also leave a Crashlytics non-fatal.
 */
@Singleton
class FirebaseLlmEvents @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmEvents {

    override fun log(name: String, params: Map<String, Any>) {
        Log.d(TAG, "$name $params")
        // Robolectric and some previews have no FirebaseApp.
        if (FirebaseApp.getApps(context).isEmpty()) return
        try {
            val bundle = Bundle()
            for ((key, value) in params) {
                when (value) {
                    is Int -> bundle.putLong(key, value.toLong())
                    is Long -> bundle.putLong(key, value)
                    is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                    else -> bundle.putString(key, value.toString().take(100))
                }
            }
            FirebaseAnalytics.getInstance(context).logEvent(name, bundle)

            val kind = params["error_kind"]
            if (name == "llm_call" && (kind == LlmException.Kind.BAD_REQUEST.name || kind == LlmException.Kind.AUTH.name)) {
                FirebaseCrashlytics.getInstance()
                    .recordException(IllegalStateException("LLM config failure: $params"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry failed: ${e.message}")
        }
    }
}
