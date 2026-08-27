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
import kotlinx.coroutines.CancellationException
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
    private val groqApiService: GroqApiService,
    private val aiTransactionProcessor: AiTransactionProcessor,
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "SpendViewModel"
        // Single source of truth for the Gemini fallback model so the two call sites can't drift.
        private const val GEMINI_MODEL = "gemini-3.5-flash"

        private const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Day the trend chart's week buckets start on. Deliberately Monday rather than the locale's
         * `firstDayOfWeek` (Sunday in en-IN): the buckets are read as "the working week", and a
         * Sunday start splits every weekend across two bars.
         */
        private const val TREND_WEEK_START = Calendar.MONDAY

        /**
         * A custom range longer than this switches from one bar per day to one bar per week. Below
         * it, weekly buckets would collapse a range into two or three bars and say nothing.
         */
        private const val CUSTOM_TREND_DAILY_MAX_DAYS = 14

        private val MONTH_ABBREVIATIONS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
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

    /**
     * Health of the background push to Firestore, straight from the repository. Surfaced so the UI
     * can tell the user their changes are only on this device — cloud write failures used to go
     * nowhere but Logcat.
     */
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    /**
     * Runs a repository mutation and hands the outcome back to the caller.
     *
     * Every mutation goes through here so no screen can report success for a write that failed —
     * which is what happened while these methods returned Unit and the UI showed its confirmation
     * unconditionally.
     */
    private fun mutate(onResult: (Result<Unit>) -> Unit, block: suspend () -> Result<Unit>) {
        viewModelScope.launch { onResult(block()) }
    }

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
                    .onFailure { Log.w(TAG, "Chat cleanup failed: ${it.message}") }
            }
        }

        // Cleanup old history (30 days)
        viewModelScope.launch {
            auth.currentUser?.uid?.let { uid ->
                repository.cleanupOldHistory(uid, 30)
                    .onFailure { Log.w(TAG, "History cleanup failed: ${it.message}") }
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
            val lastSessionId = repository.getLastSessionId(userId).getOrNull()
            currentSessionId = lastSessionId ?: java.util.UUID.randomUUID().toString()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatHistory: StateFlow<List<ChatMessage>> = _userId.flatMapLatest { userId ->
        repository.getChatMessages(userId)
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

    fun addRecurringBill(
        name: String,
        purpose: String,
        category: String,
        appName: String,
        amount: Double,
        dayOfMonth: Int,
        notes: String = "",
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        mutate(onResult) {
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

    fun updateRecurringBill(bill: RecurringBill, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.updateRecurringBill(bill) }

    fun deleteRecurringBill(bill: RecurringBill, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.deleteRecurringBill(bill) }

    // ---- Notes ----
    // Notes are custom collections; noteEntries holds every entry for the user and the UI
    // groups them by noteUuid. Amounts here are intentionally never fed into analytics.

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<Note>> = _userId.flatMapLatest { userId ->
        repository.getAllNotes(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val noteEntries: StateFlow<List<NoteEntry>> = _userId.flatMapLatest { userId ->
        repository.getAllNoteEntries(userId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addNote(title: String, colorIndex: Int, onResult: (Result<Unit>) -> Unit = {}) {
        mutate(onResult) {
            val now = System.currentTimeMillis()
            val note = Note(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = _userId.value,
                title = title,
                colorIndex = colorIndex,
                createdAt = now,
                updatedAt = now
            )
            repository.insertNote(note)
        }
    }

    fun updateNote(note: Note, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.updateNote(note) }

    /** Outcome of [logNoteAsTransaction], surfaced so the UI can show the right message. */
    enum class LogNoteResult { EMPTY, CREATED, UPDATED }

    /**
     * Logs a whole note as a single spend in the main transaction log: amount = sum of the
     * note's entry amounts, purpose = note title, appName = the user's default payment app
     * ([defaultApp], "Google Pay" by default), category derived from that app's preset.
     * Upserts by [Note.uuid] — re-logging after adding entries updates the same transaction
     * instead of creating duplicates — so tapping it in History always maps back to one note.
     */
    fun logNoteAsTransaction(
        note: Note,
        defaultApp: String,
        onComplete: (Result<LogNoteResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val userId = _userId.value
            val total = noteEntries.value
                .filter { it.noteUuid == note.uuid }
                .sumOf { it.amount }
            if (total <= 0.0) {
                onComplete(Result.success(LogNoteResult.EMPTY))
                return@launch
            }
            val app = defaultApp.ifBlank { "Google Pay" }
            val category = com.alpha.spendtracker.ui.components.APP_PRESETS
                .find { it.displayName == app }?.category ?: "Other"
            val now = System.currentTimeMillis()
            val existing = repository.getActiveSpendByNoteUuid(userId, note.uuid)
                .getOrElse { onComplete(Result.failure(it)); return@launch }
            val spend = (existing ?: Spend(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = userId,
                timestamp = now
            )).copy(
                appName = app,
                amount = total,
                purpose = note.title,
                category = category,
                notes = "",
                noteUuid = note.uuid,
                updatedAt = now
            )
            onComplete(
                repository.insert(spend).map {
                    if (existing != null) LogNoteResult.UPDATED else LogNoteResult.CREATED
                }
            )
        }
    }

    fun deleteNote(note: Note, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.deleteNote(note) }

    fun addNoteEntry(
        noteUuid: String,
        label: String,
        amount: Double,
        date: Long,
        detail: String?,
        customFields: List<NoteField>,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        mutate(onResult) {
            val now = System.currentTimeMillis()
            val entry = NoteEntry(
                uuid = java.util.UUID.randomUUID().toString(),
                userId = _userId.value,
                noteUuid = noteUuid,
                label = label,
                amount = amount,
                detail = detail,
                date = date,
                customFields = customFields,
                createdAt = now,
                updatedAt = now
            )
            repository.insertNoteEntry(entry)
        }
    }

    fun updateNoteEntry(entry: NoteEntry, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.updateNoteEntry(entry) }

    fun deleteNoteEntry(entry: NoteEntry, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.deleteNoteEntry(entry) }

    // ---- Notes history (Recycle Bin + Update History) ----

    @OptIn(ExperimentalCoroutinesApi::class)
    val noteDeletedHistory: StateFlow<List<NoteHistory>> = _userId.flatMapLatest { userId ->
        repository.getNoteHistory(userId, HistoryType.DELETED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val noteUpdatedHistory: StateFlow<List<NoteHistory>> = _userId.flatMapLatest { userId ->
        repository.getNoteHistory(userId, HistoryType.UPDATED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreNoteHistory(history: NoteHistory, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.restoreNoteFromHistory(history) }

    fun permanentlyDeleteNoteHistory(history: NoteHistory, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.permanentlyDeleteNoteHistory(history) }

    fun emptyNoteTrash(onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.clearNoteHistory(_userId.value, HistoryType.DELETED) }

    fun clearNoteUpdateHistory(onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.clearNoteHistory(_userId.value, HistoryType.UPDATED) }

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

    fun restoreSpend(history: SpendHistory, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.restoreFromHistory(history) }

    fun permanentlyDeleteHistory(history: SpendHistory, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.permanentlyDeleteHistory(history) }

    fun emptyTrash(lendBorrow: Boolean, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.clearHistory(_userId.value, HistoryType.DELETED, lendBorrow) }

    fun clearUpdateHistory(lendBorrow: Boolean, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.clearHistory(_userId.value, HistoryType.UPDATED, lendBorrow) }

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

            // A failed count would silently hand out unlimited AI sessions, so fail closed.
            val sessionCount = repository.getSessionCountSince(userId, todayStart).getOrElse {
                _historyStatus.value = AiHistoryStatus.Error(it.userMessageOrGeneric(), AiErrorType.GENERIC)
                return@launch
            }
            val msgCountInSession = repository.getMessageCountInSession(userId, currentSessionId).getOrElse {
                _historyStatus.value = AiHistoryStatus.Error(it.userMessageOrGeneric(), AiErrorType.GENERIC)
                return@launch
            }

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
                val isCurrentSessionActiveToday =
                    repository.isSessionActiveSince(userId, currentSessionId, todayStart).getOrDefault(false)
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
                        Respond with ONLY "FINANCIAL" if it is about the user's spending, transactions,
                        budgets, categories, payment apps, history, analytics, or lend/borrow — including
                        greetings and questions about what this assistant can do.
                        Respond with ONLY "OFF_TOPIC" for anything else (general knowledge, coding, news,
                        recipes, or maths unrelated to the user's own data).

                        QUESTION: "$question"
                    """.trimIndent()

                    val classifierRequest = GroqRequest(
                        model = GroqModels.FAST,
                        messages = listOf(GroqMessage("user", classificationPrompt)),
                        temperature = 0.0,
                        reasoning_effort = "low",
                        include_reasoning = false,
                        max_completion_tokens = 16
                    )
                    val response = groqApiService.getCompletion("Bearer $groqKey", classifierRequest)
                    if (response.isSuccessful) {
                        // Fail OPEN: only a verdict that says OFF_TOPIC and not FINANCIAL blocks the
                        // question. An exact-string check used to be the gate, so any decoration
                        // ("OFF_TOPIC.") slipped through — and the reverse, a chatty verdict, must
                        // never cost the user a real answer.
                        val verdict = response.body()?.choices?.firstOrNull()?.message?.content
                            ?.trim()?.uppercase().orEmpty()
                        isOffTopic = verdict.contains("OFF_TOPIC") && !verdict.contains("FINANCIAL")
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty().take(300)
                        Log.w(TAG, "Classification HTTP ${response.code()} - $errorBody")
                    }
                }
            } catch (e: CancellationException) {
                throw e
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
            // A filter that lands on zero rows used to end the conversation with a flat
            // "No transactions found for this query." — the model has nothing to answer
            // from, so the user gets no answer at all. Fall back to the recent log and
            // tell the model that is what it is looking at.
            val usedFallbackContext = filteredSpends.isEmpty() && allSpends.isNotEmpty()
            val contextSpends = if (usedFallbackContext) {
                allSpends.sortedByDescending { it.timestamp }.take(200)
            } else {
                filteredSpends
            }
            val historyPrefs = aiPreferences.value
            val currency = historyPrefs.defaultCurrency.ifBlank { "₹" }
            val today = java.text.SimpleDateFormat("yyyy-MM-dd EEEE", Locale.getDefault()).format(System.currentTimeMillis())
            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val contextText = buildString {
                if (contextSpends.isEmpty()) {
                    appendLine("The user has no recorded transactions at all yet.")
                } else {
                    if (usedFallbackContext) {
                        appendLine("NOTE: nothing matched the narrow filter for this question, so the list below is the user's most recent transactions. Answer from these, and say plainly if the period or category they asked about has none.")
                        appendLine()
                    }
                    val total = contextSpends.sumOf { it.amount }
                    val oldest = dateFmt.format(contextSpends.minOf { it.timestamp })
                    val newest = dateFmt.format(contextSpends.maxOf { it.timestamp })
                    appendLine("=== SUMMARY: ${contextSpends.size} transactions | Total: $currency${String.format(Locale.getDefault(), "%.2f", total)} | Range: $oldest → $newest ===")
                    appendLine("ROW FORMAT: - date | amount | purpose | app [| note: text]. A row with no \"note:\" segment simply has no note.")
                    appendLine()
                    contextSpends.forEach { spend ->
                        // A blank note used to be rendered as a bare "—" column. The model read that
                        // as part of the answer template and echoed the literal word "note" back at
                        // the user ("— note"). Omit the segment entirely instead, and format the
                        // amount so it doesn't reach the model as a raw Double ("500.0").
                        val amount = String.format(Locale.getDefault(), "%.2f", spend.amount)
                        val noteSegment = if (spend.notes.isBlank()) "" else " | note: ${spend.notes}"
                        appendLine("- ${dateFmt.format(spend.timestamp)} | $currency$amount | ${spend.purpose} | ${spend.appName}$noteSegment")
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
                        - If data is insufficient to answer precisely, still give the closest useful answer you can from the data above, then say in one line what is missing.
                        - Never fabricate transactions or amounts not present in the data.
                        - Never reply with only a refusal or only a clarifying question — always give the user something concrete from their data.
                        - If the question is outside the scope of personal finance/transactions, politely decline.

                        RESPONSE FORMAT:
                        - Use **bold** for amounts, category names, app names, and key numbers.
                        - Use bullet points for lists and breakdowns.
                        - For person-grouped data (lending/borrowing), use hierarchical lists:
                          * **<person>** (Total: **$currency<total>**)
                            - **<date>**: **$currency<amount>** — <that row's note text>
                        - Angle brackets mark placeholders. Never print the brackets and never print
                          the word inside them — substitute the real value. If a transaction has no
                          note, drop the "— <note>" part completely rather than writing "note".
                        - Indent nested items with 2 spaces.
                        - Finish every list you start. If there are too many transactions to list in
                          full, group or summarise them instead of stopping mid-line.
                        - End with a short actionable insight when relevant.
                        - Keep responses concise. Do not restate the user's question.
                    """.trimIndent()

                    if (groqKey.isNotBlank()) {
                        Log.d(TAG, "History: Calling Groq (${GroqModels.SMART})")
                        val groqRequest = GroqRequest(
                            model = GroqModels.SMART,
                            messages = listOf(
                                GroqMessage("system", systemPrompt),
                                GroqMessage("user", "USER QUESTION: \"$question\"")
                            ),
                            temperature = 0.5,
                            // GPT-OSS is a reasoning model: without include_reasoning=false the
                            // chain-of-thought is what lands in `content` and the user sees the
                            // model thinking out loud instead of an answer.
                            //
                            // `reasoning_effort` and the token budget are coupled here. A
                            // per-person breakdown over a few hundred transactions is a long
                            // answer, and at "medium" effort a 1500-token budget ran out partway
                            // through the list — the reply arrived cut off mid-date. Low effort
                            // plus a much larger ceiling leaves room for the whole answer;
                            // finish_reason below reports it if that is still not enough.
                            reasoning_effort = "low",
                            include_reasoning = false,
                            max_completion_tokens = 4096
                        )
                        val response = groqApiService.getCompletion("Bearer $groqKey", groqRequest)
                        if (response.isSuccessful) {
                            val choice = response.body()?.choices?.firstOrNull()
                            if (choice?.finish_reason == "length") {
                                Log.w(TAG, "History: answer truncated (hit max_completion_tokens)")
                            }
                            responseText = choice?.message?.content
                        } else {
                            // The body is the only place a decommissioned model announces itself.
                            val errorBody = response.errorBody()?.string().orEmpty().take(300)
                            Log.e(TAG, "Groq History Error: ${response.code()} - $errorBody")
                            throw Exception("Groq API error: ${response.code()} $errorBody")
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
                } catch (e: CancellationException) {
                    throw e
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

    /** Records that the one-time app-lock enable prompt has been shown. */
    fun setBiometricPrompted() {
        viewModelScope.launch {
            aiPrefsRepository.setBiometricPrompted()
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
            _aiResult.value = aiTransactionProcessor.parse(text, aiPreferences.value)
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
        timestamp: Long = System.currentTimeMillis(),
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        mutate(onResult) {
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

    fun updateSpend(spend: Spend, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.insert(spend) }

    fun deleteSpend(spend: Spend, onResult: (Result<Unit>) -> Unit = {}) =
        mutate(onResult) { repository.delete(spend) }

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
        val trendPoints = calculateTrendPoints(spends, filter, range)

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

    private fun calculateTrendPoints(
        spends: List<Spend>,
        filter: TimeFilter,
        range: Pair<Long, Long>? = null
    ): List<TrendPoint> {
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
                // One bar per calendar *week* of the month rather than per day. 31 day-bars in
                // ~330dp left ~10dp a slot, which is what forced the rotated micro-labels and made
                // the month view unreadable; five or six week-bars each carry a legible range
                // ("1–2", "3–9", …) and their own total.
                val monthStart = calendar.let {
                    it.timeInMillis = spends.firstOrNull()?.timestamp ?: System.currentTimeMillis()
                    it.set(Calendar.DAY_OF_MONTH, 1)
                    startOfDayOf(it.timeInMillis)
                }
                val monthEnd = Calendar.getInstance().apply {
                    timeInMillis = monthStart
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }.timeInMillis
                weeklyTrendPoints(spends, monthStart, monthEnd)
            }
            TimeFilter.YEAR -> {
                // Group by code of month (0 to 11)
                spends.groupBy {
                    calendar.timeInMillis = it.timestamp
                    calendar.get(Calendar.MONTH)
                }.map { (monthNum, items) ->
                    val total = items.sumOf { it.amount }
                    val name = MONTH_ABBREVIATIONS.getOrElse(monthNum) { "Month" }
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
                val start = startOfDayOf(range?.first ?: spends.minOfOrNull { it.timestamp } ?: return emptyList())
                val end = startOfDayOf(range?.second ?: spends.maxOfOrNull { it.timestamp } ?: return emptyList())
                val spanDays = ((end - start) / DAY_MS).toInt() + 1

                if (spanDays > CUSTOM_TREND_DAILY_MAX_DAYS) {
                    // Same week buckets as the month view, except the first one starts on the day
                    // the user picked and only runs to the end of *that* week; every bucket after
                    // it is a full week, and the last is clipped at the chosen end date.
                    weeklyTrendPoints(spends, start, end)
                } else {
                    // Short ranges keep a bar per day — and every day in the range gets one, spend
                    // or not, so the axis stays a real timeline instead of silently closing gaps.
                    val sdf = java.text.SimpleDateFormat("dd MMM", Locale.getDefault())
                    val byDay = spends.groupBy { startOfDayOf(it.timestamp) }
                    (0 until spanDays).map { offset ->
                        val day = Calendar.getInstance().apply {
                            timeInMillis = start
                            add(Calendar.DAY_OF_YEAR, offset)
                        }.timeInMillis
                        TrendPoint(
                            label = sdf.format(day),
                            amount = byDay[day]?.sumOf { it.amount } ?: 0.0,
                            sortKey = offset
                        )
                    }
                }
            }
        }
    }

    /**
     * Buckets [spends] into calendar weeks covering `[startDay, endDay]` (both local midnights,
     * inclusive), and labels each bucket with the days it spans.
     *
     * Weeks begin on [TREND_WEEK_START]. The first bucket is only the part of its week that falls
     * inside the range — for August 2026 that makes the buckets 1–2, 3–9, 10–16, 17–23, 24–30, 31 —
     * and the last is clipped at [endDay] the same way. Buckets with no spending are still emitted
     * so the axis reads as a continuous timeline.
     */
    private fun weeklyTrendPoints(spends: List<Spend>, startDay: Long, endDay: Long): List<TrendPoint> {
        if (endDay < startDay) return emptyList()

        val cursor = Calendar.getInstance().apply { timeInMillis = startDay }
        val edge = Calendar.getInstance()
        val labelCal = Calendar.getInstance()
        val points = mutableListOf<TrendPoint>()
        var index = 0

        // Day arithmetic goes through Calendar, not `+ n * DAY_MS`: a DST boundary inside the range
        // would otherwise slide every bucket after it by an hour and misfile the days on the seam.
        while (cursor.timeInMillis <= endDay) {
            val bucketStart = cursor.timeInMillis
            // Days left until this week rolls over — a full 7 for every bucket but the first, which
            // is however much of its week the range actually starts inside.
            val daysToWeekEnd = 6 - ((cursor.get(Calendar.DAY_OF_WEEK) - TREND_WEEK_START + 7) % 7)

            edge.timeInMillis = bucketStart
            edge.add(Calendar.DAY_OF_YEAR, daysToWeekEnd)
            val bucketEnd = minOf(edge.timeInMillis, endDay)
            // Exclusive upper edge — the *next* midnight, so the whole last day is included.
            edge.timeInMillis = bucketEnd
            edge.add(Calendar.DAY_OF_YEAR, 1)
            val bucketEndExclusive = edge.timeInMillis

            points += TrendPoint(
                label = weekBucketLabel(bucketStart, bucketEnd, labelCal),
                amount = spends.filter { it.timestamp in bucketStart until bucketEndExclusive }
                    .sumOf { it.amount },
                sortKey = index++
            )
            cursor.timeInMillis = bucketEndExclusive
        }
        return points
    }

    /**
     * "3–9" inside one month, "31 Jul–6 Aug" when the bucket straddles two, and a bare "31" for a
     * single-day bucket — the label sits under a bar, so every character it doesn't need costs
     * legibility.
     */
    private fun weekBucketLabel(start: Long, endInclusive: Long, cal: Calendar): String {
        cal.timeInMillis = start
        val startDay = cal.get(Calendar.DAY_OF_MONTH)
        val startMonth = cal.get(Calendar.MONTH)
        cal.timeInMillis = endInclusive
        val endDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val endMonth = cal.get(Calendar.MONTH)

        return when {
            startDay == endDayOfMonth && startMonth == endMonth -> "$startDay"
            startMonth == endMonth -> "$startDay–$endDayOfMonth"
            else -> "$startDay ${MONTH_ABBREVIATIONS[startMonth]}–$endDayOfMonth ${MONTH_ABBREVIATIONS[endMonth]}"
        }
    }

    /** Local midnight of the day [millis] falls in. */
    private fun startOfDayOf(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Filters transactions based on the user's question to optimize the AI prompt payload.
     */
    /** Local-midnight epoch millis, [offsetDays] from today. */
    private fun startOfDayOffset(offsetDays: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }.timeInMillis

    /** Local-midnight epoch millis of the first day of the week, [offsetWeeks] from this one. */
    private fun startOfWeekOffset(offsetWeeks: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // set(DAY_OF_WEEK, firstDayOfWeek) can jump FORWARD past today depending on where
        // the locale's week starts; stepping back by the offset never can.
        add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7))
        add(Calendar.WEEK_OF_YEAR, offsetWeeks)
    }.timeInMillis

    private fun startOfMonthOffset(offsetMonths: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offsetMonths)
    }.timeInMillis

    private fun startOfYearOffset(offsetYears: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_YEAR, 1)
        add(Calendar.YEAR, offsetYears)
    }.timeInMillis

    /**
     * The time window a question implies, as `[start, endExclusive)`, or null for "everything".
     *
     * ⚠️ "last"/"previous" used to be ignored completely: "how much did I spend **last month**?"
     * fell into the bare `month` branch and returned **this** month, so the assistant confidently
     * answered a different question — and early in a month it returned nothing at all, which read
     * to the user as the assistant failing to answer.
     */
    private fun resolveQueryRange(lower: String): Pair<Long, Long>? {
        val openEnd = Long.MAX_VALUE

        // An explicit rolling window ("last 7 days", "past 3 months") wins over everything else.
        Regex("""\b(?:last|past|previous|prev)\s+(\d{1,3})\s+(day|week|month|year)s?\b""")
            .find(lower)?.let { match ->
                val n = match.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
                val days = when (match.groupValues[2]) {
                    "day" -> n
                    "week" -> n * 7
                    "month" -> n * 30
                    else -> n * 365
                }
                return startOfDayOffset(-days) to openEnd
            }

        if (Regex("""\b(all time|alltime|overall|ever|lifetime)\b""").containsMatchIn(lower)) {
            return null
        }

        val isPrevious = Regex("""\b(last|previous|prev|past)\b""").containsMatchIn(lower)

        return when {
            lower.contains("day before yesterday") ->
                startOfDayOffset(-2) to startOfDayOffset(-1)
            lower.contains("yesterday") ->
                startOfDayOffset(-1) to startOfDayOffset(0)
            lower.contains("today") ->
                startOfDayOffset(0) to openEnd
            lower.contains("week") -> {
                val thisWeek = startOfWeekOffset(0)
                if (isPrevious) startOfWeekOffset(-1) to thisWeek else thisWeek to openEnd
            }
            lower.contains("month") -> {
                val thisMonth = startOfMonthOffset(0)
                if (isPrevious) startOfMonthOffset(-1) to thisMonth else thisMonth to openEnd
            }
            lower.contains("year") -> {
                val thisYear = startOfYearOffset(0)
                if (isPrevious) startOfYearOffset(-1) to thisYear else thisYear to openEnd
            }
            else -> null
        }
    }

    private fun filterSpendsByQuery(spends: List<Spend>, query: String): List<Spend> {
        val lower = query.lowercase()

        // 1. Determine Time Range
        val range = resolveQueryRange(lower)
        val timeFiltered = if (range == null) {
            spends // No period mentioned — hand the AI everything and let it sort.
        } else {
            spends.filter { it.timestamp >= range.first && it.timestamp < range.second }
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

