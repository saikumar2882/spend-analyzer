package com.alpha.spendtracker.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private const val RESTART_DELAY_MS = 250L
    private var isExplicitlyStopped = false
    private var accumulatedText = ""
    private var forceStandardRecognizer = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRestartRunnable: Runnable? = null

    // SpeechRecognizer error codes
    private const val ERROR_TOO_MANY_REQUESTS_CODE = 10
    private const val ERROR_SERVER_DISCONNECTED_CODE = 11
    private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13
    private const val ERROR_CANNOT_CHECK_SUPPORT_CODE = 14

    /**
     * Checks if Speech Recognition is available on this device.
     */
    fun isAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Starts speech recognition with the given language code ("te-IN", "en-IN", or "te-en").
     */
    fun startListening(
        context: Context,
        languageCode: String,
        listener: VoiceListener
    ) {
        destroy()
        isExplicitlyStopped = false
        autoRestartCount = 0
        forceStandardRecognizer = false
        accumulatedText = ""
        currentLanguage = languageCode

        if (!isAvailable(context)) {
            listener.onError("Voice recognition is not available on this device")
            return
        }

        // Give previous recognizer session a brief window to unbind before starting a new session
        mainHandler.postDelayed({
            if (!isExplicitlyStopped) {
                internalStartListening(context, listener)
            }
        }, 100L)
    }

    private fun internalStartListening(
        context: Context,
        listener: VoiceListener
    ) {
        try {
            destroyRecognizerOnly()
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
                    autoRestartCount = 0
                    listener.onListeningStateChanged(true)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Convert decibels (typically -2f to +12f) to 0.0f .. 1.0f range
                    val normalized = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    if (normalized > 0.3f) {
                        autoRestartCount = 0
                    }
                    listener.onRmsChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    val userFriendlyMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, try speaking again"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error, please check internet connection"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout, please try again"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice input needs microphone access"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error, please try again"
                        SpeechRecognizer.ERROR_SERVER -> "Server error, please try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy, retrying..."
                        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error, retrying..."
                        ERROR_TOO_MANY_REQUESTS_CODE -> "Speech service is busy or rate limited, retrying..."
                        ERROR_SERVER_DISCONNECTED_CODE -> "Speech server disconnected, please try again"
                        ERROR_LANGUAGE_NOT_SUPPORTED_CODE, ERROR_LANGUAGE_UNAVAILABLE_CODE -> "Language model not supported offline, switching to online recognition..."
                        ERROR_CANNOT_CHECK_SUPPORT_CODE -> "Speech service status unknown, try again"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.d(TAG, "SpeechRecognizer onError: code=$error msg=$userFriendlyMessage (explicitStop=$isExplicitlyStopped, restarts=$autoRestartCount)")

                    if (isExplicitlyStopped) {
                        listener.onListeningStateChanged(false)
                        return
                    }

                    // Fallback to online system recognizer if on-device language model is unavailable
                    val isLanguageModelError = (error == ERROR_LANGUAGE_NOT_SUPPORTED_CODE) ||
                            (error == ERROR_LANGUAGE_UNAVAILABLE_CODE) ||
                            (error == ERROR_CANNOT_CHECK_SUPPORT_CODE)

                    if (isLanguageModelError && !forceStandardRecognizer) {
                        Log.d(TAG, "Language model unavailable on-device for $currentLanguage; falling back to online speech recognizer...")
                        forceStandardRecognizer = true
                        scheduleDelayedRestart(context, listener)
                        return
                    }

                    // Silence timeout / no match when user is thinking before or between speech:
                    // Continuously re-arm the listener so voice input doesn't prematurely close
                    val isSilenceTimeout = (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ||
                            (error == SpeechRecognizer.ERROR_NO_MATCH)

                    if (isSilenceTimeout) {
                        Log.d(TAG, "Silence timeout / no speech match; re-arming speech listener to keep listening...")
                        scheduleDelayedRestart(context, listener)
                        return
                    }

                    // Transient errors (busy, client error, rate limited) attempt auto-restart up to MAX_AUTO_RESTARTS
                    val isTransientError = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) ||
                            (error == SpeechRecognizer.ERROR_CLIENT) ||
                            (error == ERROR_TOO_MANY_REQUESTS_CODE)

                    if (isTransientError && autoRestartCount < MAX_AUTO_RESTARTS) {
                        autoRestartCount++
                        Log.d(TAG, "Transient error ($error); auto-restarting speech listener ($autoRestartCount/$MAX_AUTO_RESTARTS)...")
                        scheduleDelayedRestart(context, listener)
                        return
                    }

                    // If we captured any transcript before the error occurred, deliver it!
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
                    } else if (!isExplicitlyStopped) {
                        // Silence on results with empty text; re-arm listener continuously
                        Log.d(TAG, "Empty speech results; re-arming listener...")
                        scheduleDelayedRestart(context, listener)
                    } else {
                        listener.onError("Didn't catch that, please try again")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim() ?: ""
                    if (transcript.isNotBlank()) {
                        autoRestartCount = 0
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

                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)

                when (currentLanguage) {
                    "te-en" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "te-IN"))
                        putExtra("android.speech.extra.LANGUAGE_SWITCH_INITIAL_ACTIVE_LANGUAGES", arrayOf("te-IN", "en-IN"))
                    }
                    "te-IN" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("te-IN", "en-IN"))
                    }
                    else -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
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

    private fun scheduleDelayedRestart(context: Context, listener: VoiceListener) {
        cancelPendingRestart()
        val runnable = Runnable {
            if (!isExplicitlyStopped) {
                internalStartListening(context, listener)
            }
        }
        pendingRestartRunnable = runnable
        mainHandler.postDelayed(runnable, RESTART_DELAY_MS)
    }

    private fun cancelPendingRestart() {
        pendingRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRestartRunnable = null
    }

    private fun createSpeechRecognizer(context: Context): SpeechRecognizer {
        val appContext = context.applicationContext
        val isTeluguOrBilingual = currentLanguage.startsWith("te")

        return try {
            // Regional languages like Telugu are processed via online cloud SpeechRecognizer unless forced
            if (!forceStandardRecognizer && !isTeluguOrBilingual && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                Log.d(TAG, "Using On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                Log.d(TAG, "Using System Default Online SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create preferred SpeechRecognizer, falling back to default", e)
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }

    /**
     * Stops listening for speech without destroying instance.
     */
    fun stop() {
        isExplicitlyStopped = true
        cancelPendingRestart()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    private fun destroyRecognizerOnly() {
        cancelPendingRestart()
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
        cancelPendingRestart()
        destroyRecognizerOnly()
        accumulatedText = ""
        autoRestartCount = 0
    }
}

