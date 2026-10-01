package com.alpha.spendtracker.ui.components

import com.alpha.spendtracker.data.AiTransactionResponse
import com.alpha.spendtracker.ui.screens.NewSpend
import com.alpha.spendtracker.ui.viewmodel.SpendDraft

/**
 * Conversions behind the multi-expense review list. Pure, so what the list shows is provably
 * what gets saved: the cards and the save both go through [toNewSpend].
 */

/**
 * The form values this result becomes, with the same fallbacks [AiConfirmationScreen] applies to
 * its initial fields (preset id → app name → the user's default app; purpose → default purpose),
 * so a log reads the same in the review list as it does when opened for editing.
 */
fun AiTransactionResponse.toNewSpend(
    defaultApp: String,
    defaultPurpose: String,
    now: Long = System.currentTimeMillis()
): NewSpend {
    val preset = APP_PRESETS.firstOrNull { it.id == appPresetId }
        ?: APP_PRESETS.firstOrNull { it.displayName.equals(appName, ignoreCase = true) }
        ?: APP_PRESETS.firstOrNull { it.displayName.equals(defaultApp, ignoreCase = true) }
        ?: APP_PRESETS.last()
    val resolvedPurpose = PURPOSE_PRESETS.firstOrNull { it.equals(purpose, ignoreCase = true) }
        ?: PURPOSE_PRESETS.firstOrNull { it.equals(defaultPurpose, ignoreCase = true) }
        ?: "Others"
    val isLendBorrow = resolvedPurpose == "Lending" || resolvedPurpose == "Borrowing"
    return NewSpend(
        preset = preset,
        amount = amount ?: 0.0,
        purpose = resolvedPurpose,
        // Lending/Borrowing keep the person in the notes ("Person - note"), as everywhere else.
        notes = if (isLendBorrow) buildLendBorrowNotes(personName, notes) else notes.trim(),
        customAppName = if (preset.id == "other") (appName ?: "").trim() else "",
        timestamp = timestamp ?: now
    )
}

/**
 * The inverse, used after a log is edited in the full form (which hands back a [NewSpend]) so it
 * can be edited again and still show what the user last entered.
 */
fun NewSpend.toResponse(): AiTransactionResponse {
    val app = if (preset.id == "other") customAppName else preset.displayName
    val isLendBorrow = purpose == "Lending" || purpose == "Borrowing"
    val (person, detail) = if (isLendBorrow) splitLendBorrowNotes(notes) else "" to notes
    return AiTransactionResponse(
        amount = amount,
        appName = app,
        appPresetId = preset.id,
        purpose = purpose,
        notes = detail,
        personName = person,
        date = "",
        timestamp = timestamp,
        needsAmount = false
    )
}

/** Undoes [buildLendBorrowNotes]: "Person - note" → (Person, note); a lone value is the person. */
fun splitLendBorrowNotes(notes: String): Pair<String, String> {
    val trimmed = notes.trim()
    if (trimmed.contains(" - ")) {
        val (person, detail) = trimmed.split(" - ", limit = 2)
        return person.trim() to detail.trim()
    }
    return trimmed to ""
}

fun NewSpend.toDraft(uuid: String): SpendDraft = SpendDraft(
    uuid = uuid,
    appName = if (preset.id == "other") customAppName else preset.displayName,
    amount = amount,
    purpose = purpose,
    category = preset.category,
    notes = notes,
    timestamp = timestamp
)

/** A log can be saved once it has an amount (and a name, if it went to an unlisted app). */
fun NewSpend.isSavable(): Boolean =
    amount > 0.0 && (preset.id != "other" || customAppName.isNotBlank())
