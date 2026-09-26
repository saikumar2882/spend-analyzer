package com.alpha.spendtracker.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

interface VoiceListener {
    fun onPartialTranscript(text: String)
    fun onFinalTranscript(text: String)
    fun onError(errorMessage: String)
    fun onRmsChanged(level: Float) {}
    fun onListeningStateChanged(isListening: Boolean) {}
}

object VoiceInputHelper {

    private const val TAG = "VoiceInputHelper"
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage: String = "te-IN"
    private var autoRestartCount = 0
    private const val MAX_AUTO_RESTARTS = 3
    private var isExplicitlyStopped = false
    private var accumulatedText = ""

    /**
     * Checks if Speech Recognition is available on this device.
     */
    fun isAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Starts speech recognition with the given language code ("te-IN", "en-IN", or "te-en").
     * Uses Google Speech Recognition service if present for better noise suppression and accuracy.
     */
    fun startListening(
        context: Context,
        languageCode: String,
        listener: VoiceListener
    ) {
        destroy()
        isExplicitlyStopped = false
        autoRestartCount = 0
        accumulatedText = ""
        currentLanguage = languageCode

        if (!isAvailable(context)) {
            listener.onError("Voice recognition is not available on this device")
            return
        }

        internalStartListening(context, listener)
    }

    private fun internalStartListening(
        context: Context,
        listener: VoiceListener
    ) {
        try {
            val recognizer = createSpeechRecognizer(context)
            speechRecognizer = recognizer

            listener.onListeningStateChanged(true)

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                    listener.onListeningStateChanged(true)
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                    listener.onListeningStateChanged(true)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Convert decibels (typically -2f to +12f) to 0.0f .. 1.0f range
                    val normalized = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    listener.onRmsChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    val userFriendlyMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, try speaking again"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Need internet connection for voice recognition"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice input needs microphone access"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error, please try again"
                        SpeechRecognizer.ERROR_SERVER -> "Server error, please try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.d(TAG, "SpeechRecognizer onError: code=$error msg=$userFriendlyMessage (explicitStop=$isExplicitlyStopped, restarts=$autoRestartCount)")

                    if (isExplicitlyStopped) {
                        listener.onListeningStateChanged(false)
                        return
                    }

                    // Transient timeout handling while user is thinking
                    if ((error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) &&
                        autoRestartCount < MAX_AUTO_RESTARTS
                    ) {
                        autoRestartCount++
                        Log.d(TAG, "Silence timeout during user thinking; auto-restarting speech listener ($autoRestartCount/$MAX_AUTO_RESTARTS)...")
                        // Destroy current recognizer and restart
                        destroyRecognizerOnly()
                        internalStartListening(context, listener)
                        return
                    }

                    // If we have accumulated text despite a timeout error, pass the result!
                    if (accumulatedText.isNotBlank()) {
                        listener.onListeningStateChanged(false)
                        listener.onFinalTranscript(accumulatedText)
                        return
                    }

                    listener.onListeningStateChanged(false)
                    listener.onError(userFriendlyMessage)
                }

                override fun onResults(results: Bundle?) {
                    listener.onListeningStateChanged(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim() ?: ""

                    val finalText = when {
                        transcript.isNotBlank() && accumulatedText.isNotBlank() && !accumulatedText.contains(transcript, ignoreCase = true) ->
                            "$accumulatedText $transcript".trim()
                        transcript.isNotBlank() -> transcript
                        else -> accumulatedText
                    }

                    if (finalText.isNotBlank()) {
                        accumulatedText = finalText
                        listener.onFinalTranscript(finalText)
                    } else if (!isExplicitlyStopped && autoRestartCount < MAX_AUTO_RESTARTS) {
                        autoRestartCount++
                        destroyRecognizerOnly()
                        internalStartListening(context, listener)
                    } else {
                        listener.onError("Didn't catch that, please try again")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim() ?: ""
                    if (transcript.isNotBlank()) {
                        val currentCombined = if (accumulatedText.isNotBlank() && !transcript.startsWith(accumulatedText, ignoreCase = true)) {
                            "$accumulatedText $transcript".trim()
                        } else {
                            transcript
                        }
                        listener.onPartialTranscript(currentCombined)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

                // Extended silence timeouts to allow thinking without cut-off
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 12000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 12000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)

                when (currentLanguage) {
                    "te-en" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "te-IN"))
                        putExtra("android.speech.extra.LANGUAGE_SWITCH_INITIAL_ACTIVE_LANGUAGES", arrayOf("te-IN", "en-IN"))
                        putExtra("android.speech.extra.BILINGUAL_RECOGNITION", true)
                    }
                    "te-IN" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("te-IN", "en-IN"))
                        putExtra("android.speech.extra.LANGUAGE_SWITCH_INITIAL_ACTIVE_LANGUAGES", arrayOf("te-IN", "en-IN"))
                    }
                    else -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(currentLanguage, "te-IN", "en-IN"))
                    }
                }
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener.onListeningStateChanged(false)
            listener.onError("Unable to start voice input: ${e.localizedMessage}")
        }
    }

    private fun createSpeechRecognizer(context: Context): SpeechRecognizer {
        val googleComponent = ComponentName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.service.SpeechRecognitionService"
        )
        return try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Speech Recognition service unavailable, falling back to default", e)
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        }
    }

    /**
     * Stops listening for speech without destroying instance.
     */
    fun stop() {
        isExplicitlyStopped = true
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    private fun destroyRecognizerOnly() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer instance", e)
        } finally {
            speechRecognizer = null
        }
    }

    /**
     * Safely cancels and destroys the speech recognizer instance.
     */
    fun destroy() {
        isExplicitlyStopped = true
        destroyRecognizerOnly()
        accumulatedText = ""
        autoRestartCount = 0
    }
}
