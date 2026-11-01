package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.parseLendBorrowNotes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Decides what the AI History Assistant is shown for one question, and renders it.
 * Pure functions over a spend list — no Android, no network — so the scoping rules are
 * unit-testable.
 *
 * Three rules shape the context, each a fix for something the assistant used to get wrong:
 *
 *  1. **Dues stay out of spending.** Lending/Borrowing rows are loans, not spending, and the
 *     dashboard already excludes them. They are only in scope when the question is about them
 *     ([DuesMode]); a "summarise my spend" used to add every loan to the total.
 *  2. **Totals are computed here, not by the model.** The old context sent up to 200 rows and a
 *     "Total" summed over that *truncated* list, then asked the model to add the rows again.
 *     Both are now exact over every matching row; the rows are a capped sample for detail.
 *  3. **Only what the question names is sent.** Period (incl. month names and years), purpose
 *     keywords and payment-app aliases narrow the rows. A question that matches nothing gets an
 *     honest "nothing matched" plus a compact overview, not 200 unrelated rows.
 *
 * Three more things are decided here for the same reason (the model is bad at arithmetic and at
 * guessing what "and last month?" refers to):
 *
 *  4. **Follow-ups inherit.** A short continuation ("and last month?", "just food", "what about
 *     Swiggy?") that names no period/purpose/app of its own takes the missing parts from the
 *     previous question ([isFollowUp], [Scope.inheritFrom], [resolveScope]). A fresh question is
 *     never touched, so "how much did I spend in total" can't pick up a stale month.
 *  5. **Comparisons are subtracted here.** "this month vs last month", "more than last year",
 *     "food vs travel this month" render both sides' exact totals plus a delta ([Comparison]);
 *     the model quotes it instead of doing the subtraction and the percentage itself.
 *  6. **Budget pace is computed here.** With a monthly budget set, a question about the current
 *     month or about budget/on-track/left gets a BUDGET block (spent, remaining, projection).
 */
object HistoryQuery {
    /** Most rows listed for a detail question. Totals still cover every matching row. */
    const val MAX_ROWS = 100

    /** Most rows listed for a "summarise / overview" question, where totals do the talking. */
    const val MAX_SUMMARY_ROWS = 30

    /** Rows listed per side of a comparison; the totals above them cover every matching row. */
    const val MAX_COMPARE_ROWS = 15

    private const val LENDING = "Lending"
    private const val BORROWING = "Borrowing"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MAX_GROUP_LINES = 12
    private const val MAX_PEOPLE = 15

    // Caps for stored text rendered into the prompt. Notes, names, labels and custom purposes are
    // free text — possibly pasted from a shared message — so each goes through
    // [PromptGuard.oneLine]: no newline to forge a `TOTALS:` header or a fake row, no `|` to forge
    // a column, no invisible characters, bounded length. Matching and grouping still use the raw
    // values; only what the model reads is cleaned.
    private const val NOTE_CHARS = 200
    private const val NAME_CHARS = 60

    private fun safe(text: String?, max: Int = NAME_CHARS): String = PromptGuard.oneLine(text, max)
    private const val MAX_MONTHS = 12
    private const val OVERVIEW_MONTHS = 6

    /**
     * How much of a result the context carries. [FULL] is for the cloud. [LOCAL] fits the
     * on-device model, whose window is under 4000 tokens *including its answer*: every TOTALS line
     * and comparison delta stays exact, and what shrinks is the sampled rows, the long group lists,
     * and the recurring-bills and notes sections, which are listed only when the question mentions
     * them. The model is then asked about the same numbers, with fewer rows to quote from.
     */
    class Limits(
        val rows: Int,
        val summaryRows: Int,
        val compareRows: Int,
        val groupLines: Int,
        val people: Int,
        val months: Int,
        val sideSectionsOnlyWhenAsked: Boolean
    ) {
        companion object {
            val FULL = Limits(MAX_ROWS, MAX_SUMMARY_ROWS, MAX_COMPARE_ROWS, MAX_GROUP_LINES, MAX_PEOPLE, MAX_MONTHS, false)
            val LOCAL = Limits(rows = 10, summaryRows = 6, compareRows = 4, groupLines = 6, people = 6, months = 6, sideSectionsOnlyWhenAsked = true)
        }
    }

    private val BILLS_ASKED = Regex("""\b(?:bills?|subscriptions?|recurring|emis?|renewals?)\b""")
    private val NOTES_ASKED = Regex("""\b(?:notes?|entries|entry|wishlist|checklist)\b""")

    enum class DuesMode {
        /** Everyday spending only (the default). */
        EXCLUDE,

        /** The question is about lending/borrowing: only those rows. */
        ONLY,

        /** The question asks for both ("…including what I lent"): shown in separate sections. */
        INCLUDE
    }

    data class Scope(
        /** `[start, endExclusive)`; null means all time. */
        val period: SpendRecap.Period?,
        val periodLabel: String,
        /** Purpose presets the question names (empty = no purpose filter). */
        val purposes: List<String>,
        /** Payment-app display names the question names (empty = no app filter). */
        val apps: List<String>,
        val dues: DuesMode,
        /** "Summarise / overview": totals matter more than rows. */
        val summary: Boolean,
        /**
         * The question itself named a period (even "all time"). False means [period] is just the
         * all-time default, which is what lets a follow-up tell "no period given" from "all time".
         */
        val periodNamed: Boolean = false,
        /** What a follow-up took from the previous question: [CARRIED_PERIOD] and/or [CARRIED_FILTERS]. */
        val carriedOver: List<String> = emptyList(),
        /** Set when the question sets two things side by side; see [Comparison]. */
        val comparison: Comparison? = null
    )

    /** What a [Comparison] sets side by side. */
    enum class CompareBy { PERIOD, PURPOSE, APP }

    /**
     * One side of a comparison. [label] is the short name the delta lines use ("last month",
     * "Travel"); [window] is the full period description with exact dates.
     */
    data class Side(
        val label: String,
        val window: String,
        val period: SpendRecap.Period?,
        val purposes: List<String>,
        val apps: List<String>
    )

    /**
     * Exactly two sides. For [CompareBy.PERIOD] they are chronological (`sides[0]` is the earlier,
     * the baseline), so a delta always reads "from earlier to later"; for purposes and apps they
     * are in the order the user wrote them.
     */
    data class Comparison(val by: CompareBy, val sides: List<Side>)

    const val CARRIED_PERIOD = "period"
    const val CARRIED_FILTERS = "filters"

    data class Context(
        val text: String,
        val scope: Scope,
        /** Rows that matched the scope — all of them, not just the ones listed. */
        val matchedCount: Int
    )

    // ------------------------------------------------------------------------------------------
    // Question → scope
    // ------------------------------------------------------------------------------------------

    private val DUES_WORDS = Regex(
        """\b(?:lend|lends|lent|lending|lendings|borrow|borrows|borrowed|borrowing|borrowings|owe|owes|owed|dues|udhar|udhaar)\b|उधार|అప్పు"""
    )
    private val INCLUDE_WORDS = Regex("""\b(?:including|include|includes|along with|together with|as well as|plus)\b""")
    private val SUMMARY_WORDS = Regex(
        """\b(?:summar\w*|overview|breakdown|break\s+down|insights?|analy[sz]\w*|report|recap)\b"""
    )
    private val ALL_TIME = Regex("""\b(?:all time|alltime|overall|ever|lifetime)\b""")
    private val YESTERDAY = Regex("""\byesterday\b""")
    private val TODAY = Regex("""\btoday\b""")
    private val ROLLING = Regex("""\b(?:last|past|previous|prev)\s+(\d{1,3})\s+(day|week|month|year)s?\b""")
    private val PER_UNIT = Regex("""\b(?:per|each|every|a|an|by)\s+(?:week|month|year)s?\b""")
    private val UNIT = Regex("""\b(?:(this|current|last|previous|prev|past)\s+)?(week|month|year)\b""")
    private val YEAR = Regex("""\b(?:in|of|for|during|year)\s+(20\d{2})\b""")
    private val MONTH_NAME = Regex(
        """\b(january|february|march|april|may|june|july|august|september|october|november|december|""" +
            """jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)\b(?:\s+(?:of\s+)?(20\d{2}))?"""
    )
    private val MONTH_ABBR = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    // Prepositions that make "may" a month rather than the verb.
    private val BEFORE_MAY = Regex("""\b(?:in|of|for|during|from|since|until|till)\s*$""")

    /**
     * Words that name a purpose. The old filter looked for the preset's exact name, so
     * "Groceries & Food" only matched if the user typed that — in practice it never fired and
     * every question was answered over the whole log. Only category-level words are here:
     * "dinner" or "chicken" live in notes under whatever purpose the user picked, so they are
     * left for the model to find rather than filtered away. Lending/Borrowing are not purposes
     * here; they are [DuesMode].
     */
    internal val PURPOSE_WORDS: List<Pair<String, Regex>> = listOf(
        "Food & Dining" to Regex("""\b(?:foods?|dining|restaurants?|meals?|snacks?|eat|eating|swiggy|zomato)\b"""),
        "Groceries" to Regex("""\b(?:groceries|grocery|supermarket|kirana|vegetables?|veggies?|fruits?|milk|blinkit|zepto)\b"""),
        "Housing" to Regex("""\b(?:housing|rent|house\s*rent|maintenance|repairs?|society)\b"""),
        "Bills & Utilities" to Regex("""\b(?:utilit(?:y|ies)|electricity|water|gas|wifi|internet|broadband|recharge|dth|current\s*bill)\b"""),
        "Shopping" to Regex("""\b(?:shopping|apparels?|clothes|clothing|fashion|footwear|dress|amazon|flipkart|myntra)\b"""),
        "Commute" to Regex("""\b(?:commute|commuting|cab|cabs|auto|metro|bus|petrol|diesel|cng|fuel|uber|ola|rapido)\b"""),
        "Travel" to Regex("""\b(?:travel|travelling|traveling|transport|flight|flights|train|trains|hotel|hotels|holiday|vacation|irctc)\b"""),
        "EMIs & Cards" to Regex("""\b(?:credit\s*cards?|card\s*bills?|cc\s*bills?|emis?|loans?|cred)\b"""),
        "Subscriptions" to Regex("""\b(?:subscriptions?|netflix|spotify|prime|youtube|ott|hotstar)\b"""),
        "Healthcare" to Regex("""\b(?:health|healthcare|medical|medicines?|hospital|doctor|clinic|pharmacy)\b"""),
        "Investments" to Regex("""\b(?:investments?|investing|stocks?|mutual\s*funds?|sip|shares?|gold)\b"""),
        "Savings" to Regex("""\b(?:savings?|deposit|rd|fd)\b"""),
        "Personal Care" to Regex("""\b(?:personal\s*care|grooming|salon|parlour|skincare|cosmetics)\b"""),
        "Education" to Regex("""\b(?:education|learning|courses?|tuition|books?|school|college|fees)\b"""),
        "Entertainment" to Regex("""\b(?:entertainment|leisure|movies?|gaming|games?|events?|cinema)\b"""),
        "Insurance & Taxes" to Regex("""\b(?:insurance|taxes|tax|lic)\b"""),
        "Gifts & Donations" to Regex("""\b(?:gifts?|donations?|charity)\b"""),
        "Fitness" to Regex("""\b(?:fitness|gym|sports)\b"""),
        "Pets" to Regex("""\b(?:pets?|pet\s*food|vet|veterinary)\b"""),
        "Work Expenses" to Regex("""\b(?:work|office|professional)\b"""),
        "Miscellaneous" to Regex("""\b(?:miscellaneous|misc|unexpected|others?)\b""")
    )

    fun parseScope(
        question: String,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        weekStart: Int = Calendar.getInstance(tz).firstDayOfWeek,
        custom: CustomPresets = CustomPresets.EMPTY
    ): Scope {
        val q = question.lowercase()
        val clock = Clock(tz, now, weekStart)
        val found = findPeriod(q, clock)

        val dues = when {
            !DUES_WORDS.containsMatchIn(q) -> DuesMode.EXCLUDE
            INCLUDE_WORDS.containsMatchIn(q) -> DuesMode.INCLUDE
            else -> DuesMode.ONLY
        }
        // Dues rows carry no purpose beyond Lending/Borrowing, so a purpose word can't narrow them.
        // The user's own categories and apps are named in full ("pet grooming", "jio mart"). When
        // one is, the built-in word inside it ("pet", "zepto" in "zepto cafe") must not also widen
        // the filter to the whole built-in bucket.
        val customCategories = custom.categories.filter { AiParser.mentionsName(q, it) }
        val customApps = custom.apps.filter { AiParser.mentionsName(q, it) }
        val purposes = if (dues == DuesMode.ONLY) emptyList()
        else PURPOSE_WORDS.filter { (_, regex) ->
            regex.findAll(q).any { m -> customCategories.none { c -> q.indexOf(c.lowercase()).let { at -> at >= 0 && m.range.first >= at && m.range.last < at + c.length } } }
        }.map { it.first } + customCategories
        val apps = APP_PRESETS.filter { preset ->
            preset.id != "other" && AiParser.mentionsApp(q, preset) &&
                customApps.none { CustomPresets.key(it).contains(CustomPresets.key(preset.displayName)) }
        }.map { it.displayName } + customApps

        val comparison = resolveComparison(q, clock, found, purposes, apps)
        return Scope(
            // A period comparison reads from its later side, so anything that only wants "the"
            // period (the budget check, the SCOPE header) sees the more recent one.
            period = if (comparison?.by == CompareBy.PERIOD) comparison.sides[1].period else found.period,
            periodLabel = if (comparison?.by == CompareBy.PERIOD) {
                "comparing ${comparison.sides[1].window} with ${comparison.sides[0].window}"
            } else found.label,
            purposes = purposes,
            apps = apps,
            dues = dues,
            summary = SUMMARY_WORDS.containsMatchIn(q),
            periodNamed = found.kind != PeriodKind.NONE,
            comparison = comparison
        )
    }

    // ------------------------------------------------------------------------------------------
    // Follow-ups
    // ------------------------------------------------------------------------------------------

    /** Most earlier questions a follow-up chain is folded over (a session holds at most 7). */
    private const val MAX_CHAIN = 6

    /** A continuation lead-in ("and last month?", "just food", "what about Swiggy?"). */
    private val FOLLOW_UP_LEAD = Regex("""^(?:(?:ok|okay|so)[,\s]+)?(?:and|also|then|only|just|same|what about|how about)\b""")

    /** Points back at the previous answer. Pronouns only: "more"/"other" turn up in fresh questions. */
    private val BACK_REFERENCE = Regex("""\b(?:it|that|those|them|these)\b""")
    private val WHITESPACE = Regex("""\s+""")

    /** Longest lead-in question treated as a follow-up; a long one is a fresh question that starts with "and". */
    private const val MAX_LEAD_WORDS = 8

    /** Longest back-reference question treated as a follow-up ("break it down", "why is that so high"). */
    private const val MAX_BACK_REFERENCE_WORDS = 6

    /**
     * Is [question] a continuation of the previous one rather than a fresh question? Deliberately
     * conservative, because a wrong "yes" silently applies a stale month or category to a
     * question that never asked for it:
     *  - a short question that opens with and / also / then / only / just / same / what about /
     *    how about, or
     *  - a very short question that names nothing of its own and points back with it / that /
     *    those / them / these.
     * "how much did I spend in total" and "which app do I use the most" are neither.
     */
    fun isFollowUp(question: String, own: Scope): Boolean {
        val q = question.trim().lowercase().trimStart { !it.isLetterOrDigit() }
        if (q.isEmpty()) return false
        val words = q.split(WHITESPACE).size
        if (FOLLOW_UP_LEAD.containsMatchIn(q) && words <= MAX_LEAD_WORDS) return true
        val namesSomething = own.periodNamed || own.purposes.isNotEmpty() || own.apps.isNotEmpty() ||
            own.dues != DuesMode.EXCLUDE
        return !namesSomething && words <= MAX_BACK_REFERENCE_WORDS && BACK_REFERENCE.containsMatchIn(q)
    }

    /**
     * Fills in what this follow-up left unsaid from [previous]. The parts are independent:
     *  - a period of its own ("and last month?") replaces the period and keeps the filters;
     *  - a purpose, app or dues word of its own ("just food", "what about Swiggy?") replaces the
     *    filters as a group and keeps the period. Replacing them together matters: carrying
     *    "Swiggy" into "what about travel?" would answer about Swiggy travel, which is nothing;
     *  - naming neither ("and the biggest one?") keeps both.
     * [summary] is never inherited, and neither is a [Comparison].
     */
    fun Scope.inheritFrom(previous: Scope): Scope {
        val ownFilters = purposes.isNotEmpty() || apps.isNotEmpty() || dues != DuesMode.EXCLUDE
        val takePeriod = !periodNamed && previous.periodNamed
        val takeFilters = !ownFilters && (previous.purposes.isNotEmpty() || previous.apps.isNotEmpty() ||
            previous.dues != DuesMode.EXCLUDE)
        return copy(
            period = if (takePeriod) previous.period else period,
            periodLabel = if (takePeriod) previous.periodLabel else periodLabel,
            periodNamed = periodNamed || previous.periodNamed,
            purposes = if (takeFilters) previous.purposes else purposes,
            apps = if (takeFilters) previous.apps else apps,
            dues = if (takeFilters) previous.dues else dues,
            carriedOver = buildList {
                if (takePeriod) add(CARRIED_PERIOD)
                if (takeFilters) add(CARRIED_FILTERS)
            }
        )
    }

    /**
     * The scope of [question] given the questions asked before it in this conversation (oldest
     * first). Each question is folded in turn, so a chain "food last month" → "and travel?" →
     * "and this week?" ends as travel this week, not food this week.
     */
    fun resolveScope(
        question: String,
        earlier: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        weekStart: Int = Calendar.getInstance(tz).firstDayOfWeek,
        custom: CustomPresets = CustomPresets.EMPTY
    ): Scope {
        var carried: Scope? = null
        for (text in earlier.takeLast(MAX_CHAIN) + question) {
            val own = parseScope(text, now, tz, weekStart, custom)
            carried = carried?.takeIf { isFollowUp(text, own) }?.let { own.inheritFrom(it) } ?: own
        }
        return carried ?: parseScope(question, now, tz, weekStart, custom)
    }

    // ------------------------------------------------------------------------------------------
    // Is the question conclusively about the user's finances?
    // ------------------------------------------------------------------------------------------

    /**
     * Words that are about money on their own. "category" and "dues" qualify here although a
     * stray non-finance question could use them: the answering model still declines those.
     */
    private val STRONG_FINANCE = Regex(
        """\b(?:spend|spends|spent|spending|expenses?|expenditure|budgets?|subscriptions?|transactions?|""" +
            """recurring|emis?|categor(?:y|ies)|payment\s+apps?|lent|lend\w*|borrow\w*|owe\w*|dues|udhar|udhaar)\b"""
    )

    /** Hindi/Telugu spellings of spend/budget; `\b` is unreliable next to combining marks. */
    private val STRONG_FINANCE_INDIC = Regex("""खर्च|ख़र्च|बजट|ఖర్చు|బడ్జెట్""")

    /** Finance words that also occur in ordinary sentences ("total population"), so they need [PERSONAL]. */
    private val WEAK_FINANCE = Regex(
        """\b(?:total|totals|average|avg|how\s+much|bills?|dues?|summar\w+|cost|costs|payments?|paid|""" +
            """savings?|income|balance|notes?|money|rupees?)\b"""
    )
    private val PERSONAL = Regex("""\b(?:my|i|me|mine|i've|i'm|ive)\b""")

    /**
     * Numbers that are part of the *shape* of a question rather than an amount: "top 3", "last 7
     * days", "in 2025", "mar 2025", "15th", "2026-09-10". Removed before looking for an amount.
     */
    private val STRUCTURAL_NUMBER = Regex(
        """\b(?:top|first|last|past|previous|prev|bottom|latest|recent|next)\s+\d{1,3}\b|""" +
            """\b\d+\s*(?:day|week|month|year)s?\b|\b(?:in|of|for|during|year|since|from)\s+(?:19|20)\d{2}\b|""" +
            """\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+(?:of\s+)?(?:19|20)\d{2}\b|""" +
            """\b\d+(?:st|nd|rd|th)\b|\b\d{1,4}[-/]\d{1,2}(?:[-/]\d{1,4})?\b"""
    )
    private val AMOUNT_MARKER = Regex("""₹|\brs\.?\s|\binr\b|\busd\b|\$|\b(?:hundred|thousand|lakhs?|lacs?|crores?)\b|\d\s*k\b""")

    /** True when [question] reads like it carries a money amount ("put 500 on groceries"). */
    internal fun hasAmount(question: String): Boolean {
        val q = question.lowercase()
        if (AMOUNT_MARKER.containsMatchIn(q)) return true
        return STRUCTURAL_NUMBER.replace(q, " ").any { it.isDigit() }
    }

    /**
     * Whether the classifier's [verdict] turns the question away: OFF_TOPIC, and neither FINANCIAL
     * nor TRACKING anywhere in it. Fails open, so a chatty or decorated verdict ("FINANCIAL, not
     * OFF_TOPIC") never costs the user a real answer.
     */
    fun isOffTopicVerdict(verdict: String): Boolean {
        val v = verdict.trim().uppercase()
        return v.contains("OFF_TOPIC") && !v.contains("FINANCIAL") && !v.contains("TRACKING")
    }

    /**
     * Whether local evidence alone settles that [question] is about the user's finances, so the
     * model-side intent classifier (one more LLM call per question) can be skipped.
     *
     * The answer is conservative on purpose — "no" only costs the cheap classifier call, which
     * fails open:
     *  - **A money amount always says no.** "put 500 on groceries" is logging, not a question, and
     *    the classifier's TRACKING verdict is the only thing that catches it once the keyword
     *    check in the ViewModel has had its chance. Numbers that only shape a question ("top 3",
     *    "last 7 days", "in 2025") are not amounts.
     *  - Yes for a strong finance word (spend, expense, budget, lent, owe, category…).
     *  - Yes for a weaker word (total, average, how much, bill, summary…) or a named period,
     *    purpose or app, together with a first-person word (my / I / me): "what is my total",
     *    "what did I do last week". Without one it stays ambiguous ("total population of India").
     *  - Yes for a named purpose or app plus a period ("swiggy last month").
     *  - Yes for a follow-up that names a period/purpose/app of its own on top of a scoped
     *    previous question ("and last month?", "what about Swiggy?").
     */
    fun isConclusivelyFinancial(question: String, scope: Scope): Boolean {
        if (hasAmount(question)) return false
        val q = question.lowercase()
        if (STRONG_FINANCE.containsMatchIn(q) || STRONG_FINANCE_INDIC.containsMatchIn(q)) return true

        val personal = PERSONAL.containsMatchIn(q)
        val ownPeriod = scope.periodNamed && CARRIED_PERIOD !in scope.carriedOver
        val ownFilters = (scope.purposes.isNotEmpty() || scope.apps.isNotEmpty() || scope.dues != DuesMode.EXCLUDE) &&
            CARRIED_FILTERS !in scope.carriedOver
        val ownSignal = ownPeriod || ownFilters

        if (personal && (WEAK_FINANCE.containsMatchIn(q) || ownSignal)) return true
        if (ownFilters && ownPeriod) return true
        return scope.carriedOver.isNotEmpty() && ownSignal
    }

    // ------------------------------------------------------------------------------------------
    // Periods
    // ------------------------------------------------------------------------------------------

    private enum class PeriodKind { NONE, ALL_TIME, ROLLING, DAY, MONTH_NAME, YEAR, UNIT }

    /** A period found in a question, with where in the text it was found so a second one can be looked for. */
    private class Found(
        val period: SpendRecap.Period?,
        /** Short name ("last month", "2026-08"); [label] adds the exact dates. */
        val name: String,
        val label: String,
        val kind: PeriodKind,
        val span: IntRange? = null,
        /** [PeriodKind.UNIT] only: "week" | "month" | "year", and whether it was "last …". */
        val unit: String? = null,
        val previous: Boolean = false
    )

    /** "this month" / "last month" (and week, year) relative to [c]. */
    private fun unitFound(unit: String, previous: Boolean, c: Clock, span: IntRange? = null): Found {
        val (current, earlier) = when (unit) {
            "week" -> c.week(0) to c.week(-1)
            "month" -> c.month(0) to c.month(-1)
            else -> c.yearOffset(0) to c.yearOffset(-1)
        }
        val p = if (previous) SpendRecap.Period(earlier, current) else SpendRecap.Period(current, Long.MAX_VALUE)
        val name = (if (previous) "last " else "this ") + unit
        return Found(p, name, c.describe(name, p), PeriodKind.UNIT, span, unit, previous)
    }

    private fun dayFound(name: String, startOffset: Int, open: Boolean, c: Clock, span: IntRange? = null): Found {
        val p = SpendRecap.Period(c.day(startOffset), if (open) Long.MAX_VALUE else c.day(startOffset + 1))
        return Found(p, name, c.describe(name, p), PeriodKind.DAY, span)
    }

    private fun findPeriod(q: String, c: Clock): Found {
        val open = Long.MAX_VALUE

        // An explicit rolling window ("last 7 days", "past 3 months") wins over everything else.
        ROLLING.find(q)?.let { m ->
            val n = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val unit = m.groupValues[2]
            val start = when (unit) {
                "day" -> c.day(-(n - 1))
                "week" -> c.day(-(n * 7 - 1))
                "month" -> c.shifted(Calendar.MONTH, -n)
                else -> c.shifted(Calendar.YEAR, -n)
            }
            val p = SpendRecap.Period(start, open)
            val name = "the last $n $unit${if (n == 1) "" else "s"}"
            return Found(p, name, c.describe(name, p), PeriodKind.ROLLING, m.range)
        }

        ALL_TIME.find(q)?.let { m ->
            return Found(null, "all time", "all time", PeriodKind.ALL_TIME, m.range)
        }

        q.indexOf("day before yesterday").takeIf { it >= 0 }?.let { at ->
            return dayFound("the day before yesterday", -2, open = false, c = c,
                span = at until at + "day before yesterday".length)
        }
        YESTERDAY.find(q)?.let { return dayFound("yesterday", -1, open = false, c = c, span = it.range) }
        TODAY.find(q)?.let { return dayFound("today", 0, open = true, c = c, span = it.range) }

        // A named month, optionally with a year ("in August", "mar 2025"). Without a year it is
        // the most recent such month that has begun — "in December" asked in October is last year.
        MONTH_NAME.findAll(q).forEach { m ->
            val word = m.groupValues[1]
            val explicitYear = m.groupValues[2].toIntOrNull()
            // "may" is also a verb: only a month after a preposition or with a year.
            if (word == "may" && explicitYear == null && !BEFORE_MAY.containsMatchIn(q.substring(0, m.range.first))) {
                return@forEach
            }
            val month = MONTH_ABBR.indexOf(word.take(3))
            if (month < 0) return@forEach
            val year = explicitYear ?: c.year.let { if (month > c.month) it - 1 else it }
            val p = SpendRecap.monthPeriod(year, month, c.tz)
            val name = "$year-${(month + 1).toString().padStart(2, '0')}"
            return Found(p, name, c.describe(name, p), PeriodKind.MONTH_NAME, m.range)
        }

        // A bare number is an amount ("above 2000"), so a year needs a word in front of it.
        YEAR.find(q)?.let { m ->
            val year = m.groupValues[1].toInt()
            val p = SpendRecap.Period(c.yearStart(year), c.yearStart(year + 1))
            return Found(p, "$year", c.describe("$year", p), PeriodKind.YEAR, m.range)
        }

        // "per month" / "each week" / "monthly" are rates, not a window ("\b" already skips the
        // latter). Masked with spaces, not removed, so match positions stay valid for the caller.
        val cleaned = PER_UNIT.replace(q) { " ".repeat(it.value.length) }
        UNIT.find(cleaned)?.let { m ->
            val previous = m.groupValues[1] in setOf("last", "previous", "prev", "past")
            return unitFound(m.groupValues[2], previous, c, m.range)
        }

        return Found(null, "all time", "all time (the question names no period)", PeriodKind.NONE)
    }

    // ------------------------------------------------------------------------------------------
    // Comparisons
    // ------------------------------------------------------------------------------------------

    private val COMPARE_WORDS = Regex(
        """\b(?:vs|versus|compare[sd]?|comparing|comparison|compared\s+(?:to|with)|difference\s+between)\b"""
    )

    /** "more than last month" — but not "more than 500", which is an amount threshold. */
    private val THAN = Regex("""\bthan\b(?!\s*(?:rs\.?\s*|₹\s*|inr\s*)?\d)""")

    /**
     * A comparator followed by a period ("vs last month", "than last year", "compared to august").
     * It says the period is the *baseline*, so "did my food and travel go up vs last month" is a
     * period comparison of food + travel, not food against travel.
     */
    private val PERIOD_AFTER_COMPARATOR = Regex(
        """\b(?:vs|versus|than|compared\s+(?:to|with))\s+(?:the\s+|in\s+)?""" +
            """(?:(?:this|current|last|previous|prev|past)\s+(?:week|month|year)|yesterday|today|""" +
            """january|february|march|april|may|june|july|august|september|october|november|december|""" +
            """jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec|20\d{2})\b"""
    )

    private val COMPARABLE_KINDS = setOf(PeriodKind.DAY, PeriodKind.MONTH_NAME, PeriodKind.YEAR, PeriodKind.UNIT, PeriodKind.ROLLING)

    private fun samePeriod(a: SpendRecap.Period?, b: SpendRecap.Period?) =
        a != null && b != null && a.start == b.start && a.endExclusive == b.endExclusive

    /**
     * Detects "A vs B". Tried in this order:
     *  1. **Two periods** in the text ("this month vs last month", "august and september compare"):
     *     the second is found by blanking out the first and looking again.
     *  2. **Two purposes or two apps** within one period ("food vs travel this month",
     *     "swiggy vs zomato"). Exactly two: with three or more, the By-purpose/By-app lines
     *     already lay them out, and picking two would silently drop the rest. Skipped when the
     *     comparator points at a period ("…vs last month"), see [PERIOD_AFTER_COMPARATOR].
     *  3. **One period and a comparator** ("more than last month", "vs last year", "compared to
     *     August"): the other side is implied — this month/week/year, or today for a day.
     *  4. **A bare "compare"** with no period at all: last month against this month.
     * A comparator is vs / versus / compare / compared to / difference between, or "than" not
     * followed by a number.
     */
    private fun resolveComparison(
        q: String,
        c: Clock,
        first: Found,
        purposes: List<String>,
        apps: List<String>
    ): Comparison? {
        val comparing = COMPARE_WORDS.containsMatchIn(q)
        if (!comparing && !THAN.containsMatchIn(q)) return null

        fun periodSide(f: Found) = Side(f.name, f.label, f.period, purposes, apps)
        fun periodComparison(a: Found, b: Found) =
            Comparison(CompareBy.PERIOD, listOf(a, b).sortedBy { it.period!!.start }.map(::periodSide))

        // 1. two periods
        if (first.kind in COMPARABLE_KINDS && first.span != null) {
            val blanked = q.replaceRange(first.span.first, first.span.last + 1, " ".repeat(first.span.count()))
            val second = findPeriod(blanked, c)
            if (second.kind in COMPARABLE_KINDS && !samePeriod(first.period, second.period)) {
                return periodComparison(first, second)
            }
        }

        // 2. two apps / two purposes within one period. Apps first: "swiggy vs blinkit" also
        // names two purposes (the apps' own words), but the user means the apps. An app
        // comparison drops the purpose filter for the same reason — it is just an echo of the
        // app names, and would leave out e.g. a Swiggy order logged under Groceries.
        val period = first.period
        val window = first.label
        val periodIsBaseline = PERIOD_AFTER_COMPARATOR.containsMatchIn(q)
        if (!periodIsBaseline) {
            if (apps.size == 2) {
                return Comparison(CompareBy.APP, apps.map { Side(it, window, period, emptyList(), listOf(it)) })
            }
            if (purposes.size == 2) {
                return Comparison(CompareBy.PURPOSE, purposes.map { Side(it, window, period, listOf(it), apps) })
            }
            // Three or more things side by side: left to the By-purpose / By-app breakdown. An
            // implied period comparison would add a "last month" nobody asked for.
            if (apps.size > 2 || purposes.size > 2) return null
        }

        // 3. one period, the other side implied
        if (period != null) {
            return when (first.kind) {
                PeriodKind.UNIT -> periodComparison(unitFound(first.unit!!, true, c), unitFound(first.unit, false, c))
                PeriodKind.MONTH_NAME, PeriodKind.YEAR -> {
                    val unit = if (first.kind == PeriodKind.YEAR) "year" else "month"
                    val now = unitFound(unit, false, c)
                    // Asking about the current month/year against itself: use the one before it.
                    if (samePeriod(period.copy(endExclusive = Long.MAX_VALUE), now.period)) {
                        periodComparison(unitFound(unit, true, c), now)
                    } else periodComparison(first, now)
                }
                PeriodKind.DAY -> periodComparison(dayFound("yesterday", -1, false, c), dayFound("today", 0, true, c))
                else -> null
            }
        }

        // 4. a bare "compare"
        if (comparing && first.kind == PeriodKind.NONE) {
            return periodComparison(unitFound("month", true, c), unitFound("month", false, c))
        }
        return null
    }

    /** Local-calendar arithmetic in one timezone, so tests can pin both "now" and the zone. */
    private class Clock(val tz: TimeZone, val now: Long, val weekStart: Int) {
        private fun cal(time: Long = now): Calendar =
            Calendar.getInstance(tz, Locale.ROOT).apply { timeInMillis = time }

        private fun Calendar.midnight(): Calendar = apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val year: Int get() = cal().get(Calendar.YEAR)
        val month: Int get() = cal().get(Calendar.MONTH)
        val dayOfMonth: Int get() = cal().get(Calendar.DAY_OF_MONTH)
        val daysInMonth: Int get() = cal().getActualMaximum(Calendar.DAY_OF_MONTH)

        fun day(offsetDays: Int): Long =
            cal().midnight().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }.timeInMillis

        /** Today's midnight moved by [amount] of a calendar [field] (months and years vary in length). */
        fun shifted(field: Int, amount: Int): Long =
            cal().midnight().apply { add(field, amount) }.timeInMillis

        fun week(offsetWeeks: Int): Long = cal().midnight().apply {
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) - weekStart + 7) % 7))
            add(Calendar.WEEK_OF_YEAR, offsetWeeks)
        }.timeInMillis

        fun month(offsetMonths: Int): Long = cal().midnight().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, offsetMonths)
        }.timeInMillis

        fun yearOffset(offsetYears: Int): Long = yearStart(year + offsetYears)

        fun yearStart(year: Int): Long = cal().midnight().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        fun date(time: Long): String = fmt("yyyy-MM-dd").format(time)

        fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }

        /** "last month (2026-09-01 → 2026-09-30)": the exact window, so the model can quote it. */
        fun describe(name: String, p: SpendRecap.Period): String =
            "$name (${date(p.start)} → ${date(minOf(p.endExclusive - 1, now))})"

        /**
         * Days of [p] that have happened so far (today counts as a whole day), at least 1. The
         * denominator for a per-day pace, so a month that is 9 days old isn't read as a short month.
         */
        fun daysCovered(p: SpendRecap.Period): Int {
            val end = minOf(p.endExclusive, day(1))
            // Rounded, not truncated: a DST change makes a "day" 23 or 25 hours long.
            return (((end - p.start) + DAY_MS / 2) / DAY_MS).toInt().coerceAtLeast(1)
        }

        /** The period is still running (or is open-ended), so it covers days that haven't happened. */
        fun inProgress(p: SpendRecap.Period): Boolean = p.endExclusive > day(1)
    }

    // ------------------------------------------------------------------------------------------
    // Scope → rows
    // ------------------------------------------------------------------------------------------

    private fun isDues(s: Spend) = s.purpose == LENDING || s.purpose == BORROWING

    /** Who a Lending/Borrowing row is with; blank when it only carries the payment app. */
    private fun personOf(s: Spend): String {
        val person = parseLendBorrowNotes(s.notes, s.appName).first.trim()
        return if (person.equals(s.appName.trim(), ignoreCase = true)) "" else person
    }

    private fun detailOf(s: Spend): String = parseLendBorrowNotes(s.notes, s.appName).second.trim()

    private class Selection(val matched: List<Spend>, val excludedDues: Int, val everyday: List<Spend>)

    private fun matchesPurpose(spendPurpose: String, targetPurposes: List<String>): Boolean {
        if (targetPurposes.isEmpty()) return true
        return targetPurposes.any { target ->
            spendPurpose.equals(target, ignoreCase = true) ||
            when (target) {
                "Housing" -> spendPurpose.equals("Rent & Utilities", ignoreCase = true) || spendPurpose.equals("Rent", ignoreCase = true)
                "Bills & Utilities" -> spendPurpose.equals("Rent & Utilities", ignoreCase = true) || spendPurpose.equals("Utilities", ignoreCase = true)
                "Food & Dining" -> spendPurpose.equals("Food & Drinks", ignoreCase = true) || spendPurpose.equals("Food", ignoreCase = true)
                "Shopping" -> spendPurpose.equals("Shopping & Apparels", ignoreCase = true)
                "EMIs & Cards" -> spendPurpose.equals("Credit Card Bill", ignoreCase = true)
                "Commute", "Travel" -> spendPurpose.equals("Travel & Commute", ignoreCase = true) || spendPurpose.equals("Fuel", ignoreCase = true)
                "Subscriptions", "Entertainment" -> spendPurpose.equals("Subscription & Leisure", ignoreCase = true)
                "Healthcare" -> spendPurpose.equals("Healthcare & Medical", ignoreCase = true) || spendPurpose.equals("Health", ignoreCase = true)
                "Investments" -> spendPurpose.equals("Investment", ignoreCase = true)
                else -> false
            }
        }
    }

    /**
     * [personTexts] are tried in order for a person's name ("what does Rahul owe me"): the first
     * text that names someone narrows the dues rows. A follow-up passes its own question first and
     * the previous one second, so "and last month?" keeps Rahul but "and Priya?" switches to her.
     */
    private fun select(spends: List<Spend>, scope: Scope, personTexts: List<String>): Selection {
        val live = spends.filter { !it.deleted }
        val everyday = live.filterNot(::isDues)
        val dues = live.filter(::isDues)
        val period = scope.period
        fun inPeriod(s: Spend) = period == null || (s.timestamp >= period.start && s.timestamp < period.endExclusive)
        fun appOk(s: Spend) = scope.apps.isEmpty() || scope.apps.any { s.appName.contains(it, ignoreCase = true) }

        val everydayHits = if (scope.dues == DuesMode.ONLY) emptyList() else everyday.filter {
            inPeriod(it) && appOk(it) && matchesPurpose(it.purpose, scope.purposes)
        }

        // A named person narrows the dues rows.
        val people = dues.map(::personOf).filter { it.length >= 2 }.distinct()
        val named = personTexts.firstNotNullOfOrNull { text ->
            people.filter {
                Regex("""(?<![\p{L}\p{M}])${Regex.escape(it.lowercase())}(?![\p{L}\p{M}])""").containsMatchIn(text)
            }.map { it.lowercase() }.toSet().takeIf { it.isNotEmpty() }
        }.orEmpty()
        val duesHits = if (scope.dues == DuesMode.EXCLUDE) emptyList() else dues.filter {
            inPeriod(it) && appOk(it) && (named.isEmpty() || personOf(it).lowercase() in named)
        }

        return Selection(
            matched = (everydayHits + duesHits).sortedByDescending { it.timestamp },
            excludedDues = if (scope.dues == DuesMode.EXCLUDE) dues.count(::inPeriod) else 0,
            everyday = everyday
        )
    }

    // ------------------------------------------------------------------------------------------
    // Rows → text
    // ------------------------------------------------------------------------------------------

    /**
     * @param earlier the questions asked before this one in the conversation, oldest first, so a
     * follow-up can inherit their scope (see [resolveScope]).
     * @param monthlyBudget `AiPreferences.monthlyBudget`; 0 means none is set.
     * "Today" is [now], so tests pin the clock and with it the budget month.
     */
    fun build(
        question: String,
        spends: List<Spend>,
        currency: String,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        weekStart: Int = Calendar.getInstance(tz).firstDayOfWeek,
        notes: List<Note> = emptyList(),
        noteEntries: List<NoteEntry> = emptyList(),
        recurringBills: List<RecurringBill> = emptyList(),
        monthlyBudget: Double = 0.0,
        earlier: List<String> = emptyList(),
        custom: CustomPresets = CustomPresets.EMPTY,
        limits: Limits = Limits.FULL
    ): Context {
        val scope = resolveScope(question, earlier, now, tz, weekStart, custom)
        val clock = Clock(tz, now, weekStart)
        val personTexts = buildList {
            add(question.lowercase())
            // Carried-over filters (dues) keep the person named in the previous question.
            if (CARRIED_FILTERS in scope.carriedOver) earlier.lastOrNull()?.let { add(it.lowercase()) }
        }
        val comparison = scope.comparison
        // One selection for a plain question, one per side for a comparison.
        val picks = if (comparison == null) {
            listOf(select(spends, scope, personTexts))
        } else {
            comparison.sides.map { side ->
                select(spends, scope.copy(period = side.period, purposes = side.purposes, apps = side.apps), personTexts)
            }
        }
        val text = buildString {
            appendScope(scope, picks.sumOf { it.excludedDues })
            when {
                picks.all { it.matched.isEmpty() } -> appendNothingMatched(picks[0], currency, clock, limits)
                comparison == null -> appendMatches(scope, picks[0], currency, clock, limits)
                else -> appendComparison(scope, comparison, picks, currency, clock, limits)
            }
            appendBudget(question, scope, spends, monthlyBudget, currency, clock)
            val q = question.lowercase()
            if (!limits.sideSectionsOnlyWhenAsked || BILLS_ASKED.containsMatchIn(q)) appendRecurringBills(recurringBills, currency)
            if (!limits.sideSectionsOnlyWhenAsked || NOTES_ASKED.containsMatchIn(q)) appendNotes(notes, noteEntries, currency, clock)
        }
        return Context(text, scope, picks.sumOf { it.matched.size })
    }

    private fun money(currency: String, v: Double) =
        (if (v < 0) "-" else "") + currency + String.format(Locale.US, "%.2f", abs(v))

    private fun StringBuilder.appendScope(scope: Scope, excludedDues: Int) {
        appendLine("=== SCOPE (what the user's question covers — nothing outside it is in the data below) ===")
        appendLine("Period: ${scope.periodLabel}")
        val comparison = scope.comparison
        if (comparison != null && comparison.by != CompareBy.PERIOD) {
            appendLine("Filters: ${comparison.sides.joinToString(" vs ") { it.label }}, compared side by side (see COMPARISON)")
        } else {
            val filters = buildList {
                if (scope.purposes.isNotEmpty()) add("purpose = ${scope.purposes.joinToString(" or ")}")
                if (scope.apps.isNotEmpty()) add("payment app = ${scope.apps.joinToString(" or ")}")
            }
            appendLine("Filters: ${filters.joinToString("; ").ifEmpty { "none" }}")
        }
        if (scope.carriedOver.isNotEmpty()) {
            // Without this the model sees "and last month?" and no hint that the rest of the
            // scope came from the question before it.
            appendLine("Follow-up: this question continues the previous one, so its ${scope.carriedOver.joinToString(" and ")} carried over.")
        }
        appendLine(
            when (scope.dues) {
                DuesMode.EXCLUDE -> "Records: everyday spending only. Lending/Borrowing are tracked separately as Dues and are NOT spending."
                DuesMode.ONLY -> "Records: Lending/Borrowing (Dues) only. These are loans, not spending."
                DuesMode.INCLUDE -> "Records: everyday spending AND Lending/Borrowing, reported in separate sections. Never add the two together."
            }
        )
        if (excludedDues > 0) {
            appendLine("Left out: $excludedDues Lending/Borrowing record(s) in this period. Mention them only if the user asks about lending or borrowing.")
        }
        appendLine()
    }

    private fun StringBuilder.appendNothingMatched(sel: Selection, cur: String, c: Clock, limits: Limits) {
        appendLine("=== RESULT: 0 transactions match this question. Say so plainly and do not invent any. ===")
        if (sel.everyday.isEmpty()) {
            appendLine("The user has no recorded transactions at all yet.")
            return
        }
        // A compact map of what *is* there, so the answer can point at the nearest data
        // instead of the old fallback of dumping the 200 most recent rows.
        val total = sel.everyday.sumOf { it.amount }
        appendLine()
        appendLine(
            "=== OVERVIEW OF ALL EVERYDAY SPENDING: ${sel.everyday.size} transactions, " +
                "${c.date(sel.everyday.minOf { it.timestamp })} → ${c.date(sel.everyday.maxOf { it.timestamp })}, " +
                "total ${money(cur, total)} ==="
        )
        appendGroups("By purpose", sel.everyday.groupBy { it.purpose.ifBlank { "Others" } }, total, cur, limits)
        appendMonths(sel.everyday, cur, c, minOf(OVERVIEW_MONTHS, limits.months))
    }

    private fun StringBuilder.appendMatches(scope: Scope, sel: Selection, cur: String, c: Clock, limits: Limits) {
        appendLine("=== TOTALS (exact, computed over all ${sel.matched.size} matching transactions — quote these; never re-add the rows below, which may be only a sample) ===")
        appendTotals(scope.dues, sel.matched, cur, c, limits)
        appendLine()

        val limit = if (scope.summary) limits.summaryRows else limits.rows
        val rows = sel.matched.take(limit)
        val sample = if (rows.size < sel.matched.size) {
            "showing the ${rows.size} most recent of ${sel.matched.size}"
        } else {
            "all ${rows.size}"
        }
        appendLine("=== TRANSACTIONS (newest first, $sample) ===")
        appendRowFormat(sel.matched.any(::isDues))
        appendRows(rows, cur, c)
    }

    /** The exact totals of [matched]: everyday spending and dues in separate blocks, never added. */
    private fun StringBuilder.appendTotals(mode: DuesMode, matched: List<Spend>, cur: String, c: Clock, limits: Limits) {
        val everyday = matched.filterNot(::isDues)
        val dues = matched.filter(::isDues)
        val both = mode == DuesMode.INCLUDE
        if (everyday.isNotEmpty()) {
            if (both) appendLine("-- Everyday spending --")
            appendEverydayTotals(everyday, cur, c, limits)
        }
        if (dues.isNotEmpty()) {
            if (both) appendLine("-- Lending & borrowing (separate from spending) --")
            appendDuesTotals(dues, cur, limits)
        }
    }

    private fun StringBuilder.appendRowFormat(hasDues: Boolean) {
        appendLine("ROW FORMAT: - date | amount | purpose | app [| note: text]. A row with no \"note:\" segment simply has no note.")
        if (hasDues) {
            appendLine("For Lending/Borrowing rows the fourth column is the PERSON, not an app.")
        }
    }

    private fun StringBuilder.appendRows(rows: List<Spend>, cur: String, c: Clock) {
        rows.forEach { s ->
            if (isDues(s)) {
                val person = safe(personOf(s)).ifBlank { "(no name)" }
                val detail = safe(detailOf(s), NOTE_CHARS).let { if (it.isBlank()) "" else " | note: $it" }
                appendLine("- ${c.date(s.timestamp)} | ${money(cur, s.amount)} | ${safe(s.purpose)} | $person$detail")
            } else {
                // A blank note is omitted, not printed as "—": the model echoed that back as
                // the literal word "note".
                val note = safe(s.notes, NOTE_CHARS).let { if (it.isBlank()) "" else " | note: $it" }
                appendLine("- ${c.date(s.timestamp)} | ${money(cur, s.amount)} | ${safe(s.purpose)} | ${safe(s.appName)}$note")
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Comparison
    // ------------------------------------------------------------------------------------------

    /** "up ₹300.00 (+25%)" — the move from [from] to [to]; the percentage is guarded against a zero base. */
    private fun change(cur: String, from: Double, to: Double): String {
        val diff = to - from
        if (abs(diff) < 0.005) return "unchanged"
        val pct = if (from > 0) String.format(Locale.US, "%+.0f%%", diff / from * 100) else "no percentage: the earlier figure is zero"
        return "${if (diff > 0) "up" else "down"} ${money(cur, abs(diff))} ($pct)"
    }

    /** "Food is ₹300.00 higher than Travel (25% more)" — two things side by side, relative to the second. */
    private fun gap(cur: String, aLabel: String, a: Double, bLabel: String, b: Double): String {
        val diff = a - b
        if (abs(diff) < 0.005) return "$aLabel and $bLabel are equal"
        val pct = if (b > 0) {
            String.format(Locale.US, "%.0f%% %s", abs(diff) / b * 100, if (diff > 0) "more" else "less")
        } else "no percentage: $bLabel is zero"
        return "$aLabel is ${money(cur, abs(diff))} ${if (diff > 0) "higher" else "lower"} than $bLabel ($pct)"
    }

    /**
     * Both sides' exact totals, the code-computed delta, and a short sample of rows per side.
     * Each side is selected with its own period/purpose/app, so nothing from one side leaks into
     * the other. Dues keep their own rules: they appear in their own block and have their own
     * delta lines, and are never added to spending.
     */
    private fun StringBuilder.appendComparison(
        scope: Scope,
        cmp: Comparison,
        picks: List<Selection>,
        cur: String,
        c: Clock,
        limits: Limits
    ) {
        val byPeriod = cmp.by == CompareBy.PERIOD
        val (a, b) = cmp.sides
        appendLine("=== COMPARISON: ${a.label} vs ${b.label} (every side's TOTALS are exact over all its matching transactions — quote them; never re-add rows) ===")
        cmp.sides.forEachIndexed { i, side ->
            appendLine("--- ${if (byPeriod) side.window else side.label} ---")
            if (picks[i].matched.isEmpty()) appendLine("0 transactions match.")
            else appendTotals(scope.dues, picks[i].matched, cur, c, limits)
        }
        appendLine()

        appendLine("=== DELTA (computed in code — quote these exactly; never recompute a difference or a percentage) ===")
        val everyday = picks.map { it.matched.filterNot(::isDues) }
        val totals = everyday.map { rows -> rows.sumOf { it.amount } }
        if (scope.dues != DuesMode.ONLY) {
            if (byPeriod) {
                appendLine("Spending: ${money(cur, totals[0])} (${a.label}) → ${money(cur, totals[1])} (${b.label}): ${change(cur, totals[0], totals[1])}")
                appendLine("Transactions: ${everyday[0].size} (${a.label}) → ${everyday[1].size} (${b.label})")
                appendPace(cmp, totals, cur, c)
            } else {
                appendLine("Spending: ${a.label} ${money(cur, totals[0])} vs ${b.label} ${money(cur, totals[1])}: ${gap(cur, a.label, totals[0], b.label, totals[1])}")
                appendLine("Transactions: ${a.label} ${everyday[0].size} | ${b.label} ${everyday[1].size}")
            }
        }
        if (scope.dues != DuesMode.EXCLUDE) {
            val dues = picks.map { p -> p.matched.filter(::isDues) }
            for ((name, purpose) in listOf("Lent" to LENDING, "Borrowed" to BORROWING)) {
                val sums = dues.map { rows -> rows.filter { it.purpose == purpose }.sumOf { it.amount } }
                if (byPeriod) {
                    appendLine("$name (separate from spending): ${money(cur, sums[0])} (${a.label}) → ${money(cur, sums[1])} (${b.label}): ${change(cur, sums[0], sums[1])}")
                } else {
                    appendLine("$name (separate from spending): ${a.label} ${money(cur, sums[0])} vs ${b.label} ${money(cur, sums[1])}: ${gap(cur, a.label, sums[0], b.label, sums[1])}")
                }
            }
        }
        appendLine()

        appendLine("=== TRANSACTIONS (newest first, at most ${limits.compareRows} per side) ===")
        appendRowFormat(picks.any { p -> p.matched.any(::isDues) })
        cmp.sides.forEachIndexed { i, side ->
            val matched = picks[i].matched
            val shown = matched.take(limits.compareRows)
            appendLine("-- ${side.label}${if (shown.size < matched.size) " (showing ${shown.size} of ${matched.size})" else ""} --")
            appendRows(shown, cur, c)
        }
    }

    /**
     * Per-day pace for a period comparison. A month that is 9 days old set against a full month
     * always looks like a collapse in raw totals, so the pace is given too, with a note when the
     * sides cover different numbers of days.
     */
    private fun StringBuilder.appendPace(cmp: Comparison, totals: List<Double>, cur: String, c: Clock) {
        val (a, b) = cmp.sides
        val pa = a.period ?: return
        val pb = b.period ?: return
        val da = c.daysCovered(pa)
        val db = c.daysCovered(pb)
        val paceA = totals[0] / da
        val paceB = totals[1] / db
        appendLine("Days covered: $da (${a.label}${if (c.inProgress(pa)) ", still in progress" else ""}) → $db (${b.label}${if (c.inProgress(pb)) ", still in progress" else ""})")
        appendLine("Per-day pace: ${money(cur, paceA)}/day (${a.label}) → ${money(cur, paceB)}/day (${b.label}): ${change(cur, paceA, paceB)}")
        if (da != db) {
            appendLine("Note: the two periods cover different numbers of days, so say that and lean on the per-day pace rather than the raw totals.")
        }
    }

    // ------------------------------------------------------------------------------------------
    // Budget
    // ------------------------------------------------------------------------------------------

    private val BUDGET_WORDS = Regex(
        """\b(?:budgets?|on\s+track|off\s+track|left|remaining|overspen[dt]|over\s*spen[dt]|over\s*spending|""" +
            """allowance|afford|safe\s+to\s+spend|run\s+out)\b"""
    )

    private fun isCalendarMonth(p: SpendRecap.Period, tz: TimeZone): Boolean {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = p.start }
        return SpendRecap.monthPeriod(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), tz) == p
    }

    /**
     * The monthly-budget picture, computed here: spent so far, remaining, days left, the pace so
     * far and a month-end projection. Appended when a budget is set and the question is about the
     * current month or mentions budget / on track / left / remaining / overspend.
     *
     * "Spent" is the Dashboard card's own definition (`SpendViewModel.currentMonthSpent`):
     * everyday spending since the 1st, Lending/Borrowing excluded; "days left" counts today, like
     * the card's recommended daily allowance. It deliberately ignores the question's purpose/app
     * filters: the budget covers everything, so "food this month, am I on track?" still sees the
     * whole month's spending.
     */
    private fun StringBuilder.appendBudget(
        question: String,
        scope: Scope,
        spends: List<Spend>,
        budget: Double,
        cur: String,
        c: Clock
    ) {
        val mentions = BUDGET_WORDS.containsMatchIn(question.lowercase())
        val monthStart = c.month(0)
        val aboutThisMonth = scope.period?.start == monthStart
        if (!mentions && !aboutThisMonth) return
        if (budget <= 0.0) {
            if (mentions) {
                appendLine()
                appendLine("=== BUDGET: the user has not set a monthly budget. Say so and that they can set one from the Dashboard; do not invent a figure. ===")
            }
            return
        }

        val everyday = spends.filter { !it.deleted && !isDues(it) }
        val spent = everyday.filter { it.timestamp >= monthStart }.sumOf { it.amount }
        val day = c.dayOfMonth
        val daysInMonth = c.daysInMonth
        val daysLeft = (daysInMonth - day + 1).coerceAtLeast(1)
        val remaining = budget - spent
        val pace = spent / day
        val projected = pace * daysInMonth

        appendLine()
        appendLine("=== BUDGET (monthly budget vs everyday spending in ${c.fmt("yyyy-MM").format(c.now)}; computed in code — quote exactly, never recompute) ===")
        appendLine(
            "Budget: ${money(cur, budget)} | Spent so far: ${money(cur, spent)} (${String.format(Locale.US, "%.0f%%", spent / budget * 100)} of budget) | " +
                if (remaining >= 0) "Remaining: ${money(cur, remaining)}" else "Over budget by: ${money(cur, -remaining)}"
        )
        appendLine("Day $day of $daysInMonth | Days left including today: $daysLeft")
        // Describes the pace so far. It deliberately stops short of a "you may spend X a day" figure:
        // Spendly records what was spent, it does not tell the user what to spend.
        appendLine("Daily average so far: ${money(cur, pace)}")
        appendLine(
            "Projected month-end spend at the current pace: ${money(cur, projected)} | " +
                if (projected > budget) "projected OVER budget by ${money(cur, projected - budget)}"
                else "projected UNDER budget by ${money(cur, budget - projected)}"
        )
        if (day < 3) appendLine("Note: only $day day(s) of data this month, so the projection is rough.")
        appendLine("Basis: everyday spending only; Lending/Borrowing are excluded, exactly like the Dashboard budget card.")

        // "Did I stay in budget last month?" — the same budget against a finished month.
        val p = scope.period
        if (mentions && p != null && p.start < monthStart && isCalendarMonth(p, c.tz)) {
            val past = everyday.filter { it.timestamp >= p.start && it.timestamp < p.endExclusive }.sumOf { it.amount }
            appendLine(
                "For ${c.fmt("yyyy-MM").format(p.start)} (a full month): spent ${money(cur, past)} of the ${money(cur, budget)} budget, " +
                    if (past <= budget) "${money(cur, budget - past)} under" else "${money(cur, past - budget)} over"
            )
        }
    }

    private fun StringBuilder.appendEverydayTotals(list: List<Spend>, cur: String, c: Clock, limits: Limits) {
        val total = list.sumOf { it.amount }
        appendLine("Total: ${money(cur, total)} | Transactions: ${list.size} | Average: ${money(cur, total / list.size)}")
        val biggest = list.maxBy { it.amount }
        appendLine("Largest: ${money(cur, biggest.amount)} on ${c.date(biggest.timestamp)} (${safe(biggest.purpose)}, ${safe(biggest.appName)})")
        appendLine("Dates covered: ${c.date(list.minOf { it.timestamp })} → ${c.date(list.maxOf { it.timestamp })}")
        appendGroups("By purpose", list.groupBy { it.purpose.ifBlank { "Others" } }, total, cur, limits)
        appendGroups("By app", list.groupBy { it.appName.ifBlank { "Other" } }, total, cur, limits)
        appendMonths(list, cur, c, limits.months)
    }

    private fun StringBuilder.appendDuesTotals(list: List<Spend>, cur: String, limits: Limits) {
        val lent = list.filter { it.purpose == LENDING }
        val borrowed = list.filter { it.purpose == BORROWING }
        val lentTotal = lent.sumOf { it.amount }
        val borrowedTotal = borrowed.sumOf { it.amount }
        appendLine("Lent (Lending): ${money(cur, lentTotal)} in ${lent.size} record(s) | Borrowed (Borrowing): ${money(cur, borrowedTotal)} in ${borrowed.size} record(s)")
        appendLine("Net (lent − borrowed): ${money(cur, lentTotal - borrowedTotal)}")

        val people = list.groupBy { personOf(it).ifBlank { "(no name)" }.lowercase() }.values
            .map { rows ->
                val name = safe(personOf(rows.first())).ifBlank { "(no name)" }
                val l = rows.filter { it.purpose == LENDING }.sumOf { it.amount }
                val b = rows.filter { it.purpose == BORROWING }.sumOf { it.amount }
                Triple(name, l to b, rows.size)
            }
            .sortedByDescending { (_, lb, _) -> maxOf(lb.first, lb.second) }
        appendLine("By person (${people.size}):")
        people.take(limits.people).forEach { (name, lb, n) ->
            appendLine("  - $name: lent ${money(cur, lb.first)}, borrowed ${money(cur, lb.second)}, net ${money(cur, lb.first - lb.second)} ($n record(s))")
        }
        if (people.size > limits.people) appendLine("  - …and ${people.size - limits.people} more")
    }

    private fun StringBuilder.appendGroups(title: String, groups: Map<String, List<Spend>>, total: Double, cur: String, limits: Limits) {
        val sorted = groups.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }
        val shown = sorted.take(limits.groupLines).joinToString("; ") { (name, rows) ->
            val sum = rows.sumOf { it.amount }
            val pct = if (total > 0) String.format(Locale.US, "%.0f%%", sum / total * 100) else "-"
            "${safe(name)} ${money(cur, sum)} ($pct, ${rows.size})"
        }
        val more = if (sorted.size > limits.groupLines) "; …and ${sorted.size - limits.groupLines} more" else ""
        appendLine("$title: $shown$more")
    }

    /** Month-by-month totals, only when the rows actually span more than one month. */
    private fun StringBuilder.appendMonths(list: List<Spend>, cur: String, c: Clock, limit: Int) {
        val fmt = c.fmt("yyyy-MM")
        val byMonth = list.groupBy { fmt.format(it.timestamp) }.toSortedMap()
        if (byMonth.size < 2) return
        val shown = byMonth.entries.toList().takeLast(limit)
        appendLine(
            "By month${if (byMonth.size > limit) " (latest $limit)" else ""}: " +
                shown.joinToString("; ") { (m, rows) -> "$m ${money(cur, rows.sumOf { it.amount })} (${rows.size})" }
        )
    }

    private fun StringBuilder.appendRecurringBills(bills: List<RecurringBill>, cur: String) {
        val active = bills.filter { !it.deleted }
        if (active.isEmpty()) return

        appendLine()
        appendLine("=== RECURRING BILLS & SUBSCRIPTIONS (${active.size} active) ===")
        val monthlyTotal = active.sumOf { bill ->
            val freq = BillFrequency.fromString(bill.frequency)
            bill.amount / freq.intervalMonths
        }
        appendLine("Estimated Monthly Recurring Cost: ${money(cur, monthlyTotal)}")
        active.forEach { bill ->
            val cardInfo = if (bill.isCreditCardBill) {
                if (bill.cardLast4.isNotBlank()) " | Credit Card (*${bill.cardLast4})" else " | Credit Card"
            } else ""
            val untilInfo = if (bill.untilDate != null) " | Until: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(bill.untilDate)}" else ""
            val noteInfo = safe(bill.notes, NOTE_CHARS).let { if (it.isBlank()) "" else " | notes: $it" }
            val appInfo = safe(bill.appName).let { if (it.isBlank()) "" else " | App/Bank: $it" }
            val catInfo = safe(bill.category).let { if (it.isBlank()) "" else " | Category: $it" }
            appendLine("- \"${safe(bill.name)}\" | Amount: ${money(cur, bill.amount)} | Due: ${bill.dayOfMonth}th (${bill.frequency})$catInfo$appInfo$cardInfo$untilInfo$noteInfo")
        }
    }

    private fun StringBuilder.appendNotes(
        notes: List<Note>,
        noteEntries: List<NoteEntry>,
        cur: String,
        c: Clock
    ) {
        val activeNotes = notes.filter { !it.deleted }
        val activeEntries = noteEntries.filter { !it.deleted }
        if (activeNotes.isEmpty() && activeEntries.isEmpty()) return

        appendLine()
        appendLine("=== NOTES & NOTE ENTRIES (${activeNotes.size} note(s), ${activeEntries.size} total entry/entries) ===")
        val entriesByNote = activeEntries.groupBy { it.noteUuid }

        activeNotes.forEach { note ->
            val entries = entriesByNote[note.uuid].orEmpty()
            val noteTotal = entries.sumOf { it.amount }
            appendLine("• Note: \"${safe(note.title)}\" (Total: ${money(cur, noteTotal)}, ${entries.size} entry/entries)")
            entries.forEach { entry -> appendLine(noteEntryLine(entry, cur, c)) }
        }

        val knownNoteUuids = activeNotes.map { it.uuid }.toSet()
        val orphanEntries = activeEntries.filter { it.noteUuid !in knownNoteUuids }
        if (orphanEntries.isNotEmpty()) {
            appendLine("• Other Note Entries (${orphanEntries.size}):")
            orphanEntries.forEach { entry -> appendLine(noteEntryLine(entry, cur, c)) }
        }
    }

    /** One note entry as a context line; the same text rendering the two loops above shared. */
    private fun noteEntryLine(entry: NoteEntry, cur: String, c: Clock): String {
        val dateStr = if (entry.date > 0) c.date(entry.date) else "No date"
        val detailStr = safe(entry.detail, NOTE_CHARS).let { if (it.isBlank()) "" else " | note: $it" }
        val fieldsStr = if (entry.customFields.isNotEmpty()) {
            " | custom fields: [" + entry.customFields.joinToString(", ") { "${safe(it.name)}: ${safe(it.value, NOTE_CHARS)}" } + "]"
        } else ""
        return "  - $dateStr | ${safe(entry.label)} | ${money(cur, entry.amount)}$detailStr$fieldsStr"
    }
}
