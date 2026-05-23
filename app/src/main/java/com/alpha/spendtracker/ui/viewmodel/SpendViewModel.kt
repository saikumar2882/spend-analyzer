/**
 * ViewModel that manages spend data and analytics for the UI components.
 */
package com.alpha.spendtracker.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alpha.spendtracker.data.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.alpha.spendtracker.BuildConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

enum class TimeFilter {
    DAY, WEEK, MONTH, YEAR, ALL, CUSTOM
}

/**
 * Main ViewModel to manage Spending Tracker operations, analytics, and states
 */
class SpendViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SpendRepository
    private val aiPrefsRepository: AiPreferencesRepository
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "SpendViewModel"

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SpendRepository(database.spendDao())
        aiPrefsRepository = AiPreferencesRepository(application)
        
        // Listen to auth changes
        auth.addAuthStateListener { firebaseAuth ->
            _userId.value = firebaseAuth.currentUser?.uid ?: "anonymous"
        }
    }

    private val _userId = MutableStateFlow(auth.currentUser?.uid ?: "anonymous")

    val aiPreferences = aiPrefsRepository.aiPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiPreferences()
    )

    private val generativeModel by lazy {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = apiKey,
            requestOptions = RequestOptions(apiVersion = "v1")
        )
    }

    private val _aiResult = MutableStateFlow<Result<AiTransactionResponse>?>(null)
    val aiResult: StateFlow<Result<AiTransactionResponse>?> = _aiResult

    fun processAiInput(text: String) {
        viewModelScope.launch {
            val prefs = aiPreferences.value

            // 1. Check Rate Limit
            if (prefs.dailyUsageCount >= 10) {
                _aiResult.value = Result.failure(Exception("Daily limit reached (10/day). Please try again tomorrow."))
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

            // 4. Try Gemini for richer extraction. If anything fails, fall back
            // to the local result (still useful).
            val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
            if (apiKey.isBlank()) {
                aiPrefsRepository.incrementUsage()
                _aiResult.value = Result.success(baseline)
                return@launch
            }

            try {
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
                    - "lent to friend", "loan" -> "Friend Lending"
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
                // Don't block the user — they typed something meaningful, we have a baseline.
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

    fun updateAiSettings(currency: String, app: String, purpose: String) {
        viewModelScope.launch {
            aiPrefsRepository.updateSettings(currency, app, purpose)
        }
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
        calculateAnalytics(filterSpendsByTime(spends, filter, range), filter, range)
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

    fun deleteSpendById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id, _userId.value)
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
    private fun calculateAnalytics(spends: List<Spend>, filter: TimeFilter, range: Pair<Long, Long>? = null): SpendingAnalytics {
        if (spends.isEmpty()) {
            return SpendingAnalytics(totalAmount = 0.0, filterType = filter, dateRange = range)
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

        // Count for friend lending specifically to display metrics/badge
        val friendLendingTotal = spends.filter { 
            it.purpose == "Friend Lending" || it.appName == "Friend Lending" 
        }.sumOf { it.amount }

        return SpendingAnalytics(
            totalAmount = total,
            categoryBreakdown = categoryTotals,
            purposeBreakdown = purposeTotals,
            appBreakdown = appTotals,
            trendPoints = trendPoints,
            friendLendingTotal = friendLendingTotal,
            transactionCount = spends.size,
            filterType = filter,
            dateRange = range
        )
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
    val friendLendingTotal: Double = 0.0,
    val transactionCount: Int = 0,
    val filterType: TimeFilter = TimeFilter.MONTH,
    val dateRange: Pair<Long, Long>? = null
)

/**
 * Representation of one interval aggregate in trend visualization
 */
data class TrendPoint(
    val label: String,
    val amount: Double,
    val sortKey: Int
)

/**
 * Factory class to instantiate Android ViewModel with local context setup
 */
class SpendViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpendViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpendViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
