package com.alpha.spendtracker.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** A spend pattern that looks like a monthly bill, offered on the Recurring Bills screen. */
data class SubscriptionSuggestion(
    /** Stable identity used to remember a permanent dismissal. */
    val key: String,
    val name: String,
    val appName: String,
    val purpose: String,
    val category: String,
    val amount: Double,
    val dayOfMonth: Int,
    val occurrences: Int,
    val lastPaidAt: Long
)

/**
 * Pure detection of monthly subscriptions in the spend log. Deliberately conservative: a wrong
 * "this looks monthly" nudge is worse than a missed one, so every rule below errs on the side of
 * staying quiet.
 *
 * - Only non-deleted spends; Lending/Borrowing and note-linked roll-ups are ignored.
 * - Grouped by (payment app, purpose, normalised notes), then by amount within ±2% or ±₹5.
 * - Walking back from the latest payment, consecutive gaps must be 25–35 days. A gap under 25
 *   days means it is paid more often than monthly, so the group is dropped entirely.
 * - 3 payments are required; 2 suffice only when the notes name the thing (e.g. "Netflix")
 *   and the purpose is one bills actually use.
 * - The latest payment must be within the last 40 days, so cancelled subscriptions stay quiet.
 * - Anything an existing non-deleted RecurringBill already covers is skipped.
 */
object SubscriptionFinder {

    private const val DAY_MS = 86_400_000.0
    const val MIN_GAP_DAYS = 25.0
    const val MAX_GAP_DAYS = 35.0
    const val RECENT_DAYS = 40.0
    const val MAX_SUGGESTIONS = 5

    private val EXCLUDED_PURPOSES = setOf("lending", "borrowing")

    // Purposes where two named payments a month apart are already a convincing signal.
    private val BILL_PURPOSES = setOf("subscription & leisure", "rent & utilities", "credit card bill")

    // Month names get stripped so "Netflix Sep" and "Netflix Oct" land in the same group.
    private val MONTH_TOKENS = setOf(
        "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june",
        "jul", "july", "aug", "august", "sep", "sept", "september", "oct", "october",
        "nov", "november", "dec", "december"
    )

    fun find(
        spends: List<Spend>,
        bills: List<RecurringBill>,
        dismissedKeys: Set<String>,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<SubscriptionSuggestion> {
        val activeBills = bills.filter { !it.deleted }
        val eligible = spends.filter {
            !it.deleted &&
                it.amount > 0.0 &&
                it.noteUuid.isBlank() &&
                it.purpose.trim().lowercase() !in EXCLUDED_PURPOSES
        }

        return eligible
            .groupBy { Triple(normalizeApp(it.appName), it.purpose.trim().lowercase(), normalizeNotes(it.notes)) }
            .flatMap { (group, groupSpends) ->
                clusterByAmount(groupSpends).mapNotNull { cluster ->
                    detect(group.first, group.second, group.third, cluster, now, timeZone)
                        ?.takeIf { s -> activeBills.none { coversSuggestion(it, s, group.third) } }
                }
            }
            .filter { it.key !in dismissedKeys }
            .sortedByDescending { it.lastPaidAt }
            .take(MAX_SUGGESTIONS)
    }

    private fun detect(
        appKey: String,
        purposeKey: String,
        notesKey: String,
        cluster: List<Spend>,
        now: Long,
        timeZone: TimeZone
    ): SubscriptionSuggestion? {
        val sorted = cluster.sortedBy { it.timestamp }
        val latest = sorted.last()
        val sinceLatest = (now - latest.timestamp) / DAY_MS
        if (sinceLatest < -1.0 || sinceLatest > RECENT_DAYS) return null

        // Walk back from the latest payment while the gaps stay month-shaped.
        val chain = mutableListOf(latest)
        for (i in sorted.lastIndex - 1 downTo 0) {
            val gap = (chain.last().timestamp - sorted[i].timestamp) / DAY_MS
            when {
                gap < MIN_GAP_DAYS -> return null
                gap > MAX_GAP_DAYS -> break
                else -> chain.add(sorted[i])
            }
        }

        val required = if (notesKey.isNotBlank() && purposeKey in BILL_PURPOSES) 2 else 3
        if (chain.size < required) return null

        return SubscriptionSuggestion(
            key = suggestionKey(appKey, purposeKey, notesKey, latest.amount),
            name = latest.notes.trim().ifBlank { latest.purpose },
            appName = latest.appName,
            purpose = latest.purpose,
            category = latest.category,
            amount = latest.amount,
            dayOfMonth = typicalDayOfMonth(chain.map { it.timestamp }, timeZone),
            occurrences = chain.size,
            lastPaidAt = latest.timestamp
        )
    }

    /** Greedy clusters of near-equal amounts, anchored on the smallest amount in each. */
    private fun clusterByAmount(spends: List<Spend>): List<List<Spend>> {
        val clusters = mutableListOf<MutableList<Spend>>()
        spends.sortedBy { it.amount }.forEach { spend ->
            val current = clusters.lastOrNull()
            if (current != null && amountsClose(current.first().amount, spend.amount)) {
                current.add(spend)
            } else {
                clusters.add(mutableListOf(spend))
            }
        }
        return clusters
    }

    fun amountsClose(a: Double, b: Double): Boolean =
        abs(a - b) <= max(5.0, max(a, b) * 0.02)

    /**
     * Median day of month. Payments straddling a month end (30th, 31st, 1st) are unwrapped
     * first, so they average to the 31st instead of to the middle of the month.
     */
    fun typicalDayOfMonth(timestamps: List<Long>, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(timeZone)
        val days = timestamps.map { cal.timeInMillis = it; cal.get(Calendar.DAY_OF_MONTH) }
        if (days.isEmpty()) return 1
        val wraps = (days.max() - days.min()) > 15
        val unwrapped = days.map { if (wraps && it <= 15) it + 31 else it }.sorted()
        val median = unwrapped[unwrapped.size / 2]
        return (if (median > 31) median - 31 else median).coerceIn(1, 31)
    }

    private fun coversSuggestion(bill: RecurringBill, s: SubscriptionSuggestion, notesKey: String): Boolean {
        val billName = normalizeNotes(bill.name)
        if (notesKey.isNotBlank() && billName.isNotBlank() &&
            (billName.contains(notesKey) || notesKey.contains(billName))
        ) return true
        val sameApp = normalizeApp(bill.appName) == normalizeApp(s.appName)
        val samePurpose = bill.purpose.trim().equals(s.purpose.trim(), ignoreCase = true)
        // A bill saved without an amount still covers the pattern.
        val amountMatches = bill.amount <= 0.0 || amountsClose(bill.amount, s.amount)
        return sameApp && samePurpose && amountMatches
    }

    fun suggestionKey(appKey: String, purposeKey: String, notesKey: String, amount: Double): String =
        "$appKey|$purposeKey|$notesKey|${amount.roundToLong()}"

    fun normalizeApp(app: String): String = app.trim().lowercase()

    /** Lowercased letters only, digits/punctuation and month names dropped, spaces collapsed. */
    fun normalizeNotes(notes: String): String =
        notes.lowercase()
            // Combining marks are kept so Hindi/Telugu words are not split at every vowel sign.
            .map {
                if (it.isLetter() || it.category == CharCategory.NON_SPACING_MARK ||
                    it.category == CharCategory.COMBINING_SPACING_MARK
                ) it else ' '
            }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() && it !in MONTH_TOKENS }
            .joinToString(" ")
}
