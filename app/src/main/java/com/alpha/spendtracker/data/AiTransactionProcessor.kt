package com.alpha.spendtracker.data

import android.util.Log
import com.alpha.spendtracker.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a natural-language expense sentence into an [AiTransactionResponse].
 *
 * Lives outside the ViewModel because two entry points need it: the in-app AI sheet
 * (`SpendViewModel`) and the home-screen widget's overlay, which must not instantiate
 * `SpendViewModel` — that ViewModel's `onCleared` tears down the singleton repository's
 * Firestore listeners, so a second, short-lived instance would kill sync for the
 * already-running MainActivity.
 */
@Singleton
class AiTransactionProcessor @Inject constructor(
    private val groqApiService: GroqApiService,
    private val aiPrefsRepository: AiPreferencesRepository,
) {

    companion object {
        private const val TAG = "AiTransactionProcessor"
        private const val GEMINI_MODEL = "gemini-3.5-flash"
        const val DAILY_LIMIT = 15
    }

    private val remoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = remoteConfigSettings {
                // Lower interval for development to pick up key changes faster
                minimumFetchIntervalInSeconds = 60
            }
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(
                mapOf(
                    "gemini_api_key" to "",
                    "groq_api_key" to ""
                )
            )
        }
    }

    suspend fun parse(text: String, prefs: AiPreferences): Result<AiTransactionResponse> {
        if (prefs.dailyUsageCount >= DAILY_LIMIT) {
            return Result.failure(Exception("Daily limit reached ($DAILY_LIMIT/day). Please try again tomorrow."))
        }
        if (text.isBlank()) {
            return Result.failure(Exception("Input cannot be empty."))
        }
        if (text.length > 500) {
            return Result.failure(Exception("Input too long (max 500 characters)."))
        }

        // Run the local heuristic parser first — gives us a deterministic
        // baseline (and a usable response even if the LLM is down).
        val baseline = AiParser.parseToBaseline(text, prefs.defaultApp, prefs.defaultPurpose)
        val localCurrency = AiParser.extractCurrency(text) ?: prefs.defaultCurrency.ifBlank { "INR" }

        var responseText: String? = null
        var lastError: Exception? = null

        for (attempt in 0..1) {
            if (responseText != null) break

            try {
                remoteConfig.fetchAndActivate().await()
                val groqKey = remoteConfig.getString("groq_api_key")
                val systemPrompt = buildSystemPrompt(localCurrency, prefs)

                if (groqKey.isNotBlank()) {
                    Log.d(TAG, "Input: Calling Groq (${GroqModels.FAST})")
                    val groqRequest = GroqRequest(
                        model = GroqModels.FAST,
                        messages = listOf(
                            GroqMessage("system", systemPrompt),
                            GroqMessage("user", "USER INPUT: \"$text\"")
                        ),
                        response_format = GroqResponseFormat(),
                        // Extraction needs no deliberation, and the reasoning trace must not
                        // land in `content` — it would break the strict-JSON contract below.
                        reasoning_effort = "low",
                        include_reasoning = false,
                        max_completion_tokens = 512
                    )
                    val response = groqApiService.getCompletion("Bearer $groqKey", groqRequest)
                    if (response.isSuccessful) {
                        responseText = response.body()?.choices?.firstOrNull()?.message?.content
                    } else {
                        // Keep the body in the message: a decommissioned model reports itself
                        // only there ("model_decommissioned"), and a bare code hid that for
                        // the whole llama-3.x sunset.
                        val errorBody = response.errorBody()?.string().orEmpty().take(300)
                        Log.e(TAG, "Groq Input Error: ${response.code()} - $errorBody")
                        throw Exception("Groq API error: ${response.code()} $errorBody")
                    }
                } else {
                    Log.d(TAG, "Input: Groq key missing, falling back to Gemini")
                    val geminiKey = remoteConfig.getString("gemini_api_key")
                    if (geminiKey.isBlank()) {
                        Log.w(TAG, "AI API Keys are missing in Remote Config")
                        return Result.success(baseline)
                    }
                    val generativeModel = GenerativeModel(modelName = GEMINI_MODEL, apiKey = geminiKey)
                    responseText = generativeModel.generateContent(content {
                        text(systemPrompt)
                        text("USER INPUT: \"$text\"")
                    }).text
                }
            } catch (e: CancellationException) {
                // Cancellation is a normal path here (cancelAiInput / a newer request
                // superseding this one). Swallowing it let the loop fall through to
                // a success result, which re-opened the confirmation sheet for input the
                // user had already discarded.
                throw e
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                val isRetryable = msg.contains("503") || msg.contains("504") ||
                    msg.contains("high demand", ignoreCase = true)

                if (isRetryable && attempt == 0) {
                    delay(2000)
                    continue
                }
                break
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "AI Raw Response: $responseText")

        val merged = if (responseText.isNullOrBlank()) {
            if (lastError != null) {
                Log.e(TAG, "AI Error after retries: ${lastError.message}", lastError)
            }
            baseline
        } else {
            aiPrefsRepository.incrementUsage()
            parseAndMerge(responseText, baseline, text, prefs.defaultApp)
        }

        return Result.success(merged)
    }

    private fun buildSystemPrompt(localCurrency: String, prefs: AiPreferences): String {
        val appList = com.alpha.spendtracker.ui.components.APP_PRESETS
            .joinToString(", ") { it.displayName }
        val purposeList = com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
            .joinToString(", ")
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(System.currentTimeMillis())

        return """
            You are a strict JSON extractor for an Indian expense tracker. Parse the user's sentence and return ONE JSON object. Output ONLY valid JSON — no markdown, no code fences, no commentary.

            USER DEFAULTS (apply when not explicitly stated):
            - Currency: $localCurrency
            - Platform: ${prefs.defaultApp}
            - Purpose: ${prefs.defaultPurpose}
            - Today: $todayStr

            PLATFORM MAPPING — resolve any fuzzy variant to a canonical name from [$appList]:
            "pp" / "phone pay" / "phonepay" → "PhonePe"
            "gpay" / "g pay" / "g-pay" / "tez" → "Google Pay"
            "amzn" / "amazon pay" → "Amazon"
            "cred pay" → "CRED"
            "paytm upi" → "Paytm"
            "upi" / unknown → use default platform above

            PURPOSE MAPPING — output EXACT string from [$purposeList]:
            Food/drinks: biryani, pizza, lunch, dinner, breakfast, coffee, chai, swiggy, zomato, blinkit, zepto, groceries → "Groceries & Food"
            Shopping: shirt, jeans, shoes, saree, amazon, flipkart, myntra, ajio, meesho → "Shopping & Apparels"
            Travel: uber, ola, rapido, auto, cab, petrol, diesel, metro, bus, flight, train, toll → "Travel & Commute"
            Entertainment: netflix, hotstar, prime, spotify, movie, concert, game, gym → "Subscription & Leisure"
            Health: medicine, tablet, doctor, hospital, clinic, pharmacy, dentist, lab test → "Healthcare & Medical"
            Bills: rent, electricity, wifi, internet, recharge, water bill, gas, dth → "Rent & Utilities"
            Finance: credit card bill, cc bill, emi, loan payment → "Credit Card Bill"
            Giving: "lent to", "gave to", "sent to [person]", "paid for [person]" → "Lending"
            Receiving: "borrowed from", "took from", "received from" → "Borrowing"
            Default (nothing clearly matches) → use the user's default purpose above: "${prefs.defaultPurpose}"

            FIELD RULES:
            - amount: largest monetary number found; null if absent.
            - appName: canonical platform name from the mapping above.
            - purpose: exact string from the purpose mapping above.
            - notes: 1-4 Title Case words describing WHAT. Exclude amount, app name, and verbs ("spent","paid","bought"). For lending/borrowing include the person name: "Lent to Rahul", "From Mom". Empty string if nothing identifiable.
            - date: YYYY-MM-DD relative to today ($todayStr). "yesterday" → today−1; "last friday" → most recent past Friday; partial date with no year → this year, shift back 1 year if result is in the future. No date → today.
            - needsAmount: true only when amount is null.

            OUTPUT (no extra keys):
            {"amount": number|null, "appName": string, "purpose": string, "notes": string, "date": string, "needsAmount": boolean}
        """.trimIndent()
    }

    /**
     * Parse the LLM JSON and merge with the local-parser baseline. Per field:
     * AI wins if it provided a non-blank, valid value; otherwise we keep baseline.
     *
     * [originalText] and [defaultApp] let us honor the user's configured default payment
     * app: the small LLM tends to guess "Google Pay" whenever the input names no app, which
     * would override the user's default. We only trust an LLM-detected app when it is actually
     * grounded in the input; otherwise we fall back to the user's default, not the guess.
     */
    private fun parseAndMerge(
        responseText: String,
        baseline: AiTransactionResponse,
        originalText: String,
        defaultApp: String
    ): AiTransactionResponse {
        val jsonString = run {
            val start = responseText.indexOf("{")
            val end = responseText.lastIndexOf("}")
            if (start in 0 until end) responseText.substring(start, end + 1) else responseText
        }
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            // Don't log the raw payload (PII) in release; length is enough to diagnose.
            Log.e(TAG, "JSON Parse Failed (len=${responseText.length})", e)
            return baseline
        }

        val aiAmount = if (json.isNull("amount")) null else json.optDouble("amount", Double.NaN)
            .takeIf { !it.isNaN() }
        val aiAppRaw = json.optString("appName", "").ifBlank { null }
        val aiPurposeRaw = json.optString("purpose", "").ifBlank { null }
        val aiNotesRaw = json.optString("notes", "").trim()
        val aiDate = json.optString("date", "").ifBlank { baseline.date }
        val aiNeedsAmount = json.optBoolean("needsAmount", false)

        val aiPreset = AiParser.normalizeAppToPreset(aiAppRaw)
        // Resolve the payment app in priority order:
        //  1. An app the user actually named in the input (local alias match is ground truth).
        //  2. An app the LLM detected that ALSO literally appears in the input — catches apps
        //     the local matcher doesn't know, while ignoring the LLM's ungrounded guesses.
        //  3. Neither — the user named no app, so use their configured default, NOT the LLM guess.
        val localApp = AiParser.findAppPreset(originalText)
        val llmAppGrounded = aiPreset != null && run {
            val hay = " ${originalText.lowercase()} "
            hay.contains(" ${aiPreset.displayName.lowercase()} ") ||
                (!aiAppRaw.isNullOrBlank() && hay.contains(aiAppRaw.lowercase()))
        }
        val finalPreset = localApp
            ?: aiPreset?.takeIf { llmAppGrounded }
            ?: AiParser.normalizeAppToPreset(defaultApp)
            ?: aiPreset
            ?: AiParser.normalizeAppToPreset(baseline.appName)
        val finalPurpose = AiParser.normalizePurpose(aiPurposeRaw) ?: baseline.purpose
        val finalNotes = aiNotesRaw.ifBlank { baseline.notes }
        val finalAmount = aiAmount ?: baseline.amount
        val finalTimestamp = parseIsoDate(aiDate) ?: baseline.timestamp

        return AiTransactionResponse(
            amount = finalAmount,
            appName = finalPreset?.displayName ?: aiAppRaw ?: baseline.appName,
            appPresetId = finalPreset?.id,
            purpose = finalPurpose,
            notes = finalNotes,
            date = aiDate,
            timestamp = finalTimestamp,
            needsAmount = aiNeedsAmount || finalAmount == null
        )
    }

    /** Parse "YYYY-MM-DD" emitted by the LLM into an epoch millis. */
    private fun parseIsoDate(date: String?): Long? {
        if (date.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(date)?.let { parsed ->
                Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        } catch (_: Exception) {
            null
        }
    }
}
