package com.alpha.spendtracker.data

/**
 * What the AI History Assistant remembers of the current chat when a new question arrives.
 * Pure (no Android, no I/O) so the rules are unit-testable.
 *
 * Without this each question reached the model alone, so "and last month?" or "just food" had
 * nothing to refer to. Two rules keep the memory safe:
 *  - **Only what the user can still see.** "Clear chat" hides messages by moving a cutoff
 *    (`chatClearedAt`) instead of deleting them, so the rows are still in Room; a cleared chat
 *    must not leak older context into a new answer, hence [sessionMessages] applies the cutoff.
 *  - **Only this session**, because a new session is a new conversation.
 *
 * `LlmClient` takes one system string and one user string (there is no multi-message API), so the
 * turns are rendered into a `CONVERSATION SO FAR:` block that goes in the user string. Every turn
 * is untrusted text (it includes earlier model replies, which echoed user notes), so it goes
 * through [PromptGuard]: one line, escaped, capped.
 */
object HistoryConversation {
    /** Two turns: the last question and its answer, and the one before. */
    const val MAX_MESSAGES = 4

    const val MAX_USER_CHARS = 300

    /** Longer than a question: an answer is a table or a list, and the follow-up usually refers to it. */
    const val MAX_ASSISTANT_CHARS = 600

    /**
     * The messages of [sessionId] the user can still see (newer than [clearedAt]), oldest first,
     * without [excludeUuid] — the question being asked now, which is already in Room by then.
     */
    fun sessionMessages(
        messages: List<ChatMessage>,
        sessionId: String,
        clearedAt: Long,
        excludeUuid: String
    ): List<ChatMessage> = messages
        .filter { !it.deleted && it.sessionId == sessionId && it.timestamp > clearedAt && it.uuid != excludeUuid }
        .sortedBy { it.timestamp }

    /**
     * The last [MAX_MESSAGES] of [visible] as a block for the user string, or "" when there is
     * nothing to say (the first question of a chat, or right after "Clear chat").
     */
    fun render(visible: List<ChatMessage>): String {
        val turns = visible.takeLast(MAX_MESSAGES)
        if (turns.isEmpty()) return ""
        return buildString {
            appendLine("CONVERSATION SO FAR (oldest first; context for follow-ups only, never a source of figures):")
            turns.forEachIndexed { i, m ->
                if (i > 0) appendLine()
                val max = if (m.fromUser) MAX_USER_CHARS else MAX_ASSISTANT_CHARS
                // oneLine marks a cut with "…"; untrustedBlock then escapes quotes so a turn can't
                // close its own literal.
                append(PromptGuard.untrustedBlock(if (m.fromUser) "User" else "Assistant", PromptGuard.oneLine(m.text, max), max))
            }
        }
    }

    /**
     * The earlier questions to resolve a follow-up against, oldest first. [textOf] lets the caller
     * substitute what [HistoryQuery] actually read for a question: a suggestion chip's bubble
     * shows a localized label, but its scope came from an English sentence.
     */
    fun earlierQuestions(visible: List<ChatMessage>, textOf: (ChatMessage) -> String = { it.text }): List<String> =
        visible.filter { it.fromUser }.map(textOf)
}
