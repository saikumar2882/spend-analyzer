package com.alpha.spendtracker.data

import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES

/**
 * Maps whatever purpose string comes back from the model (or sits in the user's saved defaults)
 * onto one of [CANONICAL_PURPOSES]. Pure and unit-tested.
 *
 * The taxonomy refresh left two spellings of several buckets in `PURPOSE_PRESETS` and the AI
 * prompt asked for both, so "lunch" landed in "Food & Dining" or "Food & Drinks" depending on
 * which part of the prompt the model weighted — and the dashboard, which groups by the raw string,
 * split one bucket into two. Everything the AI Track writes goes through here first.
 */
object AiPurposes {

    /** The catch-all bucket, and what an unrecognised purpose becomes when the caller has no better idea. */
    const val FALLBACK = "Miscellaneous"

    /** Legacy twins and common synonyms → canonical. Keys are compared through [key]. */
    internal val ALIASES: Map<String, String> = buildMap {
        fun alias(canonical: String, vararg names: String) = names.forEach { put(key(it), canonical) }

        // The pre-refresh names still stored on existing records / saved as the user's default.
        alias("Food & Dining", "Food & Drinks", "Food & Drink", "Food", "Dining", "Eating Out", "Restaurants")
        alias("Groceries", "Groceries & Food", "Grocery")
        // "Rent & Utilities" and "Travel & Commute" each straddled two buckets; the refresh's own
        // keyword table sends them to Housing and Commute (the everyday one), so do the same.
        alias("Housing", "Rent & Utilities", "Rent", "House Rent")
        alias("Commute", "Travel & Commute", "Transport", "Transportation", "Fuel", "Petrol", "Diesel", "Cab", "Taxi")
        alias("Shopping", "Shopping & Apparels", "Apparels", "Apparel", "Clothing", "Clothes")
        alias("Bills & Utilities", "Utilities", "Bills", "Recharge")
        alias("EMIs & Cards", "EMI & Cards", "Credit Card Bill", "Credit Card", "Credit Cards", "EMI", "EMIs", "Loan Payment")
        alias("Investments", "Investment", "Mutual Funds", "Stocks")
        alias("Savings", "Saving")
        alias("Subscriptions", "Subscription & Leisure", "Subscription", "OTT")
        alias("Entertainment", "Leisure", "Movies")
        alias("Healthcare", "Healthcare & Medical", "Health & Medical", "Health", "Medical", "Medicine", "Medicines")
        alias("Travel", "Trip", "Vacation", "Holiday")
        alias("Insurance & Taxes", "Insurance", "Taxes", "Tax")
        alias("Gifts & Donations", "Gifts", "Gift", "Donations", "Donation", "Charity")
        alias("Personal Care", "Grooming", "Salon")
        alias("Fitness", "Gym")
        alias("Pets", "Pet")
        alias("Work Expenses", "Work", "Office")
        alias("Lending", "Lent")
        alias("Borrowing", "Borrowed")
        // "Others" is also the app-wide default purpose (AiPreferences), so this one is common.
        alias(FALLBACK, "Others", "Other", "Misc", "Uncategorized", "General")
    }

    private val CANONICAL_BY_KEY: Map<String, String> = CANONICAL_PURPOSES.associateBy { key(it) }

    /**
     * Case, spacing, punctuation and "and" vs "&" variants collapse to one key:
     * "food and dining", "Food&Dining" and " FOOD / DINING " are all "fooddining".
     */
    private fun key(raw: String): String =
        raw.lowercase().replace(" and ", " & ").filter { it.isLetterOrDigit() }

    /**
     * The canonical purpose [raw] stands for, or null when it names none. A category the user
     * added ([custom]) counts as canonical for them; the built-ins win a clash.
     */
    fun canonicalOrNull(raw: String?, custom: CustomPresets = CustomPresets.EMPTY): String? {
        if (raw.isNullOrBlank()) return null
        val k = key(raw)
        return CANONICAL_BY_KEY[k] ?: ALIASES[k] ?: custom.categories.firstOrNull { key(it) == k }
    }

    /**
     * [raw] as a canonical purpose; an unrecognised string becomes [fallback] (the caller's
     * default purpose, or [FALLBACK]).
     *
     * Not passed through: a purpose the picker cannot show, saved verbatim ("Beverages",
     * "Taxi fare"), would open a new analytics bucket per model whim. Not guessed from the
     * model's wording either — [AiBatchParser] passes the keyword-inferred purpose as [fallback],
     * which is read from the user's own words.
     */
    fun normalizePurpose(raw: String, fallback: String = FALLBACK, custom: CustomPresets = CustomPresets.EMPTY): String =
        canonicalOrNull(raw, custom) ?: fallback
}
