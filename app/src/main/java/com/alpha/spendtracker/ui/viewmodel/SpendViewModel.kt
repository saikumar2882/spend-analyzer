/**
 * ViewModel that manages spend data and analytics for the UI components.
 */
package com.alpha.spendtracker.ui.viewmodel

import android.util.Log
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
    EMPTY_RESPONSE,
    GENERIC
}

/**
 * Main ViewModel to manage Spending Tracker operations, analytics, and states
 */
@HiltViewModel
class SpendViewModel @Inject constructor(
    private val repository: SpendRepository,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val chatDao: ChatDao
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "SpendViewModel"
    }

    private val _userId = MutableStateFlow(auth.currentUser?.uid ?: "anonymous")
    private var aiJob: Job? = null
    private var historyJob: Job? = null

    private var currentSessionId: String = ""

    private val _isBiometricAuthenticated = MutableStateFlow(false)
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

        // Listen to auth changes to start/stop sync
        auth.addAuthStateListener { firebaseAuth ->
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

        // Periodic cleanup of old chat messages (12h TTL)
        viewModelScope.launch {
            val threshold = System.currentTimeMillis() - 12 * 60 * 60 * 1000
            chatDao.deleteOldMessages(threshold)
        }
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

    private val _historyStatus = MutableStateFlow<AiHistoryStatus>(AiHistoryStatus.Idle)
    val historyStatus: StateFlow<AiHistoryStatus> = _historyStatus

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
            val userMsg = ChatMessage(userId = userId, text = question, isFromUser = true, sessionId = currentSessionId)
            chatDao.insertMessage(userMsg)
            _historyStatus.value = AiHistoryStatus.Analyzing

            // 3. Prepare Context
            val spends = allSpendsFlow.value
            val contextText = spends.joinToString("\n") { 
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.timestamp)
                "- $date: ${it.amount} on ${it.notes} (${it.purpose}) via ${it.appName}"
            }

            // 4. Call Gemini
            try {
                remoteConfig.fetchAndActivate().await()
                val apiKey = remoteConfig.getString("gemini_api_key")
                if (apiKey.isBlank()) {
                    _historyStatus.value = AiHistoryStatus.Error("AI configuration is missing.", AiErrorType.API_KEY_MISSING)
                    return@launch
                }

                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )

                val systemPrompt = """
                    You are a helpful Expense Tracker Assistant. Use the transaction history below to answer the user's question.
                    
                    FORMATTING RULES:
                    - Use **bold** for amounts, dates, and categories.
                    - Use bullet points (* or -) for lists and breakdowns.
                    - IMPORTANT: If multiple transactions belong to the same person or category, GROUP them hierarchically.
                      Example for multiple lendings:
                      * **Person Name** (Total: **Amount**)
                        - **Date**: **Amount** (**Note**)
                    - Use 2 spaces for indentation in nested lists.
                    - Keep responses concise, accurate, and professional. 
                    
                    HISTORY:
                    $contextText
                """.trimIndent()

                val response = generativeModel.generateContent(
                    content {
                        text(systemPrompt)
                        text("USER QUESTION: \"$question\"")
                    }
                )

                val responseText = response.text
                if (!responseText.isNullOrBlank()) {
                    chatDao.insertMessage(ChatMessage(userId = userId, text = responseText, isFromUser = false, sessionId = currentSessionId))
                    _historyStatus.value = AiHistoryStatus.Idle
                } else {
                    _historyStatus.value = AiHistoryStatus.Error("The AI didn't provide a response.", AiErrorType.EMPTY_RESPONSE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "History AI Error: ${e.message}", e)
                val errorMsg = e.message ?: "An unexpected error occurred."
                val errorType = if (errorMsg.contains("quota", ignoreCase = true) || errorMsg.contains("rate limit", ignoreCase = true)) {
                    AiErrorType.SERVER_RATE_LIMIT
                } else {
                    AiErrorType.GENERIC
                }
                _historyStatus.value = AiHistoryStatus.Error(errorMsg, errorType)
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

    private val remoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600
            }
            setConfigSettingsAsync(configSettings)
            setDefaultsAsync(mapOf("gemini_api_key" to ""))
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
            val localAmount = AiParser.extractAmount(text)
            val localPreset = AiParser.findAppPreset(text)
            val localPurpose = AiParser.inferPurpose(text)
            val localDesc = AiParser.extractDescription(text)
            val localTimestamp = AiParser.extractTimestamp(text)
            val baseline = AiTransactionResponse(
                amount = localAmount,
                appName = localPreset?.displayName ?: prefs.defaultApp,
                appPresetId = localPreset?.id,
                purpose = localPurpose ?: prefs.defaultPurpose,
                notes = localDesc,
                date = "today",
                timestamp = localTimestamp,
                needsAmount = localAmount == null
            )

            // 4. Try Gemini for richer extraction.
            try {
                remoteConfig.fetchAndActivate().await()
                val apiKey = remoteConfig.getString("gemini_api_key")
                if (apiKey.isBlank()) {
                    Log.w(TAG, "Gemini API Key is missing in Remote Config")
                    aiPrefsRepository.incrementUsage()
                    _aiResult.value = Result.success(baseline)
                    return@launch
                }

                val generativeModel = GenerativeModel(
                    modelName = "gemini-3.5-flash",
                    apiKey = apiKey
                )
                
                val appList = com.alpha.spendtracker.ui.components.APP_PRESETS
                    .joinToString(", ") { it.displayName }
                val purposeList = com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
                    .joinToString(", ")

                val systemPrompt = """
                    You are a strict JSON extractor for an expense tracker. Read the user's natural-language sentence and emit ONE JSON object with the fields described below. Output ONLY the JSON, no markdown, no commentary.

                    USER DEFAULTS:
                    - Default Currency: ${prefs.defaultCurrency}
                    - Default App/Platform: ${prefs.defaultApp}
                    - Default Purpose: ${prefs.defaultPurpose}
                    - Today's Date: ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())}

                    FIELDS:
                    - amount (number): the money spent. Use the largest number that represents money. If absent, use null.
                    - appName (string): MUST be one of [$appList]. Map fuzzy mentions: "phone pay" / "phonepay" -> "PhonePe"; "gpay" / "g pay" -> "Google Pay"; "amzn" -> "Amazon". If none mentioned, use "${prefs.defaultApp}".
                    - purpose (string): MUST be one of [$purposeList]. Infer from what was bought. Examples below.
                    - notes (string): a SHORT description of what was bought, in Title Case (1-4 words). e.g. "Biryani", "Uber Ride", "Electricity Bill". DO NOT include the amount, the app name, or filler verbs like "spent". If nothing identifiable, use "".
                    - date (string): YYYY-MM-DD of the spend. Parse natural-language dates relative to "Today's Date" above. Examples: "on 19th may" -> use today's year, month=05, day=19; "yesterday" -> today minus 1 day; "last friday" -> the most recent past Friday. If the result would be in the future (no year given), shift one year back. If no date is mentioned, default to today.
                    - needsAmount (boolean): true ONLY if no amount could be identified.

                    PURPOSE INFERENCE EXAMPLES:
                    - "biryani", "pizza", "lunch", "coffee", "groceries", "swiggy", "zomato" -> "Groceries & Food"
                    - "shirt", "shoes", "dress", "myntra", "ajio" -> "Shopping & Apparels"
                    - "uber", "ola", "petrol", "metro", "flight" -> "Travel & Commute"
                    - "netflix", "movie", "gym" -> "Subscription & Leisure"
                    - "medicine", "doctor", "hospital" -> "Healthcare & Medical"
                    - "rent", "electricity", "wifi", "recharge" -> "Rent & Utilities"
                    - "credit card bill", "cc bill" -> "Credit Card Bill"
                    - "lent to friend", "gave money" -> "Lending"
                    - "borrowed from friend", "took loan" -> "Borrowing"
                    - Otherwise -> "Others"

                    EXAMPLE:
                    Input: "spend 300 on biryani using phone pay"
                    Output: {"amount":300,"appName":"PhonePe","purpose":"Groceries & Food","notes":"Biryani","date":"${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())}","needsAmount":false}

                    JSON SCHEMA:
                    {"amount": number|null, "appName": string, "purpose": string, "notes": string, "date": string, "needsAmount": boolean}
                """.trimIndent()

                val response = generativeModel.generateContent(
                    content {
                        text(systemPrompt)
                        text("USER INPUT: \"$text\"")
                    }
                )

                val responseText = response.text
                Log.d(TAG, "AI Raw Response: $responseText")

                val merged = if (responseText.isNullOrBlank()) {
                    baseline
                } else {
                    parseAndMerge(responseText, baseline)
                }

                aiPrefsRepository.incrementUsage()
                _aiResult.value = Result.success(merged)
            } catch (e: Exception) {
                Log.e(TAG, "AI Error, falling back to local parser: ${e.message}", e)
                aiPrefsRepository.incrementUsage()
                _aiResult.value = Result.success(baseline)
            }
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
            Log.e(TAG, "JSON Parse Failed. Raw: $responseText", e)
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
                userId = _userId.value,
                appName = appName,
                amount = amount,
                purpose = purpose,
                category = category,
                timestamp = timestamp,
                notes = notes
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

    private val firstDayOfWeek: Int get() = Calendar.getInstance().firstDayOfWeek

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
}

/**
 * Encapsulates spending metrics, groups, and breakdown reports for user interface rendering
 */
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

