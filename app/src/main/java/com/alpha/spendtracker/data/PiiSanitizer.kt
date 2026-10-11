package com.alpha.spendtracker.data

import java.util.Locale

data class AnonymizationResult(
    val anonymizedText: String,
    val tokens: Map<String, String>
)

/**
 * Scrubs sensitive PII (Account/Card numbers, UPI IDs, Phone numbers, Personal names)
 * from user inputs before sending to external LLM APIs (Groq / Gemini) and restores them
 * on responses.
 */
object PiiSanitizer {

    private val CARD_NUMBER_REGEX = Regex(
        """\b(?:\d{4}[-\s]){3}\d{4}\b|\b(?:\d{4}[-\s]){2}\d{4}\b|\b\d{12,16}\b"""
    )

    private val UPI_ID_REGEX = Regex(
        """\b[a-zA-Z0-9._-]+@[a-zA-Z]{2,}\b"""
    )

    private val PHONE_NUMBER_REGEX = Regex(
        """(?<!\d)(?:\+91[\s-]?|0)?[6-9]\d{9}\b"""
    )

    // Common app names, payment presets, purpose keywords, or verbs that should not be treated as person names.
    //
    // ⚠️ This is also merged with AiParser.PURPOSE_KEYWORD_WORDS below. A hand-maintained list
    // alone is a losing game — "netflix", "internet", "spotify" etc. were missing here, so a
    // perfectly ordinary "paid 499 for netflix" got "netflix" silently swapped for [PERSON_1]
    // before the text ever reached Groq/Gemini: the model then had no way to tell it was a
    // subscription at all. Pulling in the same keyword vocabulary the rest of the parsing
    // pipeline already recognizes means a word can't be a "purpose" to the parser and a "person"
    // to the sanitizer at the same time, and new purpose keywords stay covered automatically.
    private val IGNORED_WORDS: Set<String> = setOf(
        "swiggy", "swiggi", "swigy", "zomato", "zomoto", "zepto", "zapto", "blinkit", "grofers",
        "amazon", "flipkart", "myntra", "ajio", "uber", "ola", "rapido", "gpay", "google", "pay",
        "phonepe", "phone", "paytm", "cred", "icici", "sbi", "hdfc", "axis", "airtel", "jio",
        "lunch", "dinner", "breakfast", "biryani", "pizza", "coffee", "tea", "chai", "groceries",
        "petrol", "diesel", "fuel", "cab", "auto", "rent", "electricity", "water", "wifi", "bill",
        "movie", "gym", "medicine", "doctor", "hospital", "recharge", "shopping", "food",
        "me", "my", "myself", "i", "friend", "cash", "card", "upi", "bank", "account", "money",
        "credit", "details", "buying", "clothes", "shirt", "pants", "jeans", "shoes", "snacks",
        "house", "trip", "ticket", "tickets", "booking", "handbag", "purse",
        "bro", "brother", "landlord", "uncle", "aunty", "cousin", "sister", "sis", "mom", "dad", "father", "mother", "boss"
    ) + AiParser.PURPOSE_KEYWORD_WORDS

    /**
     * Anonymizes card numbers, UPI IDs, phone numbers, and personal names in the input text.
     */
    fun anonymize(text: String): AnonymizationResult {
        if (text.isBlank()) {
            return AnonymizationResult(text, emptyMap())
        }

        var currentText = text
        val tokens = mutableMapOf<String, String>()

        var cardCount = 1
        var upiCount = 1
        var phoneCount = 1
        var personCount = 1

        // 1. UPI IDs (e.g. john@okicici, 9876543210@ybl)
        val upiMatches = UPI_ID_REGEX.findAll(currentText).toList()
        for (match in upiMatches) {
            val raw = match.value
            if (!tokens.containsValue(raw)) {
                val token = "[UPI_$upiCount]"
                upiCount++
                tokens[token] = raw
                currentText = currentText.replace(raw, token)
            }
        }

        // 2. Account / Card Numbers (12-16 digits)
        val cardMatches = CARD_NUMBER_REGEX.findAll(currentText).toList()
        for (match in cardMatches) {
            val raw = match.value
            if (!tokens.containsValue(raw)) {
                val token = "[CARD_$cardCount]"
                cardCount++
                tokens[token] = raw
                currentText = currentText.replace(raw, token)
            }
        }

        // 3. Phone Numbers (10 digits / +91 format)
        val phoneMatches = PHONE_NUMBER_REGEX.findAll(currentText).toList()
        for (match in phoneMatches) {
            val raw = match.value
            if (!tokens.containsValue(raw)) {
                val token = "[PHONE_$phoneCount]"
                phoneCount++
                tokens[token] = raw
                currentText = currentText.replace(raw, token)
            }
        }

        // 4. Personal Names in lending/borrowing or payment strings
        val extractedNames = extractPersonNames(currentText)
        for (name in extractedNames) {
            if (!tokens.containsValue(name)) {
                val token = "[PERSON_$personCount]"
                personCount++
                tokens[token] = name
                val nameRegex = Regex("""\b""" + Regex.escape(name) + """\b(?!@)""", RegexOption.IGNORE_CASE)
                currentText = nameRegex.replace(currentText, token)
            }
        }

        return AnonymizationResult(currentText, tokens)
    }

