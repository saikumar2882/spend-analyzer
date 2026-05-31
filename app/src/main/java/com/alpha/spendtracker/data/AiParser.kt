package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS
import java.util.Calendar

/**
 * Heuristic parser that runs locally and provides a deterministic fallback
 * for fields the LLM may misidentify. Output is merged with the LLM response
 * field-by-field (LLM wins when present and valid; parser fills the rest).
 */
object AiParser {

    private val APP_ALIASES: Map<String, List<String>> = mapOf(
        "google_pay" to listOf("google pay", "googlepay", "gpay", "g pay", "g-pay"),
        "phone_pe" to listOf("phonepe", "phone pe", "phone pay", "phonepay", "phon pe"),
        "paytm" to listOf("paytm", "pay tm"),
        "swiggy" to listOf("swiggy"),
        "zepto" to listOf("zepto"),
        "blinkit" to listOf("blinkit", "blink it", "grofers"),
        "amazon" to listOf("amazon", "amzn"),
        "flipkart" to listOf("flipkart", "flip kart"),
        "myntra" to listOf("myntra"),
        "ajio" to listOf("ajio"),
        "icici" to listOf("icici bank", "icici"),
        "yono_sbi" to listOf("yono sbi", "yono", "sbi yono")
    )

    private val PURPOSE_KEYWORDS: Map<String, List<String>> = mapOf(
        "Groceries & Food" to listOf(
            "biryani", "pizza", "burger", "lunch", "dinner", "breakfast", "brunch",
            "snack", "snacks", "food", "meal", "coffee", "tea", "chai", "juice",
            "shake", "ice cream", "dessert", "sweet", "sweets", "restaurant", "cafe",
            "bakery", "grocery", "groceries", "vegetable", "vegetables", "veggies",
            "fruit", "fruits", "rice", "dal", "chicken", "mutton", "fish", "egg",
            "eggs", "milk", "bread", "noodles", "pasta", "dosa", "idli", "samosa",
            "paneer", "kebab", "kabab", "shawarma", "roll", "wrap", "thali", "curry",
            "dinner", "supper", "kfc", "mcdonald", "mcdonalds", "dominos", "starbucks",
            "subway", "zomato", "swiggy", "atta", "wheat", "oil", "sugar", "salt",
            "groceries"
        ),
        "Shopping & Apparels" to listOf(
            "shirt", "tshirt", "t-shirt", "t shirt", "pant", "pants", "jeans", "dress",
            "saree", "sari", "shoe", "shoes", "sandal", "sandals", "watch", "bag",
            "purse", "wallet", "clothes", "clothing", "apparel", "apparels", "fashion",
            "kurta", "kurti", "shorts", "jacket", "sweater", "hoodie", "tie", "belt",
            "cosmetic", "cosmetics", "makeup", "lipstick", "perfume", "myntra", "ajio"
        ),
        "Travel & Commute" to listOf(
            "uber", "ola", "rapido", "auto", "rickshaw", "cab", "taxi", "metro",
            "bus", "train", "flight", "petrol", "fuel", "diesel", "cng", "parking",
            "toll", "ride", "travel", "trip", "ticket", "ticket booking", "irctc",
            "redbus", "indigo", "spicejet", "vistara"
        ),
        "Subscription & Leisure" to listOf(
            "netflix", "prime", "amazon prime", "spotify", "youtube", "movie",
            "movies", "bookmyshow", "concert", "game", "gaming", "subscription",
            "membership", "gym", "fitness", "yoga", "hotstar", "jio cinema",
            "disney"
        ),
        "Healthcare & Medical" to listOf(
            "medicine", "medicines", "doctor", "hospital", "clinic", "pharmacy",
            "medical", "health", "checkup", "consultation", "lab", "blood test",
            "test", "1mg", "pharmeasy", "apollo", "tablet", "tablets", "syrup",
            "ointment", "vaccine"
        ),
        "Rent & Utilities" to listOf(
            "rent", "electricity", "water bill", "water", "gas bill", "gas",
            "internet", "wifi", "phone bill", "recharge", "broadband", "utility",
            "utilities", "maintenance", "society"
        ),
        "Credit Card Bill" to listOf(
            "credit card bill", "cc bill", "card bill", "credit card payment",
            "credit card"
        ),
        "Lending" to listOf(
            "lent", "lent to", "loan", "gave to", "paid friend", "loaned"
        ),
        "Borrowing" to listOf(
            "borrowed", "borrowed from", "owed", "owe", "took from", "took loan"
        )
    )

