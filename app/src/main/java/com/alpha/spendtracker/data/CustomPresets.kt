package com.alpha.spendtracker.data

import androidx.compose.ui.graphics.Color
import com.alpha.spendtracker.ui.components.ALL_CATEGORY_GRID_ITEMS
import com.alpha.spendtracker.ui.components.APP_PRESETS
import com.alpha.spendtracker.ui.components.AppPreset
import com.alpha.spendtracker.ui.components.CANONICAL_PURPOSES
import com.alpha.spendtracker.ui.components.PURPOSE_PRESETS

/**
 * The apps and categories the user added themselves, by name only.
 *
 * Everything else about a custom item (id, colour, icon, the "Other" app group) is derived from the
 * name, so a spend that carries just `appName` / `purpose` renders the same on any screen without
 * looking this list up. Pure and unit-tested; [AiPreferencesRepository] only stores it.
 *
 * How an item gets here: the user picks "Other Platform" (or "Add new" for a category), types a
 * name and saves — [withApp] / [withCategory] then remember it. A name that already stands for a
 * built-in or an earlier custom item is not added twice; [resolveApp] / [resolveCategory] say which
 * item it stands for, so "gpay" or "food" select the existing one instead of splitting analytics.
 *
 * ⚠️ These names are interpolated into the AI Track prompt and schema enums ([AiPrompt]), so
 * [cleanName] strips the characters that could break that structure. Never store a name that has
 * not been through it.
 */
data class CustomPresets(
    val apps: List<String> = emptyList(),
    val categories: List<String> = emptyList()
) {

    /** The custom apps as picker entries. */
    val appPresets: List<AppPreset> get() = apps.map { customAppPreset(it) }

    /**
     * Every app the picker offers: the built-ins, then the custom ones, with "Other Platform"
     * last so it stays the "type a name" entry at the end of the row.
     */
    fun allAppPresets(): List<AppPreset> =
        APP_PRESETS.filter { it.id != OTHER_APP_ID } + appPresets + APP_PRESETS.filter { it.id == OTHER_APP_ID }

    /**
     * The canonical purposes plus the custom ones, for pickers that list plain names. Not
     * `PURPOSE_PRESETS`: that list keeps every legacy twin ("Food & Drinks" next to "Food & Dining")
     * so old records still resolve, and a dropdown built from it showed both. A stored legacy value
     * is mapped with [AiPurposes.canonicalOrNull] before it is looked up here.
     */
    fun allPurposeNames(): List<String> = CANONICAL_PURPOSES + categories

    /** The existing app [name] stands for (built-in name or alias, or a custom app), or null. */
    fun resolveApp(name: String): AppPreset? {
        val k = key(name)
        if (k.isEmpty() || k in RESERVED_APP_KEYS) return null
        apps.firstOrNull { key(it) == k }?.let { return customAppPreset(it) }
        return AiParser.appByExactName(name)
    }

    /** The existing category [name] stands for (built-in, legacy twin, alias or custom), or null. */
    fun resolveCategory(name: String): String? {
        val k = key(name)
        if (k.isEmpty()) return null
        categories.firstOrNull { key(it) == k }?.let { return it }
        AiPurposes.canonicalOrNull(name)?.let { return it }
        PURPOSE_PRESETS.firstOrNull { key(it) == k }?.let { return it }
        // The grid's own search words ("Books", "Metro", "Snacks") belong to a built-in tile; a custom
        // category with one of those names would leave two tiles lit for the same purpose.
        return ALL_CATEGORY_GRID_ITEMS.firstOrNull { item ->
            (listOf(item.canonicalName, item.displayName) + item.aliases).any { key(it) == k }
        }?.canonicalName
    }

    /** This list plus [raw] as a new app; unchanged when it is blank, reserved, already known or the list is full. */
    fun withApp(raw: String): CustomPresets {
        val name = cleanName(raw)
        if (name.isEmpty() || key(name) in RESERVED_APP_KEYS || resolveApp(name) != null) return this
        if (apps.size >= MAX_PER_KIND) return this
        return copy(apps = apps + name)
    }

    /** This list plus [raw] as a new category; unchanged when it is blank, already known or the list is full. */
    fun withCategory(raw: String): CustomPresets {
        val name = cleanName(raw)
        if (name.isEmpty() || resolveCategory(name) != null) return this
        if (categories.size >= MAX_PER_KIND) return this
        return copy(categories = categories + name)
    }

    fun withoutApp(name: String): CustomPresets = copy(apps = apps.filterNot { key(it) == key(name) })

    fun withoutCategory(name: String): CustomPresets = copy(categories = categories.filterNot { key(it) == key(name) })

    companion object {
        val EMPTY = CustomPresets()

        /** Longest name accepted. Input fields cap at this, so what is typed is what is saved. */
        const val MAX_NAME_LENGTH = 30

        /** Per kind. Every name rides in each AI Track prompt, so the list is bounded. */
        const val MAX_PER_KIND = 25

        private const val OTHER_APP_ID = "other"
        private const val CUSTOM_APP_ID_PREFIX = "custom_"

        // "Other" is the one-off entry itself, not a name to remember.
        private val RESERVED_APP_KEYS = setOf("other", "others", "otherplatform")

        // Quotes and backslashes would break the quoted lists in the prompt; commas split the
        // saved order lists (`userAppOrder`, `userCategoryOrder` are comma-joined); brackets,
        // braces and pipes are structural in the prompt and the history context.
        private val FORBIDDEN = Regex("""["\\,\[\]{}<>|]""")
        private val SPACES = Regex("""\s+""")

        /** [raw] reduced to something safe to store and to put in a prompt; blank when nothing is left. */
        fun cleanName(raw: String): String {
            val stripped = FORBIDDEN.replace(PromptGuard.clean(raw, Int.MAX_VALUE), " ")
            return SPACES.replace(stripped, " ").trim().take(MAX_NAME_LENGTH).trim()
        }

        /**
         * Case, spacing, punctuation and "and" vs "&" collapse to one key, so "Jio Mart" and
         * "jiomart" are the same app. Combining marks are kept: without them two different Telugu
         * or Hindi words can reduce to the same consonants.
         */
        internal fun key(raw: String): String =
            raw.lowercase().replace(" and ", " & ").filter { c ->
                c.isLetterOrDigit() || Character.getType(c).let {
                    it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
                }
            }

        /** The stored form: one name per line (cleaned names never contain a line break). */
        fun encode(names: List<String>): String = names.joinToString("\n")

        /** Reads [encode]'s output back, re-cleaning so a hand-edited or old value can't smuggle structure in. */
        fun decode(raw: String?): List<String> =
            raw.orEmpty().lineSequence()
                .map { cleanName(it) }
                .filter { it.isNotEmpty() }
                .distinctBy { key(it) }
                .take(MAX_PER_KIND)
                .toList()

        /** A custom app as a picker entry. Deterministic in [name], so any screen can rebuild it. */
        fun customAppPreset(name: String): AppPreset = AppPreset(
            id = CUSTOM_APP_ID_PREFIX + key(name),
            displayName = name,
            // The History filter groups apps by this; a custom app belongs with the unlisted ones.
            category = "Other",
            color = paletteColor(name)
        )

        fun isCustomAppId(id: String): Boolean = id.startsWith(CUSTOM_APP_ID_PREFIX)

        private val PALETTE = listOf(
            Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF8B5CF6), Color(0xFFEC4899),
            Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFF6366F1)
        )

        private fun paletteColor(name: String): Color =
            PALETTE[(key(name).hashCode() and Int.MAX_VALUE) % PALETTE.size]
    }
}
