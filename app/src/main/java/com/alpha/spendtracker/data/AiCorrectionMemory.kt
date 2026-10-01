package com.alpha.spendtracker.data

/**
 * One learned mapping from a keyword in the user's notes to the app / purpose they corrected to.
 * Hits are counted per field so an app correction never lends confidence to a purpose guess.
 */
data class CorrectionRule(
    val keyword: String,
    val app: String? = null,
    val appHits: Int = 0,
    val purpose: String? = null,
    val purposeHits: Int = 0,
    val updatedAt: Long = 0L
) {
    val hits: Int get() = appHits + purposeHits
}

/**
 * On-device "learns your corrections" logic for AI entry. Pure functions over a rule list;
 * persistence lives in [AiCorrectionRepository] and is never synced.
 */
object AiCorrectionMemory {
    const val MAX_RULES = 100
    const val MIN_APP_HITS = 1
    // A single "Lent to Rahul" correction must not turn "dinner with Rahul" into Lending.
    const val MIN_PURPOSE_HITS = 2
    private const val MAX_KEYWORDS = 2
    private const val MIN_TOKEN_LENGTH = 3

    private val TOKEN_SPLIT = Regex("""[^\p{L}\p{M}]+""")

    // Internal so SpendRecap's "most spent on" item stat drops the same filler words.
    internal val STOPWORDS = setOf(
        "the", "and", "for", "with", "from", "into", "onto", "via", "using", "through",
        "paid", "pay", "spent", "spend", "bought", "buy", "lent", "lend", "borrowed", "borrow",
        "gave", "give", "sent", "send", "got", "took", "take", "received",
        "rupees", "rupee", "inr", "today", "yesterday", "tomorrow", "last", "this", "that",
        "some", "was", "were", "has", "had", "have", "are", "but", "not", "all", "any",
        "our", "your", "his", "her", "its", "they", "them", "she", "him", "you", "my", "me"
    )

    fun tokenize(text: String): List<String> =
        text.lowercase().split(TOKEN_SPLIT).filter { it.length >= MIN_TOKEN_LENGTH }

    fun extractKeywords(notes: String): List<String> =
        tokenize(notes).filterNot { it in STOPWORDS }.distinct().take(MAX_KEYWORDS)

    /**
     * Records a correction. [app] / [purpose] are the corrected values, null for a field the
     * user left alone. Re-teaching the same value adds a hit; teaching a different value
     * replaces it and restarts that field's count.
     */
    fun learn(
        rules: List<CorrectionRule>,
        notes: String,
        app: String?,
        purpose: String?,
        now: Long
    ): List<CorrectionRule> {
        if (app.isNullOrBlank() && purpose.isNullOrBlank()) return rules
        val keywords = extractKeywords(notes)
        if (keywords.isEmpty()) return rules

        val byKeyword = rules.associateBy { it.keyword }.toMutableMap()
        for (kw in keywords) {
            val old = byKeyword[kw] ?: CorrectionRule(kw)
            byKeyword[kw] = old.copy(
                app = app?.takeIf { it.isNotBlank() } ?: old.app,
                appHits = nextHits(app, old.app, old.appHits),
                purpose = purpose?.takeIf { it.isNotBlank() } ?: old.purpose,
                purposeHits = nextHits(purpose, old.purpose, old.purposeHits),
                updatedAt = now
            )
        }
        return evict(byKeyword.values.toList(), protect = keywords.toSet())
    }

    private fun nextHits(new: String?, old: String?, oldHits: Int): Int = when {
        new.isNullOrBlank() -> oldHits
        new == old -> oldHits + 1
        else -> 1
    }

    /** Keeps [MAX_RULES], dropping lowest hits then oldest — but never the rule just taught. */
    private fun evict(rules: List<CorrectionRule>, protect: Set<String>): List<CorrectionRule> {
        if (rules.size <= MAX_RULES) return rules
        val (kept, candidates) = rules.partition { it.keyword in protect }
        val survivors = candidates
            .sortedWith(compareByDescending<CorrectionRule> { it.hits }.thenByDescending { it.updatedAt })
            .take((MAX_RULES - kept.size).coerceAtLeast(0))
        return kept + survivors
    }

    data class Match(val app: String? = null, val purpose: String? = null)

    /** Longest matching keyword wins, chosen independently for each field. */
    fun match(rules: List<CorrectionRule>, text: String): Match {
        if (rules.isEmpty()) return Match()
        val tokens = tokenize(text).toSet()
        val matching = rules.filter { it.keyword in tokens }
        return Match(
            app = matching.filter { it.app != null && it.appHits >= MIN_APP_HITS }
                .maxByOrNull { it.keyword.length }?.app,
            purpose = matching.filter { it.purpose != null && it.purposeHits >= MIN_PURPOSE_HITS }
                .maxByOrNull { it.keyword.length }?.purpose
        )
    }

    /**
     * Overlays learned values on a parsed result. An app the user explicitly named in [text]
     * still beats a learned app; otherwise learned values beat the LLM guess and the defaults.
     */
    fun apply(result: AiTransactionResponse, text: String, rules: List<CorrectionRule>): AiTransactionResponse {
        val m = match(rules, text)
        var out = result

        if (m.app != null && AiParser.findAppPreset(text) == null) {
            val preset = AiParser.normalizeAppToPreset(m.app)
            val appName = preset?.displayName ?: m.app
            if (!appName.equals(out.appName, ignoreCase = true)) {
                out = out.copy(appName = appName, appPresetId = preset?.id ?: "other", learnedApp = true)
            }
        }

        if (m.purpose != null && m.purpose != out.purpose) {
            val isLendBorrow = m.purpose == "Lending" || m.purpose == "Borrowing"
            out = out.copy(
                purpose = m.purpose,
                personName = if (isLendBorrow) out.personName.ifBlank { AiParser.extractCounterparty(text) } else "",
                learnedPurpose = true
            )
        }
        return out
    }
}