    fun findAppPreset(text: String): AppPreset? {
        val lower = " " + text.lowercase().trim() + " "
        // Sort by alias length descending so "phone pay" matches before "pay"
        val sorted = APP_ALIASES.entries
            .flatMap { (id, aliases) -> aliases.map { id to it } }
            .sortedByDescending { it.second.length }
        for ((id, alias) in sorted) {
            if (lower.contains(" $alias ") || lower.contains(" $alias.") ||
                lower.contains(" $alias,") || lower.contains("$alias ")) {
                return APP_PRESETS.firstOrNull { it.id == id }
            }
        }
        return null
    }

    fun inferPurpose(text: String): String? {
        val lower = text.lowercase()
        val scored = PURPOSE_KEYWORDS.mapValues { (_, keywords) ->
            keywords.count { kw -> lower.contains(kw) }
        }.filterValues { it > 0 }
        return scored.maxByOrNull { it.value }?.key
    }

    fun extractAmount(text: String): Double? {
        val cleaned = text.lowercase()
        // Try in order of specificity
        val patterns = listOf(
            // 1.5k, 2k
            Regex("""(?<![\w.])(\d+(?:\.\d+)?)\s*k\b"""),
            // ₹300 / rs 300 / rs. 300 / inr 300 / rupees 300
            Regex("""(?:₹|rs\.?|inr|rupees?)\s*(\d+(?:[,.]?\d+)*)"""),
            // 300 ₹ / 300 rs / 300 rupees
            Regex("""(\d+(?:[,.]?\d+)*)\s*(?:₹|rs\.?|inr|rupees?)"""),
            // Standalone number (last resort)
            Regex("""(?<![\w.])(\d+(?:\.\d+)?)(?![\w.])""")
        )

        for ((idx, regex) in patterns.withIndex()) {
            val m = regex.find(cleaned) ?: continue
            val rawNum = m.groupValues[1].replace(",", "")
            val num = rawNum.toDoubleOrNull() ?: continue
            val multiplier = if (idx == 0) 1000.0 else 1.0
            return num * multiplier
        }
        return null
    }

