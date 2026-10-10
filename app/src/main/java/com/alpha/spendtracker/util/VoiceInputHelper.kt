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

/**
 * Continuous speech capture on top of [SpeechRecognizer].
 *
 * The platform recognizer ends a session on its own after a second or two of silence, and the
 * `EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS` extras that are meant to lengthen that are advisory —
 * Google's recognizer ignores them. So the helper does not trust the recognizer to decide when the
 * user is done: every finished session is committed to [accumulatedText] and a new one is started,
 * and the helper only finishes itself after [SILENCE_GRACE_MS] without any speech (or when the user
 * stops it).
 */
object VoiceInputHelper {

    private const val TAG = "VoiceInputHelper"

    /** How long to keep waiting once the user has said something and then gone quiet. */
    private const val SILENCE_GRACE_MS = 5_000L

    /** How long to wait for the user to start at all before giving up. */
    private const val NO_SPEECH_TIMEOUT_MS = 12_000L

    private const val MAX_AUTO_RESTARTS = 3
    private const val RESTART_DELAY_MS = 150L
    private const val START_DELAY_MS = 100L

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage: String = "te-IN"
    private var autoRestartCount = 0
    private var isExplicitlyStopped = false

    /** Text from sessions the recognizer has already finished (plus anything the caller seeded). */
    private var accumulatedText = ""

    /** The in-flight session's latest partial result — not yet committed to [accumulatedText]. */
    private var sessionPartial = ""

    /**
     * Whether to use the on-device recognizer. Off by default: the system recognizer is the one that
     * works for every language here, whereas the on-device one reported `ERROR_SERVER_DISCONNECTED`
     * for English on devices where its service wasn't bound. It is only tried as a fallback when the
     * online recognizer has no network, and once per session at most ([onDeviceTried]).
     */
    private var useOnDevice = false
    private var onDeviceTried = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingStartRunnable: Runnable? = null
    private var idleRunnable: Runnable? = null

    // SpeechRecognizer error codes added after API 24 (the constants need API 31+/33).
    private const val ERROR_TOO_MANY_REQUESTS_CODE = 10
    private const val ERROR_SERVER_DISCONNECTED_CODE = 11
    private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
    private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13
    private const val ERROR_CANNOT_CHECK_SUPPORT_CODE = 14

    /** Checks if Speech Recognition is available on this device. */
    fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts speech recognition with the given language code ("te-IN", "en-IN", or "te-en").
     *
     * [previousText] seeds the transcript, so resuming after a stop (or switching language) extends
     * what was already captured instead of replacing it.
     */
    fun startListening(
        context: Context,
        languageCode: String,
        listener: VoiceListener,
        previousText: String = ""
    ) {
        destroy()
        isExplicitlyStopped = false
        autoRestartCount = 0
        useOnDevice = false
        onDeviceTried = false
        accumulatedText = previousText.trim()
        sessionPartial = ""
        currentLanguage = languageCode

        if (!isAvailable(context)) {
            listener.onError("Voice recognition is not available on this device")
            return
        }

        armIdleTimer(listener)
        // Give the previous recognizer session a brief window to unbind before starting a new one.
        scheduleStart(context, listener, START_DELAY_MS)
    }

    private fun join(first: String, second: String): String =
        listOf(first, second).filter { it.isNotBlank() }.joinToString(" ").trim()

    /** Everything heard so far: finished sessions plus the one still in flight. */
    private fun currentText(): String = join(accumulatedText, sessionPartial)

