package com.alpha.spendtracker.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * On-device stats behind the Sunday recap notification and the monthly Wrapped card.
 * Pure functions over a spend list — no Android, no network, no AI — so every number the user
 * sees is unit-testable.
 *
 * Every entry point filters through [isEligible]: tombstones and Lending/Borrowing never count,
 * matching the dashboard analytics. Callers are still expected to pass an already userId-scoped
 * list (Room is shared across accounts on a device).
 */
object SpendRecap {
    /** Weeks run Monday–Sunday, same as the dashboard trend chart's TREND_WEEK_START. */
    const val WEEK_START = Calendar.MONDAY

    /** The Sunday recap is due from this hour on the week's last day. */
    const val WEEKLY_RECAP_HOUR = 19

    /** A late run (Doze, device off) may still send the recap up to this long after it was due. */
    const val WEEKLY_GRACE_HOURS = 36

    /** The Wrapped for last month may be announced on days 1..this of the new month. */
    const val WRAPPED_GRACE_DAYS = 3

    /** An item must show up in at least this many spends to be the "most spent on" item. */
    const val MIN_ITEM_SPENDS = 2

    private const val LENDING = "Lending"
    private const val BORROWING = "Borrowing"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    // Words that say nothing about *what* was bought, on top of AiCorrectionMemory.STOPWORDS.
    private val ITEM_FILLER = setOf(
        "order", "orders", "ordered", "bill", "bills", "payment", "item", "items",
        "stuff", "things", "misc", "other", "others"
    )

    fun isEligible(spend: Spend): Boolean =
        !spend.deleted && spend.purpose != LENDING && spend.purpose != BORROWING

    /** Same sanitising the AI confirmation sheet applies to the stored default currency. */
    fun currencySymbol(raw: String): String = if (raw.isBlank() || raw.length > 2) "₹" else raw

    /** Percent change of [total] against [previous]; null when there is nothing to compare with. */
    fun changePct(total: Double, previous: Double): Double? =
        if (previous > 0.0) (total - previous) / previous * 100.0 else null

    // ------------------------------------------------------------------------------------------
    // Periods
    // ------------------------------------------------------------------------------------------

    /** Half-open `[start, endExclusive)` window in epoch millis. */
    data class Period(val start: Long, val endExclusive: Long) {
        operator fun contains(time: Long): Boolean = time >= start && time < endExclusive
    }

    private fun calendar(tz: TimeZone, time: Long): Calendar =
        Calendar.getInstance(tz, Locale.ROOT).apply { timeInMillis = time }

