package com.alpha.spendtracker.widget

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.spendtracker.data.AiParser
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.data.SharedPaymentText
import com.alpha.spendtracker.data.Spend
import com.alpha.spendtracker.data.SpendRepository
import com.alpha.spendtracker.ui.components.toNewSpend
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.util.ReceiptTextReader
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class QuickAddUiState(
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

/** Sentinel error; the activity swaps in the localized "no amount found" message. */
const val SHARE_NO_AMOUNT = "share_no_amount"

/** Sentinel error: the shared image held no readable text. */
const val SHARE_IMAGE_UNREADABLE = "share_image_unreadable"

sealed interface QuickAddEffect {
    /**
     * Tracked directly from widget without opening confirmation modal in main app.
     */
    data class SavedDirectly(val summaryText: String) : QuickAddEffect
}

/**
 * Backs the home-screen widget's input overlay.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val processor: AiTransactionProcessor,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val repository: SpendRepository
) : ViewModel() {

    val aiPreferences: StateFlow<AiPreferences> = aiPrefsRepository.aiPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiPreferences()
    )

    fun updateVoiceLanguage(language: String) {
        viewModelScope.launch {
            aiPrefsRepository.updateVoiceLanguage(language)
        }
    }

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    private val _effects = Channel<QuickAddEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var parseJob: Job? = null

    fun process(text: String) {
        parseJob?.cancel()
        _uiState.value = QuickAddUiState(isProcessing = true)
        parseJob = viewModelScope.launch {
            val prefs = aiPreferences.value
            val result = processor.parse(text, prefs)
            result.fold(
                onSuccess = { responses ->
                    val validResponses = responses.filter { (it.amount ?: 0.0) > 0.0 && !it.needsAmount }
                    if (validResponses.isNotEmpty()) {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        val defaultApp = prefs.defaultApp
                        val defaultPurpose = prefs.defaultPurpose
                        val currency = prefs.defaultCurrency.ifBlank { "₹" }
                        val now = System.currentTimeMillis()

                        val savedSpends = mutableListOf<NewSpend>()
                        validResponses.forEach { resp ->
                            val newSpend = resp.toNewSpend(defaultApp, defaultPurpose, now)
                            val spend = Spend(
                                uuid = java.util.UUID.randomUUID().toString(),
                                userId = userId,
                                appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                                amount = newSpend.amount,
                                purpose = newSpend.purpose,
                                category = newSpend.preset.category,
                                timestamp = newSpend.timestamp,
                                notes = newSpend.notes,
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.insert(spend)
                            savedSpends.add(newSpend)
                        }

                        val summary = if (savedSpends.size == 1) {
                            val s = savedSpends.first()
                            val amountStr = if (s.amount % 1.0 == 0.0) s.amount.toLong().toString() else String.format(Locale.getDefault(), "%.2f", s.amount)
                            "✅ Tracked $currency$amountStr for ${s.purpose}"
                        } else {
                            "✅ Tracked ${savedSpends.size} expenses"
                        }
                        _effects.send(QuickAddEffect.SavedDirectly(summary))
                    } else {
                        _uiState.value = QuickAddUiState(
                            isProcessing = false,
                            errorMessage = "Please specify an amount to track (e.g., 'Track 500 for lunch')."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = QuickAddUiState(
                        isProcessing = false,
                        errorMessage = error.message ?: "Something went wrong. Try again."
                    )
                }
            )
        }
    }

    /**
     * Text shared from a payment app. Automatically tracks directly to local store.
     */
    fun processShared(text: String) {
        parseJob?.cancel()
        _uiState.value = QuickAddUiState(isProcessing = true)
        parseJob = viewModelScope.launch {
            val prefs = aiPreferences.value
            val result = processor.parse(text, prefs, allowMultiple = false).getOrNull()?.firstOrNull()
                ?: AiParser.parseToBaseline(text, prefs.defaultApp, prefs.defaultPurpose)
                    .takeIf { it.amount != null }
            if (result != null && (result.amount ?: 0.0) > 0.0) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val currency = prefs.defaultCurrency.ifBlank { "₹" }
                val newSpend = result.toNewSpend(prefs.defaultApp, prefs.defaultPurpose)
                val spend = Spend(
                    uuid = java.util.UUID.randomUUID().toString(),
                    userId = userId,
                    appName = if (newSpend.preset.id == "other") newSpend.customAppName else newSpend.preset.displayName,
                    amount = newSpend.amount,
                    purpose = newSpend.purpose,
                    category = newSpend.preset.category,
                    timestamp = newSpend.timestamp,
                    notes = newSpend.notes,
                    updatedAt = System.currentTimeMillis()
                )
                repository.insert(spend)
                val amountStr = if (newSpend.amount % 1.0 == 0.0) newSpend.amount.toLong().toString() else String.format(Locale.getDefault(), "%.2f", newSpend.amount)
                _effects.send(QuickAddEffect.SavedDirectly("✅ Tracked $currency$amountStr for ${newSpend.purpose}"))
            } else {
                _uiState.value = QuickAddUiState(isProcessing = false, errorMessage = SHARE_NO_AMOUNT)
            }
        }
    }

    /**
     * A receipt shared as an image. Reads it with on-device OCR, then tracks directly.
     */
    fun processSharedImage(context: Context, uri: Uri, sourcePackage: String?) {
        parseJob?.cancel()
        _uiState.value = QuickAddUiState(isProcessing = true)
        parseJob = viewModelScope.launch {
            val raw = ReceiptTextReader.read(context.applicationContext, uri)
            val input = raw?.let { SharedPaymentText.toParserInput(it, sourcePackage) }.orEmpty()
            if (input.isBlank()) {
                _uiState.value = QuickAddUiState(isProcessing = false, errorMessage = SHARE_IMAGE_UNREADABLE)
            } else {
                processShared(input)
            }
        }
    }

    fun cancel() {
        parseJob?.cancel()
        parseJob = null
        _uiState.value = QuickAddUiState()
    }
}