    /**
     * De-anonymizes the text by replacing tokens with their original values.
     */
    fun deanonymize(text: String, tokens: Map<String, String>): String {
        if (text.isBlank() || tokens.isEmpty()) return text
        var result = text
        for ((token, original) in tokens) {
            if (result.contains(token)) {
                result = result.replace(token, original)
            } else {
                // Fallback for potential LLM formatting variations (e.g., [PERSON 1], person_1, PERSON_1)
                val variations = listOf(
                    token.lowercase(Locale.getDefault()),
                    token.uppercase(Locale.getDefault()),
                    token.replace("_", " "),
                    token.removeSurrounding("[", "]")
                )
                for (v in variations) {
                    if (result.contains(v, ignoreCase = true)) {
                        result = result.replace(Regex(Regex.escape(v), RegexOption.IGNORE_CASE), original)
                    }
                }
            }
        }
        return result
    }

    private fun extractPersonNames(text: String): List<String> {
        val names = mutableListOf<String>()

        // Check counterparty extracted by AiParser
        val counterparty = AiParser.extractCounterparty(text)
        if (counterparty.isNotBlank() && isCandidateName(counterparty)) {
            names.add(counterparty)
        }

        // Patterns for explicit name extractions in payment/lend/borrow strings
        val namePatterns = listOf(
            Regex("""\bto\s+(?:(?:my|the|landlord|friend|bro|brother|uncle|aunty)\s+)?([A-Za-z]+)(?=\s+(?:using|via|through|with|from|by|on|at|in|for|account|upi|yesterday|today|last|lent|borrowed|paid|given|\[CARD_\d+\]|\[PHONE_\d+\]|\[UPI_\d+\]|\[|\d+)\b|\s*[.,!]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\bfrom\s+(?:(?:my|the|landlord|friend|bro|brother|uncle|aunty)\s+)?([A-Za-z]+)(?=\s+(?:using|via|through|with|by|on|at|in|for|to|account|upi|yesterday|today|last|lent|borrowed|paid|given|\[CARD_\d+\]|\[PHONE_\d+\]|\[UPI_\d+\]|\[|\d+)\b|\s*[.,!]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\bfor\s+(?:(?:my|the|landlord|friend|bro|brother|uncle|aunty)\s+)?([A-Za-z]+)(?=\s+(?:using|via|through|with|by|on|at|in|from|to|account|upi|yesterday|today|last|lent|borrowed|paid|given|\[CARD_\d+\]|\[PHONE_\d+\]|\[UPI_\d+\]|\[|\d+)\b|\s*[.,!]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:lent|gave|paid|sent)\s+([A-Za-z]+)\s+\d+(?:[.,]\d+)?\s*(?:rs\.?|₹|inr|rupees?|k)?\b""", RegexOption.IGNORE_CASE)
        )

        for (pattern in namePatterns) {
            for (match in pattern.findAll(text)) {
                if (match.groupValues.size > 1) {
                    val candidate = match.groupValues[1].trim()
                        .replace(Regex("""^(?:my|the|landlord|friend|bro|brother|uncle|aunty)\s+""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""\s+(?:account|upi|bank|card|bill)$""", RegexOption.IGNORE_CASE), "")
                    if (isCandidateName(candidate)) {
                        names.add(candidate)
                    }
                }
            }
        }

        return names.distinct()
    }

    private fun isCandidateName(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase(Locale.getDefault())
        if (lower == "person" || lower == "upi" || lower == "card" || lower == "phone") return false
        val words = lower.split(" ")
        if (words.any { IGNORED_WORDS.contains(it) }) return false
        // Ensure candidate is not a numeric string or token
        return !(candidate.startsWith("[") && candidate.endsWith("]"))
    }
}
