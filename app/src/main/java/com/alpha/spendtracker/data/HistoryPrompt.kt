package com.alpha.spendtracker.data

/**
 * The history assistant's prompt for the on-device model. Pure, so a test can read it the way the
 * model does.
 *
 * The cloud prompt (inline in `SpendViewModel.askAiAboutHistory`) is a page of guidelines; Gemini
 * Nano's window is under 4000 tokens, answer included, and a small model follows a short rule list
 * better than a long one. These are the rules that matter most for a model that must quote exact,
 * code-computed figures and never improvise numbers; the SCOPE/TOTALS/DELTA/BUDGET blocks inside
 * [data] carry the rest. English only: the on-device model is not trusted with Telugu or Hindi, so
 * the caller does not offer it a call in those languages.
 */
internal object HistoryPrompt {

    // The data is multi-line and unindented, so it is spliced in after trimIndent: one flush-left
    // line in the template would make trimIndent's common indent zero and leave the rest padded
    // (spaces that cost tokens in a window this small).
    private const val DATA_SLOT = "@@DATA@@"

    fun compactSystem(today: String, currency: String, data: String): String = """
        You are a concise expense assistant. Today is $today. Reply in English. Prefix every amount with the currency **$currency**.

        DATA (exact figures, computed in code):
        $DATA_SLOT

        RULES:
        - SCOPE says what period, filters and record types the data covers. Answer only about that.
        - Quote the figures under TOTALS, DELTA and BUDGET exactly as written. Never add up the rows and never recompute a difference or a percentage: the rows are only a sample.
        - Use the rows for dates, notes and questions about one item.
        - Lending and borrowing are loans, not spending. Keep them out of spending totals unless SCOPE includes them, and then in a separate section.
        - If 0 transactions matched, say so plainly and point at the overview. Never invent a transaction or an amount.
        - Use **bold** for amounts and names. Finish every list you start. Be brief; do not restate the question.
        - Start any final takeaway with **Insight:** <text>.

        @@SECURITY@@
    """.trimIndent()
        .replace("@@SECURITY@@", PromptGuard.UNTRUSTED_INPUT_RULE)
        // Last, so nothing inside the data (a note is free text) is ever scanned for a slot.
        .replace(DATA_SLOT, data)
}
