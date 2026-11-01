package com.alpha.spendtracker.data

import android.util.Log
import com.alpha.spendtracker.BuildConfig
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Turns a natural-language expense sentence into the [AiTransactionResponse]s it describes — one
 * for "lunch 250", several for "tea 20, auto 80 and lunch 150".
 *
 * The on-device model (Gemini Nano, when the phone has one) is asked first, with a compact prompt
 * ([AiPrompt.compactSystemPrompt]). It is small, so its answer is only kept when it holds up
 * ([onDeviceAnswerHolds]): it must parse, not lose an expense, and not report an amount the
 * sentence does not contain. Otherwise the cloud gets the same sentence; if the cloud cannot
 * answer either, the baseline stands in rather than an answer we have just decided not to trust.
 * A sentence in Telugu/Hindi script skips the on-device model (it is not trusted with those).
 * When the on-device model answers nothing leaves the phone and no daily use is charged.
 *
 * For the cloud, two behaviours, chosen by the privacy (E2EE) setting:
 * - Privacy ON  -> [PiiSanitizer] anonymizes names, numbers and handles before the text leaves the
 *   phone, and the model's answer is de-anonymized on the way back.
 * - Privacy OFF -> the raw text goes to the model.
 *
 * Either way [AiParser]'s local baseline is computed first, and stands in (flagged
 * `usedFallback`) when no model answers or the answer is unusable; this class never fails the
 * [Result] for a model problem. The model call itself — keys, model ids, retries, on-device →
 * Groq → Gemini failover — is [LlmClient]'s. The prompt and response schema are [AiPrompt]'s.
 */
