package com.alpha.spendtracker.util

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
}

object VoiceInputHelper {

    private const val TAG = "VoiceInputHelper"
    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Checks if Speech Recognition is available on this device.
     * If false, the mic button should hide itself silently.
     */
    fun isAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Starts speech recognition with the given language code ("hi-IN" or "en-IN").
     * Safely destroys any active recognizer before creating a new instance.
     */
    fun startListening(
        context: Context,
        languageCode: String,
        listener: VoiceListener
    ) {
        // Prevent recognizer busy crash by destroying any existing instance first
        destroy()

        if (!isAvailable(context)) {
            listener.onError("Voice recognition is not available on this device")
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val userFriendlyMessage = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, try again"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Need internet for voice"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice needs mic access — you can still type"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error, try again"
                        SpeechRecognizer.ERROR_SERVER -> "Server error, please try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected, try again"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer busy"
                        else -> "Speech recognition error"
                    }
                    Log.d(TAG, "SpeechRecognizer onError: code=$error msg=$userFriendlyMessage")
                    listener.onError(userFriendlyMessage)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    if (transcript.isNotBlank()) {
                        listener.onFinalTranscript(transcript)
                    } else {
                        listener.onError("Didn't catch that, try again")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    if (transcript.isNotBlank()) {
                        listener.onPartialTranscript(transcript)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (languageCode == "te-en") {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                    putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", arrayOf("en-IN", "te-IN"))
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            listener.onError("Unable to start voice input: ${e.localizedMessage}")
        }
    }

    /**
     * Stops listening for speech.
     */
    fun stop() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    /**
     * Safely cancels and destroys the speech recognizer instance.
     */
    fun destroy() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }
}