    private fun Calendar.toStartOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** The Monday–Sunday week containing [time]. */
    fun weekContaining(time: Long, tz: TimeZone = TimeZone.getDefault()): Period {
        val cal = calendar(tz, time).toStartOfDay()
        val back = (cal.get(Calendar.DAY_OF_WEEK) - WEEK_START + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -back)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7)
        return Period(start, cal.timeInMillis)
    }

    fun previousWeek(week: Period, tz: TimeZone = TimeZone.getDefault()): Period =
        // Anchor on noon of the day before so a DST shift can't land us in the wrong week.
        weekContaining(week.start - DAY_MS / 2, tz)

    /** Calendar month [month] (0-based, as [Calendar.MONTH]) of [year]. */
    fun monthPeriod(year: Int, month: Int, tz: TimeZone = TimeZone.getDefault()): Period {
        val cal = Calendar.getInstance(tz, Locale.ROOT).apply {
            clear()
            set(year, month, 1, 0, 0, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return Period(start, cal.timeInMillis)
    }

    /** `yyyy-MM` key for a month, as carried in the Wrapped notification intent. */
    fun monthKey(year: Int, month: Int): String =
        String.format(Locale.ROOT, "%04d-%02d", year, month + 1)

    /** Inverse of [monthKey]; returns (year, 0-based month) or null for anything malformed. */
    fun parseMonthKey(key: String?): Pair<Int, Int>? {
        val parts = key?.split('-') ?: return null
        if (parts.size != 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return year to month - 1
    }

    /** `yyyy-MM-dd` of [time]'s local day — used as the dedupe key for a week. */
    fun dayKey(time: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val cal = calendar(tz, time)
        return String.format(
            Locale.ROOT, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * The week whose Sunday recap is due at [now], or null. Due from [WEEKLY_RECAP_HOUR] on the
     * week's Sunday until [WEEKLY_GRACE_HOURS] later; outside that window the moment has passed
     * and a recap would read as stale. Dedupe (one per week) is the caller's job.
     */
    fun dueWeek(now: Long, tz: TimeZone = TimeZone.getDefault()): Period? {
        for (week in listOf(weekContaining(now, tz), previousWeek(weekContaining(now, tz), tz))) {
            val trigger = calendar(tz, week.endExclusive - DAY_MS / 2).toStartOfDay().apply {
                set(Calendar.HOUR_OF_DAY, WEEKLY_RECAP_HOUR)
            }.timeInMillis
            if (now >= trigger && now < trigger + WEEKLY_GRACE_HOURS * 60L * 60 * 1000) return week
        }
        return null
    }

    /**
     * The (year, 0-based month) whose Wrapped should be announced at [now]: the previous month,
     * from the evening of the 1st through day [WRAPPED_GRACE_DAYS]. Null otherwise.
     */
    fun dueWrappedMonth(now: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Int, Int>? {
        val cal = calendar(tz, now)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val due = (day == 1 && cal.get(Calendar.HOUR_OF_DAY) >= WEEKLY_RECAP_HOUR - 1) ||
            day in 2..WRAPPED_GRACE_DAYS
        if (!due) return null
        cal.add(Calendar.MONTH, -1)
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    // ------------------------------------------------------------------------------------------
    // Item-level "what did I spend most on"
    // ------------------------------------------------------------------------------------------

    /** One thing named in spend notes, e.g. "chicken" across 6 spends totalling 1,800. */
    data class ItemStat(val keyword: String, val total: Double, val count: Int) {
        /** "chicken" -> "Chicken"; scripts without case are left as they are. */
        val label: String get() = keyword.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /** The distinct item keywords a spend's notes mention, minus filler and its own app's name. */
    fun itemKeywords(spend: Spend): Set<String> {
        if (spend.notes.isBlank()) return emptySet()
        val appTokens = AiCorrectionMemory.tokenize(spend.appName).toSet()
        return AiCorrectionMemory.tokenize(spend.notes)
            .filterNot { it in AiCorrectionMemory.STOPWORDS || it in ITEM_FILLER || it in appTokens }
            .toSet()
    }

    /**
     * Items ranked by total spent, highest first. A spend adds its full amount to every item its
     * notes mention (so "chicken biryani" counts toward both). An item needs [minSpends] spends
     * to qualify — one big purchase is already covered by "biggest spend".
     */
    fun topItems(spends: List<Spend>, limit: Int = 3, minSpends: Int = MIN_ITEM_SPENDS): List<ItemStat> {
        val totals = HashMap<String, Double>()
        val counts = HashMap<String, Int>()
        for (spend in spends) {
            if (!isEligible(spend)) continue
            for (kw in itemKeywords(spend)) {
                totals[kw] = (totals[kw] ?: 0.0) + spend.amount
                counts[kw] = (counts[kw] ?: 0) + 1
            }
        }
        return totals.keys
            .filter { (counts[it] ?: 0) >= minSpends }
            .map { ItemStat(it, totals.getValue(it), counts.getValue(it)) }
            .sortedWith(
                compareByDescending<ItemStat> { it.total }
                    .thenByDescending { it.count }
                    .thenBy { it.keyword }
            )
            .take(limit)
    }

    // ------------------------------------------------------------------------------------------
    // Weekly recap
    // ------------------------------------------------------------------------------------------

    data class WeeklyRecap(
        val week: Period,
        val total: Double,
        val count: Int,
        val previousTotal: Double,
        /** Null when the week before had no spends. */
        val changePct: Double?,
        val biggest: Spend,
        val topItem: ItemStat?
    )

    /** The recap for [week], or null when nothing eligible was logged in it. */
    fun weekly(spends: List<Spend>, week: Period, tz: TimeZone = TimeZone.getDefault()): WeeklyRecap? {
        val eligible = spends.filter(::isEligible)
        val inWeek = eligible.filter { it.timestamp in week }
        if (inWeek.isEmpty()) return null
        val previous = previousWeek(week, tz)
        val total = inWeek.sumOf { it.amount }
        val previousTotal = eligible.filter { it.timestamp in previous }.sumOf { it.amount }
        return WeeklyRecap(
            week = week,
            total = total,
            count = inWeek.size,
            previousTotal = previousTotal,
            changePct = changePct(total, previousTotal),
            biggest = inWeek.maxWith(compareBy<Spend> { it.amount }.thenBy { it.timestamp }),
            topItem = topItems(inWeek, limit = 1).firstOrNull()
        )
    }

    // ------------------------------------------------------------------------------------------
    // Monthly Wrapped
    // ------------------------------------------------------------------------------------------

    data class AppUsage(val appName: String, val count: Int, val total: Double)

    data class MonthlyWrapped(
        val year: Int,
        /** 0-based, as [Calendar.MONTH]. */
        val month: Int,
        val period: Period,
        val total: Double,
        val count: Int,
        val previousTotal: Double,
        /** Null when the previous month had no spends. */
        val changePct: Double?,
        /** Purpose with the highest total, e.g. "Food & Dining". */
        val topCategory: Pair<String, Double>?,
        /** App used most often (by number of spends; ties go to the higher total). */
        val topApp: AppUsage?,
        val biggest: Spend?,
        val noSpendDays: Int,
        val longestNoSpendStreak: Int,
        /** Days the no-spend stats cover: the whole month, or only the finished days of this one. */
        val daysCounted: Int,
        /** True while [now] is still inside the month. */
        val inProgress: Boolean,
        val topItems: List<ItemStat>
    ) {
        val isEmpty: Boolean get() = count == 0
    }

    fun monthly(
        spends: List<Spend>,
        year: Int,
        month: Int,
        now: Long,
        tz: TimeZone = TimeZone.getDefault()
    ): MonthlyWrapped {
        val period = monthPeriod(year, month, tz)
        val prevCal = Calendar.getInstance(tz, Locale.ROOT).apply { clear(); set(year, month, 1); add(Calendar.MONTH, -1) }
        val previous = monthPeriod(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH), tz)

        val eligible = spends.filter(::isEligible)
        val inMonth = eligible.filter { it.timestamp in period }
        val total = inMonth.sumOf { it.amount }
        val previousTotal = eligible.filter { it.timestamp in previous }.sumOf { it.amount }

        val topCategory = inMonth
            .filter { it.purpose.isNotBlank() }
            .groupBy { it.purpose }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxWithOrNull(compareBy<Map.Entry<String, Double>> { it.value }.thenByDescending { it.key })
            ?.toPair()

        val topApp = inMonth
            .filter { it.appName.isNotBlank() }
            .groupBy { it.appName }
            .map { (name, list) -> AppUsage(name, list.size, list.sumOf { it.amount }) }
            .maxWithOrNull(
                compareBy<AppUsage> { it.count }.thenBy { it.total }.thenByDescending { it.appName }
            )

        // Only finished days count: today isn't over, so "no spend yet" isn't a no-spend day.
        val todayStart = calendar(tz, now).toStartOfDay().timeInMillis
        val countEnd = minOf(period.endExclusive, todayStart)
        val spendDays = inMonth.map { dayKey(it.timestamp, tz) }.toSet()
        var noSpendDays = 0
        var streak = 0
        var longest = 0
        var daysCounted = 0
        val cursor = calendar(tz, period.start)
        while (cursor.timeInMillis < countEnd) {
            daysCounted++
            if (dayKey(cursor.timeInMillis, tz) in spendDays) {
                streak = 0
            } else {
                noSpendDays++
                streak++
                if (streak > longest) longest = streak
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }

        return MonthlyWrapped(
            year = year,
            month = month,
            period = period,
            total = total,
            count = inMonth.size,
            previousTotal = previousTotal,
            changePct = changePct(total, previousTotal),
            topCategory = topCategory,
            topApp = topApp,
            biggest = inMonth.maxWithOrNull(compareBy<Spend> { it.amount }.thenBy { it.timestamp }),
            noSpendDays = noSpendDays,
            longestNoSpendStreak = longest,
            daysCounted = daysCounted,
            inProgress = now in period,
            topItems = topItems(inMonth, limit = 3)
        )
    }
}
