package com.alpha.spendtracker.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

sealed interface QuickAddEffect {
    /** The sentence parsed cleanly — open the app so the user can confirm and save it. */
    data class HandOff(val result: AiTransactionResponse) : QuickAddEffect
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
    aiPrefsRepository: AiPreferencesRepository,
) : ViewModel() {

    val aiPreferences: StateFlow<AiPreferences> = aiPrefsRepository.aiPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiPreferences()
    )

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

    fun cancel() {
        parseJob?.cancel()
        parseJob = null
        _uiState.value = QuickAddUiState()
    }
}
