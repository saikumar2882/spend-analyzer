package com.alpha.spendtracker.data

import androidx.annotation.StringRes
import com.alpha.spendtracker.R

/**
 * The one-tap questions offered in the AI History Assistant.
 *
 * Each pairs a localized [labelRes] (what the chip and the chat bubble show) with the English
 * [question] that [HistoryQuery] actually reads. [HistoryQuery] finds the period, category and
 * dues words by keyword, and only knows English ones, so sending the Hindi/Telugu label to it
 * would silently scope every chip to "all time". The label still goes to the model, which
 * replies in the app language either way.
 *
 * Every question here is covered by a test that checks it resolves to the scope its label
 * promises, so a chip can't be added that the assistant would answer over the wrong data.
 */
object AssistantSuggestions {

    data class Suggestion(
        @StringRes val labelRes: Int,
        /** English wording for [HistoryQuery.build]; see the class comment. */
        val question: String,
        /** Only worth offering when the user has Lending/Borrowing records to ask about. */
        val needsDues: Boolean = false
    )

    val ALL: List<Suggestion> = listOf(
        Suggestion(R.string.ai_example_1, "Summarize my spending this month"),
        Suggestion(R.string.ai_example_7, "What was my biggest expense this month?"),
        Suggestion(R.string.ai_example_3, "What did I spend on food this week?"),
        Suggestion(R.string.ai_example_4, "Top 3 categories of last month"),
        Suggestion(R.string.ai_example_5, "Which payment app do I use the most?"),
        Suggestion(R.string.ai_example_8, "Show my spending in the last 7 days"),
        Suggestion(R.string.ai_example_2, "Who do I owe money to right now?", needsDues = true),
        Suggestion(R.string.ai_example_6, "Show all my lendings grouped by person", needsDues = true)
    )

    fun visible(hasDues: Boolean): List<Suggestion> = ALL.filter { !it.needsDues || hasDues }
}