    private fun internalStartListening(context: Context, listener: VoiceListener) {
        try {
            destroyRecognizerOnly()
            sessionPartial = ""
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
                    armIdleTimer(listener)
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
                    handleError(context, listener, error)
                }

                override fun onResults(results: Bundle?) {
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()

                    // The session is over: commit it. Sessions are independent, so each result is
                    // only its own segment and is appended, never compared with earlier text.
                    sessionPartial = ""
                    if (transcript.isNotBlank()) accumulatedText = join(accumulatedText, transcript)

                    if (isExplicitlyStopped) {
                        // The user stopped us (or the idle timer did): this is the last word.
                        listener.onListeningStateChanged(false)
                        if (accumulatedText.isNotBlank()) listener.onFinalTranscript(accumulatedText)
                        return
                    }

                    // The recognizer decided the user was done; the user hasn't said so. Keep the
                    // text, keep listening, and let the idle timer be the one to end it.
                    if (transcript.isNotBlank()) {
                        listener.onPartialTranscript(accumulatedText)
                        armIdleTimer(listener)
                    }
                    scheduleStart(context, listener, RESTART_DELAY_MS)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val transcript = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (transcript.isNotBlank()) {
                        autoRestartCount = 0
                        sessionPartial = transcript
                        armIdleTimer(listener)
                        listener.onPartialTranscript(currentText())
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(buildIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            cancelTimers()
            listener.onListeningStateChanged(false)
            listener.onError("Unable to start voice input: ${e.localizedMessage}")
        }
    }

    private fun handleError(context: Context, listener: VoiceListener, error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that, try speaking again"
            SpeechRecognizer.ERROR_NETWORK -> "Network error, please check internet connection"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout, please try again"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Voice input needs microphone access"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error, please try again"
            SpeechRecognizer.ERROR_SERVER -> "Server error, please try again"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy, please try again"
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error, please try again"
            ERROR_TOO_MANY_REQUESTS_CODE -> "Speech service is busy or rate limited, please try again"
            ERROR_SERVER_DISCONNECTED_CODE -> "Speech service disconnected, please try again"
            ERROR_LANGUAGE_NOT_SUPPORTED_CODE, ERROR_LANGUAGE_UNAVAILABLE_CODE -> "This language isn't available for voice input on this device"
            ERROR_CANNOT_CHECK_SUPPORT_CODE -> "Speech service status unknown, try again"
            else -> "Speech recognition error ($error)"
        }
        Log.d(TAG, "onError: code=$error msg=$message (explicitStop=$isExplicitlyStopped, restarts=$autoRestartCount, onDevice=$useOnDevice)")

        if (isExplicitlyStopped) {
            listener.onListeningStateChanged(false)
            return
        }

        // No connection to the online recognizer: try the on-device one once, if there is one.
        val isNetworkError = error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        if (isNetworkError && !onDeviceTried && canUseOnDevice(context)) {
            Log.d(TAG, "Online recognizer unreachable; trying on-device recognizer")
            useOnDevice = true
            onDeviceTried = true
            scheduleStart(context, listener, RESTART_DELAY_MS)
            return
        }

        // The on-device recognizer is the unreliable one (disconnected service, missing language
        // pack): drop back to the system recognizer instead of surfacing its error.
        val isOnDeviceFailure = useOnDevice && (
            error == ERROR_SERVER_DISCONNECTED_CODE ||
                error == ERROR_LANGUAGE_NOT_SUPPORTED_CODE ||
                error == ERROR_LANGUAGE_UNAVAILABLE_CODE ||
                error == ERROR_CANNOT_CHECK_SUPPORT_CODE ||
                error == SpeechRecognizer.ERROR_CLIENT
            )
        if (isOnDeviceFailure) {
            Log.d(TAG, "On-device recognizer failed ($error); falling back to the system recognizer")
            useOnDevice = false
            scheduleStart(context, listener, RESTART_DELAY_MS)
            return
        }

        // Silence or an unrecognised phrase is not a failure while the user is still thinking: keep
        // listening. The idle timer bounds this, so it can no longer loop forever.
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
            scheduleStart(context, listener, RESTART_DELAY_MS)
            return
        }

        val isTransient = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == ERROR_TOO_MANY_REQUESTS_CODE ||
            error == ERROR_SERVER_DISCONNECTED_CODE
        if (isTransient && autoRestartCount < MAX_AUTO_RESTARTS) {
            autoRestartCount++
            Log.d(TAG, "Transient error ($error); restarting ($autoRestartCount/$MAX_AUTO_RESTARTS)")
            scheduleStart(context, listener, RESTART_DELAY_MS)
            return
        }

        // Out of options. Whatever was captured is still worth delivering.
        finishSession()
        listener.onListeningStateChanged(false)
        val text = currentText()
        if (text.isNotBlank()) listener.onFinalTranscript(text) else listener.onError(message)
    }

    private fun canUseOnDevice(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !currentLanguage.startsWith("te") &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context.applicationContext)

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

        // Advisory only — honoured by some engines, ignored by Google's. The real wait is enforced
        // by the idle timer; these just help where they do work.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_GRACE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_GRACE_MS)
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

    /** Starts (or restarts) a recognizer session after [delayMs], replacing any queued start. */
    private fun scheduleStart(context: Context, listener: VoiceListener, delayMs: Long) {
        cancelPendingStart()
        val runnable = Runnable {
            if (!isExplicitlyStopped) internalStartListening(context, listener)
        }
        pendingStartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingStart() {
        pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingStartRunnable = null
    }

    /**
     * (Re)starts the "user has gone quiet" countdown. Called on every sign of speech, so it only
     * ever fires after a real stretch of silence — [SILENCE_GRACE_MS] once there is text, and
     * [NO_SPEECH_TIMEOUT_MS] while there is none yet.
     */
    private fun armIdleTimer(listener: VoiceListener) {
        cancelIdleTimer()
        val delay = if (currentText().isNotBlank()) SILENCE_GRACE_MS else NO_SPEECH_TIMEOUT_MS
        val runnable = Runnable { onIdleTimeout(listener) }
        idleRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    private fun cancelIdleTimer() {
        idleRunnable?.let { mainHandler.removeCallbacks(it) }
        idleRunnable = null
    }

    private fun cancelTimers() {
        cancelPendingStart()
        cancelIdleTimer()
    }

    private fun onIdleTimeout(listener: VoiceListener) {
        if (isExplicitlyStopped) return
        val text = currentText()
        finishSession()
        listener.onListeningStateChanged(false)
        if (text.isNotBlank()) {
            listener.onFinalTranscript(text)
        } else {
            listener.onError("No speech detected")
        }
    }

    /** Ends the session without waiting for the recognizer, so no late callback can restart it. */
    private fun finishSession() {
        isExplicitlyStopped = true
        cancelTimers()
        destroyRecognizerOnly()
    }

    private fun createSpeechRecognizer(context: Context): SpeechRecognizer {
        val appContext = context.applicationContext
        return try {
            if (useOnDevice && canUseOnDevice(appContext)) {
                Log.d(TAG, "Using On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                Log.d(TAG, "Using System Default SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create preferred SpeechRecognizer, falling back to default", e)
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }

    /**
     * Stops listening for speech without destroying the instance, so the in-flight session can still
     * deliver its final result.
     */
    fun stop() {
        isExplicitlyStopped = true
        cancelTimers()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer", e)
        }
    }

    private fun destroyRecognizerOnly() {
        cancelPendingStart()
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

    /** Safely cancels and destroys the speech recognizer instance. */
    fun destroy() {
        isExplicitlyStopped = true
        cancelTimers()
        destroyRecognizerOnly()
        accumulatedText = ""
        sessionPartial = ""
        autoRestartCount = 0
    }
}