    /**
     * Extracts the "thing" being spent on (or the recipient) as a description, e.g.
     * "spend 300 on biryani using phone pay" -> "Biryani".
     * "lent 5000 to sreenu friend on 19th may" -> "Sreenu Friend".
     *
     * Avoids capturing date phrases — the same words that [extractTimestamp]
     * understands are excluded via negative lookahead.
     */
    fun extractDescription(text: String): String {
        val lower = text.lowercase()
        val months = MONTHS.keys.joinToString("|")
        val days = DAYS_OF_WEEK.keys.joinToString("|")

        // Substrings that look like a date and should NOT be picked up as a description.
        val dateLookahead = "(?:" +
            "\\d{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:$months)|" +
            "(?:$months)\\s+\\d{1,2}|" +
            "today|yesterday|tomorrow|day\\s+before\\s+yesterday|" +
            "\\d{1,2}[/-]\\d{1,2}|" +
            "\\d+\\s+days?\\s+ago|" +
            "last\\s+(?:$days|week)|" +
            "(?:$days)" +
            ")"

        val stopAfter = "(?:using|via|through|with|from|by|on|at|in)"

        val patterns = listOf(
            // Recipient: "lent/paid/gave/sent N (rs|k)? to <person>"
            Regex("""\b(?:lent|paid|gave|sent)\s+\d+(?:[.,]\d+)?\s*(?:rs\.?|₹|inr|rupees?|k)?\s+to\s+(.+?)(?=\s+$stopAfter\b|$)"""),
            // Source: "borrowed N (rs|k)? from <person>"
            Regex("""\b(?:borrowed|took)\s+\d+(?:[.,]\d+)?\s*(?:rs\.?|₹|inr|rupees?|k)?\s+from\s+(.+?)(?=\s+$stopAfter\b|$)"""),
            // Subject: "on <thing>" where <thing> isn't a date
            Regex("""\bon\s+(?!$dateLookahead)(.+?)(?=\s+$stopAfter\b|$)"""),
            // Subject: "for <thing>" where <thing> isn't a date
            Regex("""\bfor\s+(?!$dateLookahead)(.+?)(?=\s+$stopAfter\b|$)"""),
            // Place: "at <place>" where <place> isn't a date
            Regex("""\bat\s+(?!$dateLookahead)(.+?)(?=\s+$stopAfter\b|$)""")
        )

        for (p in patterns) {
            val m = p.find(lower) ?: continue
            var desc = m.groupValues[1].trim()
            // Strip any trailing "from/by/on/..." remnant
            listOf("using", "via", "through", "with", "from", "by", "on", "at", "in").forEach { sw ->
                val idx = desc.indexOf(" $sw ")
                if (idx > 0) desc = desc.substring(0, idx).trim()
            }
            // Strip any amount tokens that snuck in
            desc = desc.replace(Regex("""\b\d+(?:[.,]\d+)?\s*(?:k|rs\.?|₹|inr|rupees?)?\b"""), "").trim()
            if (desc.isNotBlank()) {
                return desc.split(' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            }
        }
        return ""
    }

    private val MONTHS: Map<String, Int> = mapOf(
        "january" to 0, "jan" to 0,
        "february" to 1, "feb" to 1,
        "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3,
        "may" to 4,
        "june" to 5, "jun" to 5,
        "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7,
        "september" to 8, "sept" to 8, "sep" to 8,
        "october" to 9, "oct" to 9,
        "november" to 10, "nov" to 10,
        "december" to 11, "dec" to 11
    )

    private val DAYS_OF_WEEK: Map<String, Int> = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    /**
     * Pulls a date out of a free-form sentence and returns a timestamp (millis).
     * Recognized patterns include:
     *   - today / yesterday / day before yesterday / tomorrow
     *   - N days ago, N weeks ago, last week
     *   - last monday / on friday / past sunday
     *   - 2026-05-19  (ISO)
     *   - 19/05, 19/05/2026, 19-05-26
     *   - 19th may, 19 may 2026, may 19, may 19th 2026
     * Returns null when nothing matches — caller should fall back to "now".
     * If only month+day is given (no year) and the resulting date would be in
     * the future, we shift one year back since past expenses are far more common.
     */
    fun extractTimestamp(text: String): Long? {
        val lower = text.lowercase()
        val baseCal = midnightToday()

        // 1. Relative keywords
        when {
            Regex("""\bday\s+before\s+yesterday\b""").containsMatchIn(lower) -> {
                baseCal.add(Calendar.DAY_OF_MONTH, -2)
                return baseCal.timeInMillis
            }
            Regex("""\byesterday\b""").containsMatchIn(lower) -> {
                baseCal.add(Calendar.DAY_OF_MONTH, -1)
                return baseCal.timeInMillis
            }
            Regex("""\btomorrow\b""").containsMatchIn(lower) -> {
                baseCal.add(Calendar.DAY_OF_MONTH, 1)
                return baseCal.timeInMillis
            }
            Regex("""\btoday\b""").containsMatchIn(lower) -> {
                return baseCal.timeInMillis
            }
        }

        Regex("""\b(\d+)\s+days?\s+ago\b""").find(lower)?.let {
            baseCal.add(Calendar.DAY_OF_MONTH, -it.groupValues[1].toInt())
            return baseCal.timeInMillis
        }
        Regex("""\b(\d+)\s+weeks?\s+ago\b""").find(lower)?.let {
            baseCal.add(Calendar.WEEK_OF_YEAR, -it.groupValues[1].toInt())
            return baseCal.timeInMillis
        }
        if (Regex("""\blast\s+week\b""").containsMatchIn(lower)) {
            baseCal.add(Calendar.WEEK_OF_YEAR, -1)
            return baseCal.timeInMillis
        }

        // 2. "last monday" / "on friday" / "past sunday"
        for ((name, dow) in DAYS_OF_WEEK) {
            if (Regex("""\b(?:last|this|on|past)\s+$name\b""").containsMatchIn(lower) ||
                Regex("""\b$name\b""").containsMatchIn(lower)) {
                val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                var diff = todayDow - dow
                if (diff <= 0) diff += 7
                baseCal.add(Calendar.DAY_OF_MONTH, -diff)
                return baseCal.timeInMillis
            }
        }

        // 3. ISO: 2026-05-19
        Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""").find(lower)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                return midnightOf(y, m - 1, d)
            }
        }

        // 4. DD/MM(/YYYY) or DD-MM(-YYYY)
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""").find(lower)?.let {
            val day = it.groupValues[1].toInt()
            val month = it.groupValues[2].toInt()
            val yearStr = it.groupValues[3]
            if (day in 1..31 && month in 1..12) {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val year = if (yearStr.isBlank()) currentYear else {
                    val y = yearStr.toInt()
                    if (y < 100) 2000 + y else y
                }
                return clampToPast(midnightOf(year, month - 1, day), yearProvided = yearStr.isNotBlank())
            }
        }

        // 5. "19th may", "19 may 2026", "19 may"
        val monthAlt = MONTHS.keys.joinToString("|")
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($monthAlt)(?:\s+(\d{2,4}))?\b""").find(lower)?.let {
            val day = it.groupValues[1].toInt()
            val month = MONTHS[it.groupValues[2]] ?: return@let
            val yearStr = it.groupValues[3]
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val year = if (yearStr.isBlank()) currentYear else {
                val y = yearStr.toInt()
                if (y < 100) 2000 + y else y
            }
            if (day in 1..31) {
                return clampToPast(midnightOf(year, month, day), yearProvided = yearStr.isNotBlank())
            }
        }

        // 6. "may 19", "may 19th 2026"
        Regex("""\b($monthAlt)\s+(\d{1,2})(?:st|nd|rd|th)?(?:[,\s]+(\d{2,4}))?\b""").find(lower)?.let {
            val month = MONTHS[it.groupValues[1]] ?: return@let
            val day = it.groupValues[2].toInt()
            val yearStr = it.groupValues[3]
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val year = if (yearStr.isBlank()) currentYear else {
                val y = yearStr.toInt()
                if (y < 100) 2000 + y else y
            }
            if (day in 1..31) {
                return clampToPast(midnightOf(year, month, day), yearProvided = yearStr.isNotBlank())
            }
        }

        return null
    }

    private fun midnightToday(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun midnightOf(year: Int, monthZeroBased: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun clampToPast(millis: Long, yearProvided: Boolean): Long {
        if (yearProvided) return millis
        val now = System.currentTimeMillis()
        // Allow up to 1 day in the future to absorb timezone wiggle.
        return if (millis > now + 86_400_000L) {
            Calendar.getInstance().apply {
                timeInMillis = millis
                add(Calendar.YEAR, -1)
            }.timeInMillis
        } else millis
    }

    /**
     * Normalize a free-form purpose string from the LLM to the nearest preset.
     * Returns null if nothing reasonable matches.
     */
    fun normalizePurpose(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val lower = raw.trim().lowercase()
        // Exact (case-insensitive) match first
        PURPOSE_PRESETS.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }?.let { return it }
        // Partial match by significant token
        return PURPOSE_PRESETS.firstOrNull { preset ->
            val presetTokens = preset.lowercase().split(" ", "&").filter { it.length > 3 }
            presetTokens.any { lower.contains(it) }
        }
    }

    /**
     * Normalize a free-form app name from the LLM to a known preset (or null).
     */
    fun normalizeAppToPreset(raw: String?): AppPreset? {
        if (raw.isNullOrBlank()) return null
        return findAppPreset(raw)
            ?: APP_PRESETS.firstOrNull { it.displayName.equals(raw.trim(), ignoreCase = true) }
    }
}
