package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Everything the AI Track parse call tells the model: the system prompt and the JSON schema its
 * answer is constrained to. Pure (no Android, no I/O) so a unit test can read the prompt the way
 * the model does, and the live eval (`AiGoldenEvalTest`) runs against the very same text.
 *
 * ⚠️ The purpose and app vocabularies are generated from [CANONICAL_PURPOSES] and [APP_NAMES], never
 * typed into the prompt by hand: the mapping section, the few-shot examples, the schema enums and
 * [AiPurposes] all read the same list. Before this, the prompt listed every `PURPOSE_PRESETS` twin
 * ("Food & Drinks" and "Food & Dining"), its mapping said "Commute" while its examples said
 * "Travel & Commute", and the model followed whichever it weighed more.
 */
internal object AiPrompt {

    /** Names for the two schema shapes; Groq requires one. */
    const val SCHEMA_NAME_SINGLE = "expense"
    const val SCHEMA_NAME_MULTI = "expenses"

    /**
     * The platforms the model may name. "Other Platform" is left out: it is not something a user
     * says, and an unlisted app ("HDFC", "Uber") is better answered with "" — the user's default
     * app — than with a placeholder that [AiBatchParser] would then store as the app's name.
     */
    val APP_NAMES: List<String> = APP_PRESETS.filter { it.id != "other" }.map { it.displayName }

    /** Words that point at a purpose. One row per entry of [CANONICAL_PURPOSES]. */
    class PurposeHint(val purpose: String, val cues: List<String>)

    val PURPOSE_HINTS: List<PurposeHint> = listOf(
        PurposeHint("Food & Dining", listOf("biryani", "pizza", "burger", "lunch", "dinner", "breakfast", "snacks", "coffee", "chai", "tea", "juice", "restaurant", "cafe", "swiggy", "zomato")),
        PurposeHint("Groceries", listOf("grocery", "groceries", "supermarket", "kirana", "vegetables", "fruits", "milk", "atta", "blinkit", "zepto")),
        PurposeHint("Housing", listOf("rent", "house rent", "room rent", "pg rent", "maintenance", "society", "repairs", "plumber")),
        PurposeHint("Commute", listOf("cab", "uber", "ola", "rapido", "auto", "metro", "bus", "petrol", "fuel", "diesel", "parking", "toll")),
        PurposeHint("Lending", listOf("\"lent to\"", "\"gave to\"", "\"sent to [person]\"", "\"paid for [person]\"")),
        PurposeHint("Borrowing", listOf("\"borrowed from\"", "\"took from\"", "\"received from\"")),
        PurposeHint("Shopping", listOf("shirt", "jeans", "shoes", "saree", "electronics", "amazon", "flipkart", "myntra", "ajio", "meesho")),
        PurposeHint("Bills & Utilities", listOf("electricity", "wifi", "internet", "recharge", "water bill", "gas", "dth", "current bill", "phone bill")),
        PurposeHint("EMIs & Cards", listOf("credit card bill", "cc bill", "emi", "loan payment")),
        PurposeHint("Investments", listOf("stocks", "mutual funds", "sip", "fd", "shares", "crypto", "gold")),
        PurposeHint("Savings", listOf("savings", "emergency fund", "recurring deposit", "piggy bank")),
        PurposeHint("Subscriptions", listOf("netflix", "hotstar", "prime", "spotify", "youtube premium", "ott", "membership")),
        PurposeHint("Healthcare", listOf("medicine", "tablet", "doctor", "hospital", "clinic", "pharmacy", "dentist", "lab test")),
        PurposeHint("Personal Care", listOf("salon", "haircut", "spa", "grooming", "skincare")),
        PurposeHint("Education", listOf("tuition", "school fees", "college fees", "course", "books", "exam fee")),
        PurposeHint("Entertainment", listOf("movies", "bookmyshow", "concert", "game", "gaming", "cinema", "outing")),
        PurposeHint("Travel", listOf("flight", "train", "hotel", "holiday", "trip", "vacation", "irctc")),
        PurposeHint("Insurance & Taxes", listOf("insurance", "premium", "tax", "gst", "itr")),
        PurposeHint("Gifts & Donations", listOf("gift", "donation", "charity", "temple", "festival")),
        PurposeHint("Fitness", listOf("gym", "fitness", "yoga", "sports", "trainer", "classes")),
        PurposeHint("Pets", listOf("pet food", "vet", "dog", "cat")),
        PurposeHint("Work Expenses", listOf("office supplies", "client meeting", "coworking")),
        PurposeHint("Miscellaneous", listOf("anything that fits no other purpose")),
    )

