package com.alpha.spendtracker.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.alpha.spendtracker.ui.components.containsIndicScript
import com.alpha.spendtracker.ui.components.getLocalizedPresetName
import com.alpha.spendtracker.ui.components.isPresetOrLocalized
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * On-Device Dynamic Translation Helper using Google ML Kit Translate SDK.
 * Dynamically translates non-static/user-generated text into the active app language (e.g. Telugu, Hindi).
 */
object MlKitTranslator {

    private const val TAG = "MlKitTranslator"

    private val translators = ConcurrentHashMap<String, Translator>()
    private val translationCache = ConcurrentHashMap<String, String>()

    /**
     * Gets the ML Kit language code matching the app's currently active locale.
     * Returns null if current language is English or unsupported.
     */
    val currentTargetLanguageTag: String?
        get() {
            val locale = activeAppLocale
            return when (locale.language) {
                "te" -> TranslateLanguage.TELUGU
                "hi" -> TranslateLanguage.HINDI
                else -> null
            }
        }

    /**
     * Translates input text into the active app language on-the-fly.
     * Static dictionary presets and text already in target script take absolute priority.
     * ML Kit machine translation is never run on text that is already localized or is a preset.
     */
    suspend fun translateText(text: String): String {
        if (text.isBlank()) return text
        val targetLang = currentTargetLanguageTag ?: return text

        if (containsIndicScript(text) || isPresetOrLocalized(text)) {
            return getLocalizedPresetName(text)
        }

        val localizedPreset = getLocalizedPresetName(text)
        if (localizedPreset != text) {
            return localizedPreset
        }

        val cacheKey = "$targetLang:$text"
        translationCache[cacheKey]?.let { return it }

        return try {
            val translator = getOrCreateTranslator(targetLang)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            val translated = translator.translate(text).await()
            if (translated.isNotBlank()) {
                translationCache[cacheKey] = translated
                translated
            } else {
                text
            }
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit translation failed for '$text': ${e.message}")
            text
        }
    }

    private fun getOrCreateTranslator(targetLang: String): Translator {
        return translators.getOrPut(targetLang) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }
}

/**
 * Compose state producer that dynamically translates text into the app's selected language.
 * Static dictionary presets and text already in target script take absolute priority over ML Kit.
 */
@Composable
fun rememberTranslatedText(originalText: String): State<String> {
    val currentLocale = activeAppLocale
    val staticTranslation = getLocalizedPresetName(originalText)

    return produceState(initialValue = staticTranslation, key1 = originalText, key2 = currentLocale.language) {
        if (originalText.isNotBlank() && MlKitTranslator.currentTargetLanguageTag != null) {
            if (containsIndicScript(originalText) || isPresetOrLocalized(originalText)) {
                value = getLocalizedPresetName(originalText)
            } else {
                value = MlKitTranslator.translateText(originalText)
            }
        } else {
            value = staticTranslation
        }
    }
}
