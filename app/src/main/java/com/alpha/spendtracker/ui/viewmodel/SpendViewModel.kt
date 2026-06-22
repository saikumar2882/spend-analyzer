/**
 * ViewModel that manages spend data and analytics for the UI components.
 */
package com.alpha.spendtracker.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.spendtracker.data.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class TimeFilter {
    DAY, WEEK, MONTH, YEAR, ALL, CUSTOM
}

sealed class AiHistoryStatus {
    object Idle : AiHistoryStatus()
    object Analyzing : AiHistoryStatus()
    data class Error(val message: String, val type: AiErrorType) : AiHistoryStatus()
}

enum class AiErrorType {
    SERVER_RATE_LIMIT,
    CLIENT_RATE_LIMIT,
    API_KEY_MISSING,
    GENERIC
}

/**
 * Main ViewModel to manage Spending Tracker operations, analytics, and states
 */
@HiltViewModel
class SpendViewModel @Inject constructor(
    private val repository: SpendRepository,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val chatDao: ChatDao,
    private val groqApiService: GroqApiService,
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "SpendViewModel"
        // Single source of truth for the Gemini fallback model so the two call sites can't drift.
        private const val GEMINI_MODEL = "gemini-3.5-flash"
    }

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            _userId.value = uid
            repository.startSync(uid, viewModelScope)
            initializeChatSession(uid)
        } else {
            _userId.value = "anonymous"
            repository.stopSync()
        }
    }

    private val _userId = MutableStateFlow(auth.currentUser?.uid ?: "anonymous")
    private var aiJob: Job? = null
    private var historyJob: Job? = null

    private var currentSessionId: String = ""

    private val _isBiometricAuthenticated = MutableStateFlow(value = false)
    val isBiometricAuthenticated: StateFlow<Boolean> = _isBiometricAuthenticated

    fun setBiometricAuthenticated(authenticated: Boolean) {
        _isBiometricAuthenticated.value = authenticated
    }

    init {
        // Start sync if user is already logged in
        auth.currentUser?.let { user ->
            _userId.value = user.uid
            repository.startSync(user.uid, viewModelScope)
            initializeChatSession(user.uid)
        }

        // Listen to auth changes to start/stop sync. Held in a field so it can be removed in
        // onCleared() — otherwise the FirebaseAuth singleton pins this ViewModel across every
        // Activity recreation, leaking a sync listener each time.
        auth.addAuthStateListener(authListener)

        // Periodic cleanup of old chat messages (12h TTL)
        viewModelScope.launch {
            auth.currentUser?.uid?.let { uid ->
                repository.cleanupOldChatMessages(uid, 12)
            }
        }

        // Cleanup old history (30 days)
        viewModelScope.launch {
            auth.currentUser?.uid?.let { uid ->
                repository.cleanupOldHistory(uid, 30)
            }
        }
    }

    override fun onCleared() {
        // Detach from the FirebaseAuth singleton and stop the Firestore sync so this ViewModel
        // (and its repository listeners) can be garbage-collected on Activity recreation.
        auth.removeAuthStateListener(authListener)
        repository.stopSync()
        super.onCleared()
    }

    private fun initializeChatSession(userId: String) {
        viewModelScope.launch {
            val lastSessionId = chatDao.getLastSessionId(userId)
            currentSessionId = lastSessionId ?: java.util.UUID.randomUUID().toString()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatHistory: StateFlow<List<ChatMessage>> = _userId.flatMapLatest { userId ->
        chatDao.getChatMessages(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val recurringBills: StateFlow<List<RecurringBill>> = _userId.flatMapLatest { userId ->
        repository.getAllRecurringBills(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addRecurringBill(name: String, purpose: String, category: String, appName: String, amount: Double, dayOfMonth: Int, notes: String = "") {
        viewModelScope.launch {
            val bill = RecurringBill(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = _userId.value,
                name = name,
                purpose = purpose,
                category = category,
                appName = appName,
                amount = amount,
                dayOfMonth = dayOfMonth,
                notes = notes,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertRecurringBill(bill)
        }
    }

    fun updateRecurringBill(bill: RecurringBill) {
        viewModelScope.launch {
            repository.updateRecurringBill(bill)
        }
    }

    fun deleteRecurringBill(bill: RecurringBill) {
        viewModelScope.launch {
            repository.deleteRecurringBill(bill)
        }
    }

    private val _historyStatus = MutableStateFlow<AiHistoryStatus>(AiHistoryStatus.Idle)
    val historyStatus: StateFlow<AiHistoryStatus> = _historyStatus

    @OptIn(ExperimentalCoroutinesApi::class)
    val deletedHistory: StateFlow<List<SpendHistory>> = _userId.flatMapLatest { userId ->
        repository.getHistory(userId, HistoryType.DELETED)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val updatedHistory: StateFlow<List<SpendHistory>> = _userId.flatMapLatest { userId ->
        repository.getHistory(userId, HistoryType.UPDATED)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun restoreSpend(history: SpendHistory) {
        viewModelScope.launch {
            repository.restoreFromHistory(history)
        }
    }

    fun permanentlyDeleteHistory(history: SpendHistory) {
        viewModelScope.launch {
            repository.permanentlyDeleteHistory(history)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.clearHistory(_userId.value, HistoryType.DELETED)
        }
    }

    fun clearUpdateHistory() {
        viewModelScope.launch {
            repository.clearHistory(_userId.value, HistoryType.UPDATED)
        }
    }

    fun askAiAboutHistory(question: String) {
        if (question.isBlank()) return
        
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val userId = _userId.value
            if (currentSessionId.isEmpty()) {
                currentSessionId = java.util.UUID.randomUUID().toString()
            }
            
            // 1. Rate Limiting Check (Client Side)
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val sessionCount = chatDao.getSessionCountSince(userId, todayStart)
            val msgCountInSession = chatDao.getMessageCountInSession(userId, currentSessionId)

            if (msgCountInSession >= 7) {
                if (sessionCount >= 2) {
                    _historyStatus.value = AiHistoryStatus.Error(
                        "You've reached your daily limit of 2 sessions (7 messages each).",
                        AiErrorType.CLIENT_RATE_LIMIT
                    )
                    return@launch
                } else {
                    currentSessionId = java.util.UUID.randomUUID().toString()
                }
            } else {
                val isCurrentSessionActiveToday = chatDao.isSessionActiveSince(userId, currentSessionId, todayStart)
                if (!isCurrentSessionActiveToday && sessionCount >= 2) {
                    _historyStatus.value = AiHistoryStatus.Error(
                        "You've reached your daily limit of 2 sessions.",
                        AiErrorType.CLIENT_RATE_LIMIT
                    )
                    return@launch
                }
            }

            // 2. Insert User Message
            val userMsg = ChatMessage(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = userId,
                text = question,
                fromUser = true,
                timestamp = System.currentTimeMillis(),
                sessionId = currentSessionId
            )
            repository.insertChatMessage(userMsg)
            _historyStatus.value = AiHistoryStatus.Analyzing

            // 3. Proxy/Audit Layer: Intent Classification
            // We use a small, fast model to check if the question is within the Spendley domain.
            var isOffTopic = false
            try {
                remoteConfig.fetchAndActivate().await()
                val groqKey = remoteConfig.getString("groq_api_key")
                if (groqKey.isNotBlank()) {
                    val classificationPrompt = """
                        Classify the following user question for a personal finance app.
                        Respond with ONLY "FINANCIAL" if it's about spending, budgets, history, or lend/borrow.
                        Respond with "OFF_TOPIC" for anything else (general knowledge, coding, chat, math not related to data, etc.).
                        
                        QUESTION: "$question"
                    """.trimIndent()
                    
                    val classifierRequest = GroqRequest(
                        model = "llama-3.1-8b-instant",
                        messages = listOf(GroqMessage("user", classificationPrompt)),
                        temperature = 0.0
                    )
                    val response = groqApiService.getCompletion("Bearer $groqKey", classifierRequest)
                    if (response.isSuccessful) {
                        val result = response.body()?.choices?.firstOrNull()?.message?.content?.trim()?.uppercase()
                        if (result == "OFF_TOPIC") {
                            isOffTopic = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Classification failed, continuing with strict prompt: ${e.message}")
            }

            if (isOffTopic) {
                val offTopicResponse = "I can only help with Spendley-related information, such as your transactions, budgets, and spending analytics. Please ask something about your finances! 🚀"
                repository.insertChatMessage(
                    ChatMessage(
                        uuid = java.util.UUID.randomUUID().toString(),
                        userId = userId,
                        text = offTopicResponse,
                        fromUser = false,
                        timestamp = System.currentTimeMillis(),
                        sessionId = currentSessionId
                    )
                )
                _historyStatus.value = AiHistoryStatus.Idle
                return@launch
            }

            // 4. Prepare Context (Only if on-topic)
            val allSpends = allSpendsFlow.value
            val filteredSpends = filterSpendsByQuery(allSpends, question)
            val historyPrefs = aiPreferences.value
            val currency = historyPrefs.defaultCurrency.ifBlank { "₹" }
            val today = java.text.SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault()).format(System.currentTimeMillis())
            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val contextText = buildString {
                if (filteredSpends.isEmpty()) {
                    appendLine("No transactions found for this query.")
                } else {
                    val total = filteredSpends.sumOf { it.amount }
                    val oldest = dateFmt.format(filteredSpends.minOf { it.timestamp })
                    val newest = dateFmt.format(filteredSpends.maxOf { it.timestamp })
                    appendLine("=== SUMMARY: ${filteredSpends.size} transactions | Total: $currency${String.format(Locale.getDefault(), "%.2f", total)} | Range: $oldest → $newest ===")
                    appendLine()
                    filteredSpends.forEach { spend ->
                        appendLine("- ${dateFmt.format(spend.timestamp)} | $currency${spend.amount} | ${spend.notes.ifBlank { "—" }} | ${spend.purpose} | ${spend.appName}")
                    }
                }
            }

            // 5. Call AI (Prefer Groq/Llama for open-source & speed) with Retry logic
            var responseText: String? = null
            var lastError: Exception? = null

            for (attempt in 0..1) {
                if (responseText != null) break
                
                try {
                    val groqKey = remoteConfig.getString("groq_api_key")

                    val systemPrompt = """
                        You are a smart, concise Expense Tracker Assistant. Today is $today.
                        
                        USER PREFERENCE:
                        - Default Currency: $currency
                        
                        CRITICAL:
                        - All monetary amounts in your response MUST be prefixed with the user's currency: **$currency**.
                        - Use the currency symbol **$currency** consistently for every amount mentioned.

                        TRANSACTION DATA:
                        $contextText

                        ANALYSIS GUIDELINES:
                        - Compute totals, averages, and comparisons using ONLY the transactions listed above.
                        - Identify top spending category/app and flag unusually large single transactions when relevant.
                        - For trend questions, derive day-over-day or week-over-week patterns from the data when available.
                        - If data is insufficient to answer precisely, state it briefly and suggest what time range would help.
                        - Never fabricate transactions or amounts not present in the data.
                        - If the question is outside the scope of personal finance/transactions, politely decline.

                        RESPONSE FORMAT:
                        - Use **bold** for amounts, category names, app names, and key numbers.
                        - Use bullet points for lists and breakdowns.
                        - For person-grouped data (lending/borrowing), use hierarchical lists:
                          * **Name** (Total: **$currency amount**)
                            - **date**: **$currency amount** — note
                        - Indent nested items with 2 spaces.
                        - End with a short actionable insight when relevant.
                        - Keep responses concise. Do not restate the user's question.
                    """.trimIndent()

                    if (groqKey.isNotBlank()) {
                        Log.d(TAG, "History: Calling Groq (llama-3.3-70b)")
                        val groqRequest = GroqRequest(
                            model = "llama-3.3-70b-versatile",
                            messages = listOf(
                                GroqMessage("system", systemPrompt),
                                GroqMessage("user", "USER QUESTION: \"$question\"")
                            )
                        )
                        val response = groqApiService.getCompletion("Bearer $groqKey", groqRequest)
                        if (response.isSuccessful) {
                            responseText = response.body()?.choices?.firstOrNull()?.message?.content
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Groq History Error: ${response.code()} - $errorBody")
                            throw Exception("Groq API error: ${response.code()}")
                        }
                    } else {
                        Log.d(TAG, "History: Groq key missing, falling back to Gemini")
                        val geminiKey = remoteConfig.getString("gemini_api_key")
                        if (geminiKey.isBlank()) {
                            _historyStatus.value = AiHistoryStatus.Error("AI configuration is missing.", AiErrorType.API_KEY_MISSING)
                            repository.deleteChatMessage(userMsg)
                            return@launch
                        }
                        val generativeModel = GenerativeModel(modelName = GEMINI_MODEL, apiKey = geminiKey)
                        responseText = generativeModel.generateContent(content {
                            text(systemPrompt)
                            text("USER QUESTION: \"$question\"")
                        }).text
                    }
                } catch (e: Exception) {
                    lastError = e
                    val rawError = e.message ?: ""
                    val isRetryable = rawError.contains("503") || 
                            rawError.contains("504") || 
                            rawError.contains("high demand", ignoreCase = true) ||
                            rawError.contains("unavailable", ignoreCase = true)
                            
                    if (isRetryable && attempt == 0) {
                        delay(2000) // Wait 2 seconds before retrying
                        continue
                    }
                    break // Non-retryable error or max retries reached
                }
            }

            if (!responseText.isNullOrBlank()) {
                repository.insertChatMessage(
                    ChatMessage(
                        uuid = java.util.UUID.randomUUID().toString(),
                        userId = userId,
                        text = responseText,
                        fromUser = false,
                        timestamp = System.currentTimeMillis(),
                        sessionId = currentSessionId
                    )
                )
                _historyStatus.value = AiHistoryStatus.Idle
            } else if (lastError != null) {
                val e = lastError
                Log.e(TAG, "History AI Error: ${e.message}", e)
                val rawError = e.message ?: "An unexpected error occurred."
                
                val errorType = when {
                    rawError.contains("quota", ignoreCase = true) || 
                    rawError.contains("rate limit", ignoreCase = true) ||
                    rawError.contains("429") -> AiErrorType.SERVER_RATE_LIMIT
                    
                    rawError.contains("503") || 
                    rawError.contains("high demand", ignoreCase = true) ||
                    rawError.contains("unavailable", ignoreCase = true) ||
                    rawError.contains("overloaded", ignoreCase = true) ||
                    rawError.contains("experiencing high demand", ignoreCase = true) -> AiErrorType.SERVER_RATE_LIMIT
                    
                    rawError.contains("API key", ignoreCase = true) -> AiErrorType.API_KEY_MISSING
                    else -> AiErrorType.GENERIC
                }

                val userFriendlyMsg = when (errorType) {
                    AiErrorType.SERVER_RATE_LIMIT -> "The AI service is currently under high demand or you've reached the rate limit. Please try again in a few minutes."
                    AiErrorType.API_KEY_MISSING -> "AI configuration is incorrect or missing."
                    else -> "Sorry, I couldn't process that right now. Please try again later."
                }

                _historyStatus.value = AiHistoryStatus.Error(userFriendlyMsg, errorType)
                repository.deleteChatMessage(userMsg)
            }
        }
    }

    val aiPreferences = aiPrefsRepository.aiPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiPreferences()
    )

    fun updateAiPreferences(currency: String, app: String, purpose: String) {
        viewModelScope.launch {
            aiPrefsRepository.updateSettings(currency, app, purpose)
        }
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPrefsRepository.updateBiometricEnabled(enabled)
        }
    }

    fun dismissUpdateVersion(version: String) {
        viewModelScope.launch {
            aiPrefsRepository.setDismissedUpdateVersion(version)
        }
    }

    private val remoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = remoteConfigSettings {
                // Lower interval for development to pick up key changes faster
                minimumFetchIntervalInSeconds = 60 
            }
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(mapOf(
                "gemini_api_key" to "",
                "groq_api_key" to ""
            ))
        }
    }

    private val _aiResult = MutableStateFlow<Result<AiTransactionResponse>?>(null)
    val aiResult: StateFlow<Result<AiTransactionResponse>?> = _aiResult

    fun processAiInput(text: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val prefs = aiPreferences.value

            // 1. Check Rate Limit
            if (prefs.dailyUsageCount >= 15) {
                _aiResult.value = Result.failure(Exception("Daily limit reached (15/day). Please try again tomorrow."))
                return@launch
            }

            // 2. Validate Input
            if (text.isBlank()) {
                _aiResult.value = Result.failure(Exception("Input cannot be empty."))
                return@launch
            }
            if (text.length > 500) {
                _aiResult.value = Result.failure(Exception("Input too long (max 500 characters)."))
                return@launch
            }

            // 3. Run the local heuristic parser first — gives us a deterministic
            // baseline (and a usable response even if the LLM is down).
            val baseline = AiParser.parseToBaseline(text, prefs.defaultApp, prefs.defaultPurpose)
            val localCurrency = AiParser.extractCurrency(text) ?: prefs.defaultCurrency.ifBlank { "INR" }

            // 4. Try AI (Prefer Groq for speed and open-weights models) with Retry logic
            var responseText: String? = null
            var lastError: Exception? = null

            for (attempt in 0..1) {
                if (responseText != null) break
                
                try {
                    remoteConfig.fetchAndActivate().await()
                    val groqKey = remoteConfig.getString("groq_api_key")
                    
                    val appList = com.alpha.spendtracker.ui.components.APP_PRESETS
                        .joinToString(", ") { it.displayName }
                    val purposeList = com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
                        .joinToString(", ")

                    val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
                    val systemPrompt = """
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
                        Default → "Others"

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

                    if (groqKey.isNotBlank()) {
                        Log.d(TAG, "Input: Calling Groq (llama-3.1-8b)")
                        val groqRequest = GroqRequest(
                            model = "llama-3.1-8b-instant",
                            messages = listOf(
                                GroqMessage("system", systemPrompt),
                                GroqMessage("user", "USER INPUT: \"$text\"")
                            ),
                            response_format = GroqResponseFormat()
                        )
                        val response = groqApiService.getCompletion("Bearer $groqKey", groqRequest)
                        if (response.isSuccessful) {
                            responseText = response.body()?.choices?.firstOrNull()?.message?.content
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Groq Input Error: ${response.code()} - $errorBody")
                            throw Exception("Groq API error: ${response.code()}")
                        }
                    } else {
                        Log.d(TAG, "Input: Groq key missing, falling back to Gemini")
                        val geminiKey = remoteConfig.getString("gemini_api_key")
                        if (geminiKey.isBlank()) {
                            Log.w(TAG, "AI API Keys are missing in Remote Config")
                            _aiResult.value = Result.success(baseline)
                            return@launch
                        }
                        val generativeModel = GenerativeModel(modelName = GEMINI_MODEL, apiKey = geminiKey)
                        responseText = generativeModel.generateContent(content {
                            text(systemPrompt)
                            text("USER INPUT: \"$text\"")
                        }).text
                    }
                } catch (e: Exception) {
                    lastError = e
                    val msg = e.message ?: ""
                    val isRetryable = msg.contains("503") || msg.contains("504") || msg.contains("high demand", ignoreCase = true)
                    
                    if (isRetryable && attempt == 0) {
                        delay(2000)
                        continue
                    }
                    break
                }
            }

            if (com.alpha.spendtracker.BuildConfig.DEBUG) Log.d(TAG, "AI Raw Response: $responseText")

            val merged = if (responseText.isNullOrBlank()) {
                if (lastError != null) {
                    Log.e(TAG, "AI Error after retries: ${lastError.message}", lastError)
                }
                baseline
            } else {
                aiPrefsRepository.incrementUsage()
                parseAndMerge(responseText, baseline)
            }

            _aiResult.value = Result.success(merged)
        }
    }

    /**
     * Parse the LLM JSON and merge with the local-parser baseline. Per field:
     * AI wins if it provided a non-blank, valid value; otherwise we keep baseline.
     */
    private fun parseAndMerge(responseText: String, baseline: AiTransactionResponse): AiTransactionResponse {
        val jsonString = run {
            val start = responseText.indexOf("{")
            val end = responseText.lastIndexOf("}")
            if (start in 0 until end) responseText.substring(start, end + 1) else responseText
        }
        val json = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            // Don't log the raw payload (PII) in release; length is enough to diagnose.
            Log.e(TAG, "JSON Parse Failed (len=${responseText?.length ?: 0})", e)
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
        val finalPreset = aiPreset ?: AiParser.normalizeAppToPreset(baseline.appName)
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

    fun clearAiResult() {
        _aiResult.value = null
    }

    fun cancelAiInput() {
        aiJob?.cancel()
        aiJob = null
        _aiResult.value = null
    }

    // Raw spends flow from Room database filtered by current user
    @OptIn(ExperimentalCoroutinesApi::class)
    val allSpendsFlow: StateFlow<List<Spend>> = _userId.flatMapLatest { userId ->
        repository.getAllSpends(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val selectedFilter = MutableStateFlow(TimeFilter.MONTH)
    val customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)

    val uiState: StateFlow<SpendingAnalytics> = combine(
        allSpendsFlow,
        selectedFilter,
        customDateRange
    ) { spends, filter, range ->
        // Exclude lending/borrowing from main dashboard analytics
        val userSpends = spends.filter { it.purpose != "Lending" && it.purpose != "Borrowing" }
        
        val filtered = filterSpendsByTime(userSpends, filter, range)
        val prevTotal = calculatePreviousPeriodTotal(userSpends, filter, range)
        
        calculateAnalytics(filtered, filter, range, prevTotal)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpendingAnalytics()
    )

    fun addSpend(
        appName: String,
        amount: Double,
        purpose: String,
        category: String,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val spend = Spend(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = _userId.value,
                appName = appName,
                amount = amount,
                purpose = purpose,
                category = category,
                timestamp = timestamp,
                notes = notes,
                updatedAt = System.currentTimeMillis()
            )
            repository.insert(spend)
        }
    }

    fun updateSpend(spend: Spend) {
        viewModelScope.launch {
            repository.insert(spend)
        }
    }

    fun deleteSpend(spend: Spend) {
        viewModelScope.launch {
            repository.delete(spend)
        }
    }

    fun setFilter(filter: TimeFilter) {
        selectedFilter.value = filter
    }

    fun setCustomRange(start: Long, end: Long) {
        customDateRange.value = Pair(start, end)
        selectedFilter.value = TimeFilter.CUSTOM
    }

    // Helper: Filter spends mathematically based on selected TimeFilter
    private fun filterSpendsByTime(spends: List<Spend>, filter: TimeFilter, range: Pair<Long, Long>? = null): List<Spend> {
        if (filter == TimeFilter.ALL) return spends

        val startOfPeriod = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (filter) {
            TimeFilter.DAY -> {}
            TimeFilter.WEEK -> startOfPeriod.set(Calendar.DAY_OF_WEEK, startOfPeriod.firstDayOfWeek)
            TimeFilter.MONTH -> startOfPeriod.set(Calendar.DAY_OF_MONTH, 1)
            TimeFilter.YEAR -> startOfPeriod.set(Calendar.DAY_OF_YEAR, 1)
            TimeFilter.CUSTOM -> {
                return if (range != null) {
                    spends.filter { it.timestamp in range.first..range.second }
                } else spends
            }
            TimeFilter.ALL -> return spends
        }

        val startMillis = startOfPeriod.timeInMillis
        return spends.filter { it.timestamp >= startMillis }
    }

    // Helper: Calculate advanced metrics and grouping data categories for high-fidelity dashboards
    private fun calculateAnalytics(
        spends: List<Spend>,
        filter: TimeFilter,
        range: Pair<Long, Long>? = null,
        previousPeriodTotal: Double = 0.0
    ): SpendingAnalytics {
        if (spends.isEmpty()) {
            return SpendingAnalytics(
                totalAmount = 0.0,
                filterType = filter,
                dateRange = range,
                previousPeriodTotal = previousPeriodTotal
            )
        }

        val total = spends.sumOf { it.amount }

        // Category breakdown
        val categoryTotals = spends.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // Purpose (Lending, Groceries etc.) breakdown
        val purposeTotals = spends.groupBy { it.purpose }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        // App/Platform-wise breakdown
        val appTotals = spends.groupBy { it.appName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        // Trend Breakdown for beautiful graph (bar/line charts based on day index or calendar buckets)
        val trendPoints = calculateTrendPoints(spends, filter)

        val topCategory = categoryTotals.maxByOrNull { it.value }?.toPair()
        val (elapsedDays, totalPeriodDays) = periodDaysInfo(filter, range)
        val dailyAverage = if (elapsedDays > 0) total / elapsedDays else 0.0
        val projectedTotal = if (totalPeriodDays != null && elapsedDays in 1 until totalPeriodDays) {
            dailyAverage * totalPeriodDays
        } else null

        return SpendingAnalytics(
            totalAmount = total,
            categoryBreakdown = categoryTotals,
            purposeBreakdown = purposeTotals,
            appBreakdown = appTotals,
            trendPoints = trendPoints,
            transactionCount = spends.size,
            filterType = filter,
            dateRange = range,
            previousPeriodTotal = previousPeriodTotal,
            dailyAverage = dailyAverage,
            projectedTotal = projectedTotal,
            topCategory = topCategory
        )
    }

    /**
     * Sum of expenses in the period immediately preceding the current one.
     * Returns 0.0 for ALL (no previous defined). For CUSTOM, uses an equal-length window
     * ending just before the current range start.
     */
    private fun calculatePreviousPeriodTotal(
        spends: List<Spend>,
        filter: TimeFilter,
        range: Pair<Long, Long>?
    ): Double {
        if (filter == TimeFilter.ALL || spends.isEmpty()) return 0.0

        val (start, end) = when (filter) {
            TimeFilter.DAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                val s = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                s to cal.timeInMillis - 1
            }
            TimeFilter.WEEK -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.WEEK_OF_YEAR, -1)
                }
                val s = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                s to cal.timeInMillis - 1
            }
            TimeFilter.MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                val s = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                s to cal.timeInMillis - 1
            }
            TimeFilter.YEAR -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_YEAR, 1)
                    add(Calendar.YEAR, -1)
                }
                val s = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                s to cal.timeInMillis - 1
            }
            TimeFilter.CUSTOM -> {
                if (range == null) return 0.0
                val span = range.second - range.first
                (range.first - span - 1) to (range.first - 1)
            }
            TimeFilter.ALL -> return 0.0
        }

        return spends.filter { it.timestamp in start..end }.sumOf { it.amount }
    }

    /**
     * Returns (elapsedDays, totalPeriodDays). totalPeriodDays is null when the period
     * has no fixed length (ALL) — projection is then meaningless.
     */
    private fun periodDaysInfo(filter: TimeFilter, range: Pair<Long, Long>?): Pair<Int, Int?> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        return when (filter) {
            TimeFilter.DAY -> 1 to 1
            TimeFilter.WEEK -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                val elapsed = (((now - cal.timeInMillis) / dayMs) + 1).toInt().coerceIn(1, 7)
                elapsed to 7
            }
            TimeFilter.MONTH -> {
                val cal = Calendar.getInstance()
                val today = cal.get(Calendar.DAY_OF_MONTH)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                today to daysInMonth
            }
            TimeFilter.YEAR -> {
                val cal = Calendar.getInstance()
                val today = cal.get(Calendar.DAY_OF_YEAR)
                val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
                today to daysInYear
            }
            TimeFilter.ALL -> {
                // Use earliest spend timestamp would require the list; fall back to no projection.
                1 to null
            }
            TimeFilter.CUSTOM -> {
                if (range == null) return 1 to null
                val span = (((range.second - range.first) / dayMs) + 1).toInt().coerceAtLeast(1)
                val capped = now.coerceAtMost(range.second)
                val elapsed = (((capped - range.first) / dayMs) + 1).toInt().coerceIn(1, span)
                elapsed to span
            }
        }
    }

    private fun calculateTrendPoints(spends: List<Spend>, filter: TimeFilter): List<TrendPoint> {
        val calendar = Calendar.getInstance()
        
        return when (filter) {
            TimeFilter.DAY -> {
                // Group by hour
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.HOUR_OF_DAY)
                }.map { (hour, items) ->
                    val total = items.sumOf { it.amount }
                    val hourStr = String.format(Locale.getDefault(), "%02d:00", hour)
                    TrendPoint(label = hourStr, amount = total, sortKey = hour)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.WEEK -> {
                // Group by Day of Week
                val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.DAY_OF_WEEK)
                }.map { (dayOfWeek, items) ->
                    val total = items.sumOf { it.amount }
                    val name = dayNames.getOrElse(dayOfWeek - 1) { "Day" }
                    TrendPoint(label = name, amount = total, sortKey = dayOfWeek)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.MONTH -> {
                // Group by day of month (1 to 31)
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.DAY_OF_MONTH)
                }.map { (dayOfMonth, items) ->
                    val total = items.sumOf { it.amount }
                    TrendPoint(label = dayOfMonth.toString(), amount = total, sortKey = dayOfMonth)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.YEAR -> {
                // Group by code of month (0 to 11)
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.MONTH)
                }.map { (monthNum, items) ->
                    val total = items.sumOf { it.amount }
                    val name = monthNames.getOrElse(monthNum) { "Month" }
                    TrendPoint(label = name, amount = total, sortKey = monthNum)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.ALL -> {
                // Group by month-year or simply calendar year
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.YEAR)
                }.map { (yr, items) ->
                    val total = items.sumOf { it.amount }
                    TrendPoint(label = yr.toString(), amount = total, sortKey = yr)
                }.sortedBy { it.sortKey }
            }
            TimeFilter.CUSTOM -> {
                // For custom range, show short date format like "22 May"
                val sdf = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.DAY_OF_YEAR)
                }.map { (dayOfYear, items) ->
                    val total = items.sumOf { it.amount }
                    val firstItem = items.first()
                    TrendPoint(label = sdf.format(firstItem.timestamp), amount = total, sortKey = dayOfYear)
                }.sortedBy { it.sortKey }
            }
        }
    }

    /**
     * Filters transactions based on the user's question to optimize the AI prompt payload.
     */
    private fun filterSpendsByQuery(spends: List<Spend>, query: String): List<Spend> {
        val lower = query.lowercase()
        
        // 1. Determine Time Range
        val calendar = Calendar.getInstance()
        val startOfToday = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeFiltered = when {
            lower.contains("today") -> spends.filter { it.timestamp >= startOfToday }
            lower.contains("yesterday") -> {
                val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000
                spends.filter { it.timestamp in startOfYesterday until startOfToday }
            }
            lower.contains("week") -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                spends.filter { it.timestamp >= calendar.timeInMillis }
            }
            lower.contains("month") -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                spends.filter { it.timestamp >= calendar.timeInMillis }
            }
            lower.contains("year") -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                spends.filter { it.timestamp >= calendar.timeInMillis }
            }
            else -> spends // Default to all if no time mentioned (AI will handle sorting)
        }

        // 2. Filter by Category/Purpose or App name if specifically mentioned
        val categories = com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
        val apps = com.alpha.spendtracker.ui.components.APP_PRESETS.map { it.displayName }
        
        val mentionedCategory = categories.firstOrNull { lower.contains(it.lowercase()) }
        val mentionedApp = apps.firstOrNull { lower.contains(it.lowercase()) }

        var finalFiltered = timeFiltered
        if (mentionedCategory != null) {
            finalFiltered = finalFiltered.filter { it.purpose.equals(mentionedCategory, ignoreCase = true) || it.category.equals(mentionedCategory, ignoreCase = true) }
        }
        if (mentionedApp != null) {
            finalFiltered = finalFiltered.filter { it.appName.contains(mentionedApp, ignoreCase = true) }
        }

        // 3. Final safety: If the list is still too long, take the most recent 200
        // to ensure we don't hit payload limits but keep enough context.
        return finalFiltered.sortedByDescending { it.timestamp }.take(200)
    }
}

/**
 * Encapsulates spending metrics, groups, and breakdown reports for user interface rendering
 */
@Immutable
data class SpendingAnalytics(
    val totalAmount: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val purposeBreakdown: Map<String, Double> = emptyMap(),
    val appBreakdown: List<Pair<String, Double>> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val transactionCount: Int = 0,
    val filterType: TimeFilter = TimeFilter.MONTH,
    val dateRange: Pair<Long, Long>? = null,
    val previousPeriodTotal: Double = 0.0,
    val dailyAverage: Double = 0.0,
    val projectedTotal: Double? = null,
    val topCategory: Pair<String, Double>? = null
)

/**
 * Representation of one interval aggregate in trend visualization
 */
data class TrendPoint(
    val label: String,
    val amount: Double,
    val sortKey: Int
)

