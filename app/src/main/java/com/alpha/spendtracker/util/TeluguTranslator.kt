package com.alpha.spendtracker.util

import java.util.Locale

object TeluguTranslator {

    private val numberWords = mapOf(
        "ఒకటి" to "1", "ఒక్క" to "1", "రెండు" to "2", "మూడు" to "3",
        "నాలుగు" to "4", "ఐదు" to "5", "ఆరు" to "6", "ఏడు" to "7",
        "ఎనిమిది" to "8", "తొమ్మిది" to "9", "పది" to "10",
        "యాభై" to "50", "నూరు" to "100", "వంద" to "100", "ఐదు వందలు" to "500",
        "వెయ్యి" to "1000", "వేలు" to "thousand"
    )

    private val phrasesMap = linkedMapOf(
        // Common full phrases
        "భోజనం కోసం" to "for food",
        "మధ్యాహ్న భోజనం" to "lunch",
        "రాత్రి భోజనం" to "dinner",
        "కరెంట్ బిల్లు" to "electricity bill",
        "కరెంట్ బిల్" to "electricity bill",
        "అప్పు ఇచ్చాను" to "lent",
        "అప్పు తీసుకున్నాను" to "borrowed",
        "ఖర్చు చేశాను" to "spent",
        "ఖర్చు చేసా" to "spent",
        "ఖర్చు అయింది" to "spent",
        "ఖర్చు ఐంది" to "spent",
        "రూపాయలు ఖర్చు" to "Rs spent",
        "karchu chesa" to "spent",
        "karchu chesaanu" to "spent",
        "karchu aindi" to "spent",
        "appu ichanu" to "lent",
        "appu teeskunna" to "borrowed",
        "pampinchanu" to "sent"
    )

    private val wordMap = mapOf(
        // Verbs
        "ఖర్చు" to "spent",
        "ఇచ్చాను" to "paid",
        "ఇచ్చా" to "paid",
        "ఇచ్చేసాను" to "paid",
        "కట్టాను" to "paid",
        "కట్టా" to "paid",
        "తీసుకున్నాను" to "borrowed",
        "తీసుకున్నా" to "borrowed",
        "కొన్నాను" to "bought",
        "కొన్నా" to "bought",
        "పంపించాను" to "sent",
        "పంపించా" to "sent",
        "చేసాను" to "done",

        // Categories & Items
        "భోజనం" to "food",
        "బోజనం" to "food",
        "తిండి" to "food",
        "టిఫిన్" to "tiffin",
        "అల్పాహారం" to "breakfast",
        "చాయ్" to "tea",
        "టీ" to "tea",
        "కాఫీ" to "coffee",
        "కూరగాయలు" to "groceries",
        "సరుకులు" to "groceries",
        "కిరాణా" to "groceries",
        "పెట్రోల్" to "petrol",
        "పెట్రోలు" to "petrol",
        "డీజిల్" to "diesel",
        "బండి" to "bike/vehicle",
        "ఆటో" to "auto",
        "క్యాబ్" to "cab",
        "అద్దె" to "rent",
        "రెంట్" to "rent",
        "రీఛార్జ్" to "recharge",
        "బట్టలు" to "clothes",
        "దుస్తులు" to "clothes",
        "మందులు" to "medicines",
        "ఆస్పత్రి" to "hospital",
        "డాక్టర్" to "doctor",
        "సినిమా" to "movie",
        "రూపాయలు" to "Rs",
        "రూపాయల" to "Rs",
        "రూ" to "Rs",
        "రూ." to "Rs",

        // Teluglish keywords
        "karchu" to "spent",
        "ichanu" to "paid",
        "ichha" to "paid",
        "ichina" to "paid",
        "kattanu" to "paid",
        "teeskunna" to "borrowed",
        "teeskunanu" to "borrowed",
        "konnanu" to "bought",
        "kosam" to "for",
        "ki" to "for",
        "ku" to "for",
        "nunchi" to "from",
        "bhojanam" to "food",
        "tiffin" to "tiffin",
        "petrol" to "petrol",
        "rent" to "rent"
    )

    /**
     * Translates Telugu script or Teluglish/Bilingual expense speech text into clear English.
     */
    fun translateToEnglish(input: String): String {
        if (input.isBlank()) return input

        var text = input

        // 1. Replace Telugu digits (०-९ -> 0-9)
        text = convertTeluguDigits(text)

        // 2. Phrase replacements
        for ((phrase, replacement) in phrasesMap) {
            if (text.contains(phrase, ignoreCase = true)) {
                text = text.replace(Regex("(?i)${Regex.escape(phrase)}"), replacement)
            }
        }

        // 3. Number word replacements
        for ((word, replacement) in numberWords) {
            if (text.contains(word, ignoreCase = true)) {
                text = text.replace(Regex("(?i)${Regex.escape(word)}"), replacement)
            }
        }

        // 4. Word-by-word translation
        val tokens = text.split(Regex("\\s+"))
        val translatedTokens = tokens.map { token ->
            val cleaned = token.replace(Regex("[^\\p{L}\\p{N}]"), "")
            val replacement = wordMap[cleaned.lowercase(Locale.ROOT)]
                ?: wordMap[cleaned]
            if (replacement != null) {
                token.replace(cleaned, replacement)
            } else {
                removeOrRomanizeTeluguScript(token)
            }
        }

        val result = translatedTokens.joinToString(" ").replace(Regex("\\s+"), " ").trim()

        return formatEnglishSentence(result)
    }

    private fun convertTeluguDigits(input: String): String {
        val teluguDigits = charArrayOf('౦', '౧', '౨', '౩', '౪', '౫', '౬', '౭', '౮', '౯')
        val sb = StringBuilder()
        for (ch in input) {
            val idx = teluguDigits.indexOf(ch)
            if (idx != -1) {
                sb.append(idx)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun removeOrRomanizeTeluguScript(token: String): String {
        return token
    }

    private fun formatEnglishSentence(input: String): String {
        if (input.isBlank()) return input

        val formatted = input
            .replace(Regex("(?i)\\bfor for\\b"), "for")
            .replace(Regex("(?i)\\bspent spent\\b"), "spent")
            .replace(Regex("\\s+"), " ")
            .trim()

        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