@Singleton
class AiTransactionProcessor @Inject constructor(
    private val llmClient: LlmClient,
    private val events: LlmEvents,
    private val aiPrefsRepository: AiPreferencesRepository,
    private val correctionRepository: AiCorrectionRepository,
) {

    companion object {
        private const val TAG = "AiTransactionProcessor"
        const val DAILY_LIMIT = 100
        internal const val MAX_INPUT_CHARS = 500

        // `ai_parse` event values. Alerting on fallback_* is how a silently degraded parse (a
        // decommissioned model, an exhausted token budget) becomes visible.
        internal const val OUTCOME_LLM = "llm"
        internal const val OUTCOME_NO_REPLY = "fallback_no_reply"
        internal const val OUTCOME_UNUSABLE_JSON = "fallback_unusable_json"
        internal const val OUTCOME_TRUNCATED = "truncated"

        /** The on-device answer failed [onDeviceAnswerHolds] and the cloud could not give a second one. */
        internal const val OUTCOME_LOCAL_REJECTED = "fallback_local_rejected"

        /** Scripts (Devanagari … Malayalam) the on-device model is not trusted with. */
        private val INDIC_SCRIPTS = 0x0900..0x0DFF

        /** Pure: true when [text] has a letter in an Indic script. */
        internal fun hasIndicScript(text: String): Boolean =
            text.any { it.isLetter() && it.code in INDIC_SCRIPTS }

        /**
         * Pure: whether to believe an answer from the small on-device model. It must have parsed
         * (`outcome == llm`, so not cut off), must not return fewer entries than the regex found
         * separate expenses (a lost expense), and every amount it reports must be one the regex also
         * found in the sentence: a small model that "corrects" 250 to 25 is worse than no model. When
         * the regex found no amount at all ("two fifty rupees"), there is nothing to contradict.
         */
        internal fun onDeviceAnswerHolds(
            interpreted: Interpreted,
            baselines: List<AiBatchParser.Parsed>,
            allowMultiple: Boolean
        ): Boolean {
            if (interpreted.outcome != OUTCOME_LLM) return false
            val answered = interpreted.parsed
            if (allowMultiple && answered.size < baselines.size) return false
            val known = baselines.mapNotNull { it.response.amount }
            if (known.isEmpty()) return true
            return answered.all { entry ->
                val amount = entry.response.amount
                amount == null || known.any { abs(it - amount) < 0.005 }
            }
        }
    }

    /**
     * Parses [text]. [allowMultiple] false (a shared payment receipt is one payment) restores the
     * single-object prompt and keeps only one entry.
     */
    suspend fun parse(
        text: String,
        prefs: AiPreferences,
        allowMultiple: Boolean = true
    ): Result<List<AiTransactionResponse>> {
        if (text.isBlank()) {
            return Result.failure(Exception("Input cannot be empty."))
        }
        if (text.length > MAX_INPUT_CHARS) {
            return Result.failure(Exception("Input too long (max $MAX_INPUT_CHARS characters)."))
        }

        val learnedRules = correctionRepository.rules.first()
        val anonymization = if (prefs.isE2eEncryptionEnabled) PiiSanitizer.anonymize(text) else null
        // What the cloud sees: the anonymized text when privacy is on, otherwise the raw sentence.
        val cloudText = anonymization?.anonymizedText ?: text
        Log.d(TAG, "Parsing with privacy=${anonymization != null} multi=$allowMultiple")

        val baselines = AiBatchParser.baselines(text, prefs.defaultApp, prefs.defaultPurpose, allowMultiple, prefs.customPresets)
        val request = buildRequest(cloudText, prefs, allowMultiple, onDevice = !hasIndicScript(text))
        var reply = llmClient.complete(request)
        var llmReply = reply.getOrNull()
        var interpreted = interpret(llmReply, cloudText, anonymization?.tokens, baselines, prefs, allowMultiple)

        // The on-device model is small. When its answer does not hold up, the cloud gets the same
        // request (the cloud-only form, so it is not routed back to the phone).
        var localRejected = false
        if (llmReply?.provider == LlmProvider.LOCAL && !onDeviceAnswerHolds(interpreted, baselines, allowMultiple)) {
            localRejected = true
            Log.w(TAG, "On-device answer rejected (outcome=${interpreted.outcome}); asking the cloud")
            val second = llmClient.complete(request.copy(local = LocalUse.Never))
            // Even when it failed: the event's error_kind should say why the cloud could not help.
            reply = second
            val secondReply = second.getOrNull()
            if (secondReply != null) {
                llmReply = secondReply
                interpreted = interpret(secondReply, cloudText, anonymization?.tokens, baselines, prefs, allowMultiple)
            } else {
                // Nothing else to ask: the baseline is a better answer than one we just decided is wrong.
                interpreted = Interpreted(markFallback(baselines), OUTCOME_LOCAL_REJECTED, usedFallback = true)
            }
        }

        // A usage is charged when a *cloud* model answered. The daily limit exists for the cloud's
        // free tier; the phone's own model costs nothing.
        if (llmReply != null && llmReply.provider != LlmProvider.LOCAL) aiPrefsRepository.incrementUsage()

        if (interpreted.outcome != OUTCOME_LLM) {
            Log.w(TAG, "AI parse outcome=${interpreted.outcome} — ${if (interpreted.usedFallback) "using local baseline" else "model answer kept"}")
        }
        events.log(
            "ai_parse",
            mapOf(
                "outcome" to interpreted.outcome,
                "provider" to (llmReply?.provider?.name ?: "NONE"),
                // Why there was no reply (NO_KEY, RATE_LIMIT, BAD_REQUEST …); no message text.
                "error_kind" to ((reply.exceptionOrNull() as? LlmException)?.kind?.name ?: "NONE"),
                "used_fallback" to interpreted.usedFallback,
                "entries" to interpreted.parsed.size,
                "multi" to allowMultiple,
                "privacy" to (anonymization != null),
                "local_rejected" to localRejected
            )
        )
        return Result.success(withLearnedCorrections(interpreted.parsed, learnedRules, prefs.customPresets))
    }

    /** What the model's reply (or its absence) turned into, and why — see the `OUTCOME_*` values. */
    internal class Interpreted(
        val parsed: List<AiBatchParser.Parsed>,
        val outcome: String,
        val usedFallback: Boolean
    )

    /**
     * Pure: turns the model's [reply] into expenses, or the [baselines] when there is no reply or
     * it can't be read. [tokens] is the anonymization map when privacy was on; the model only ever
     * saw placeholders, so names are restored before anything reaches the user.
     */
    internal fun interpret(
        reply: LlmReply?,
        cloudText: String,
        tokens: Map<String, String>?,
        baselines: List<AiBatchParser.Parsed>,
        prefs: AiPreferences,
        allowMultiple: Boolean
    ): Interpreted {
        if (reply == null) return Interpreted(markFallback(baselines), OUTCOME_NO_REPLY, usedFallback = true)

        if (BuildConfig.DEBUG) Log.d(TAG, "AI Raw Response: ${reply.text}")
        val parsed = AiBatchParser.parse(reply.text, cloudText, prefs.defaultApp, prefs.defaultPurpose, allowMultiple, prefs.customPresets)
        // A reply cut off at the token ceiling usually has no closing brace, so it lands here too;
        // it gets its own outcome because the fix (a bigger budget) differs from a model that
        // simply answered in prose.
        val outcome = when {
            reply.truncated -> OUTCOME_TRUNCATED
            parsed == null -> OUTCOME_UNUSABLE_JSON
            else -> OUTCOME_LLM
        }
        if (parsed == null) return Interpreted(markFallback(baselines), outcome, usedFallback = true)
        return Interpreted(if (tokens == null) parsed else restore(parsed, tokens), outcome, usedFallback = false)
    }

    /** Puts the real names back into an answer the model wrote about placeholders. */
    private fun restore(parsed: List<AiBatchParser.Parsed>, tokens: Map<String, String>) = parsed.map { item ->
        val resp = item.response
        AiBatchParser.Parsed(
            resp.copy(
                notes = PiiSanitizer.deanonymize(resp.notes, tokens),
                personName = PiiSanitizer.deanonymize(resp.personName, tokens),
                appName = resp.appName?.let { PiiSanitizer.deanonymize(it, tokens) }
            ),
            PiiSanitizer.deanonymize(item.source, tokens)
        )
    }

    /** Flags baseline results as degraded so the confirmation screen can tell the user to double-check them. */
    private fun markFallback(parsed: List<AiBatchParser.Parsed>): List<AiBatchParser.Parsed> =
        parsed.map { it.copy(response = it.response.copy(usedFallback = true)) }

    /** [onDevice]: offer the call to the phone's own model first (see [LocalUse.Compact]). */
    internal fun buildRequest(
        cloudText: String,
        prefs: AiPreferences,
        allowMultiple: Boolean,
        onDevice: Boolean = false
    ): LlmRequest {
        val localCurrency = AiParser.extractCurrency(cloudText) ?: prefs.defaultCurrency.ifBlank { "INR" }
        // The user's own app and category names are free text they typed, so with the privacy
        // setting on they stay on the phone: the prompt omits them and they are matched locally
        // (AiParser / AiBatchParser) against the sentence instead.
        val cloudCustom = if (prefs.isE2eEncryptionEnabled) CustomPresets.EMPTY else prefs.customPresets
        // Shared payment text, OCR output and voice transcripts all land here, so the text is
        // an escaped literal (a `"` in it can no longer end the quotes) with invisible
        // characters stripped; AiPrompt's SECURITY rule tells the model it is data.
        val user = PromptGuard.untrustedBlock("USER INPUT", cloudText, MAX_INPUT_CHARS)
        return LlmRequest(
            label = "parse",
            tier = LlmTier.FAST,
            system = AiPrompt.systemPrompt(localCurrency, prefs.defaultPurpose, allowMultiple, custom = cloudCustom),
            user = user,
            // The phone's model never sends anything anywhere, so unlike the cloud prompt its own
            // prompt may name the user's custom apps and categories even with privacy on.
            local = if (onDevice) {
                LocalUse.Compact(
                    AiPrompt.compactSystemPrompt(localCurrency, prefs.defaultPurpose, allowMultiple, custom = prefs.customPresets),
                    user
                )
            } else LocalUse.Never,
            // GPT-OSS is a reasoning model: even at "low" effort, reasoning tokens are drawn from
            // this same ceiling before the JSON answer is emitted (see the identical note on the
            // history assistant's call). A tight budget here was silently truncating the JSON —
            // AiBatchParser.parse then can't find a closing brace, returns null, and the request
            // quietly falls back to the much weaker local baseline with no indication anything
            // went wrong. LlmReply.truncated now surfaces that as the `truncated` outcome.
            maxTokens = if (allowMultiple) 2048 else 768,
            json = AiPrompt.jsonSchema(allowMultiple, cloudCustom)
        )
    }

    /** Each expense is matched against the user's learned corrections using its own words. */
    private fun withLearnedCorrections(parsed: List<AiBatchParser.Parsed>, rules: List<CorrectionRule>, custom: CustomPresets) =
        parsed.map { AiCorrectionMemory.apply(it.response, it.source, rules, custom) }
}
