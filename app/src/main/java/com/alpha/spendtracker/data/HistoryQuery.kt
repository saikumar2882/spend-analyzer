package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.parseLendBorrowNotes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
 */
object HistoryQuery {
    /** Most rows listed for a detail question. Totals still cover every matching row. */
    const val MAX_ROWS = 100

    /** Most rows listed for a "summarise / overview" question, where totals do the talking. */
    const val MAX_SUMMARY_ROWS = 30

    private const val LENDING = "Lending"
    private const val BORROWING = "Borrowing"
    private const val MAX_GROUP_LINES = 12
    private const val MAX_PEOPLE = 15
    private const val MAX_MONTHS = 12
    private const val OVERVIEW_MONTHS = 6

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
        val summary: Boolean
    )

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
        "Groceries & Food" to Regex("""\b(?:foods?|groceries|grocery|dining|restaurants?|meals?|snacks?)\b"""),
        "Shopping & Apparels" to Regex("""\b(?:shopping|apparels?|clothes|clothing|fashion)\b"""),
        "Credit Card Bill" to Regex("""\bcredit\s*cards?\b"""),
        "Rent & Utilities" to Regex("""\b(?:rent|utilit(?:y|ies)|electricity)\b"""),
        "Travel & Commute" to Regex("""\b(?:travel|travelling|traveling|commute|commuting|transport)\b"""),
        "Subscription & Leisure" to Regex("""\b(?:subscriptions?|leisure|entertainment)\b"""),
        "Healthcare & Medical" to Regex("""\b(?:health|healthcare|medical|medicines?|hospital|doctor)\b""")
    )

    fun parseScope(
        question: String,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        weekStart: Int = Calendar.getInstance(tz).firstDayOfWeek
    ): Scope {
        val q = question.lowercase()
        val clock = Clock(tz, now, weekStart)
        val (period, label) = resolvePeriod(q, clock)

        val dues = when {
            !DUES_WORDS.containsMatchIn(q) -> DuesMode.EXCLUDE
            INCLUDE_WORDS.containsMatchIn(q) -> DuesMode.INCLUDE
            else -> DuesMode.ONLY
        }

        return Scope(
            period = period,
            periodLabel = label,
            // Dues rows carry no purpose beyond Lending/Borrowing, so a purpose word can't narrow them.
            purposes = if (dues == DuesMode.ONLY) emptyList()
            else PURPOSE_WORDS.filter { (_, regex) -> regex.containsMatchIn(q) }.map { it.first },
            apps = APP_PRESETS.filter { it.id != "other" && AiParser.mentionsApp(q, it) }.map { it.displayName },
            dues = dues,
            summary = SUMMARY_WORDS.containsMatchIn(q)
        )
    }

    private fun resolvePeriod(q: String, c: Clock): Pair<SpendRecap.Period?, String> {
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
            return p to c.describe("the last $n $unit${if (n == 1) "" else "s"}", p)
        }

        if (ALL_TIME.containsMatchIn(q)) return null to "all time"

        if (q.contains("day before yesterday")) {
            val p = SpendRecap.Period(c.day(-2), c.day(-1))
            return p to c.describe("the day before yesterday", p)
        }
        if (Regex("""\byesterday\b""").containsMatchIn(q)) {
            val p = SpendRecap.Period(c.day(-1), c.day(0))
            return p to c.describe("yesterday", p)
        }
        if (Regex("""\btoday\b""").containsMatchIn(q)) {
            val p = SpendRecap.Period(c.day(0), open)
            return p to c.describe("today", p)
        }

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
            return p to c.describe("$year-${(month + 1).toString().padStart(2, '0')}", p)
        }

        // A bare number is an amount ("above 2000"), so a year needs a word in front of it.
        YEAR.find(q)?.let { m ->
            val year = m.groupValues[1].toInt()
            val p = SpendRecap.Period(c.yearStart(year), c.yearStart(year + 1))
            return p to c.describe("$year", p)
        }

        // "per month" / "each week" / "monthly" are rates, not a window ("\b" already skips the latter).
        val cleaned = PER_UNIT.replace(q, " ")
        UNIT.find(cleaned)?.let { m ->
            val previous = m.groupValues[1] in setOf("last", "previous", "prev", "past")
            val unit = m.groupValues[2]
            val (current, earlier) = when (unit) {
                "week" -> c.week(0) to c.week(-1)
                "month" -> c.month(0) to c.month(-1)
                else -> c.yearOffset(0) to c.yearOffset(-1)
            }
            return if (previous) {
                val p = SpendRecap.Period(earlier, current)
                p to c.describe("last $unit", p)
            } else {
                val p = SpendRecap.Period(current, open)
                p to c.describe("this $unit", p)
            }
        }

        return null to "all time (the question names no period)"
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

    private fun select(spends: List<Spend>, scope: Scope, q: String): Selection {
        val live = spends.filter { !it.deleted }
        val everyday = live.filterNot(::isDues)
        val dues = live.filter(::isDues)
        val period = scope.period
        fun inPeriod(s: Spend) = period == null || (s.timestamp >= period.start && s.timestamp < period.endExclusive)
        fun appOk(s: Spend) = scope.apps.isEmpty() || scope.apps.any { s.appName.contains(it, ignoreCase = true) }

        val everydayHits = if (scope.dues == DuesMode.ONLY) emptyList() else everyday.filter {
            inPeriod(it) && appOk(it) && (scope.purposes.isEmpty() || it.purpose in scope.purposes)
        }

        // A named person narrows the dues rows ("what does Rahul owe me").
        val named = dues.map(::personOf).filter { it.length >= 2 }.distinct().filter {
            Regex("""(?<![\p{L}\p{M}])${Regex.escape(it.lowercase())}(?![\p{L}\p{M}])""").containsMatchIn(q)
        }.map { it.lowercase() }.toSet()
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

    fun build(
        question: String,
        spends: List<Spend>,
        currency: String,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
        weekStart: Int = Calendar.getInstance(tz).firstDayOfWeek
    ): Context {
        val scope = parseScope(question, now, tz, weekStart)
        val clock = Clock(tz, now, weekStart)
        val sel = select(spends, scope, question.lowercase())
        val text = buildString {
            appendScope(scope, sel)
            if (sel.matched.isEmpty()) appendNothingMatched(sel, currency, clock)
            else appendMatches(scope, sel, currency, clock)
        }
        return Context(text, scope, sel.matched.size)
    }

    private fun money(currency: String, v: Double) =
        (if (v < 0) "-" else "") + currency + String.format(Locale.US, "%.2f", kotlin.math.abs(v))

    private fun StringBuilder.appendScope(scope: Scope, sel: Selection) {
        appendLine("=== SCOPE (what the user's question covers — nothing outside it is in the data below) ===")
        appendLine("Period: ${scope.periodLabel}")
        val filters = buildList {
            if (scope.purposes.isNotEmpty()) add("purpose = ${scope.purposes.joinToString(" or ")}")
            if (scope.apps.isNotEmpty()) add("payment app = ${scope.apps.joinToString(" or ")}")
        }
        appendLine("Filters: ${filters.joinToString("; ").ifEmpty { "none" }}")
        appendLine(
            when (scope.dues) {
                DuesMode.EXCLUDE -> "Records: everyday spending only. Lending/Borrowing are tracked separately as Dues and are NOT spending."
                DuesMode.ONLY -> "Records: Lending/Borrowing (Dues) only. These are loans, not spending."
                DuesMode.INCLUDE -> "Records: everyday spending AND Lending/Borrowing, reported in separate sections. Never add the two together."
            }
        )
        if (sel.excludedDues > 0) {
            appendLine("Left out: ${sel.excludedDues} Lending/Borrowing record(s) in this period. Mention them only if the user asks about lending or borrowing.")
        }
        appendLine()
    }

    private fun StringBuilder.appendNothingMatched(sel: Selection, cur: String, c: Clock) {
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
        appendGroups("By purpose", sel.everyday.groupBy { it.purpose.ifBlank { "Others" } }, total, cur)
        appendMonths(sel.everyday, cur, c, OVERVIEW_MONTHS)
    }

    private fun StringBuilder.appendMatches(scope: Scope, sel: Selection, cur: String, c: Clock) {
        val everyday = sel.matched.filterNot(::isDues)
        val dues = sel.matched.filter(::isDues)
        val both = scope.dues == DuesMode.INCLUDE

        appendLine("=== TOTALS (exact, computed over all ${sel.matched.size} matching transactions — quote these; never re-add the rows below, which may be only a sample) ===")
        if (everyday.isNotEmpty()) {
            if (both) appendLine("-- Everyday spending --")
            appendEverydayTotals(everyday, cur, c)
        }
        if (dues.isNotEmpty()) {
            if (both) appendLine("-- Lending & borrowing (separate from spending) --")
            appendDuesTotals(dues, cur)
        }
        appendLine()

        val limit = if (scope.summary) MAX_SUMMARY_ROWS else MAX_ROWS
        val rows = sel.matched.take(limit)
        val sample = if (rows.size < sel.matched.size) {
            "showing the ${rows.size} most recent of ${sel.matched.size}"
        } else {
            "all ${rows.size}"
        }
        appendLine("=== TRANSACTIONS (newest first, $sample) ===")
        appendLine("ROW FORMAT: - date | amount | purpose | app [| note: text]. A row with no \"note:\" segment simply has no note.")
        if (dues.isNotEmpty()) {
            appendLine("For Lending/Borrowing rows the fourth column is the PERSON, not an app.")
        }
        rows.forEach { s ->
            if (isDues(s)) {
                val person = personOf(s).ifBlank { "(no name)" }
                val detail = detailOf(s).let { if (it.isBlank()) "" else " | note: $it" }
                appendLine("- ${c.date(s.timestamp)} | ${money(cur, s.amount)} | ${s.purpose} | $person$detail")
            } else {
                // A blank note is omitted, not printed as "—": the model echoed that back as
                // the literal word "note".
                val note = if (s.notes.isBlank()) "" else " | note: ${s.notes}"
                appendLine("- ${c.date(s.timestamp)} | ${money(cur, s.amount)} | ${s.purpose} | ${s.appName}$note")
            }
        }
    }

    private fun StringBuilder.appendEverydayTotals(list: List<Spend>, cur: String, c: Clock) {
        val total = list.sumOf { it.amount }
        appendLine("Total: ${money(cur, total)} | Transactions: ${list.size} | Average: ${money(cur, total / list.size)}")
        val biggest = list.maxBy { it.amount }
        appendLine("Largest: ${money(cur, biggest.amount)} on ${c.date(biggest.timestamp)} (${biggest.purpose}, ${biggest.appName})")
        appendLine("Dates covered: ${c.date(list.minOf { it.timestamp })} → ${c.date(list.maxOf { it.timestamp })}")
        appendGroups("By purpose", list.groupBy { it.purpose.ifBlank { "Others" } }, total, cur)
        appendGroups("By app", list.groupBy { it.appName.ifBlank { "Other" } }, total, cur)
        appendMonths(list, cur, c, MAX_MONTHS)
    }

    private fun StringBuilder.appendDuesTotals(list: List<Spend>, cur: String) {
        val lent = list.filter { it.purpose == LENDING }
        val borrowed = list.filter { it.purpose == BORROWING }
        val lentTotal = lent.sumOf { it.amount }
        val borrowedTotal = borrowed.sumOf { it.amount }
        appendLine("Lent (Lending): ${money(cur, lentTotal)} in ${lent.size} record(s) | Borrowed (Borrowing): ${money(cur, borrowedTotal)} in ${borrowed.size} record(s)")
        appendLine("Net (lent − borrowed): ${money(cur, lentTotal - borrowedTotal)}")

        val people = list.groupBy { personOf(it).ifBlank { "(no name)" }.lowercase() }.values
            .map { rows ->
                val name = personOf(rows.first()).ifBlank { "(no name)" }
                val l = rows.filter { it.purpose == LENDING }.sumOf { it.amount }
                val b = rows.filter { it.purpose == BORROWING }.sumOf { it.amount }
                Triple(name, l to b, rows.size)
            }
            .sortedByDescending { (_, lb, _) -> maxOf(lb.first, lb.second) }
        appendLine("By person (${people.size}):")
        people.take(MAX_PEOPLE).forEach { (name, lb, n) ->
            appendLine("  - $name: lent ${money(cur, lb.first)}, borrowed ${money(cur, lb.second)}, net ${money(cur, lb.first - lb.second)} ($n record(s))")
        }
        if (people.size > MAX_PEOPLE) appendLine("  - …and ${people.size - MAX_PEOPLE} more")
    }

    private fun StringBuilder.appendGroups(title: String, groups: Map<String, List<Spend>>, total: Double, cur: String) {
        val sorted = groups.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }
        val shown = sorted.take(MAX_GROUP_LINES).joinToString("; ") { (name, rows) ->
            val sum = rows.sumOf { it.amount }
            val pct = if (total > 0) String.format(Locale.US, "%.0f%%", sum / total * 100) else "-"
            "$name ${money(cur, sum)} ($pct, ${rows.size})"
        }
        val more = if (sorted.size > MAX_GROUP_LINES) "; …and ${sorted.size - MAX_GROUP_LINES} more" else ""
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
}
