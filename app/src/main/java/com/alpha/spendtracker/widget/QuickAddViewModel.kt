package com.alpha.spendtracker.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.spendtracker.data.AiParser
import com.alpha.spendtracker.data.AiPreferences
import com.alpha.spendtracker.data.AiPreferencesRepository
import com.alpha.spendtracker.data.AiTransactionProcessor
import com.alpha.spendtracker.data.AiTransactionResponse
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
import javax.inject.Inject

data class QuickAddUiState(
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

/** Sentinel error; the activity swaps in the localized "no amount found" message. */
const val SHARE_NO_AMOUNT = "share_no_amount"

sealed interface QuickAddEffect {
    /**
     * The sentence parsed cleanly — open the app so the user can confirm and save it. A list
     * because one sentence can hold several expenses ("tea 20, auto 80").
     */
    data class HandOff(val results: List<AiTransactionResponse>) : QuickAddEffect
}

/**
 * Backs the home-screen widget's input overlay.
 *
 * Deliberately *not* `SpendViewModel`: that one starts/stops the singleton repository's
 * Firestore listeners in `init`/`onCleared`, so spinning up a second instance for a
 * throwaway overlay would tear down sync for a MainActivity running behind it.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val processor: AiTransactionProcessor,
    private val aiPrefsRepository: AiPreferencesRepository,
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
            val result = processor.parse(text, aiPreferences.value)
            result.fold(
                onSuccess = { _effects.send(QuickAddEffect.HandOff(it)) },
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
     * Text shared from a payment app. Same AI parse as typed input, but a receipt is already
     * structured enough for the local parser — so when AI can't run (daily limit, no network)
     * the user still lands on the confirmation sheet instead of an error. A receipt is one
     * payment, so this asks for a single expense even if its text mentions several amounts.
     */
    fun processShared(text: String) {
        parseJob?.cancel()
        _uiState.value = QuickAddUiState(isProcessing = true)
        parseJob = viewModelScope.launch {
            val prefs = aiPreferences.value
            val result = processor.parse(text, prefs, allowMultiple = false).getOrNull()?.firstOrNull()
                ?: AiParser.parseToBaseline(text, prefs.defaultApp, prefs.defaultPurpose)
                    .takeIf { it.amount != null }
            if (result != null) {
                _effects.send(QuickAddEffect.HandOff(listOf(result)))
            } else {
                _uiState.value = QuickAddUiState(isProcessing = false, errorMessage = SHARE_NO_AMOUNT)
            }
        }
    }

    fun cancel() {
        parseJob?.cancel()
        parseJob = null
        _uiState.value = QuickAddUiState()
    }
}
