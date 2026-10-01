package com.alpha.spendtracker.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Turns the LLM's JSON — or, when it is unavailable, the local heuristics — into the list of
 * expenses one sentence describes. Pure (no network, no Android beyond `org.json`), so it is
 * unit-tested; [AiTransactionProcessor] only does the I/O around it.
 *
 * One sentence can now hold several expenses ("tea 20, auto 80 and lunch 150"), so everything
 * that used to be done once per sentence is done once per expense: the payment app, the date,
 * and the keywords handed to the learned-corrections memory. [Parsed.source] carries the slice of
 * the sentence each expense came from for exactly that.
 */
object AiBatchParser {

    /** A sentence with more expenses than this is almost certainly noise, and the review list must stay usable. */
    const val MAX_TRANSACTIONS = 10

    /** One expense, with the words of the user's sentence it came from. */
    data class Parsed(val response: AiTransactionResponse, val source: String)

    /**
     * The offline answer: [AiParser] over the whole sentence, or over each expense when
     * [multi] and the sentence splits into several (see [AiParser.splitExpenses]).
     *
     * A payment app or date said once applies to every expense, mirroring what the LLM prompt
     * asks for — but only when exactly one app is named, so "tea 20 on phonepe, coffee 30 on paytm"
     * keeps each its own.
     */
    fun baselines(text: String, defaultApp: String, defaultPurpose: String, multi: Boolean): List<Parsed> {
        val segments = if (multi) AiParser.splitExpenses(text) else listOf(text)
        if (segments.size < 2) {
            return listOf(Parsed(AiParser.parseToBaseline(text, defaultApp, defaultPurpose), text))
        }
        val sharedApp = segments.mapNotNull { AiParser.findAppPreset(it) }.distinctBy { it.id }.singleOrNull()
        val sharedTimestamp = AiParser.extractTimestamp(text)
        return segments.take(MAX_TRANSACTIONS).map { segment ->
            val own = AiParser.parseToBaseline(segment, defaultApp, defaultPurpose)
            val withApp = if (own.appPresetId == null && sharedApp != null) {
                own.copy(appName = sharedApp.displayName, appPresetId = sharedApp.id)
            } else own
            Parsed(withApp.copy(timestamp = withApp.timestamp ?: sharedTimestamp), segment)
        }
    }

    /**
     * Reads the model's reply. Accepts the multi shape `{"transactions":[…]}` and the older flat
     * single object. Returns null when it is not usable JSON, so the caller falls back to
     * [baselines].
     *
     * [multi] false (a payment receipt shared from another app) keeps only the first entry: a
     * receipt is one payment, and a stray second "expense" read off its footer would be wrong.
     */
    fun parse(
        responseText: String,
        text: String,
        defaultApp: String,
        defaultPurpose: String,
        multi: Boolean
    ): List<Parsed>? {
        val jsonString = run {
            val start = responseText.indexOf("{")
            val end = responseText.lastIndexOf("}")
            if (start in 0 until end) responseText.substring(start, end + 1) else responseText
        }
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return null
        }

        val array = root.optJSONArray("transactions")
        val items: List<JSONObject> = if (array != null) {
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } else {
            listOf(root)
        }
        val entries = items.take(if (multi) MAX_TRANSACTIONS else 1)
        if (entries.isEmpty()) return null

        if (entries.size == 1) {
            // The ordinary case, and exactly what a one-expense sentence always did: the whole
            // sentence is the source for the local app match and the baseline.
            val baseline = AiParser.parseToBaseline(text, defaultApp, defaultPurpose)
            return listOf(Parsed(merge(entries[0], baseline, appText = text, mentionText = text, defaultApp = defaultApp), text))
        }

