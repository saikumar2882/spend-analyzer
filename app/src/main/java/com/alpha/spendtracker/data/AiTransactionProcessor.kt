package com.alpha.spendtracker.data

import android.util.Log
import com.alpha.spendtracker.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a natural-language expense sentence into the [AiTransactionResponse]s it describes — one
 * for "lunch 250", several for "tea 20, auto 80 and lunch 150".
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
    private val correctionRepository: AiCorrectionRepository,
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

    /**
     * @param allowMultiple false asks for exactly one expense — used for a payment receipt shared
     * from another app, which is one payment however many amounts its text mentions.
     */
    suspend fun parse(
        text: String,
        prefs: AiPreferences,
        allowMultiple: Boolean = true
    ): Result<List<AiTransactionResponse>> {
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
        val baselines = AiBatchParser.baselines(text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
        val localCurrency = AiParser.extractCurrency(text) ?: prefs.defaultCurrency.ifBlank { "INR" }
        val learnedRules = correctionRepository.rules.first()

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
                            GroqMessage("user", "USER INPUT: \"$text\"")
                        ),
                        response_format = GroqResponseFormat(),
                        // Extraction needs no deliberation, and the reasoning trace must not
                        // land in `content` — it would break the strict-JSON contract below.
                        reasoning_effort = "low",
                        include_reasoning = false,
                        // `reasoning_effort` shares this ceiling, and every extra expense adds a
                        // ~70-token JSON entry, so several need more room than the one-object reply.
                        max_completion_tokens = if (allowMultiple) 1536 else 512
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
                        return Result.success(withLearnedCorrections(baselines, learnedRules))
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

        val parsed = if (responseText.isNullOrBlank()) {
            if (lastError != null) {
                Log.e(TAG, "AI Error after retries: ${lastError.message}", lastError)
            }
            baselines
        } else {
            aiPrefsRepository.incrementUsage()
            AiBatchParser.parse(responseText, text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple)
                ?: run {
                    // Don't log the raw payload (PII) in release; length is enough to diagnose.
                    Log.e(TAG, "JSON Parse Failed (len=${responseText.length})")
                    baselines
                }
        }

        return Result.success(withLearnedCorrections(parsed, learnedRules))
    }

    /** Each expense is matched against the user's learned corrections using its own words. */
    private fun withLearnedCorrections(parsed: List<AiBatchParser.Parsed>, rules: List<CorrectionRule>) =
        parsed.map { AiCorrectionMemory.apply(it.response, it.source, rules) }

    private fun buildSystemPrompt(localCurrency: String, prefs: AiPreferences, multi: Boolean): String {
        val appList = com.alpha.spendtracker.ui.components.APP_PRESETS
            .joinToString(", ") { it.displayName }
        val purposeList = com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
            .joinToString(", ")
        val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dayFormat.format(System.currentTimeMillis())
        val yesterdayStr = dayFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)

        return """
            You are a strict JSON extractor for an Indian expense tracker. Parse the user's sentence and return ONE JSON object. Output ONLY valid JSON — no markdown, no code fences, no commentary.

            USER DEFAULTS (apply when not explicitly stated):
            - Currency: $localCurrency
            - Purpose: ${prefs.defaultPurpose}
            - Today: $todayStr

            PLATFORM MAPPING — resolve any fuzzy variant, misspelling, voice-transcription error, or Telugu/Hindi-script spelling to a canonical name from [$appList]:
            "swiggi" / "swigy" / "sweegy" / "instamart" / "స్విగ్గీ" / "स्विगी" → "Swiggy"
            "zapto" / "jepto" / "జెప్టో" / "ज़ेप्टो" → "Zepto"
            "zomoto" / "jomato" / "జొమాటో" / "ज़ोमैटो" → "Zomato"
            "blink it" / "grofers" → "Blinkit"
            "pp" / "phone pay" / "phonepay" → "PhonePe"
            "gpay" / "g pay" / "g-pay" / "tez" → "Google Pay"
            "amzn" / "amazon pay" → "Amazon"
            "cred pay" → "CRED"
            "paytm upi" → "Paytm"
            "upi" / no platform mentioned → "" (empty string — do NOT guess, the app fills in the user's default)
            IMPORTANT: a food/grocery app name (Swiggy, Zomato, Zepto, Blinkit) IS the platform — never replace it with a UPI app.

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
            "tea 20, auto 80 on phonepe yesterday" → {"transactions": [{"src": "tea 20", "amount": 20, "appName": "PhonePe", "purpose": "Groceries & Food", "notes": "Tea", "personName": "", "date": "$yesterdayStr", "needsAmount": false}, {"src": "auto 80", "amount": 80, "appName": "PhonePe", "purpose": "Travel & Commute", "notes": "Auto", "personName": "", "date": "$yesterdayStr", "needsAmount": false}]}
            "borrowed 2000 from mom" → {"transactions": [{"src": "borrowed 2000 from mom", "amount": 2000, "appName": "", "purpose": "Borrowing", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}]}""" else """OUTPUT (no extra keys):
            {"amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}

            EXAMPLES:
            "lent 500 to rahul for lunch via gpay" → {"amount": 500, "appName": "Google Pay", "purpose": "Lending", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}
            "300 on groceries in zapto" → {"amount": 300, "appName": "Zepto", "purpose": "Groceries & Food", "notes": "Groceries", "personName": "", "date": "$todayStr", "needsAmount": false}
            "borrowed 2000 from mom" → {"amount": 2000, "appName": "", "purpose": "Borrowing", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}"""}
        """.trimIndent()
    }
}