    private const val PURPOSE_MAPPING_SLOT = "@@PURPOSE_MAPPING@@"

    // Examples take their purposes from here so they can't spell one differently from the mapping.
    private const val FOOD = "Food & Dining"
    private const val GROCERIES = "Groceries"
    private const val COMMUTE = "Commute"
    private const val LENDING = "Lending"
    private const val BORROWING = "Borrowing"

    /**
     * [custom] is what this call tells the model about the user's own apps and categories: they
     * join the platform list, the purpose list and (in [schema]) the enums, so "jiomart 450" can
     * come back as the "Jio Mart" the user added. The caller decides what is safe to send — with the
     * privacy setting on it passes [CustomPresets.EMPTY], and the names are matched on-device instead.
     */
    fun systemPrompt(
        localCurrency: String,
        defaultPurpose: String,
        multi: Boolean,
        now: Long = System.currentTimeMillis(),
        custom: CustomPresets = CustomPresets.EMPTY
    ): String {
        // ISO dates are ASCII by definition; the device locale must not turn the digits into
        // another script (ar, fa, …) before the model sees "Today: …".
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dayFormat.format(Date(now))
        val yesterdayStr = dayFormat.format(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.time)
        val appList = (APP_NAMES + custom.apps).joinToString(", ")
        val purposeList = (CANONICAL_PURPOSES + custom.categories).joinToString(", ")
        // A saved default may be a legacy twin ("Food & Drinks") or the old "Others"; the model is
        // told the canonical bucket so it never echoes a spelling the schema doesn't allow.
        val default = AiPurposes.normalizePurpose(defaultPurpose, custom = custom)
        // Multi-line, so it is spliced in after trimIndent: one unindented line in the template
        // would make trimIndent's common indent zero and leave every other line padded.
        val builtInMapping = PURPOSE_HINTS.joinToString("\n") { hint ->
            "\"${hint.purpose}\": ${hint.cues.joinToString(", ")}"
        }
        // A user-made category has no cue words, only its name, so the model is told to match on that.
        val purposeMapping = if (custom.categories.isEmpty()) builtInMapping else builtInMapping + "\n" +
            "The user's own categories — choose one over a built-in purpose when the expense clearly fits its name:\n" +
            custom.categories.joinToString("\n") { "\"$it\": the user's own category" }

        return """
            You are a strict JSON extractor for an Indian expense tracker. Parse the user's sentence and return ONE JSON object. Output ONLY valid JSON — no markdown, no code fences, no commentary.

            USER DEFAULTS (apply when not explicitly stated):
            - Currency: $localCurrency
            - Purpose: $default
            - Today: $todayStr

            PLATFORM MAPPING — resolve any fuzzy variant, misspelling, voice-transcription error, or Telugu/Hindi-script spelling to a canonical name from [$appList]:
            "swiggi" / "swigy" / "sweegy" / "instamart" / "స్విగ్గీ" / "స్విగ్గి" → "Swiggy"
            "zapto" / "jepto" / "జెప్టో" / "జెప్తో" → "Zepto"
            "zomoto" / "jomato" / "జొమాటో" / "జోమాటో" → "Zomato"
            "blink it" / "grofers" → "Blinkit"
            "pp" / "phone pay" / "phonepay" → "PhonePe"
            "gpay" / "g pay" / "g-pay" / "tez" → "Google Pay"
            "amzn" / "amazon pay" → "Amazon"
            "cash" / "cash payment" / "paid cash" / "నగదు" / "नकद" → "Cash"
            "cred" / "cred pay" / "cred app" → "CRED"
            "paytm upi" → "Paytm"
            "upi" / no platform mentioned / a platform not in the list → "" (empty string — do NOT guess, the app fills in the user's default)
            IMPORTANT: a food/grocery app name (Swiggy, Zomato, Zepto, Blinkit) IS the platform — never replace it with a UPI app.

            PURPOSE MAPPING — output EXACT string from [$purposeList]. Cue words → purpose:
            $PURPOSE_MAPPING_SLOT
            Default (nothing clearly matches) → use the user's default purpose above: "$default"

            FIELD RULES:
            - LANGUAGE TRANSLATION: The input may be in English, Telugu (e.g., "భోజనం కోసం 500 ఖర్చు చేశాను"), or Teluglish/Bilingual (e.g., "500 dinner ki karchu chesa"). ALWAYS translate Telugu words and context into clear, concise ENGLISH for `notes` and `purpose`. NEVER output Telugu script or unparsed Teluglish in JSON fields.
            - amount: ${if (multi) "the monetary amount of THIS expense" else "largest monetary number found"}; null if absent.
            - appName: canonical platform name from the mapping above, ONLY if the user mentioned one; otherwise "".
            - purpose: exact string from the purpose mapping above.
            - notes: 1-4 Title Case words describing WHAT in ENGLISH. Exclude amount, app name, and verbs ("spent","paid","bought"). For Lending/Borrowing, notes is ONLY the optional reason/detail ("Rent", "Trip Expenses") and must NOT contain the person's name or words like "Lent to"/"From"; empty string if no reason is given. Empty string if nothing identifiable.
            - personName: ONLY for purpose "$LENDING" or "$BORROWING" — the other person's name in Title Case ENGLISH/Latin script, without relations prefixes like "my" ("lent 500 to rahul" → "Rahul", "borrowed 2k from my mom for rent" → "Mom"). Empty string for every other purpose or when no person is named.
            - date: YYYY-MM-DD relative to today ($todayStr). "yesterday" → today−1; "last friday" → most recent past Friday; partial date with no year → this year, shift back 1 year if result is in the future. No date → today.
            - needsAmount: true only when amount is null.

            ${if (multi) """OUTPUT (no extra keys):
            {"transactions": [{"src": string, "amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}]}

            MULTIPLE EXPENSES:
            - One entry in "transactions" per separate expense, in the order the user said them. Most inputs hold exactly one.
            - "tea 20, auto 80 and lunch 150" is THREE entries. "500 for lunch and chai" is ONE entry: a single amount covering several items.
            - "src": the words of the user's sentence this entry came from, copied exactly — not translated, not reworded.
            - A payment app or date said once for the whole sentence ("... all on gpay", "yesterday I spent ...") applies to every entry. One said next to a single expense applies to that expense only.
            - Never invent an expense the user did not state. At most ${AiBatchParser.MAX_TRANSACTIONS} entries.

            EXAMPLES:
            "lent 500 to rahul for lunch via gpay" → {"transactions": [{"src": "lent 500 to rahul for lunch via gpay", "amount": 500, "appName": "Google Pay", "purpose": "$LENDING", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}]}
            "tea 20, auto 80 on phonepe yesterday" → {"transactions": [{"src": "tea 20", "amount": 20, "appName": "PhonePe", "purpose": "$FOOD", "notes": "Tea", "personName": "", "date": "$yesterdayStr", "needsAmount": false}, {"src": "auto 80", "amount": 80, "appName": "PhonePe", "purpose": "$COMMUTE", "notes": "Auto", "personName": "", "date": "$yesterdayStr", "needsAmount": false}]}
            "borrowed 2000 from mom" → {"transactions": [{"src": "borrowed 2000 from mom", "amount": 2000, "appName": "", "purpose": "$BORROWING", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}]}""" else """OUTPUT (no extra keys):
            {"amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}

            EXAMPLES:
            "lent 500 to rahul for lunch via gpay" → {"amount": 500, "appName": "Google Pay", "purpose": "$LENDING", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}
            "300 on groceries in zapto" → {"amount": 300, "appName": "Zepto", "purpose": "$GROCERIES", "notes": "Groceries", "personName": "", "date": "$todayStr", "needsAmount": false}
            "borrowed 2000 from mom" → {"amount": 2000, "appName": "", "purpose": "$BORROWING", "notes": "", "personName": "Mom", "date": "$todayStr", "needsAmount": false}"""}
        """.trimIndent().replace(PURPOSE_MAPPING_SLOT, purposeMapping) + "\n\n" + PromptGuard.UNTRUSTED_INPUT_RULE
    }

    /**
     * [systemPrompt]'s task in a fraction of the text, for the on-device model. Gemini Nano's window
     * is under 4000 tokens, answer included, and the cloud prompt is about half of that before the
     * user says a word; a small model also follows a short instruction better than a long one.
     *
     * Same vocabulary (it reads [PURPOSE_HINTS], [APP_NAMES] and the user's custom presets), same
     * JSON shapes (so [AiBatchParser] reads either) and same security rule. Gone: the app
     * misspelling table (the on-device alias match in [AiBatchParser] resolves those afterwards),
     * most cue words, and all but one or two examples. Nothing in it leaves the phone, so unlike the
     * cloud prompt it may name the user's own apps and categories even with the privacy setting on.
     */
    fun compactSystemPrompt(
        localCurrency: String,
        defaultPurpose: String,
        multi: Boolean,
        now: Long = System.currentTimeMillis(),
        custom: CustomPresets = CustomPresets.EMPTY
    ): String {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dayFormat.format(Date(now))
        val yesterdayStr = dayFormat.format(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -1) }.time)
        val appList = (APP_NAMES + custom.apps).joinToString(", ")
        val default = AiPurposes.normalizePurpose(defaultPurpose, custom = custom)
        val purposes = PURPOSE_HINTS.joinToString("\n") { hint ->
            "\"${hint.purpose}\": ${hint.cues.take(COMPACT_CUES).joinToString(", ")}"
        } + custom.categories.joinToString("") { "\n\"$it\": the user's own category" }

        val shape = if (multi) {
            """{"transactions": [{"src": string, "amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}]}
            One entry per separate expense, in order. "tea 20, auto 80" is TWO entries; "500 for lunch and chai" is ONE. "src" is that expense's own words, copied exactly. A date or app said once for the whole sentence applies to every entry. Never invent an expense."""
        } else {
            """{"amount": number|null, "appName": string, "purpose": string, "notes": string, "personName": string, "date": string, "needsAmount": boolean}"""
        }
        val example = if (multi) {
            """"lent 500 to rahul for lunch" → {"transactions": [{"src": "lent 500 to rahul for lunch", "amount": 500, "appName": "", "purpose": "$LENDING", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}]}"""
        } else {
            """"lent 500 to rahul for lunch" → {"amount": 500, "appName": "", "purpose": "$LENDING", "notes": "Lunch", "personName": "Rahul", "date": "$todayStr", "needsAmount": false}"""
        }

        return """
            You extract expenses from one sentence for an Indian expense tracker. Reply with ONLY a JSON object: no markdown, no commentary.

            Today: $todayStr. Currency: $localCurrency. Default purpose: "$default".
            Payment apps: $appList. appName is one of these ONLY when the sentence names it, otherwise "".
            purpose must be exactly one of these (cue words after each):
            @@PURPOSES@@
            When nothing clearly fits, use "$default".

            Fields:
            - amount: the money amount${if (multi) " of this expense" else " (the largest number)"}; null if there is none.
            - notes: 1-4 Title Case ENGLISH words saying WHAT it was. No amount, app or verb. For $LENDING/$BORROWING only the reason, never the person.
            - personName: only for $LENDING/$BORROWING, the other person's name in English letters ("lent 500 to rahul" → "Rahul"); otherwise "".
            - date: YYYY-MM-DD. "yesterday" is $yesterdayStr. No date means $todayStr.
            - needsAmount: true only when amount is null.

            Output (no extra keys):
            $shape

            Example:
            $example
        """.trimIndent().replace("@@PURPOSES@@", purposes) + "\n\n" + PromptGuard.UNTRUSTED_INPUT_RULE
    }

    /** Cue words kept per purpose in [compactSystemPrompt]. */
    private const val COMPACT_CUES = 4

    /**
     * The JSON schema the reply is constrained to (Groq strict structured output).
     *
     * Kept to the subset Groq documents for strict mode on `openai/gpt-oss-20b`: every property is
     * `required`, every object `additionalProperties: false`, the one optional value (`amount`) is a
     * `["number","null"]` type array, and the only constraint keyword is `enum`. `minimum`,
     * `maxItems`, `pattern` and friends are deliberately absent — the docs do not list them for
     * strict mode, and the model can't be trusted to honour what the decoder doesn't enforce, so
     * [AiBatchParser] caps and validates on its side instead.
     */
    fun schema(multi: Boolean, custom: CustomPresets = CustomPresets.EMPTY): Map<String, Any> {
        val string = mapOf("type" to "string")
        val properties = linkedMapOf<String, Any>()
        // `src` first: copying the user's words before deciding the fields keeps each entry anchored.
        if (multi) properties["src"] = string
        properties["amount"] = mapOf("type" to listOf("number", "null"))
        properties["appName"] = mapOf("type" to "string", "enum" to APP_NAMES + custom.apps + "")
        properties["purpose"] = mapOf("type" to "string", "enum" to CANONICAL_PURPOSES + custom.categories)
        properties["notes"] = string
        properties["personName"] = string
        properties["date"] = string
        properties["needsAmount"] = mapOf("type" to "boolean")
        val item = objectOf(properties)
        if (!multi) return item
        return objectOf(
            linkedMapOf<String, Any>("transactions" to mapOf("type" to "array", "items" to item))
        )
    }

    fun jsonSchema(multi: Boolean, custom: CustomPresets = CustomPresets.EMPTY): LlmJson.Schema =
        LlmJson.Schema(if (multi) SCHEMA_NAME_MULTI else SCHEMA_NAME_SINGLE, schema(multi, custom))

    private fun objectOf(properties: LinkedHashMap<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties,
        "required" to properties.keys.toList(),
        "additionalProperties" to false
    )
}