        val parsed = entries.map { json ->
            // `src` lets each expense be matched to its own words. Without it the whole sentence
            // would hand every expense the first app named anywhere in it.
            val src = json.optString("src", "").trim()
            val segment = src.ifBlank { "" }
            val baseline = AiParser.parseToBaseline(segment, defaultApp, defaultPurpose)
            val response = merge(json, baseline, appText = segment, mentionText = text, defaultApp = defaultApp)
            Parsed(response, src.ifBlank { text })
        }
        // Several expenses with no amount are noise, not logs; with one, the review list still
        // lets the user fill it in, but a pile of them would bury the real ones.
        val withAmounts = parsed.filter { it.response.amount != null }
        return withAmounts.ifEmpty { null }
    }

    /**
     * Merges one JSON entry with its baseline. Per field, the model wins if it gave a non-blank,
     * valid value; otherwise the baseline stays.
     *
     * [appText] is the words checked against the app alias table (the whole sentence for a single
     * expense, the entry's own `src` for several). [mentionText] is always the whole sentence: the
     * model reflexively answers "Google Pay" for any payment, so that one app is believed only if
     * the user said it somewhere.
     */
    private fun merge(
        json: JSONObject,
        baseline: AiTransactionResponse,
        appText: String,
        mentionText: String,
        defaultApp: String
    ): AiTransactionResponse {
        val aiAmount = if (json.isNull("amount")) null else json.optDouble("amount", Double.NaN)
            .takeIf { !it.isNaN() }
        val aiAppRaw = json.optString("appName", "").ifBlank { null }
        val aiPurposeRaw = json.optString("purpose", "").ifBlank { null }
        val aiNotesRaw = json.optString("notes", "").trim()
        val aiPersonRaw = json.optString("personName", "").trim()
        val aiDate = json.optString("date", "").ifBlank { baseline.date }
        val aiNeedsAmount = json.optBoolean("needsAmount", false)

        val aiPreset = AiParser.normalizeAppToPreset(aiAppRaw)
        // Resolve the payment app in priority order:
        //  1. An app the local alias table finds in the input (incl. voice misspellings, Indic script).
        //  2. The LLM's app. It is trusted even when not spelled literally in the input — voice
        //     transcripts say "swiggi"/"జెప్టో", and requiring a verbatim match threw away correct
        //     answers in favour of the default. Exception: the model reflexively guesses
        //     "Google Pay" when no app was named, so that one must actually be mentioned.
        //  3. The user's configured default.
        val localApp = AiParser.findAppPreset(appText)
        val trustedAiPreset = aiPreset?.takeIf {
            it.id != "google_pay" || AiParser.mentionsApp(mentionText, it)
        }
        val finalPreset = localApp
            ?: trustedAiPreset
            ?: AiParser.normalizeAppToPreset(defaultApp)
            ?: AiParser.normalizeAppToPreset(baseline.appName)
        val finalPurpose = AiParser.normalizePurpose(aiPurposeRaw) ?: baseline.purpose
        val isLendBorrow = finalPurpose == "Lending" || finalPurpose == "Borrowing"
        val finalPerson = if (isLendBorrow) aiPersonRaw.ifBlank { baseline.personName } else ""
        val finalNotes = aiNotesRaw.ifBlank { baseline.notes }.let { notes ->
            // Older-style replies ("Lent to Rahul") would duplicate the person in the note.
            if (isLendBorrow && finalPerson.isNotBlank() && notes.contains(finalPerson, ignoreCase = true)) "" else notes
        }
        val finalAmount = aiAmount ?: baseline.amount
        val finalTimestamp = parseIsoDate(aiDate) ?: baseline.timestamp

        return AiTransactionResponse(
            amount = finalAmount,
            appName = finalPreset?.displayName ?: baseline.appName,
            appPresetId = finalPreset?.id,
            purpose = finalPurpose,
            notes = finalNotes,
            personName = finalPerson,
            date = aiDate,
            timestamp = finalTimestamp,
            needsAmount = aiNeedsAmount || finalAmount == null
        )
    }

    /** Parse "YYYY-MM-DD" emitted by the LLM into epoch millis (noon, so a DST shift can't change the day). */
    internal fun parseIsoDate(date: String?): Long? {
        if (date.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(date)?.let { parsed ->
                Calendar.getInstance().apply {
                    time = parsed
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        } catch (_: Exception) {
            null
        }
    }
}
