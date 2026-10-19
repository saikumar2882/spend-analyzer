package com.alpha.spendtracker.data

import android.content.Context
import android.util.Log
import com.alpha.spendtracker.BuildConfig
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a natural-language expense sentence into the [AiTransactionResponse]s it describes — one
 * for "lunch 250", several for "tea 20, auto 80 and lunch 150".
 *
 * Implements 4 AI Execution Modes based on Privacy Toggle (E2EE) and Local Model Availability:
 * - MODE 1: Privacy ENABLED + Local Model AVAILABLE -> Run On-Device Local Model
 * - MODE 2: Privacy ENABLED + NO Local Model -> Send to Groq API WITH PII Sanitizer
 * - MODE 3: Privacy DISABLED + Local Model AVAILABLE -> Run On-Device Local Model
 * - MODE 4: Privacy DISABLED + NO Local Model -> Send directly to Groq API WITHOUT PII Sanitizer
 */
@Singleton
class AiTransactionProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groqApiService: GroqApiService,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val correctionRepository: AiCorrectionRepository,
) {

    companion object {
        private const val TAG = "AiTransactionProcessor"
        private const val GEMINI_MODEL = "gemini-3.5-flash"
        const val DAILY_LIMIT = 100
    }

    private val remoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60
            }
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(
                mapOf(
                    "groq_api_key" to "",
                    "gemini_api_key" to ""
                )
            )
        }
    }

    /**
     * Checks if a local LLM model file exists on device storage.
     */
    fun isLocalLlmAvailable(): Boolean {
        val modelFile = File("${context.filesDir}/models/gemma-2b-it-gpu-int4.bin")
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Parses user natural language expense text according to the 4 Privacy/AI Modes.
     */
    suspend fun parse(
        text: String,
        prefs: AiPreferences,
        allowMultiple: Boolean = true
    ): Result<List<AiTransactionResponse>> {
        if (text.isBlank()) {
            return Result.failure(Exception("Input cannot be empty."))
        }
        if (text.length > 500) {
            return Result.failure(Exception("Input too long (max 500 characters)."))
        }

        val isPrivacyEnabled = prefs.isE2eEncryptionEnabled
        val isLocalAvailable = isLocalLlmAvailable()
        val learnedRules = correctionRepository.rules.first()

        Log.d(TAG, "AI Processing Mode Check: PrivacyEnabled=$isPrivacyEnabled, LocalLlmAvailable=$isLocalAvailable")

        return when {
            // MODE 1 & MODE 3: Local Model is available on-device
            isLocalAvailable -> {
                Log.d(TAG, "MODE 1/3: Local AI Model is active -> Processing 100% on-device")
                val baselines = AiBatchParser.baselines(text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                aiPrefsRepository.incrementUsage()
                Result.success(withLearnedCorrections(baselines, learnedRules))
            }

            // MODE 2: Privacy ENABLED + NO Local Model -> Call Groq API WITH PII Sanitizer
            isPrivacyEnabled && !isLocalAvailable -> {
                Log.d(TAG, "MODE 2: Privacy Enabled & No Local Model -> Calling Groq with PII Sanitizer")
                val anonymization = PiiSanitizer.anonymize(text)
                val anonymizedInput = anonymization.anonymizedText
                val piiTokens = anonymization.tokens

                val baselines = AiBatchParser.baselines(text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                val responseText = callCloudApi(anonymizedInput, prefs, allowMultiple)

                val parsedList = if (!responseText.isNullOrBlank()) {
                    aiPrefsRepository.incrementUsage()
                    val rawParsed = AiBatchParser.parse(responseText, anonymizedInput, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                    if (rawParsed != null) {
                        rawParsed.map { item ->
                            val resp = item.response
                            val restoredResp = resp.copy(
                                notes = PiiSanitizer.deanonymize(resp.notes, piiTokens),
                                personName = PiiSanitizer.deanonymize(resp.personName, piiTokens),
                                appName = resp.appName?.let { PiiSanitizer.deanonymize(it, piiTokens) }
                            )
                            val restoredSource = PiiSanitizer.deanonymize(item.source, piiTokens)
                            AiBatchParser.Parsed(restoredResp, restoredSource)
                        }
                    } else {
                        Log.w(TAG, "MODE 2: model responded but output wasn't usable JSON — falling back to local baseline")
                        markFallback(baselines)
                    }
                } else markFallback(baselines)

                Result.success(withLearnedCorrections(parsedList, learnedRules))
            }

            // MODE 4: Privacy DISABLED + NO Local Model -> Call Groq API WITHOUT PII Sanitizer
            else -> {
                Log.d(TAG, "MODE 4: Privacy Disabled & No Local Model -> Calling Groq directly without PII Sanitizer")
                val baselines = AiBatchParser.baselines(text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                val responseText = callCloudApi(text, prefs, allowMultiple)

                val parsedList = if (!responseText.isNullOrBlank()) {
                    aiPrefsRepository.incrementUsage()
                    val rawParsed = AiBatchParser.parse(responseText, text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                    if (rawParsed != null) {
                        rawParsed
                    } else {
                        Log.w(TAG, "MODE 4: model responded but output wasn't usable JSON — falling back to local baseline")
                        markFallback(baselines)
                    }
                } else markFallback(baselines)

                Result.success(withLearnedCorrections(parsedList, learnedRules))
            }
        }
    }

    /** Flags baseline results as degraded so the confirmation screen can tell the user to double-check them. */
    private fun markFallback(parsed: List<AiBatchParser.Parsed>): List<AiBatchParser.Parsed> =
        parsed.map { it.copy(response = it.response.copy(usedFallback = true)) }

    /**
     * Calls Groq API (or Gemini fallback) with the prompt text.
     */
    private suspend fun callCloudApi(inputPrompt: String, prefs: AiPreferences, allowMultiple: Boolean): String? {
        val localCurrency = AiParser.extractCurrency(inputPrompt) ?: prefs.defaultCurrency.ifBlank { "INR" }
        var responseText: String? = null
        var lastError: Exception? = null

        for (attempt in 0..1) {
            if (responseText != null) break

            try {
                remoteConfig.fetchAndActivate().await()
                val groqKey = remoteConfig.getString("groq_api_key")
                val systemPrompt = buildSystemPrompt(localCurrency, prefs, allowMultiple)

                if (groqKey.isNotBlank()) {
                    Log.d(TAG, "Input: Calling Groq (${GroqModels.FAST})")
                    val groqRequest = GroqRequest(
                        model = GroqModels.FAST,
                        messages = listOf(
                            GroqMessage("system", systemPrompt),
                            GroqMessage("user", "USER INPUT: \"$inputPrompt\"")
                        ),
                        response_format = GroqResponseFormat(),
                        reasoning_effort = "low",
                        include_reasoning = false,
                        // GPT-OSS is a reasoning model: even at "low" effort, reasoning tokens are
                        // drawn from this same ceiling before the JSON answer is emitted (see the
                        // identical note on the history assistant's call). A tight budget here was
                        // silently truncating the JSON — AiBatchParser.parse then can't find a
                        // closing brace, returns null, and the request quietly falls back to the
                        // much weaker local baseline with no indication anything went wrong.
                        max_completion_tokens = if (allowMultiple) 2048 else 768
                    )
                    val response = groqApiService.getCompletion("Bearer $groqKey", groqRequest)
                    if (response.isSuccessful) {
                        val choice = response.body()?.choices?.firstOrNull()
                        if (choice?.finish_reason == "length") {
                            Log.w(TAG, "Input: response truncated (hit max_completion_tokens)")
                        }
                        responseText = choice?.message?.content
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty().take(300)
                        Log.e(TAG, "Groq Error: ${response.code()} - $errorBody")
                        throw Exception("Groq API error: ${response.code()} $errorBody")
                    }
                } else {
                    Log.d(TAG, "Input: Groq key missing, falling back to Gemini")
                    val geminiKey = remoteConfig.getString("gemini_api_key")
                    if (geminiKey.isBlank()) {
                        Log.w(TAG, "AI API Keys are missing in Remote Config")
                        return null
                    }
                    val generativeModel = GenerativeModel(modelName = GEMINI_MODEL, apiKey = geminiKey)
                    responseText = generativeModel.generateContent(content {
                        text(systemPrompt)
                        text("USER INPUT: \"$inputPrompt\"")
                    }).text
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                // 429 (per-minute rate limit) is transient exactly like a 503/504 — worth one
                // short retry instead of immediately degrading to the local baseline.
                val isRetryable = msg.contains("503") || msg.contains("504") ||
                        msg.contains("429") || msg.contains("rate limit", ignoreCase = true) ||
                        msg.contains("high demand", ignoreCase = true)
                if (isRetryable && attempt == 0) {
                    delay(2000)
                    continue
                }
                break
            }
        }

        if (BuildConfig.DEBUG && responseText != null) Log.d(TAG, "AI Raw Response: $responseText")
        return responseText
    }

    /** Each expense is matched against the user's learned corrections using its own words. */
    private fun withLearnedCorrections(parsed: List<AiBatchParser.Parsed>, rules: List<CorrectionRule>) =
        parsed.map { AiCorrectionMemory.apply(it.response, it.source, rules) }

    private fun buildSystemPrompt(localCurrency: String, prefs: AiPreferences, multi: Boolean): String {
        val appList = APP_PRESETS
            .joinToString(", ") { it.displayName }
        val purposeList = PURPOSE_PRESETS
            .joinToString(", ")
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dayFormat.format(System.currentTimeMillis())
        val yesterdayStr = dayFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)

        return """
            You are a strict JSON extractor for an Indian expense tracker. Parse the user's sentence and return ONE JSON object. Output ONLY valid JSON — no markdown, no code fences, no commentary.

            USER DEFAULTS (apply when not explicitly stated):
            - Currency: $localCurrency
            - Purpose: ${prefs.defaultPurpose}
            - Today: $todayStr

            PLATFORM MAPPING — resolve any fuzzy variant, misspelling, voice-transcription error, or Telugu/Hindi-script spelling to a canonical name from [$appList]:
            "swiggi" / "swigy" / "sweegy" / "instamart" / "స్విగ్గీ" / "స్విగ్గి" → "Swiggy"
            "zapto" / "jepto" / "జెప్టో" / "జెప్తో" → "Zepto"
            "zomoto" / "jomato" / "జొమాటో" / "జోమాటో" → "Zomato"
            "blink it" / "grofers" → "Blinkit"
            "pp" / "phone pay" / "phonepay" → "PhonePe"
            "gpay" / "g pay" / "g-pay" / "tez" → "Google Pay"
            "amzn" / "amazon pay" → "Amazon"
            "cash" / "cash payment" / "paid cash" / "నగదు" / "नकद" → "Cash"
            "cred" / "cred pay" / "cred app" → "CRED"
            "paytm upi" → "Paytm"
            "upi" / no platform mentioned → "" (empty string — do NOT guess, the app fills in the user's default)
            IMPORTANT: a food/grocery app name (Swiggy, Zomato, Zepto, Blinkit) IS the platform — never replace it with a UPI app.

            PURPOSE MAPPING — output EXACT string from [$purposeList]:
            Food/drinks: biryani, pizza, lunch, dinner, breakfast, coffee, chai, swiggy, zomato → "Food & Drinks"
            Groceries: grocery, groceries, supermarket, kirana, blinkit, zepto, milk, veggies, fruits → "Groceries"
            Shopping: shirt, jeans, shoes, saree, amazon, flipkart, myntra, ajio, meesho → "Shopping & Apparels"
            Travel: uber, ola, rapido, auto, cab, metro, bus, flight, train, toll → "Travel & Commute"
            Fuel: petrol, fuel, diesel, cng, gas station → "Fuel"
            Entertainment: netflix, hotstar, prime, spotify, movie, concert, game, gym → "Subscription & Leisure"
            Health: medicine, tablet, doctor, hospital, clinic, pharmacy, dentist, lab test → "Healthcare & Medical"
            Bills: rent, electricity, wifi, internet, recharge, water bill, gas, dth → "Rent & Utilities"
            Finance: credit card bill, cc bill, emi, loan payment → "Credit Card Bill"
            Investment: stocks, mutual funds, sip, fd, shares, crypto, savings → "Investment"
            Giving: "lent to", "gave to", "sent to [person]", "paid for [person]" → "Lending"
            Receiving: "borrowed from", "took from", "received from" → "Borrowing"
            Default (nothing clearly matches) → use the user's default purpose above: "${prefs.defaultPurpose}"

            FIELD RULES:
            - LANGUAGE TRANSLATION: The input may be in English, Telugu (e.g., "భోజనం కోసం 500 ఖర్చు చేశాను"), or Teluglish/Bilingual (e.g., "500 dinner ki karchu chesa"). ALWAYS translate Telugu words and context into clear, concise ENGLISH for `notes` and `purpose`. NEVER output Telugu script or unparsed Teluglish in JSON fields.
            - amount: ${if (multi) "the monetary amount of THIS expense" else "largest monetary number found"}; null if absent.
            - appName: canonical platform name from the mapping above, ONLY if the user mentioned one; otherwise "".
            - purpose: exact string from the purpose mapping above.
            - notes: 1-4 Title Case words describing WHAT in ENGLISH. Exclude amount, app name, and verbs ("spent","paid","bought"). For Lending/Borrowing, notes is ONLY the optional reason/detail ("Rent", "Trip Expenses") and must NOT contain the person's name or words like "Lent to"/"From"; empty string if no reason is given. Empty string if nothing identifiable.
            - personName: ONLY for purpose "Lending" or "Borrowing" — the other person's name in Title Case ENGLISH/Latin script, without relations prefixes like "my" ("lent 500 to rahul" → "Rahul", "borrowed 2k from my mom for rent" → "Mom"). Empty string for every other purpose or when no person is named.
            - date: YYYY-MM-DD relative to today ($todayStr). "yesterday" → today−1; "last friday" → most recent past Friday; partial date with no year → this year, shift back 1 year if result is in the future. No date → today.
            - needsAmount: true only when amount is null.

            ${if (multi) """OUTPUT (no extra keys):
            {"transactions": [{"src": string, "amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}]}

            MULTIPLE EXPENSES:
            - One entry in "transactions" per separate expense, in the order the user said them. Most inputs hold exactly one.
            - "tea 20, auto 80 and lunch 150" is THREE entries. "500 for lunch and chai" is ONE entry: a single amount covering several items.
            - "src": the words of the user's sentence this entry came from, copied exactly — not translated, not reworded.
            - A payment app or date said once for the whole sentence ("... all on gpay", "yesterday I spent ...") applies to every entry. One said next to a single expense applies to that expense only.
            - Never invent an expense the user did not state. At most ${AiBatchParser.MAX_TRANSACTIONS} entries.

            EXAMPLES:
            "lent 500 to rahul for lunch via gpay" → {"transactions": [{"src": "lent 500 to rahul for lunch via gpay", "amount": 500, "appName": "Google Pay", "purpose": "Lending", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}]}
            "tea 20, auto 80 on phonepe yesterday" → {"transactions": [{"src": "tea 20", "amount": 20, "appName": "PhonePe", "purpose": "Food & Drinks", "notes": "Tea", "personName": "", "date": "$yesterdayStr", "needsAmount": false}, {"src": "auto 80", "amount": 80, "appName": "PhonePe", "purpose": "Travel & Commute", "notes": "Auto", "personName": "", "date": "$yesterdayStr", "needsAmount": false}]}
            "borrowed 2000 from mom" → {"transactions": [{"src": "borrowed 2000 from mom", "amount": 2000, "appName": "", "purpose": "Borrowing", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}]}""" else """OUTPUT (no extra keys):
            {"amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}

            EXAMPLES:
            "lent 500 to rahul for lunch via gpay" → {"amount": 500, "appName": "Google Pay", "purpose": "Lending", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}
            "300 on groceries in zapto" → {"amount": 300, "appName": "Zepto", "purpose": "Groceries", "notes": "Groceries", "personName": "", "date": "$todayStr", "needsAmount": false}
            "borrowed 2000 from mom" → {"amount": 2000, "appName": "", "purpose": "Borrowing", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}"""}
        """.trimIndent()
    }
}
